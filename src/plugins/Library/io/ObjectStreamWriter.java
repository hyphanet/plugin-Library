/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.io;

import java.io.OutputStream;
import java.io.IOException;

/**
** Simpler version of {@link java.io.ObjectOutput} that has only one method.
**
** @author infinity0
*/
public interface ObjectStreamWriter<T> {

    /**
    ** Write the given object to the given stream.
    */
    public void writeObject(T o, OutputStream os) throws IOException;

}
