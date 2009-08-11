/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import java.util.Formatter;

/**
** Immutable class representing the parts of a progress.
**
** @author infinity0
*/
public class ProgressParts {

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
	** Total estimated parts. A value of {@code -1} means "unknown". A value
	** equal to {@link #known} means the estimate is final and definite. (If
	** you want to say "we think we are finished, but we are not sure", then
	** use {@code known+1}.)
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
		return normalise(d, d, k, -1);
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
		if (0 > d || d > s || s > k || (t >= 0 && k > t)) {
			throw new IllegalArgumentException("ProgressParts must obey the contract 0 <= done <= started <= known and (totalest < 0 || known <= totalest)");
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
		this(d, s, k, -1);
	}

	/**
	** Constructus a new {@code ProgressPart} from the given arguments.
	**
	** @param d Parts {@linkplain #done}, same as parts {@linkplain #started}
	** @param k Parts {@linkplain #known}
	** @param t {@linkplain #totalest Estimated} total parts
	** @throws IllegalArgumentException if the constraints for the fields are
	**         not met.
	*/
	public ProgressParts(int d, int k) {
		this(d, d, k, -1);
	}

	/**
	** Whether the total is finalized, ie. whether {@code known == totalest}.
	*/
	final public boolean finalizedTotal() {
		return known == totalest;
	}

	/**
	** Whether an estimate exists, ie. whether {@code totalest >= 0}.
	*/
	final public boolean hasEstimate() {
		return totalest >= 0;
	}

	/**
	** Whether the task is done, ie. whether all four fields are equal.
	*/
	final public boolean isDone() {
		return done == started && started == known && known == totalest;
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
		return totalest == 0? 0.0f: (float)done / totalest;
	}

	/**
	** Returns the parts started as a fraction of the estimated total parts.
	** Note: this will return negative (ie. invalid) if there is no estimate.
	*/
	final public float getEstimatedFractionStarted() {
		return totalest == 0? 0.0f: (float)started / totalest;
	}

	/**
	** Returns the parts known as a fraction of the estimated total parts.
	** Note: this will return negative (ie. invalid) if there is no estimate.
	*/
	final public float getEstimatedFractionKnown() {
		return totalest == 0? 0.0f: (float)known / totalest;
	}

	/**
	** Returns a summary of the progress in the form {@code d/s/k/t??}.
	**
	** * If {@code done == started}, only one will be included.
	** * If the total is {@linkplain finalized}, the estimate will be ommited.
	** * If there is no estimate, only the {@code ??} will be included.
	*/
	final public String toString() {
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
	** * If the total is {@linkplain finalized}, the estimate will be ommited.
	** * If there is no estimate, only the {@code ??} will be included.
	*/
	final public String toFractionString() {
		Formatter f = new Formatter();
		if (finalizedTotal()) {
			f.format("%.4f", getKnownFractionDone());
		} else if (hasEstimate()) {
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
	** * If the total is {@linkplain finalized}, the estimate will be ommited.
	** * If there is no estimate, only the {@code ??} will be included.
	*/
	final public String toPercentageString() {
		Formatter f = new Formatter();
		if (finalizedTotal()) {
			f.format("%.2f", getKnownFractionDone()*100);
		} else if (hasEstimate()) {
			f.format("%.2f (??)", getKnownFractionDone()*100);
		} else {
			f.format("%.2f (%.2??)", getKnownFractionDone()*100, getEstimatedFractionDone()*100);
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
	** ;{@code false} : {@link #getParts()} will only give an estimate if all
	** the subprogresses have estimates.
	*/
	public static ProgressParts getSubParts(Iterable<? extends Progress> subprogress, boolean try_to_be_smart) throws TaskAbortException {
		int d = 0, s = 0, k = 0, t = 0;
		int num = 0, unknown = 0;
		for (Progress p: subprogress) {
			++num;
			if (p == null) { ++unknown; continue; }
			ProgressParts parts = p.getParts();
			d += parts.done;
			s += parts.started;
			k += parts.known;
			if (parts.totalest < 0) { ++unknown; }
			else { t += parts.totalest; }
		}
		if (num == unknown) {
			t = -1;
		} else if (unknown > 0) {
			t = (try_to_be_smart)? (int)Math.round(t * num / (float)(num-unknown)): -1;
		}
		return new ProgressParts(d, s, k, t);
	}

	/**
	** Constructs a new {@code ProgressParts} based on the number of progresses
	** (done, started, known) in the given iterable, with the given total
	** estimate. If the input estimate is {@code 0}, then the estimate of the
	** resulting object is taken to be the same as the parts known.
	*/
	public static ProgressParts getParts(Iterable<? extends Progress> subprogress, int estimate) throws TaskAbortException {
		int d = 0, s = 0, k = 0;
		for (Progress p: subprogress) {
			++k;
			if (p == null) { continue; }
			++s;
			if (p.isDone()) { ++d; }
		}
		return new ProgressParts(d, s, k, estimate == 0? k: estimate);
	}

}
