/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.util.concurrent;

import static plugins.Library.util.func.Tuples.X2;
import static plugins.Library.util.func.Tuples.X3;

import java.lang.ref.WeakReference;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import plugins.Library.util.func.Closure;
import plugins.Library.util.func.SafeClosure;

import freenet.support.Logger;

/**
 * A class that wraps around an {@link Executor}, for processing any given type
 * of object, not just {@link Runnable}. Each object must be accompanied by a
 * secondary "deposit" object, which is returned with the object when it has
 * been processed. Any exceptions thrown are also returned.
 *
 * @param <T> Type of object to be processed
 * @param <E> Type of object to be used as a deposit
 * @param <X> Type of exception thrown by {@link #clo}
 * @author infinity0
 */
public class ObjectProcessor<T, E, X extends Exception> implements Scheduler {
    final protected BlockingQueue<T>        in;
    final protected BlockingQueue<X2<T, X>> out;
    final protected Map<T, E>               dep;
    final protected Closure<T, X>           clo;
    final protected Executor                exec;
    final protected ExceptionConvertor<X>   convertor;

    // Allows a group of ObjectProcessor's to all notify the
    // same object so that the caller doesn't have to poll.
    protected Notifier              notifier;
    protected volatile boolean      open       = true;
    protected int                   dispatched = 0;
    protected int                   completed  = 0;
    protected int                   started    = 0;
    private static volatile boolean logMINOR;
    private static volatile boolean logDEBUG;

    static {
        Logger.registerClass(ObjectProcessor.class);
    }

    // Most ObjectProcessor's are likely to be autostart()'ed, and this way we can
    // still use ConcurrentMap for pending while having garbage collection.
    private final WeakReference<ObjectProcessor> myRef = new WeakReference<ObjectProcessor>(this);

    // TODO NORM make a more intelligent way of adjusting this
    final public static int               default_maxconc = 0x28;
    int                                   maxconc         = default_maxconc;
    final protected SafeClosure<X2<T, X>> postProcess     = new SafeClosure<X2<T, X>>() {
        /* @Override* */
        public void invoke(X2<T, X> res) {
            try {
                out.put(res);

                if (notifier != null) {
                    notifier.notifyUpdate();
                }

                synchronized (ObjectProcessor.this) {
                    ++completed;
                }
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException();
            }
        }
    };

    public int outputCapacity() {
        return out.remainingCapacity();
    }

    public int outputSize() {
        return out.size();
    }

    final private static ConcurrentMap<WeakReference<ObjectProcessor>, Boolean> pending =
        new ConcurrentHashMap<WeakReference<ObjectProcessor>, Boolean>();

    // This must only be modified in a static synchronized block
    private static Thread auto = null;

    /**
     * Constructs a new processor. The processor itself will be thread-safe
     * as long as the queues and deposit map are not exposed to other threads,
     * and the closure's invoke method is also thread-safe.
     *
     * If the {@code closure} parameter is {@code null}, it is expected that
     * {@link #createJobFor(Object)} will be overridden appropriately.
     *
     * @param input Queue for input items
     * @param output Queue for output/error items
     * @param deposit Map for item deposits
     * @param closure Closure to call on each item
     * @param executor Executor to run each closure call
     * @param autostart Whether to start an {@link #auto()} autohandler
     * @param notifier A Notifier to tell whenever something has happened - e.g. results are ready.
     */
    public ObjectProcessor(BlockingQueue<T> input, BlockingQueue<X2<T, X>> output,
                           Map<T, E> deposit, Closure<T, X> closure, Executor executor,
                           ExceptionConvertor<X> conv, Notifier n) {
        in        = input;
        out       = output;
        dep       = deposit;
        clo       = closure;
        exec      = executor;
        convertor = conv;
        notifier  = n;
    }

    public ObjectProcessor(BlockingQueue<T> input, BlockingQueue<X2<T, X>> output,
                           Map<T, E> deposit, Closure<T, X> closure, Executor executor,
                           ExceptionConvertor<X> conv) {
        this(input, output, deposit, closure, executor, conv, null);
    }

    // FIXME should notifier be volatile? If we set it before starting, maybe that's not necessary?
    public void setNotifier(Notifier n) {
        this.notifier = n;
    }

    public synchronized void setMaxConc(int x) {
        maxconc = x;
    }

    /**
     * Safely submits the given item and deposit to the given processer. Only
     * use this when the input queue's {@link BlockingQueue#put(Object)} method
     * does not throw {@link InterruptedException}, such as that of {@link
     * java.util.concurrent.PriorityBlockingQueue}.
     */
    public static <T, E, X extends Exception> void submitSafe(ObjectProcessor<T, E, X> proc,
            T item, E deposit) {
        try {
            proc.submit(item, deposit);
        } catch (InterruptedException e) {
            throw new IllegalArgumentException(
                "ObjectProcessor: abuse of submitSafe(). Blame the programmer," +
                " who did not know what they were doing", e);
        }
    }

    /**
     * Submits an item for processing, with the given deposit.
     *
     * @throws IllegalStateException if the processor has already been {@link
     *         #close() closed}
     * @throws IllegalArgumentException if the item is already being held
     */
    public void submit(T item, E deposit) throws InterruptedException {
        synchronized (this) {
            if ( !open) {
                throw new IllegalStateException("ObjectProcessor: not open");
            }

            if (dep.containsKey(item)) {
                throw new IllegalArgumentException("ObjectProcessor: object " + item +
                        " already submitted");
            }

            dep.put(item, deposit);
        }

        // in.put() can block. Don't hold the outer lock during pushing.
        // Note that this can result in more stuff being in dep than is in in.
        // This is okay, assuming that
        // we don't have an infinite number of calling threads.
        in.put(item);
    }

    /**
     * Updates the deposit for a given item.
     *
     * @throws IllegalStateException if the processor has already been {@link
     *         #close() closed}
     * @throws IllegalArgumentException if the item is not currently being held
     */
    public synchronized void update(T item, E deposit) {
        if ( !open) {
            throw new IllegalStateException("ObjectProcessor: not open");
        }

        if ( !dep.containsKey(item)) {
            throw new IllegalArgumentException("ObjectProcessor: object " + item +
                    " not yet submitted");
        }

        dep.put(item, deposit);
    }

    /**
     * Retrieved a processed item, along with its deposit and any exception
     * that caused processing to abort.
     */
    public X3<T, E, X> accept() throws InterruptedException {
        X2<T, X> item = out.take();

        // DO NOT hold the lock while blocking on out.
        synchronized (this) {
            return X3(item._0, dep.remove(item._0), item._1);
        }
    }

    /**
     * Whether there are any unprocessed items (including completed tasks not
     * yet retrieved by the submitter).
     */
    public synchronized boolean hasPending() {
        return !dep.isEmpty();
    }

    /**
     * Whether there are any completed items that have not yet been retrieved.
     */
    public synchronized boolean hasCompleted() {
        return !out.isEmpty();
    }

    /**
     * Number of unprocessed tasks.
     */
    public synchronized int size() {
        return dep.size();
    }

    /**
     * Retrieves an item by calling {@link BlockingQueue#take()} on the input
     * queue. If this succeeds, a job is {@linkplain #createJobFor(Object)
     * created} for it, and sent to {@link #exec} to be executed.
     *
     * This method is provided for completeness, in case anyone needs it;
     * {@link #auto()} should be adequate for most purposes.
     *
     * @throws InterruptedExeception if interrupted whilst waiting
     */
    public synchronized void dispatchTake() throws InterruptedException {
        throw new UnsupportedOperationException("not implemented");

        /*
         * TODO NORM first needs a way of obeying maxconc
         * T item = in.take();
         * exec.execute(createJobFor(item));
         * ++dispatched;
         */
    }

    /**
     * Retrieves an item by calling {@link BlockingQueue#poll()} on the input
     * queue. If this succeeds, a job is {@linkplain #createJobFor(Object)
     * created} for it, and sent to {@link #exec} to be executed.
     *
     * This method is provided for completeness, in case anyone needs it;
     * {@link #auto()} should be adequate for most purposes.
     *
     * @return Whether a task was retrieved and executed
     */
    public boolean dispatchPoll() {
        synchronized (this) {
            if (dispatched - completed >= maxconc) {
                return false;
            }
        }

        // DO NOT hold the lock while blocking on in.
        T item = in.poll();

        synchronized (this) {
            if (item == null) {
                return false;
            }

            exec.execute(createJobFor(item));
            ++dispatched;

            return true;
        }
    }

    /**
     * Creates a {@link Runnable} to process the item and push it onto the
     * output queue, along with any exception that aborted the process.
     *
     * The default implementation invokes {@link #clo} on the item, and then
     * adds the appropriate data onto the output queue.
     */
    protected Runnable createJobFor(final T item) {
        if (clo == null) {
            throw new IllegalStateException(
                "ObjectProcessor: no closure given, but createJobFor() was not overidden");
        }

        return new Runnable() {
            /* @Override* */
            public void run() {
                X ex = null;

                synchronized (ObjectProcessor.this) {
                    ++started;
                }

                RuntimeException ee = null;

                try {
                    clo.invoke(item);
                }

                // FIXME NORM this could throw RuntimeException
                catch (RuntimeException e) {
                    Logger.error(this, "Caught " + e, e);
                    System.err.println("In ObjProc-" + name + " : " + e);
                    e.printStackTrace();
                    ex = convertor.convert(e);
                } catch (Exception e) {
                    ex = (X) e;
                }

                postProcess.invoke(X2(item, ex));
            }
        };
    }

    /**
     * Start a new thread to run the {@link #pending} processors, if one is not
     * already running.
     */
    private static synchronized void ensureAutoHandler() {
        if (auto != null) {
            return;
        }

        auto = new Thread() {
            @Override
            public void run() {
                final int timeout = 4;
                int       t       = timeout;

                while (( !pending.isEmpty() && (t = timeout) == timeout) || (t-- > 0)) {
                    for (Iterator<WeakReference<ObjectProcessor>> it = pending.keySet().iterator();
                            it.hasNext(); ) {
                        WeakReference<ObjectProcessor> ref  = it.next();
                        ObjectProcessor                proc = ref.get();

                        if (proc == null) {
                            it.remove();

                            continue;
                        }

                        try {
                            boolean o = proc.open;

                            while (proc.dispatchPoll());

                            if ( !o) {
                                it.remove();
                            }
                        } catch (RejectedExecutionException e) {

                            // FIXME NORM
                            // neither Executors.DEFAULT_EXECUTOR nor Freenet's in-built executors
                            // throw this, so this is not a high priority
                            System.out.println("REJECTED EXECUTION" + e);
                        }
                    }

                    try {

                        // FIXME why do we sleep here at all?
                        // FIXME for small jobs, such as merging to disk in SpiderIndexUploader,
                        // this can dominate the execution time!
                        // FIXME the executors are perfectly capable of limiting the number of
                        // threads running...
                        Thread.sleep(100);
                    } catch (InterruptedException e) {

                        // TODO LOW log this somewhere
                    }

                    // System.out.println("pending " + pending.size());
                    if (t > 0) {
                        continue;
                    }

                    synchronized (ObjectProcessor.class) {

                        // if auto() was called just before we entered this synchronized block,
                        // then its ensureAutoHandler() would have done nothing. so we want to keep
                        // this thread running to take care of the new addition.
                        if ( !pending.isEmpty()) {
                            continue;
                        }

                        // otherwise we can safely discard this thread, since ensureAutoHandler()
                        // cannot be called as long as we are in this block.
                        auto = null;

                        return;
                    }
                }

                throw new AssertionError(
                    "ObjectProcessor: bad exit in autohandler. this is a bug; please report.");
            }
        };
        auto.start();
    }

    /**
     * Add this processor to the collection of {@link #pending} processes, and
     * makes sure there is a thread to handle them.
     *
     * @return Whether the processor was not already being handled.
     */
    public boolean auto() {
        Boolean r = ObjectProcessor.pending.put(myRef, Boolean.TRUE);

        ObjectProcessor.ensureAutoHandler();

        return r == null;
    }

    /**
     * Call {@link #auto()} and return {@code this}.
     */
    public ObjectProcessor autostart() {
        auto();

        return this;
    }

    /**
     * Stop accepting new submissions or deposit updates. Held items can still
     * be processed and retrieved, and if an {@linkplain #auto() auto-handler}
     * is running, it will run until all such items have been processed.
     */
    /* @Override* */
    public void close() {
        open = false;
    }

    // public class Object

    /**
    ** {@inheritDoc}
    **
    ** This implementation just calls {@link #close()}.
    */
    @Override
    public void finalize() {
        close();
    }

    protected String name;

    public void setName(String n) {
        name = n;
    }

    @Override
    public String toString() {
        return "ObjProc-" + name + ":{" + size() + "|" + dispatched + "|" + started + "|" +
               completed + "}";
    }
}
