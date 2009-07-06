/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;

import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Comparator;
import java.util.ArrayList;

/**
** A {@link Packer} of {@link Map}s. It keeps track of the first keys of each
** partition. (TODO maybe explicitly make this use SortedMap and firstKey()?)
**
** @author infinity0
*/
public class MapPacker<K, T extends Map>
extends Packer<K, T>
implements MapSerialiser<K, T> {

	final protected static Comparator<Map> BinElementComparator = new Comparator<Map>() {
		public int compare(Map c1, Map c2) {
			if (c1 == c2) { return 0; }
			int d = c2.size() - c1.size();
			// this is a bit of a hack but is needed since Tree* treats two objects
			// as "equal" if their "compare" returns 0
			if (d != 0) { return d; }
			int h = c2.hashCode() - c1.hashCode();
			// on the off chance that the hashCodes are equal but the objects are not,
			// test the string representations of them...
			return (h != 0)? h: (c2.equals(c1))? 0: c2.toString().compareTo(c1.toString());
		}
	};

	public MapPacker(int c, Class<? extends T> cc, IterableSerialiser<Map<K, T>> s) {
		super(c, BinElementComparator, cc, s);
	}

	/*========================================================================
	  abstract public class Packer
	 ========================================================================*/

	@Override protected T newPartitionOf(Iterator it, int max) {
		T el = newElement();
		for (int j=0; j<max; ++j) {
			Map.Entry en = (Map.Entry)it.next();
			el.put(en.getKey(), en.getValue());
		}
		return el;
	}

	@Override protected void addPartitionTo(T element, T partition) {
		// for some reason java does not realise Map.entrySet() is Set<Map.Entry>
		for (Map.Entry en: (java.util.Set<Map.Entry>)partition.entrySet()) {
			element.put(en.getKey(), en.getValue());
		}
	}

	@Override protected Iterable iterableOf(T element) {
		return element.entrySet();
	}

	@Override protected int sizeOf(T element) {
		return element.size();
	}

	@Override protected void addBinToMeta(Map<String, Object> meta, T partition, int binindex) {
		if (!meta.containsKey("bins")) { meta.put("bins", new ArrayList<Integer>()); }
		if (!meta.containsKey("keys")) { meta.put("keys", new ArrayList()); }
		((List)meta.get("bins")).add(binindex);
		Iterator it = partition.keySet().iterator();
		((List)meta.get("keys")).add(it.hasNext()? it.next(): null);
	}

	@Override protected List<Integer> getBinsFromMeta(Map<String, Object> meta) {
		// some archivers will push a list as an array
		Object list = meta.get("bins");
		return (list instanceof Integer[])? java.util.Arrays.asList((Integer[])list): (List<Integer>)list;
	}

}
