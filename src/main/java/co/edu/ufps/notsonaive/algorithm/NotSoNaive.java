package co.edu.ufps.notsonaive.algorithm;

import co.edu.ufps.notsonaive.exception.InvalidPatternException;
import ufps.util.colecciones_seed.ListaCD;

/**
 * Not So Naive exact string matching algorithm (Hancart, 1992), implemented
 * as a faithful translation of the C code published by C. Charras and
 * T. Lecroq in "Exact String Matching Algorithms" (ESMAJ):
 * https://www-igm.univ-mlv.fr/~lecroq/string/node13.html
 *
 * <pre>
 * Preprocessing (O(1) time, O(1) space):
 *   if x[0] == x[1] then k = 2, ell = 1
 *   else                 k = 1, ell = 2
 *
 * Searching (O(n*m) worst case):
 *   j = 0
 *   while j &lt;= n - m:
 *     if x[1] != y[j+1]:            j += k
 *     else:
 *       if x[2..m-1] == y[j+2..j+m-1] and x[0] == y[j]: report j
 *       j += ell
 * </pre>
 *
 * Conventions:
 * <ul>
 *   <li>The search is case sensitive: characters are compared by exact value.</li>
 *   <li>Positions are 0-based indexes of the first character of each occurrence.</li>
 *   <li>Overlapping occurrences are all reported.</li>
 *   <li>A one-character pattern has no x[1]; it is handled by the degenerate
 *       case where every window is checked by comparing x[0] only and the
 *       window shifts one position (k = ell = 1).</li>
 * </ul>
 *
 * Positions are collected in a SEED {@link ListaCD} (circular doubly linked
 * list with header node), whose {@code insertarAlFinal} runs in O(1).
 */
public final class NotSoNaive {

    /** Maximum number of trace steps recorded for the step-by-step view. */
    public static final int MAX_TRACE_STEPS = 500;

    private final char[] x;
    private final int m;
    private final int k;
    private final int ell;

    /**
     * Builds the matcher and runs the constant-time preprocessing phase.
     *
     * @param pattern the pattern x; must not be null or empty
     * @throws InvalidPatternException if the pattern is null or empty
     */
    public NotSoNaive(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new InvalidPatternException("The pattern must not be empty.");
        }
        this.x = pattern.toCharArray();
        this.m = x.length;
        if (m == 1) {
            this.k = 1;
            this.ell = 1;
        } else if (x[0] == x[1]) {
            this.k = 2;
            this.ell = 1;
        } else {
            this.k = 1;
            this.ell = 2;
        }
    }

    /**
     * Searches every occurrence of the pattern in the text.
     *
     * @param text the text y
     * @return the positions found plus instrumentation counters
     */
    public SearchStatistics search(String text) {
        return run(text == null ? new char[0] : text.toCharArray(), null);
    }

    /**
     * Same search, additionally recording each attempt (window) so the
     * algorithm can be explained step by step. Recording stops after
     * {@link #MAX_TRACE_STEPS} attempts; the search itself always completes.
     *
     * @param text the text y
     * @return the statistics, with the trace list filled in
     */
    public SearchStatistics searchWithTrace(String text) {
        return run(text == null ? new char[0] : text.toCharArray(), new ListaCD<>());
    }

    private SearchStatistics run(char[] y, ListaCD<TraceStep> trace) {
        final int n = y.length;
        final ListaCD<Integer> positions = new ListaCD<>();
        long comparisons = 0;
        long attempts = 0;

        if (m > n) { // also covers the empty text
            return new SearchStatistics(positions, comparisons, attempts, k, ell, m, n, trace, 0);
        }

        if (m == 1) {
            final char c = x[0];
            for (int j = 0; j <= n - 1; j++) {
                attempts++;
                comparisons++;
                boolean match = c == y[j];
                if (match) {
                    positions.insertarAlFinal(j);
                }
                record(trace, j, match ? TraceStep.Outcome.MATCH : TraceStep.Outcome.MISMATCH_FIRST, 1, 1, 0);
            }
            return new SearchStatistics(positions, comparisons, attempts, k, ell, m, n, trace, n);
        }

        int j = 0;
        while (j <= n - m) {
            attempts++;
            long before = comparisons;
            comparisons++;
            if (x[1] != y[j + 1]) {
                record(trace, j, TraceStep.Outcome.MISMATCH_SECOND, k, comparisons - before, 1);
                j += k;
            } else {
                // memcmp(x + 2, y + j + 2, m - 2): compare positions 2 .. m-1
                int i = 2;
                while (i < m) {
                    comparisons++;
                    if (x[i] != y[j + i]) {
                        break;
                    }
                    i++;
                }
                TraceStep.Outcome outcome;
                if (i == m) {
                    // only now compare x[0] with y[j] (the "&&" short-circuit of the C code)
                    comparisons++;
                    if (x[0] == y[j]) {
                        positions.insertarAlFinal(j);
                        outcome = TraceStep.Outcome.MATCH;
                    } else {
                        outcome = TraceStep.Outcome.MISMATCH_FIRST;
                    }
                } else {
                    outcome = TraceStep.Outcome.MISMATCH_REST;
                }
                record(trace, j, outcome, ell, comparisons - before, i);
                j += ell;
            }
        }
        return new SearchStatistics(positions, comparisons, attempts, k, ell, m, n, trace, j);
    }

    private static void record(ListaCD<TraceStep> trace, int j, TraceStep.Outcome outcome,
                               int shift, long comparisons, int stopI) {
        if (trace != null && trace.getTamanio() < MAX_TRACE_STEPS) {
            trace.insertarAlFinal(new TraceStep(j, outcome, shift, comparisons, stopI));
        }
    }

    public int getK() {
        return k;
    }

    public int getEll() {
        return ell;
    }

    public int getPatternLength() {
        return m;
    }
}
