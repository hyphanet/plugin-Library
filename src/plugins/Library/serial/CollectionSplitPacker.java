/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.util.IdentityComparator;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Comparator;
import java.util.ArrayList;

/**
** A {@link SplitPacker} of {@link Collection}s.
**
** @author infinity0
*/
public class CollectionSplitPacker<K, T extends Collection>
extends SplitPacker<K, T>
implements MapSerialiser<K, T> {

	final protected static Comparator<Collection> BinElementComparator = new IdentityComparator<Collection>() {
		public int compare(Collection c1, Collection c2) {
			if (c1 == c2) { return 0; }
			int d = c1.size() - c2.size();
			if (d != 0) { return (d < 0)? 1: -1; }
			return super.compare(c1, c2);
		}
	};

	public CollectionSplitPacker(IterableSerialiser<Map<K, T>> s, int c, Class<? extends T> cc) {
		super(s, c, BinElementComparator, cc);
	}

	/*========================================================================
	  abstract public class SplitPacker
	 ========================================================================*/

	@Override protected T newPartitionOf(Iterator it, int max) {
		T el = newElement();
		for (int j=0; j<max; ++j) {
			el.add(it.next());
		}
		return el;
	}

	@Override protected void addPartitionTo(T element, T partition) {
		for (Object t: partition) {
			element.add(t);
		}
	}

	@Override protected Iterable iterableOf(T element) {
		return element;
	}

	@Override protected int sizeOf(T element) {
		return element.size();
	}

}
