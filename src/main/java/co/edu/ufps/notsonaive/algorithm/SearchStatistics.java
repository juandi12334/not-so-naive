package co.edu.ufps.notsonaive.algorithm;

import co.edu.ufps.notsonaive.support.SeedCollections;
import ufps.util.colecciones_seed.ListaCD;

/**
 * Immutable output of a Not So Naive run: the occurrences found (kept in a
 * SEED {@link ListaCD}) and instrumentation counters used to discuss the
 * algorithm's behaviour. Counters are not the time complexity; they only
 * count character comparisons and attempts for a particular input.
 */
public final class SearchStatistics {

    private final ListaCD<Integer> positions;
    private final long comparisons;
    private final long attempts;
    private final int k;
    private final int ell;
    private final int patternLength;
    private final int textLength;
    private final ListaCD<TraceStep> trace;
    private final int finalJ;

    SearchStatistics(ListaCD<Integer> positions, long comparisons, long attempts, int k, int ell,
                     int patternLength, int textLength, ListaCD<TraceStep> trace, int finalJ) {
        this.positions = positions;
        this.comparisons = comparisons;
        this.attempts = attempts;
        this.k = k;
        this.ell = ell;
        this.patternLength = patternLength;
        this.textLength = textLength;
        this.trace = trace;
        this.finalJ = finalJ;
    }

    /** @return the value of j when the search loop ended (the first j with j &gt; n - m) */
    public int getFinalJ() {
        return finalJ;
    }

    /** @return occurrences in increasing order (0-based), as a SEED list */
    public ListaCD<Integer> getPositions() {
        return positions;
    }

    public int getMatchCount() {
        return positions.getTamanio();
    }

    public long getComparisons() {
        return comparisons;
    }

    public long getAttempts() {
        return attempts;
    }

    public int getK() {
        return k;
    }

    public int getEll() {
        return ell;
    }

    public int getPatternLength() {
        return patternLength;
    }

    public int getTextLength() {
        return textLength;
    }

    /** @return recorded attempts, or {@code null} when tracing was not requested */
    public ListaCD<TraceStep> getTrace() {
        return trace;
    }

    /** @return the positions copied into a plain array, in order */
    public int[] positionsArray() {
        return SeedCollections.toIntArray(positions);
    }
}
