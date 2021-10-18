/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.search;

import plugins.Library.index.TermEntry;
import plugins.Library.index.TermIndexEntry;
import plugins.Library.index.TermTermEntry;
import plugins.Library.index.TermPageEntry;
import plugins.Library.util.exec.Execution;
import plugins.Library.util.exec.TaskAbortException;

import freenet.support.Logger;

import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * Unmodifiable Set which makes sure all data in results being combined is
 * retained and is created as the result of Set operations on other Sets. Does
 * not contain references to the original Sets.
 * <br /> <br />
 * The set isn't finalised until a run is finished and isDone() is true.
 *
 * @author MikeB
 */
public class ResultSet implements Set<TermEntry>, Runnable{
	private ResultOperation resultOperation;
	private boolean done = false;
	private Set<TermEntry>[] subresults;
	private RuntimeException exception;

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
	private final boolean ignoreTAEs;

	/**
	 * @param subject The subject for each of the entries in the Set
	 * @param resultOperation {@link ResultOperation} to be performed on each of the given Sets
	 * @param subRequests List of subrequests to test each getResult()
	 * @throws TaskAbortException if thrown from a higher getResult() method
	 *
	 * TODO reevaluate relevance for all combinations, and find a way to calculate relevance of phrases
	 */
	ResultSet(String subject, ResultOperation resultOperation, List<Execution<Set<TermEntry>>> subRequests, boolean ignoreTAEs) throws TaskAbortException {
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
		this.resultOperation = resultOperation;

		// Make sure any TaskAbortExceptions are found here and not when it's run
		this.ignoreTAEs = ignoreTAEs;
		subresults = getResultSets(subRequests, ignoreTAEs);
	}

	public synchronized void run() {
		if(done)
			throw new IllegalStateException("This ResultSet has already run and is finalised.");

		try{
			// Decide what to do
			switch(resultOperation){
				case SINGLE:	// Just add everything
					addAllToEmptyInternal(subresults[0]);
					break;
				case DIFFERENTINDEXES:	// Same as UNION currently
				case UNION:	// Add every one without overwriting
					unite(subresults);
					break;
				case INTERSECTION:	// Add one then retain the others
					intersect(subresults);
					break;
				case REMOVE:	// Add one then remove the other
					exclude(subresults[0], subresults[1]);
					break;
				case PHRASE:
					phrase(subresults);
					break;
			}
		}catch(RuntimeException e){
			exception = e;	// Exeptions thrown here are stored in case this is being run in a thread, in this case it is thrown in isDone() or iterator()
			throw e;
		}
		subresults = null; // forget the subresults
		done = true;
	}

	/**
	 * Copy a collection into a ResultSet
	 * @param subject to change the subject of each entry
	 * @param copy Collection to copy
	 */
	private ResultSet(String subject, Collection<? extends TermEntry> copy, boolean ignoreTAEs) {
		this.subject = subject;
		internal = new HashMap();
		this.ignoreTAEs = ignoreTAEs;
		addAllToEmptyInternal(copy);
	}


	// Set interface methods, folow the standard contract

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
	 * @throws RuntimeException if a RuntimeException was caught while generating the Set
	 */
	public Iterator<TermEntry> iterator() {
		if(exception != null)
			throw new RuntimeException("RuntimeException thrown in ResultSet thread", exception);
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
	 * Add all entries in the first collection but not in the second
	 */
	private void exclude(Collection<? extends TermEntry> add, Collection<? extends TermEntry> subtract) {
		for (TermEntry termEntry : add){
			if(getIgnoreSubject(termEntry, subtract)==null)
				addInternal(termEntry);
		}
	}

	/**
	 * Iterate over all the collections adding and merging all their entries
	 * @param collections to be merged into this collection
	 * TODO proper relevance calculating here, currently i think the relevance of the first one added will have less impact than the others, the other 3 types are more important i believe
	 */
	private void unite(Collection<? extends TermEntry>... collections) {
		for(Collection<? extends TermEntry> c : collections)
			if(c==null)
				Logger.error(this, "the result was null");
			else
				for (TermEntry termEntry : c) {
					TermEntry entry = convertEntry(termEntry);
					if(contains(entry))
						addInternal(mergeEntries(internal.get(entry), entry));
					else
						addInternal(entry);
				}
	}

	/**
	 * Iterate over the first collection adding those elements which exist in all the other collections
	 * @param collections a bunch of collections to intersect
	 */
	private void intersect(Collection<? extends TermEntry>... collections) {
		Collection<? extends TermEntry> firstCollection = collections[0];
		// Iterate over it
		for (Iterator<? extends TermEntry> it = firstCollection.iterator(); it.hasNext();) {
			TermEntry termEntry = it.next();
			// if term entry is contained in all the other collections add it
			float combinedrelevance = termEntry.rel;

			int i;
			for (i = 1; i < collections.length; i++) {
				Collection<? extends TermEntry> collection = collections[i];
				// See if collection contains termEntry

				TermEntry termEntry2 = getIgnoreSubject(termEntry, collection);
				if ( termEntry2 == null )
					break;
				else	// add to combined relevance
					combinedrelevance += termEntry2.rel;
			}
			if (i==collections.length){
				TermEntry newEntry = convertEntry(termEntry, combinedrelevance/collections.length);
				addInternal(newEntry);
			}
		}
	}

	/**
	 * Iterate over the first collection, and the termpositions of each entry,
	 * keeping those positions which are followed in the other collections. Keeps
	 * those entries which have positions remaingin after this process
	 * @param collections
	 */
	private void phrase(Collection<? extends TermEntry>... collections) {
		Collection<? extends TermEntry> firstCollection = collections[0];
		// Iterate over it
		for (TermEntry termEntry : firstCollection) {
			if(!(termEntry instanceof TermPageEntry))
				continue;
			// if term entry is followed in all the others, add it to this
			TermPageEntry termPageEntry = (TermPageEntry)termEntry;
			if(!termPageEntry.hasPositions())
				continue;
			Map<Integer, String> positions = new HashMap(termPageEntry.positionsMap());

			int i;	// Iterate over the other collections, checking for following
			for (i = 1; positions != null && i < collections.length && positions.size() > 0; i++) {
				Collection<? extends TermEntry> collection = collections[i];
				if(collection == null)
					continue;	// Treat stop words as blanks, dont check
				// See if collection follows termEntry
				TermPageEntry termPageEntry1 = (TermPageEntry)getIgnoreSubject(termPageEntry, collection);
				if(termPageEntry1==null || !termPageEntry1.hasPositions())	// If collection doesnt contain this termpageentry or has not positions, it does not follow
					positions = null;
				else{
					for (Iterator<Integer> it = positions.keySet().iterator(); it.hasNext();) {
						int posi = it.next();
						if ( !termPageEntry1.hasPosition(posi+i))
							it.remove();
						else
							Logger.minor(this, termPageEntry.page + "["+positions.keySet()+"] is followed by "+termPageEntry1.page+"["+termPageEntry1.positions()+"] +"+i);
					}
				}
			}
			// if this termentry has any positions remaining, add it
			if(positions != null && positions.size() > 0)
				addInternal(new TermPageEntry(subject, termPageEntry.rel, termPageEntry.page, termPageEntry.title, positions));
		}
	}

	private TermEntry convertEntry(TermEntry termEntry) {
		return convertEntry(termEntry, termEntry.rel);
	}

	private TermEntry convertEntry(TermEntry termEntry, float rel) {
		TermEntry entry;
		if (termEntry instanceof TermTermEntry)
			entry = new TermTermEntry(subject, rel, ((TermTermEntry)termEntry).term );
		else if (termEntry instanceof TermPageEntry)
			entry = new TermPageEntry(subject, rel, ((TermPageEntry)termEntry).page, ((TermPageEntry)termEntry).title, ((TermPageEntry)termEntry).positionsMap() );
		else if (termEntry instanceof TermIndexEntry)
			entry = new TermIndexEntry(subject, rel, ((TermIndexEntry)termEntry).index );
		else
			throw new UnsupportedOperationException("The TermEntry type " + termEntry.getClass().getName() + " is not currently supported in ResultSet");
		return entry;
	}


	// TODO merge and combine can be cut down
	/**
	 * Merge a group of TermEntries each pair of which(a, b) must be a.equalsTarget
	 * The new TermEntry created will have the subject of this ResultSet, it
	 * will try to combine all the optional fields from the TermEntrys being merged,
	 * with priority being put on those earliest in the arguments
	 *
	 * @param entries
	 * @return The merged entry
	 */
	private TermEntry mergeEntries(TermEntry... entries) {
		for (int i = 1; i < entries.length; i++)
			if(!entries[0].equalsTarget(entries[i]))
				throw new IllegalArgumentException("entries were not equal : "+entries[0].toString()+" & "+entries[i].toString());

		TermEntry combination = entries[0];

		// This mess could be replaced by a method in the TermEntrys which returns a new TermEntry with a changed subject
		if(combination instanceof TermIndexEntry){
			combination = new TermIndexEntry(subject, entries[0].rel, ((TermIndexEntry)combination).index);
		} else if(combination instanceof TermPageEntry){
			combination = new TermPageEntry(subject, entries[0].rel, ((TermPageEntry)combination).page, ((TermPageEntry)combination).title, ((TermPageEntry)combination).positionsMap());
		} else if(combination instanceof TermTermEntry){
			combination = new TermTermEntry(subject, entries[0].rel, ((TermTermEntry)combination).term);
		} else
			throw new IllegalArgumentException("Unknown type : "+combination.getClass().toString());

		for (int i = 1; i < entries.length; i++) {
			TermEntry termEntry = entries[i];
			combination = combine(combination, termEntry);
		}

		return combination;
	}

	private TermEntry combine(TermEntry entry1, TermEntry entry2) {
		if(!entry1.equalsTarget(entry2))
			throw new IllegalArgumentException("Combine can only be performed on equal TermEntrys");

		float newRel = entry1.rel / ((entry2.rel == 0)? 1 : 2 ) + entry2.rel / ((entry1.rel == 0)? 1 : 2 );

		if(entry1 instanceof TermPageEntry){
			TermPageEntry pageentry1 = (TermPageEntry)entry1;
			TermPageEntry pageentry2 = (TermPageEntry)entry2;
			// Merge positions
			Map newPos = null;
			if((!pageentry1.hasPositions()) && pageentry2.hasPositions())
				newPos = new HashMap(pageentry2.positionsMap());
			else if(pageentry1.hasPositions()){
				newPos = new HashMap(pageentry1.positionsMap());
				if(pageentry2.hasPositions())
					newPos.putAll(pageentry2.positionsMap());
			}
			return new TermPageEntry(pageentry1.subj, newRel, pageentry1.page, (pageentry1.title!=null)?pageentry1.title:pageentry2.title, newPos);

		} else if(entry1 instanceof TermIndexEntry){
			TermIndexEntry castEntry = (TermIndexEntry) entry1;
			// nothing to merge, no optional fields	except relevance
			return new TermIndexEntry(castEntry.subj, newRel, castEntry.index);

		} else if(entry1 instanceof TermTermEntry){
			// nothing to merge, no optional fields	except relevance
			TermTermEntry castEntry = (TermTermEntry) entry1;

			return new TermTermEntry(castEntry.subj, newRel, castEntry.term);

		} else
			throw new UnsupportedOperationException("This type of TermEntry is not yet supported in the combine code : "+entry1.getClass().getName());

	}

	/**
	 * Strip all results out of List of requests
	 * @param subRequests
	 * @return An array containing the stripped sets
	 * @throws {@link TaskAbortException} from subRequests' {@link Execution#getResult()}
	 */
	private Set<TermEntry>[] getResultSets(List<Execution<Set<TermEntry>>> subRequests, boolean ignoreTAEs) throws TaskAbortException{
		Set<TermEntry>[] sets = new Set[subRequests.size()];
		int x = 0;
		for (int i = 0; i < subRequests.size(); i++) {
			if(subRequests.get(i) == null){
				if(resultOperation == ResultOperation.PHRASE)
					sets[x++] = null;
				else
					throw new NullPointerException("Nulls not allowed in subRequests for operations other than phrase.");
			} else {
				try {
					sets[x++] = subRequests.get(i).getResult();
				} catch (TaskAbortException e) {
					if(!ignoreTAEs) throw e;
				}
			}
		}
		if(x != subRequests.size()) {
			Set<TermEntry>[] newSets = new Set[x];
			System.arraycopy(sets, 0, newSets, 0, newSets.length);
			sets = newSets;
		}
		return sets;
	}

	/**
	 * Gets a TermEntry from the collection which is equal to entry ignoring subject
	 * @param entry
	 * @param collection
	 */
	private TermEntry getIgnoreSubject(TermEntry entry, Collection<? extends TermEntry> collection){
		TermEntry result = null;
			for (TermEntry termEntry : collection) {
				if (entry.equalsTarget(termEntry)){
					result = termEntry;
					break;
				}
			}
		return result;
	}

	@Override public String toString(){
		return internal.keySet().toString();
	}

	/**
	 * Returns true if the ResultSet has completed its operations
	 * @throws RuntimeException if a RuntimeException was caught while generating the Set
	 */
	public boolean isDone(){
		if(exception != null)
			throw new RuntimeException("RuntimeException thrown in ResultSet thread", exception);
		return done;
	}


	/**
	 * Iterator which doesn't allow remove()
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
