/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.exec;

/**
 * For {@link Execution}s which are composed of multiple parallel subrequests
 *
 * @author MikeB
 */
public interface CompositeExecution<V> extends Execution<V>, CompositeProgress {
}
