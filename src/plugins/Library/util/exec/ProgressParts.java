/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.exec;

import java.util.Formatter;

/**
** Immutable class representing the parts of a progress.
**
** @author infinity0
*/
public class ProgressParts {

    /**
    ** The value for {@link #totalest} representing an unknown estimate.
    */
    final public static int ESTIMATE_UNKNOWN = -2;

    /**
    ** The value for {@link #totalest} representing a finalized estimate.
    */
    final public static int TOTAL_FINALIZED = -1;

    /**
    ** Parts done. This is never greater than parts started.
    */
    final public int done;

    /**
    ** Parts started. This is never greater than parts known.
    */
    final public int started;

    /**
    ** Parts known. If there is an estimate, this is never greater than it.
    */
    final public int known;

    /**
    ** Total estimated parts. Special values:
    **
    ** ;{@code -1} : The final total is known and is equal to {@link #known}.
    ** ;{@code -2} : No estimate is available.
    **
    ** (All other negative values are invalid.)
    */
    final public int totalest;

    /**
    ** Constructs a new {@code ProgressPart} from the given arguments, fixing
    ** any values which are greater than their next neighbour (For example,
    ** 5/4/3/2 -> 2/2/2/2, and 3/7/4/? -> 3/4/4/?).
    **
    ** @param d Parts {@linkplain #done}
    ** @param s Parts {@linkplain #started}
    ** @param k Parts {@linkplain #known}
    ** @param t {@linkplain #totalest Estimated} total parts
    */
    public static ProgressParts normalise(int d, int s, int k, int t) {
        if (t >= 0 && k > t) { k = t; }
        if (s > k) { s = k; }
        if (d > s) { d = s; }
        return new ProgressParts(d, s, k, t);
    }

    /**
    ** Constructs a new {@code ProgressPart} from the given arguments, fixing
    ** any values which are greater than their next neighbour (for example,
    ** 5/4/3/2 -> 2/2/2/2, and 3/7/4/? -> 3/4/4/?).
    **
    ** @param d Parts {@linkplain #done}, same as parts {@linkplain #started}
    ** @param k Parts {@linkplain #known}
    */
    public static ProgressParts normalise(int d, int k) {
        return normalise(d, d, k, ESTIMATE_UNKNOWN);
    }

    /**
    ** Constructus a new {@code ProgressPart} from the given arguments.
    **
    ** @param d Parts {@linkplain #done}
    ** @param s Parts {@linkplain #started}
    ** @param k Parts {@linkplain #known}
    ** @param t {@linkplain #totalest Estimated} total parts
    ** @throws IllegalArgumentException if the constraints for the fields are
    **         not met.
    */
    public ProgressParts(int d, int s, int k, int t) {
        if (0 > d || d > s || s > k || (t >= 0 && k > t) || (t < 0 && t != ESTIMATE_UNKNOWN && t != TOTAL_FINALIZED)) {
            throw new IllegalArgumentException("ProgressParts (" + d + "/" + s + "/" + k + "/" + t + ") must obey the contract 0 <= done <= started <= known <= totalest or totalest == ESTIMATE_UNKNOWN or TOTAL_FINALIZED");
        }
        done = d;
        started = s;
        known = k;
        totalest = t;
    }

    /**
    ** Constructus a new {@code ProgressPart} from the given arguments.
    **
    ** @param d Parts {@linkplain #done}
    ** @param s Parts {@linkplain #started}
    ** @param k Parts {@linkplain #known}
    ** @throws IllegalArgumentException if the constraints for the fields are
    **         not met.
    */
    public ProgressParts(int d, int s, int k) {
        this(d, s, k, ESTIMATE_UNKNOWN);
    }

    /**
    ** Constructus a new {@code ProgressPart} from the given arguments.
    **
    ** @param d Parts {@linkplain #done}, same as parts {@linkplain #started}
    ** @param k Parts {@linkplain #known}
    ** @throws IllegalArgumentException if the constraints for the fields are
    **         not met.
    */
    public ProgressParts(int d, int k) {
        this(d, d, k, ESTIMATE_UNKNOWN);
    }

    /**
    ** Whether the total is finalized. ({@code totalest == TOTAL_FINALIZED})
    */
    final public boolean finalizedTotal() {
        return totalest == TOTAL_FINALIZED;
    }

    /**
    ** Whether an estimate exists. ({@code totalest != ESTIMATE_UNKNOWN})
    */
    final public boolean hasEstimate() {
        return totalest != ESTIMATE_UNKNOWN;
    }

    /**
    ** Whether the task is done, ie. whether {@code done == staretd == known}
    ** and the total is {@linkplain #totalest finalized}.
    */
    final public boolean isDone() {
        return done == started && started == known && totalest == TOTAL_FINALIZED;
    }

    /**
    ** Returns the parts done as a fraction of the parts known.
    */
    final public float getKnownFractionDone() {
        return known == 0? 0.0f: (float)done / known;
    }

    /**
    ** Returns the parts started as a fraction of the parts known.
    */
    final public float getKnownFractionStarted() {
        return known == 0? 0.0f: (float)started / known;
    }

    /**
    ** Returns the parts done as a fraction of the estimated total parts.
    ** Note: this will return negative (ie. invalid) if there is no estimate.
    */
    final public float getEstimatedFractionDone() {
        return totalest == 0? 0.0f: totalest == TOTAL_FINALIZED? getKnownFractionDone(): (float)done / totalest;
    }

    /**
    ** Returns the parts started as a fraction of the estimated total parts.
    ** Note: this will return negative (ie. invalid) if there is no estimate.
    */
    final public float getEstimatedFractionStarted() {
        return totalest == 0? 0.0f: totalest == TOTAL_FINALIZED? getKnownFractionStarted(): (float)started / totalest;
    }

    /**
    ** Returns the parts known as a fraction of the estimated total parts.
    ** Note: this will return negative (ie. invalid) if there is no estimate.
    */
    final public float getEstimatedFractionKnown() {
        return totalest == 0? 0.0f: totalest == TOTAL_FINALIZED? 1.0f: (float)known / totalest;
    }

    /**
    ** Returns a summary of the progress in the form {@code d/s/k/t??}.
    **
    ** * If {@code done == started}, only one will be included.
    ** * If the total is {@linkplain #finalizedTotal() finalized}, the estimate
    **   will be ommited.
    ** * If there is no estimate, only the {@code ??} will be included.
    */
    @Override final public String toString() {
        String s = done + "/";
        if (done != started) { s += started + "/"; }
        s += known;
        if (!finalizedTotal()) {
            s += hasEstimate()? "/" + totalest + "??" : "/??";
        }
        return s;
    }

    /**
    ** Returns a summary of the progress in the form {@code known_fraction_done
    ** (estimated_fraction_done)}.
    **
    ** * If the total is {@linkplain #finalizedTotal() finalized}, the estimate
    **   will be ommited.
    ** * If there is no estimate, only the {@code ??} will be included.
    */
    final public String toFractionString() {
        Formatter f = new Formatter();
        if (finalizedTotal()) {
            f.format("%.4f", getKnownFractionDone());
        } else if (!hasEstimate()) {
            f.format("%.4f (??)", getKnownFractionDone());
        } else {
            f.format("%.4f (%.4f??)", getKnownFractionDone(), getEstimatedFractionDone());
        }
        return f.toString();
    }

    /**
    ** Returns a summary of the progress in the form {@code known_percent_done
    ** (estimated_percent_done)}.
    **
    ** * If the total is {@linkplain #finalizedTotal() finalized}, the estimate
    **   will be ommited.
    ** * If there is no estimate, only the {@code ??} will be included.
    */
    final public String toPercentageString() {
        Formatter f = new Formatter();
        if (finalizedTotal()) {
            f.format("%.2f", getKnownFractionDone()*100);
        } else if (!hasEstimate()) {
            f.format("%.2f (??)", getKnownFractionDone()*100);
        } else {
            f.format("%.2f (%.2f??)", getKnownFractionDone()*100, getEstimatedFractionDone()*100);
        }
        return f.toString();
    }


    /**
    ** Constructs a new {@code ProgressParts} based on the total number of
    ** parts (done, started, known) in the given iterable. Total estimated
    ** parts is calculated based on the value of {@code try_to_be_smart}:
    **
    ** ;{@code true} : try to extrapolate an estimated total parts based on the
    ** number of subprogresses having estimates, the sum of these estimates,
    ** and the total number of subprogresses.
    **
    ** ;{@code false} : an estimate will only be supplied if all subprogresses
    ** have estimates.
    */
    public static ProgressParts getSubParts(Iterable<? extends Progress> subprogress, boolean try_to_be_smart) throws TaskAbortException {
        int d = 0, s = 0, k = 0, t = 0;
        int num = 0, unknown = 0;
        boolean totalfinalized = true;
        for (Progress p: subprogress) {
            ++num;
            if (p == null) { ++unknown; continue; }
            ProgressParts parts = p.getParts();
            d += parts.done;
            s += parts.started;
            k += parts.known;
            switch (parts.totalest) {
            case ESTIMATE_UNKNOWN:
                ++unknown;
                totalfinalized = false;
                break;
            case TOTAL_FINALIZED:
                t += parts.known;
                break;
            default:
                totalfinalized = false;
                t += parts.totalest;
            }
        }
        if (num == unknown) {
            t = ESTIMATE_UNKNOWN;
        } else if (unknown > 0) {
            t = (try_to_be_smart)? (int)Math.round(t * num / (float)(num-unknown)): ESTIMATE_UNKNOWN;
        }
        return new ProgressParts(d, s, k, (totalfinalized)? TOTAL_FINALIZED: t);
    }

    /**
    ** Constructs a new {@code ProgressParts} based on the number of progresses
    ** (done, started, known) in the given iterable, with the given total
    ** estimate.
    */
    public static ProgressParts getParts(Iterable<? extends Progress> subprogress, int estimate) throws TaskAbortException {
        int d = 0, s = 0, k = 0;
        for (Progress p: subprogress) {
            ++k;
            if (p == null) { continue; }
            ++s;
            if (p.isDone()) { ++d; }
        }
        return new ProgressParts(d, s, k, estimate);
    }

}
