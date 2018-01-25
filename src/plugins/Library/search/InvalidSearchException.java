/*
 * This code is part of Freenet. It is distributed under the GNU General Public License, version 2
 * (or at your option any later version). See http://www.gnu.org/ for further details of the GPL.
 */

package plugins.Library.search;

/**
 * Exception when something is wrong with search parameters but nothing else
 * 
 * @author MikeB
 */
public class InvalidSearchException extends Exception {
  public InvalidSearchException(String s) {
    super(s);
  }

  public InvalidSearchException(String string, Exception ex) {
    super(string, ex);
  }
}
