/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.event.ChainedProgress;

/**
 * For Requests which are composed of multiple serial subrequests
 *
 * @author MikeB
 */
public interface ChainedRequest<R> extends Request<R>, ChainedProgress {
}
