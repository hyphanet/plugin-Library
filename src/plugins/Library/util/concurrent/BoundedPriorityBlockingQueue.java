package plugins.Library.util.concurrent;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BoundedPriorityBlockingQueue<E> extends PriorityBlockingQueue<E> {
    
    int maxSize;

    /* Locking strategy:
     * We do not have access to the parent's lock object.
     * We need to be able to wait for the queue to become non-full, meaning we need to be able to signal it.
     * We need to be able to do so reliably i.e. we need a lock surrounding it.
     * We could do this with a Lock and a Condition, but the easiest solution is just to lock on an Object.
     * Because callers might expect us to use an internal lock, we'll use a separate object.
     */
    protected final Object fullLock = new Object();
    
    public BoundedPriorityBlockingQueue(int capacity) {
        super(capacity);
        this.maxSize = capacity;
    }
    
    public BoundedPriorityBlockingQueue(int capacity, Comparator<E> comp) {
        super(capacity, comp);
        this.maxSize = capacity;
    }
    
    // add() calls offer()
    // put() calls offer()
    
    @Override
    public void put(E o) {
        synchronized(fullLock) {
            if(super.size() < maxSize) {
                super.offer(o);
                return;
            }
            // We have to wait.
            while(true) {
                try {
                    fullLock.wait();
                } catch (InterruptedException e) {
                    // Ignore.
                    // PriorityBlockingQueue.offer doesn't throw it so we can't throw it.
                }
                if(super.size() < maxSize) {
                    super.offer(o);
                    return;
                }
            }
        }
    }
    
    @Override
    public boolean offer(E o, long timeout, TimeUnit unit) {
        synchronized(fullLock) {
            if(super.size() < maxSize) {
                super.offer(o);
                return true;
            }
            long waitTime = unit.toMillis(timeout);
            if(waitTime <= 0) waitTime = 0;
            long now = System.currentTimeMillis();
            // We have to wait.
            long end = now + waitTime;
            while(now < end) {
                try {
                    fullLock.wait((int)(Math.min(Integer.MAX_VALUE, end - now)));
                } catch (InterruptedException e) {
                    // Ignore.
                    // PriorityBlockingQueue.offer doesn't throw it so we can't throw it.
                }
                if(super.size() < maxSize) {
                    super.offer(o);
                    return true;
                }
                now = System.currentTimeMillis();
            }
            return false;
        }
    }
    
    @Override
    public boolean offer(E o) {
        synchronized(fullLock) {
            if(super.size() < maxSize) {
                super.offer(o);
                return true;
            } else return false;
        }
    }
    
    public E poll() {
        E o = super.poll();
        if(o == null) return null;
        synchronized(fullLock) {
            fullLock.notifyAll();
        }
        return o;
    }
    
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E o = super.poll(timeout, unit);
        if(o == null) return null;
        synchronized(fullLock) {
            fullLock.notifyAll();
        }
        return o;
    }
    
    public E take() throws InterruptedException {
        E o = super.take();
        synchronized(fullLock) {
            fullLock.notifyAll();
        }
        return o;
    }
    
    // peek() is safe.
    // size() is safe.
    
    public int remainingCapacity() {
        synchronized(fullLock) {
            return maxSize - size();
        }
    }
    
    public boolean remove(Object o) {
        if(super.remove(o)) {
            synchronized(fullLock) {
                fullLock.notifyAll();
            }
            return true;
        } else return false;
    }
    
    // contains() is safe
    // toArray() is safe
    // toString() is safe
    
    public int drainTo(Collection<? super E> c) {
        int moved = super.drainTo(c);
        if(moved > 0) {
            synchronized(fullLock) {
                fullLock.notifyAll();
            }
        }
        return moved;
    }
    
    public int drainTo(Collection<? super E> c, int maxElements) {
        int moved = super.drainTo(c, maxElements);
        if(moved > 0) {
            synchronized(fullLock) {
                fullLock.notifyAll();
            }
        }
        return moved;
    }
    
    public void clear() {
        super.clear();
        synchronized(fullLock) {
            fullLock.notifyAll();
        }
    }
    
    public Iterator<E> iterator() {
        final Iterator<E> underlying = super.iterator();
        return new Iterator<E>() {

            public boolean hasNext() {
                return underlying.hasNext();
            }

            public E next() {
                return underlying.next();
            }

            public void remove() {
                underlying.remove();
                synchronized(fullLock) {
                    fullLock.notifyAll();
                }
            }
            
        };
    }
    
    // writeObject() is safe

}
