/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.serial.Serialiser.*;
import plugins.Library.serial.IterableSerialiser;
import plugins.Library.serial.MapSerialiser;
import plugins.Library.serial.Translator;
import plugins.Library.serial.TaskAbortException;

import java.util.Comparator;
import java.util.Collection;
import java.util.Map;
import java.util.ArrayList;

/**
** {@link Skeleton} of a {@link BTreeSet}. DOCUMENT
**
** TODO make this implement Skeleton<Pair<Integer, Integer>, ?> ? - ie.
** deflate(int, int) to get the relevant subset?
**
** @author infinity0
*/
public class SkeletonBTreeSet<E> extends BTreeSet<E> /*implements Skeleton<E, MapSerialiser<E, E>>*/ {

	// TODO when we implement internal_entries in SkeletonBTreeMap, then
	// we can have it automatically set to TRUE here.

	public void setSerialiser(IterableSerialiser<SkeletonBTreeMap<E, E>.SkeletonNode> n, MapSerialiser<E, E> v) {
		((SkeletonBTreeMap<E, E>)bkmap).setSerialiser(n, v);
	}

	public SkeletonBTreeSet(Comparator<? super E> cmp, int node_min) {
		super(new SkeletonBTreeMap<E, E>(cmp, node_min));
	}

	public SkeletonBTreeSet(int node_min) {
		super(new SkeletonBTreeMap<E, E>(node_min));
	}

	protected SkeletonBTreeSet(SkeletonBTreeMap<E, E> m) {
		super(m);
	}

	/*========================================================================
	  public interface Skeleton TODO actually implement this...
	 ========================================================================*/

	public boolean isBare() {
		return ((SkeletonBTreeMap<E, E>)bkmap).isBare();
	}

	public boolean isLive() {
		return ((SkeletonBTreeMap<E, E>)bkmap).isLive();
	}

	public void deflate() throws TaskAbortException {
		((SkeletonBTreeMap<E, E>)bkmap).deflate();
	}

	public void inflate() throws TaskAbortException {
		((SkeletonBTreeMap<E, E>)bkmap).inflate();
	}

	// URGENT tidy this - see SkeletonBTreeMap.inflate() for details
	public plugins.Library.serial.CompoundProgress getPPP() { return ((SkeletonBTreeMap<E, E>)bkmap).ppp; }

	/**
	** Creates a translator for the nodes of the B-tree. This method just calls
	** the {@link SkeletonBTreeMap#makeNodeTranslator(Translator, Translator)
	** corresponding method} for the map backing this set.
	**
	** @param ktr Translator for the keys
	** @param mtr Translator for each node's local entries map
	*/
	public <Q, R> SkeletonBTreeMap<E, E>.NodeTranslator<Q, R> makeNodeTranslator(Translator<E, Q> ktr, Translator<SkeletonTreeMap<E, E>, R> mtr) {
		return ((SkeletonBTreeMap<E, E>)bkmap).makeNodeTranslator(ktr, mtr);
	}


	/************************************************************************
	** {@link Translator} between a {@link Collection} and a {@link Map} of
	** keys to themselves. Used by {@link TreeSetTranslator}.
	**
	** @author infinity0
	*/
	abstract public static class MapCollectionTranslator<E, M extends Map<E, E>, C extends Collection<E>>
	implements Translator<M, C> {

		public static <E, M extends Map<E, E>, C extends Collection<E>> C app(M src, C dst) {
			for (E k: src.keySet()) { dst.add(k); }
			return dst;
		}

		public static <E, M extends Map<E, E>, C extends Collection<E>> M rev(C src, M dst) {
			for (E e: src) { dst.put(e, e); }
			return dst;
		}

	}


	/************************************************************************
	** Translator for the {@link java.util.TreeMap} backing a {@link
	** java.util.TreeSet}. This will turn a map into a list and vice versa,
	** so that the serialised representation doesn't store duplicates.
	**
	** maybe this belongs in a SkeletonTreeSet? if that's ever implemented..
	**
	** @author infinity0
	*/
	public static class TreeSetTranslator<E>
	extends MapCollectionTranslator<E, SkeletonTreeMap<E, E>, Collection<E>> {

		public Collection<E> app(SkeletonTreeMap<E, E> src) {
			Collection<E> dst = new ArrayList<E>(src.size());
			app(src, dst);
			return dst;
		}

		public SkeletonTreeMap<E, E> rev(Collection<E> src) {
			// TODO maybe make it use a comparator that can be passed into the
			// constructor
			SkeletonTreeMap<E, E> dst = new SkeletonTreeMap<E, E>();
			rev(src, dst);
			return dst;
		}

	}


	/************************************************************************
	** {@link Translator} with access to the members of {@link BTreeSet}.
	**
	** This implementation just serialises the map backing the set.
	**
	** @author infinity0
	*/
	public static class TreeTranslator<E, T> implements Translator<SkeletonBTreeSet<E>, Map<String, Object>> {

		final SkeletonBTreeMap.TreeTranslator<E, E> trans;

		public TreeTranslator(Translator<E, T> k, Translator<SkeletonTreeMap<E, E>, ? extends Collection<T>> m) {
			trans = new SkeletonBTreeMap.TreeTranslator<E, E>(k, m);
		}

		@Override public Map<String, Object> app(SkeletonBTreeSet<E> tree) {
			return trans.app((SkeletonBTreeMap<E, E>)tree.bkmap);
		}

		@Override public SkeletonBTreeSet<E> rev(Map<String, Object> tree) {
			return new SkeletonBTreeSet<E>(trans.rev(tree));
		}

	}

}
