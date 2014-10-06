/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.index.TermEntry.EntryType;

/**
** A {@link TermEntry} that associates a subject term with a related term.
**
** @author infinity0
*/
public class TermTermEntry extends TermEntry {

    /**
    ** Related term target for this entry.
    */
    final public String term;

    public TermTermEntry(String s, float r, String t) {
        super(s, r);
        if (t == null) {
            throw new IllegalArgumentException("can't have a null term!");
        }
        term = t.intern();
    }

    /*========================================================================
      abstract public class TermEntry
     ========================================================================*/

    @Override public EntryType entryType() {
        assert(getClass() == TermTermEntry.class);
        return EntryType.TERM;
    }

    @Override public int compareTo(TermEntry o) {
        int a = super.compareTo(o);
        if (a != 0) { return a; }
        return term.compareTo(((TermTermEntry)o).term);
    }

    @Override public boolean equals(Object o) {
        return o == this || super.equals(o) && term.equals(((TermTermEntry)o).term);
    }

    @Override public boolean equalsTarget(TermEntry entry) {
        return entry == this || (entry instanceof TermTermEntry) && term.equals(((TermTermEntry)entry).term);
    }

    @Override public int hashCode() {
        return super.hashCode() ^ term.hashCode();
    }
}
