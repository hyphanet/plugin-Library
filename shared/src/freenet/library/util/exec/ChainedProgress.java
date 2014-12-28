/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.library.util.exec;

/**
** Interface representing a {@link Progress} made up a sequence of progresses,
** each occuring one after the other, and whose completion usually (this is
** not enforced) depends on all of the subprogresses completing successfully.
**
** @author infinity0
*/
public interface ChainedProgress extends Progress {

	/**
	** {@inheritDoc}
	**
	** Implementations should return {@link ProgressParts#getParts(Iterable,
	** int)}, with the first argument being the collection of already
	** completed progresses plus the current one.
	*/
	/*@Override**/ public ProgressParts getParts() throws TaskAbortException;

	/**
	** Gets the latest subprogress. The earlier ones should all be complete.
	** Note: '''this can return {@code null}''' which means the next stage
	** hasn't started yet.
	*/
	public Progress getCurrentProgress();

	/*

	display would look something like

	{as for Progress.this}
		+----------------------------------------------------+
		| {as for getCurrentProgress()}                      |
		+----------------------------------------------------+

	*/

}
