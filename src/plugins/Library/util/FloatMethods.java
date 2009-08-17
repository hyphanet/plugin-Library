/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import java.util.Arrays;

/**
** Various floating-point methods
**
** @author infinity0
*/
public class FloatMethods {

	private FloatMethods() { }

	/**
	** Sum an array of floats. The values are assumed to be positive, and the
	** array will be sorted in the process. The values are summed in order of
	** increasing size; this reduces the rounding error.
	*/
	public static float sumPositive(float[] array) {
		Arrays.sort(array);
		if (array[0] < 0) {
			throw new IllegalArgumentException("This method is for >0 float arrays only");
		}
		float sum = 0;
		for (int i=0; i<array.length; ++i) {
			sum += array[i];
		}
		return sum;
	}

}
