/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

/**
** A class that translates an object into one of another type. Used mostly in
** conjunction with an {@link Serialiser} that can take objects of the latter
** type but not the former type.
**
** @author infinity0
*/
public interface Translator<T, I> {

	/**
	** Reverse the translation.
	**
	** URGENT DOCUMENT minimal thing, as in Archiver, figure out what
	** to do with it. throw IllegalArgumentException?
	*/
	T rev(I intermediate);

	/**
	** Apply the translation.
	*/
	I app(T translatee);

}
