/*
 * This code is part of Freenet. It is distributed under the GNU General Public License, version 2
 * (or at your option any later version). See http://www.gnu.org/ for further details of the GPL.
 */

/**
 * Handles higher-level search functions such as set operations on the results, splitting search
 * queries, searching on multiple indexes.
 *
 * TODO support for stopwords, maybe it should be an exception passed back from getResult() so that
 * that one can be ignored and need to send a message to the user to say it was excluded ( Could be
 * a TermEntry )
 * 
 * @author MikeB
 */
package plugins.Library.search;

