/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.event;

import plugins.Library.event.ChainedProgress;

/**
 * For {@link Execution}s which are composed of multiple serial subrequests
 *
 * @author MikeB
 */
public interface ChainedExecution<V> extends Execution<V>, ChainedProgress {
}
