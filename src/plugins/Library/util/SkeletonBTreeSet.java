/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.serial.Archiver;
import plugins.Library.serial.MapSerialiser;

import java.util.Comparator;

/**
** {@link Skeleton} of a {@link BTreeSet}. DOCUMENT
**
** @author infinity0
*/
public class SkeletonBTreeSet<E> extends BTreeSet<E> /*implements Skeleton<E>*/ {


	// TODO maybe make this write-once
	public void setSerialiser(Archiver<SkeletonBTreeMap<E, E>.SkeletonNode> n, MapSerialiser<E, E> v) {
		((SkeletonBTreeMap)map).setSerialiser(n, v);
	}

	public SkeletonBTreeSet(Comparator<? super E> cmp, int node_min) {
		super(new SkeletonBTreeMap<E, E>(cmp, node_min));
	}

	public SkeletonBTreeSet(int node_min) {
		super(new SkeletonBTreeMap<E, E>(node_min));
	}

	public SkeletonBTreeSet() {
		super(new SkeletonBTreeMap<E, E>());
	}




}
