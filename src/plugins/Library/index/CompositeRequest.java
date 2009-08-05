/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import java.util.List;

/**
 * For Requests which are composed of multiple subrequests
 *
 * @author MikeB
 */
public interface CompositeRequest<R> extends Request {
	/**
	 ** To be overridden by subclasses which depend on subrequests.
	 **
	 ** @return List of Requests, or null
	 */
	public List<Request<R>> getSubRequests();
}
