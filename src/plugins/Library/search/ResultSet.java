/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search;

import freenet.support.Logger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import plugins.Library.index.Request;
import plugins.Library.index.TermPageEntry;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import plugins.Library.index.TermEntry;
import plugins.Library.index.TermIndexEntry;
import plugins.Library.index.TermTermEntry;
import plugins.Library.serial.TaskAbortException;

/**
 * Unmodifiable Set which makes sure all data in results being combined is
 * retained and is created as the result of Set operations on other Sets. Does
 * not contain references to the original Sets.
 *
 * FIXME get phrase search to work
 * @author MikeB
 */
public class ResultSet implements Set<TermEntry>{


	/**
	 * What should be done with the results of subsearches\n
	 * INTERSECTION : ( >2 subRequests) result is common results of subsearches\n
	 * UNION : ( >2 subRequests) result is all results of subsearches\n
	 * REMOVE : ( 2 subRequests) result is the results of the first subsearch with the second search removed
	 * PHRASE : ( >2 subRequests) For a phrase, the subsearches are in the order of the words in a phrase
	 * SINGLE : ( 1 subRequests) To encapsulate a single request
	 * DIFFERENTINDEXES : ( >2 subRequests) for a serch made up of requests on multiple indexes
	 */
	public enum ResultOperation{INTERSECTION, UNION, REMOVE, PHRASE, SINGLE, DIFFERENTINDEXES};

	private HashMap<TermEntry, TermEntry> internal;
	private final String subject;

	/**
	 * @param subject for each of the entries in the Set
	 * @param resultOperation to be performed on each of the given Sets {@see ResultOperation}
	 * @param subRequets List of subrequests to test each getResult()
	 * @throws plugins.Library.serial.TaskAbortException if thrown from a higher getResult() method
	 * @throws plugins.Library.search.IllegalArgumentException if the number of subRequests is not acceptable {@see ResultOperation}
	 */
	ResultSet(String subject, ResultOperation resultOperation, List<Request<Set<TermEntry>>> subRequests) throws TaskAbortException {
		if(resultOperation==ResultOperation.SINGLE && subRequests.size()!=1)
			throw new IllegalArgumentException(subRequests.size() + " requests supplied with SINGLE operation");
		if(resultOperation==ResultOperation.REMOVE && subRequests.size()!=2)
			throw new IllegalArgumentException("Negative operations can only have 2 parameters");
		if(		(	resultOperation==ResultOperation.PHRASE
					|| resultOperation == ResultOperation.INTERSECTION
					|| resultOperation == ResultOperation.UNION
					|| resultOperation == ResultOperation.DIFFERENTINDEXES )
				&& subRequests.size()<2)
			throw new IllegalArgumentException(resultOperation.toString() + " operations need more than one term");

		this.subject = subject;
		internal = new HashMap();


		switch(resultOperation){
			case SINGLE:	// Just add everything
				addAllToEmptyInternal(subRequests.get(0).getResult());
				break;
			case DIFFERENTINDEXES:	// Same as UNION currently
			case UNION:	// Add every one without overwriting
				for(Request<Set<TermEntry>> r : subRequests)
					if(r.getResult()==null)
						Logger.error(this, "the result of "+r +" was null");
					else
						addAllInternal(r.getResult());
				break;
			case INTERSECTION:	// Add one then retain the others
				intersect(getResultSets(subRequests));
				break;
			case REMOVE:	// Add one then remove the other
				addAllInternal((Set<TermEntry>)subRequests.get(0).getResult());
				removeAllInternal((Set<TermEntry>)subRequests.get(1).getResult());
				break;
//			case PHRASE:	// ?? TODO
//				Logger.minor(this, "Getting results for phrase");
//				it = subRequests.iterator();
//				r = it.next();
//				addAllToEmptyInternal(r.getResult());
//				while (it.hasNext()) {
//					r = it.next();
//					retainAllFollowedInternal(r.getResult());
//				}
//				break;
		}
	}

	/**
	 * Copy a collection into a ResultSet
	 * @param subject to change the subject of each entry
	 * @param copy Collection to copy
	 */
	private ResultSet(String subject, Collection<? extends TermEntry> copy) {
		this.subject = subject;
		internal = new HashMap();
		addAllToEmptyInternal(copy);
	}




	public int size() {
		return internal.size();
	}

	public boolean isEmpty() {
		return internal.isEmpty();
	}

	public boolean contains(Object o) {
		return internal.containsKey(o);
	}

	/**
	 * @return Iterator of Set, remove is unsupported
	 */
	public Iterator<TermEntry> iterator() {
		return new ResultIterator(internal.keySet().iterator());
	}

	public Object[] toArray() {
		return internal.keySet().toArray();
	}

	public <T> T[] toArray(T[] a) {
		return internal.keySet().toArray(a);
	}

	/**
	 * Not supported : Set is unmodifiable
	 * @throws UnsupportedOperationException always
	 */
	public boolean add(TermEntry e) {
		throw new UnsupportedOperationException("Set is unmodifiable.");
	}

	/**
	 * Not supported : Set is unmodifiable
	 * @throws UnsupportedOperationException always
	 */
	public boolean remove(Object o) {
		throw new UnsupportedOperationException("Set is unmodifiable.");
	}

	public boolean containsAll(Collection<?> c) {
		return internal.keySet().containsAll(c);
	}

	/**
	 * Not supported : Set is unmodifiable
	 * @throws UnsupportedOperationException always
	 */
	public boolean addAll(Collection<? extends TermEntry> c) {
		throw new UnsupportedOperationException("Set is unmodifiable.");
	}

	/**
	 * Not supported : Set is unmodifiable
	 * @throws UnsupportedOperationException always
	 */
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException("Set is unmodifiable.");
	}

	/**
	 * Not supported : Set is unmodifiable
	 * @throws UnsupportedOperationException always
	 */
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException("Set is unmodifiable.");
	}

	/**
	 * Not supported : Set is unmodifiable
	 * @throws UnsupportedOperationException always
	 */
	public void clear() {
		throw new UnsupportedOperationException("Set is unmodifiable.");
	}



	// Private internal functions for changing the set
	// TODO relevences, metadata extra, check copy constructors

	private void addInternal(TermEntry entry) {
		internal.put(entry, entry);
	}

	/**
	 * Overwrite add all entries in result to the internal Set converted to this
	 * ResultSet's subject. If the Set already has anything inside it
	 * you should use addAllInternal below. 
	 * @param result
	 */
	private void addAllToEmptyInternal(Collection<? extends TermEntry> result) {
		for (TermEntry termEntry : result) {
			TermEntry entry = convertEntry(termEntry);
			addInternal(entry);
		}
	}

	/**
	 * Merge add all entries in result to the internal Set converted to this
	 * ResultSet's subject. If both Sets have any of the same entries those will
	 * be merged
	 * @param result
	 */
	private void addAllInternal(Collection<? extends TermEntry> result) {
		for (TermEntry termEntry : result) {
			TermEntry entry = convertEntry(termEntry);
			if(contains(entry))
				addInternal(mergeEntries(internal.get(entry), entry));
			else
				addInternal(entry);
		}
	}

	private void removeAllInternal(Collection<? extends TermEntry> result) {
		for (TermEntry termEntry : result) {
			TermEntry entry = convertEntry(termEntry);
			internal.remove(entry);
		}
	}

	/**
	 * Find the smallest collection, iterate over it and add elements which appear in all Collections
	 * @param collections
	 */
	private void intersect(Collection<? extends TermEntry>... collections) {
		// Find shortest collection
		int shortest = 0; int shortestsize = Integer.MAX_VALUE;
		for (int i = 1; i < collections.length; i++)
			if (collections[i].size() < shortestsize)
				shortest = i;

		Collection<? extends TermEntry> shortCollection = collections[shortest];
		// Iterate over it
		for (TermEntry termEntry : shortCollection) {
			// if term entry is in all the others, add it to this
			Set<TermEntry> entries = new HashSet(collections.length);
			entries.add(termEntry);

			for (int i = 0; i < collections.length; i++) {
				if(i==shortest)
					continue;	// dont compare against self

				Collection<? extends TermEntry> collection = collections[i];
				// See if collection contains termEntry
				boolean thisContains = false;
				for (TermEntry termEntry1 : collection) {
					if(termEntry.equalsIgnoreSubject(termEntry1)){
						// this collection contains the entry
						thisContains = true;
						// add it to the entries
						entries.add(termEntry1);
						break;
					}
				}
				if(!thisContains)
					break;
			}
			// If all contained this entry, merge them all
			if(entries.size() == collections.length)
				addInternal(mergeEntries(entries.toArray(new TermEntry[0])));
		}
	}

//	// TODO : this doesnt do phrase, it is a version of the intersection code, with some errors
//	private void retainAllFollowedInternal(Collection<? extends TermEntry> result) {
//		ResultSet retainFrom = new ResultSet(subject, result);
//		for (TermEntry termEntry : internal) {
//			TermEntry entry = convertEntry(termEntry);
//			boolean retainthis = false;
//			for (TermEntry termEntry1 : result) {
//				TermEntry entry1 = convertEntry(termEntry1);
//				if(entry.equals(entry1)) {
//					retainthis = true;
//					break;
//				}
//			}
//			if(retainthis)
//				;	// TODO make sure all data on entries are retained
//			else
//				internal.remove(entry);
//		}
//	}


	private TermEntry convertEntry(TermEntry termEntry) {
		if (termEntry instanceof TermTermEntry)
			return new TermTermEntry( subject, ((TermTermEntry)termEntry).getTerm() );
		else if (termEntry instanceof TermPageEntry)
			return new TermPageEntry( subject, ((TermPageEntry)termEntry).getURI(), ((TermPageEntry)termEntry).getTitle(), ((TermPageEntry)termEntry).getPositions() );
		else if (termEntry instanceof TermIndexEntry)
			return new TermIndexEntry( subject, ((TermIndexEntry)termEntry).getIndex() );
		else
			throw new UnsupportedOperationException("The TermEntry type " + termEntry.getClass().getName() + " is not currently supported in ResultSet");
	}

	/**
	 * Merge a group of TermEntries each pair of which(a, b) must be a.equalsIgnoreSubject
	 * The new TermEntry created will have the subject of this ResultSet, it
	 * will try to combine all the optional fields from the TermEntrys being merged,
	 * with priority being put on those earliest in the arguments
	 *
	 * @param entries
	 * @return
	 */
	private TermEntry mergeEntries(TermEntry... entries) {
		for (int i = 1; i < entries.length; i++)
			if(!entries[0].equalsIgnoreSubject(entries[i]))
				throw new IllegalArgumentException("entries were not equal : "+entries[0].toString()+" & "+entries[i].toString());
		
		TermEntry combination = entries[0];

		// This mess could be replaced by a method in the TermEntrys which returns a new TermEntry with a changed subject
		if(combination instanceof TermIndexEntry){
			combination = new TermIndexEntry(subject, ((TermIndexEntry)combination).getIndex());
		} else if(combination instanceof TermPageEntry){
			combination = new TermPageEntry(subject, ((TermPageEntry)combination).getURI(), ((TermPageEntry)combination).getTitle(), ((TermPageEntry)combination).getPositions());
		} else if(combination instanceof TermTermEntry){
			combination = new TermTermEntry(subject, ((TermTermEntry)combination).getTerm());
		} else
			throw new IllegalArgumentException("Unknown type : "+combination.getClass().toString());
		
		for (int i = 1; i < entries.length; i++) {
			TermEntry termEntry = entries[i];
			combination = combination.combine(termEntry);
		}

		return combination;
	}

	/**
	 * Strip all results out of List of requests
	 * @param subRequests
	 * @return
	 * @throws plugins.Library.serial.TaskAbortException from SubRequests {@link Request.getResult()}
	 */
	private Set<TermEntry>[] getResultSets(List<Request<Set<TermEntry>>> subRequests) throws TaskAbortException{
		Set<TermEntry>[] sets = new Set[subRequests.size()];
		for (int i = 0; i < subRequests.size(); i++) {
			sets[i] = subRequests.get(i).getResult();
		}
		return sets;
	}


	public String toString(){
		return internal.keySet().toString();
	}


	/**
	 * Iterator which doesnt allow remove()
	 */
	class ResultIterator implements Iterator<TermEntry> {
		Iterator<TermEntry> internal;

		private ResultIterator(Iterator<TermEntry> iterator) {
			internal = iterator;
		}

		public boolean hasNext() {
			return internal.hasNext();
		}

		public TermEntry next() {
			return internal.next();
		}

		public void remove() {
			throw new UnsupportedOperationException("Removal not allowed.");
		}

	}
}
