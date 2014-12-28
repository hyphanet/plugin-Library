/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.util.exec;

/**
** Interface representing a {@link Progress} made up of several subprogresses,
** which are generally (though not necessarily) independent.
**
** @author infinity0
*/
public interface CompositeProgress extends Progress {

	/**
	** {@inheritDoc}
	**
	** Implementations should return {@link ProgressParts#getSubParts(Iterable,
	** boolean)}, with the first argument being the same underlying collection
	** as {@link #getSubProgress()}.
	**
	** TODO HIGH: BaseCompositeProgress actually uses {@link
	** ProgressParts#getParts(Iterable, int)}.... should we change the above
	** spec?
	*/
	/*@Override**/ public ProgressParts getParts() throws TaskAbortException;

	/**
	** Gets all the Progress objects backing this progress. The iterator
	** should not support {@link java.util.Iterator#remove()}.
	*/
	public Iterable<? extends Progress> getSubProgress();

	/**
	** Whether the tasks has partially completed. (Implementations at their
	** discretion might chooseto take this to mean "enough tasks to be
	** acceptable", rather "any one task".)
	*/
	public boolean isPartiallyDone();

	/*
	display would look something like:

	{as for (Progress)this}
	[ details ]
		+--------------------------------------------------+
		| for (Progress p: getSubProgress())               |
		|   {as for p}                                     |
		+--------------------------------------------------+

	*/


}

