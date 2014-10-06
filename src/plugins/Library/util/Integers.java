/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
** Various integer methods.
**
** @author infinity0
*/
final public class Integers {

    private Integers() { }

    // TODO LOW "allocateEvenlyRandom" ie. distributely evenly but with ties
    // being broken randomly, in the style of dithering

    /**
    ** Allocate {@code total} resources evenly over {@code num} recipients.
    **
    ** @return An {@link Iterable} of {@link Integer}, that "contains" {@code
    **         num} elements, each of which is either {@code k+1} or {@code k}.
    **         The {@code k+1} will themselves also be dispersed evenly
    **         throughout the iteration.
    */
    public static Iterable<Integer> allocateEvenly(final int total, final int num) {
        if (total < 0) {
            throw new IllegalArgumentException("Total must be non-negative.");
        }
        if (num <= 0) {
            throw new IllegalArgumentException("Count must be positive.");
        }

        return new Iterable<Integer>() {

            final int k = total / num;
            final int r = total % num;
            final double step = (double)num / r;

            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    double d = step / 2;
                    int i = 0;

                    /*@Override**/ public boolean hasNext() {
                        return i < num;
                    }

                    /*@Override**/ public Integer next() {
                        if (i >= num) {
                            throw new NoSuchElementException();
                        } else if ((int)d == i++) {
                            d += step;
                            return k+1;
                        } else {
                            return k;
                        }
                    }

                    /*@Override**/ public void remove() {
                        throw new UnsupportedOperationException();
                    }

                };
            }

            @Override public String toString() {
                char[] xs = new char[num];
                int j=0;
                for (Integer ii: this) {
                    xs[j++] = (ii == k)? ' ': '+';
                }
                return total + "/" + num + ": " + k + "+[" + new String(xs) + "]";
            }

        };
    }

}
