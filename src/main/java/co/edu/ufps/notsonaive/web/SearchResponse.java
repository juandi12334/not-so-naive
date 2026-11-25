package co.edu.ufps.notsonaive.web;

import co.edu.ufps.notsonaive.algorithm.TraceStep;
import co.edu.ufps.notsonaive.model.SearchResult;
import co.edu.ufps.notsonaive.model.TextDocument;
import ufps.util.colecciones_seed.ListaCD;

/**
 * JSON body returned by {@code POST /api/search}. Built from a {@link SearchResult};
 * collections are exposed as plain arrays copied from the SEED structures.
 */
public record SearchResponse(
        String algorithm,
        String fileName,
        long fileSizeBytes,
        int textLength,
        int lineCount,
        String pattern,
        int patternLength,
        int matchCount,
        int[] positions,
        boolean positionsTruncated,
        double elapsedMillis,
        long comparisons,
        long attempts,
        int k,
        int ell,
        String[] notices,
        String text,
        boolean previewTruncated,
        TraceRow[] trace,
        int finalJ,
        int traceLimit) {

    /**
     * One attempt (window) of the step-by-step trace, produced by the real NotSoNaive run.
     * {@code stopI} is the value of i when the inner loop ended: m when x[2..m-1] all
     * matched, 1 when the attempt stopped at x[1], 0 for one-character patterns.
     */
    public record TraceRow(int j, String outcome, int shift, long comparisons, int stopI) {
    }

    static SearchResponse of(SearchResult r, int maxPositions, int maxPreviewChars, int traceLimit) {
        TextDocument doc = r.getDocument();
        int count = r.getMatchCount();
        int returned = Math.min(count, maxPositions);
        int[] positions = new int[returned];
        for (int i = 0; i < returned; i++) {
            positions[i] = r.getPositions().get(i);
        }

        String[] notices = new String[doc.notices().getTamanio()];
        int n = 0;
        for (String notice : doc.notices()) {
            notices[n++] = notice;
        }

        TraceRow[] trace = null;
        ListaCD<TraceStep> steps = r.getStatistics().getTrace();
        if (steps != null) {
            trace = new TraceRow[steps.getTamanio()];
            int t = 0;
            for (TraceStep s : steps) {
                trace[t++] = new TraceRow(s.j(), s.outcome().name(), s.shift(), s.comparisons(), s.stopI());
            }
        }

        boolean previewTruncated = doc.content().length() > maxPreviewChars;
        return new SearchResponse(
                "Not So Naive",
                doc.fileName(),
                doc.sizeBytes(),
                doc.content().length(),
                doc.lineCount(),
                r.getPattern(),
                r.getPattern().length(),
                count,
                positions,
                returned < count,
                r.getElapsedNanos() / 1_000_000.0,
                r.getStatistics().getComparisons(),
                r.getStatistics().getAttempts(),
                r.getStatistics().getK(),
                r.getStatistics().getEll(),
                notices,
                previewTruncated ? doc.content().substring(0, maxPreviewChars) : doc.content(),
                previewTruncated,
                trace,
                r.getStatistics().getFinalJ(),
                traceLimit);
    }
}
