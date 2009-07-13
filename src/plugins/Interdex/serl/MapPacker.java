/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

import plugins.Interdex.serl.Serialiser.*;
import plugins.Interdex.util.IdentityComparator;

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

	final protected static Comparator<Map> BinElementComparator = new IdentityComparator<Map>() {
		public int compare(Map c1, Map c2) {
			if (c1 == c2) { return 0; }
			int d = c1.size() - c2.size();
			if (d != 0) { return (d < 0)? 1: -1; }
			return super.compare(c1, c2);
		}
	};

	public MapPacker(IterableSerialiser<Map<K, T>> s, int c, Class<? extends T> cc) {
		super(s, c, BinElementComparator, cc);
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

	/**
	** {@inheritDoc}
	**
	** This implementation also keeps track of the first key of the element.
	*/
	@Override protected void addBinToMeta(Map<String, Object> meta, T partition, int binindex) {
		if (!meta.containsKey("keys")) { meta.put("keys", new ArrayList()); }
		Iterator it = partition.keySet().iterator();
		((List)meta.get("keys")).add(it.hasNext()? it.next(): null);
	}

}
