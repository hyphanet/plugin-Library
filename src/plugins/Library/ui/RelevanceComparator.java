/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.ui;

import plugins.Library.index.TermEntry;
import plugins.Library.util.IdentityComparator;

/**
 * Compares the relevance of two TermEntrys, extends IdentityComparator so that two unique entries
 * will not return a comparison of 0
 *
 * @author MikeB
 */
public class RelevanceComparator extends IdentityComparator<TermEntry> {
    public static final RelevanceComparator comparator = new RelevanceComparator();

    @Override
    public int compare(TermEntry o1, TermEntry o2) {
        float rel1 = o1.rel;
        float rel2 = o2.rel;

        if (rel1 == rel2) {
            return super.compare(o1, o2);
        } else {
            return (rel1 < rel2) ? +1 : -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        return getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        int hash = 7;

        return hash;
    }
}
