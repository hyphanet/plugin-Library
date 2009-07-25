/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import freenet.keys.FreenetURI;

/**
** This class implements a BloomFilter for use with an IndexNode.
** http://en.wikipedia.org/wiki/Bloom_filter
**
** The Bloom filter is a space-efficient probabilistic data structure that is
** used to test whether an element is a member of a set. False positives are
** possible, but false negatives are not.
**
** An empty Bloom filter is a bit array of m bits, all set to 0. There must
** also be k different hash functions defined, each of which maps some set
** element to one of the m array positions with a uniform random distribution.
**
** To add an element, feed it to each of the k hash functions to get k array
** positions. Set the bits at ALL these positions to 1.
**
** To query for an element, feed it to each of the k hash functions to get k
** array positions. If the element is not in the set, then AT LEAST ONE of the
** bits at these positions will be 0. If the the element is in the set, then
** ALL of these bits will be 1; however, this could have also occured from the
** insertion of other elements. The more elements that are added to the set,
** the larger the probability of false positives.
**
** Elements cannot be removed from the set, although this can be addressed with
** a CountingBloomFilter (not featured in this implementation).
**
** @author infinity0
*/
public class TokenBloomFilter implements TokenFilter {

	// TODO
	// filter data structure

	// TODO: constructor

	public boolean has(Token token) { return true; }
	public void put(Token token) {}

}
