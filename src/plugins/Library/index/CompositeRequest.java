/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.event.CompositeProgress;

/**
 * For Requests which are composed of multiple parallel subrequests
 *
 * @author MikeB
 */
public interface CompositeRequest<R> extends Request<R>, CompositeProgress {
}
