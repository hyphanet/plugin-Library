/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io;

import java.io.InputStream;
import java.io.IOException;

/**
** Simpler version of {@link java.io.ObjectInput} that has only one method.
**
** @author infinity0
*/
public interface ObjectStreamReader<T> {

    /**
    ** Read and return the object from the given stream.
    */
    public T readObject(InputStream is) throws IOException;

}
