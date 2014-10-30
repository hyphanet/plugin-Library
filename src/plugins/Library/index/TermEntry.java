/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */


package plugins.Library.index;

/**
 * Represents data associated with a given subject {@link String} term.
 *
 * Subclasses '''must''' override the following methods to use information
 * specific to that subclass:
 *
 * * {@link #entryType()}
 * * {@link #compareTo(TermEntry)}
 * * {@link #equals(Object)}
 * * {@link #hashCode()}
 *
 * @author infinity0
 */
abstract public class TermEntry implements Comparable<TermEntry> {
    final static long serialVersionUID = 0xF23194B7F015560CL;

    public enum EntryType { INDEX, TERM, PAGE }

    ;

    /**
     * Subject term of this entry.
     */
    final public String subj;

    /**
     * Relevance rating. Must be in the half-closed interval (0,1].
     * a relevance of 0 indicates that the relevance is unset
     */
    final public float rel;

    public TermEntry(String s, float r) {
        if (s == null) {
            throw new IllegalArgumentException("can't have a null subject!");
        }

        if (r < 0 /* || r > 1 */) {  // FIXME: I don't see how our relevance algorithm can be guaranteed to produce relevance <1.
            throw new IllegalArgumentException(
                "Relevance must be in the half-closed interval (0,1]. Supplied: " + r);
        }

        subj = s.intern();
        rel = r;
    }

    public TermEntry(TermEntry t, float newRel) {
        this.subj = t.subj;
        this.rel = newRel;
    }

    /**
     * Returns the type of TermEntry. This '''must''' be constant for
     * instances of the same class, and different between classes.
     */
    abstract public EntryType entryType();

    /**
    ** {@inheritDoc}
    **
    ** Compares two entries, based on how useful they might be to the end user.
    **
    ** This implementation sorts by order of descending relevance, then by
    ** order of ascending {@link #entryType()}.
    **
    ** Subclasses '''must overridde this method'' to also use information
    ** specific to the subclass.
    **
    ** @throws IllegalArgumentException if the entries have different subjects
    */
    /* @Override* */
    public int compareTo(TermEntry o) {
        if (this == o) {
            return 0;
        }

        int c = subj.compareTo(o.subj);

        if (c != 0) {
            return c;
        }

        if (rel != o.rel) {
            return (rel > o.rel) ? -1 : 1;
        }

        EntryType a = entryType(), b = o.entryType();

        return a.compareTo(b);
    }

    /**
    ** {@inheritDoc}
    **
    ** This implementation tests whether the run-time classes of the argument
    ** is identical to the run-time class of this object. If they are, then
    ** it tests the relevance and subject fields.
    **
    ** Subclasses '''must overridde this method'' to also use information
    ** specific to the subclass.
    */
    @Override
    public boolean equals(Object o) {
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }

        TermEntry en = (TermEntry) o;

        return (rel == en.rel) && subj.equals(en.subj);
    }

    /**
     * Returns whether the parts of this TermEntry other than the subject are equal
     *
     * Subclasses '''must overridde this method'' to use information
     * specific to the subclass.
     */
    public abstract boolean equalsTarget(TermEntry entry);

    /**
    ** {@inheritDoc}
    **
    ** This implementation XORs the hashcode of {@link #subj} and {@link #rel}.
    **
    ** Subclasses '''must overridde this method'' to also use information
    ** specific to the subclass.
    */
    @Override
    public int hashCode() {
        return subj.hashCode() ^ Float.floatToIntBits(rel);
    }

    @Override
    public String toString() {
        return subj + ":" + rel;
    }

    /*
     * Calculates an accumulated score to sort the entry by, using the formula
     * relevance^3 * quality; in other words, relevance is (for sorting
     * results) 3 times as important as quality. For example, for (rel, qual)
     * we have (0.85, 0.95) < (1.0, 0.6) < (0.85, 1.00)
     *
     * public float score() {
     *   if (score_ == null) {
     *       score_ = new Float(rel * rel* rel * qual);
     *   }
     *   return score_;
     * }
     */
}
