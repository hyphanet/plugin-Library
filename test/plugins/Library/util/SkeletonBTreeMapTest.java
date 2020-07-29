/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import junit.framework.TestCase;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import plugins.Library.io.DataFormatException;
import plugins.Library.io.serial.IterableSerialiser;
import plugins.Library.io.serial.MapSerialiser;
import plugins.Library.io.serial.ScheduledSerialiser;
import plugins.Library.io.serial.Translator;
import plugins.Library.util.concurrent.Executors;
import plugins.Library.util.concurrent.ObjectProcessor;
import plugins.Library.util.exec.TaskAbortException;
import plugins.Library.util.func.Tuples.X2;

import static plugins.Library.util.func.Tuples.X2;

public class SkeletonBTreeMapTest extends TestCase {

	SkeletonBTreeMap<String, Integer> skelmap;
	String oneKey;

	private static long lastNumber = 0;

	private static synchronized Long getNextNumber() {
		return new Long(++lastNumber);
	}

	static class SkelMapNodeSerialiser<K, V>
			implements IterableSerialiser<SkeletonBTreeMap<K, V>.SkeletonNode>,
			ScheduledSerialiser<SkeletonBTreeMap<K, V>.SkeletonNode> {

		final private Map<Long, Object> store = Collections.synchronizedMap(new HashMap<Long, Object>());
		SkelMapMapSerialiser<K, V> mapSerialiser;
		
		Translator<SkeletonBTreeMap<K, V>, Map<String, Object>> ttrans = new
		SkeletonBTreeMap.TreeTranslator<K, V>(null, null);

		SkeletonBTreeMap<K, V>.NodeTranslator<K, Map<String, Object>> ntrans;

		Translator<SkeletonTreeMap<K, V>, Map<String, Object>> tmtrans = new SkeletonTreeMap.TreeMapTranslator<K, V>() {

			@Override
			public Map<String, Object> app(SkeletonTreeMap<K, V> translatee) {
				return app(translatee, new TreeMap<String, Object>(), null);
			}

			@Override
			public SkeletonTreeMap<K, V> rev(Map<String, Object> intermediate) throws DataFormatException {
				return rev(intermediate, new SkeletonTreeMap<K, V>(), null);
			}
		};

		SkelMapNodeSerialiser(SkeletonBTreeMap<K, V> skelmap, SkelMapMapSerialiser<K, V> ms) {
			mapSerialiser = ms;
			ntrans = skelmap.makeNodeTranslator(null, tmtrans);
		}

		@Override
		public void pull(PullTask<SkeletonBTreeMap<K, V>.SkeletonNode> task) throws TaskAbortException {
			assert task != null;
			assert task.meta != null;
			assert task.meta instanceof SkeletonBTreeMap.GhostNode;
			SkeletonBTreeMap<K, V>.GhostNode gn = (SkeletonBTreeMap<K, V>.GhostNode) task.meta;
			assert gn.meta instanceof Long;
			assert store.containsKey(gn.meta);
			Map<String, Object> map = (Map<String, Object>) store.get(gn.meta);
			SkeletonBTreeMap<K, V>.SkeletonNode node;
			try {
				node = ntrans.rev(map);
			} catch (DataFormatException e) {
				throw new TaskAbortException("Unpacking SkeletonNode", e);
			}
			task.data = node;
		}

		@Override
		public void push(PushTask<SkeletonBTreeMap<K, V>.SkeletonNode> task) throws TaskAbortException {
			assert task.data.isBare();
			Map<String, Object> map = ntrans.app(task.data);

			Long pos = getNextNumber();
			store.put(pos, map);
			task.meta = task.data.makeGhost(pos);
		}

		@Override
		public void pull(Iterable<PullTask<SkeletonBTreeMap<K, V>.SkeletonNode>> tasks) throws TaskAbortException {
			throw new TaskAbortException("NIY", new Throwable());
		}

		@Override
		public void push(Iterable<PushTask<SkeletonBTreeMap<K, V>.SkeletonNode>> tasks) throws TaskAbortException {
			throw new TaskAbortException("NIY", new Throwable());
		}

		@Override
		public <E> ObjectProcessor<PullTask<SkeletonBTreeMap<K, V>.SkeletonNode>, E, TaskAbortException> pullSchedule(
				BlockingQueue<PullTask<SkeletonBTreeMap<K, V>.SkeletonNode>> input,
				BlockingQueue<X2<PullTask<SkeletonBTreeMap<K, V>.SkeletonNode>, TaskAbortException>> output,
				Map<PullTask<SkeletonBTreeMap<K, V>.SkeletonNode>, E> deposit) {

			return new ObjectProcessor<PullTask<SkeletonBTreeMap<K, V>.SkeletonNode>, E, TaskAbortException>(input,
					output, deposit, null, Executors.DEFAULT_EXECUTOR, new TaskAbortExceptionConvertor()) {
				@Override
				protected Runnable createJobFor(final PullTask<SkeletonBTreeMap<K, V>.SkeletonNode> task) {
					return new Runnable() {
						@Override
						public void run() {
							TaskAbortException ex = null;
							try {
								pull(task);
							} catch (TaskAbortException e) {
								ex = e;
							} catch (RuntimeException e) {
								ex = new TaskAbortException("pull failed", e);
							}
							postProcess.invoke(X2(task, ex));
						}
					};
				}
			}.autostart();

		}

		@Override
		public <E> ObjectProcessor<PushTask<SkeletonBTreeMap<K, V>.SkeletonNode>, E, TaskAbortException> pushSchedule(
				BlockingQueue<PushTask<SkeletonBTreeMap<K, V>.SkeletonNode>> input,
				BlockingQueue<X2<PushTask<SkeletonBTreeMap<K, V>.SkeletonNode>, TaskAbortException>> output,
				Map<PushTask<SkeletonBTreeMap<K, V>.SkeletonNode>, E> deposit) {
			ObjectProcessor<PushTask<SkeletonBTreeMap<K, V>.SkeletonNode>, E, TaskAbortException> objectProcessor = new ObjectProcessor<PushTask<SkeletonBTreeMap<K, V>.SkeletonNode>, E, TaskAbortException>(
					input, output, deposit, null, Executors.DEFAULT_EXECUTOR, new TaskAbortExceptionConvertor()) {
				@Override
				protected Runnable createJobFor(final PushTask<SkeletonBTreeMap<K, V>.SkeletonNode> task) {
					return new Runnable() {
						@Override
						public void run() {
							// Simulate push.
							TaskAbortException ex = null;
							try {
								push(task);
							} catch (TaskAbortException e) {
								ex = e;
							} catch (RuntimeException e) {
								ex = new TaskAbortException("push failed", e);
							}
							postProcess.invoke(X2(task, ex));
						}
					};
				}
			};
			return objectProcessor.autostart();
		}

	}

	static class SkelMapMapSerialiser<K, V> implements MapSerialiser<K, V> {
		final private Map<Long, Object> store = Collections.synchronizedMap(new HashMap<Long, Object>());

		@Override
		public void pull(Map<K, PullTask<V>> tasks, Object mapmeta) throws TaskAbortException {
			for (Map.Entry<K, PullTask<V>> en : tasks.entrySet()) {
				en.getValue().data = ((Map<K, V>) store.get(en.getValue().meta)).get(en.getKey());
			}
		}

		@Override
		public void push(Map<K, PushTask<V>> tasks, Object mapmeta) throws TaskAbortException {
			Map<K, V> map = new HashMap<K, V>();
			for (Map.Entry<K, PushTask<V>> en : tasks.entrySet()) {
				map.put(en.getKey(), en.getValue().data);
			}
			Long pos = getNextNumber();
			store.put(pos, map);
			for (Map.Entry<K, PushTask<V>> en : tasks.entrySet()) {
				en.getValue().meta = pos;
			}
		}

	}

	private static String rndStr() {
		return UUID.randomUUID().toString();
	}

	private static String rndKey() {
		return rndStr().substring(0,8);
	}

	protected void setUp() throws TaskAbortException {
		skelmap = new SkeletonBTreeMap<String, Integer>(2);
		SkelMapMapSerialiser<String, Integer> mapSerialiser = new SkelMapMapSerialiser<String, Integer>();
		skelmap.setSerialiser(new SkelMapNodeSerialiser<String, Integer>(skelmap, mapSerialiser), mapSerialiser);
		assertTrue(skelmap.isBare());
	}

	private void add(int count, int laps) throws TaskAbortException {
		int calculatedSize = skelmap.size();
		for (int l = 0; l < laps; ++l) {
			SortedMap<String, Integer> map = new TreeMap<String, Integer>();
			for (int i = 0; i < count; ++i) {
				String key = rndKey();
				map.put(key, i);
				oneKey = key;
			}
			skelmap.update(map, new TreeSet<String>());
			calculatedSize += count;
			assertTrue(skelmap.isBare());
			assertEquals(calculatedSize, skelmap.size());
		}
	}

	public void testSetup() {
		assertTrue(true);
	}

	public void test1() throws TaskAbortException {
		add(1, 1);
	}

	public void test3() throws TaskAbortException {
		add(3, 1);
	}

	public void test4() throws TaskAbortException {
		add(4, 1);
	}

	public void test10() throws TaskAbortException {
		add(10, 1);
	}

	public void test100() throws TaskAbortException {
		add(100, 1);
	}

	public void BIGtest1000() throws TaskAbortException {
		add(1000, 1);
	}

	public void BIGtest10000() throws TaskAbortException {
		add(10000, 1);
	}

	public void test1x3() throws TaskAbortException {
		add(1, 3);
	}

	public void test1x4() throws TaskAbortException {
		add(1, 4);
	}

	public void test1x5() throws TaskAbortException {
		add(1, 5);
	}

	public void test6x5() throws TaskAbortException {
		add(6, 5);
	}

	public void test10x5() throws TaskAbortException {
		add(10, 5);
	}

	public void BIGtest10x50() throws TaskAbortException {
		add(10, 50);
	}
}
