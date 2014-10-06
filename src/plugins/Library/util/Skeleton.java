/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import plugins.Library.io.serial.Serialiser;
import plugins.Library.util.exec.TaskAbortException;

/**
** Defines an interface for an extension of a data structure which is only
** partially loaded into memory. Operations on the missing data should throw
** {@link DataNotLoadedException}.
**
** @author infinity0
** @see Serialiser
** @see DataNotLoadedException
*/
public interface Skeleton<K, S extends Serialiser<?>> {

    /**
    ** Whether the skeleton is fully loaded and has no data missing.
    */
    public boolean isLive();

    /**
    ** Whether the skeleton is bare and has no data loaded at all.
    */
    public boolean isBare();

    /**
    ** Get the serialiser for this skeleton.
    */
    public S getSerialiser();

    /**
    ** Get the meta data associated with this skeleton.
    */
    public Object getMeta();

    /**
    ** Set the meta data associated with this skeleton.
    */
    public void setMeta(Object m);

    /**
    ** Inflate the entire skeleton so that after the method call, {@link
    ** #isLive()} returns true.
    */
    public void inflate() throws TaskAbortException;

    /**
    ** Deflate the entire skeleton so that after the method call, {@link
    ** #isBare()} returns true.
    */
    public void deflate() throws TaskAbortException;

    /**
    ** Partially inflate the skeleton based on some parameter object. This
    ** method may or may not also inflate other parts of the skeleton.
    **
    ** @param param The parameter for the partial inflate.
    */
    public void inflate(K param) throws TaskAbortException;

    /**
    ** Partially deflate the skeleton based on some parameter object. This
    ** method may or may not also deflate other parts of the skeleton.
    **
    ** @param param The parameter for the partial deflate.
    */
    public void deflate(K param) throws TaskAbortException;

}
