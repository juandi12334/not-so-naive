package co.edu.ufps.notsonaive.algorithm;

import co.edu.ufps.notsonaive.exception.InvalidPatternException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NotSoNaiveTest {

    private static int[] find(String text, String pattern) {
        return new NotSoNaive(pattern).search(text).positionsArray();
    }

    /**
     * Independent brute-force reference used ONLY in tests to cross-check
     * the production algorithm. It never runs in production code.
     */
    private static int[] reference(String y, String x) {
        List<Integer> out = new ArrayList<>();
        for (int j = 0; j + x.length() <= y.length(); j++) {
            int i = 0;
            while (i < x.length() && x.charAt(i) == y.charAt(j + i)) {
                i++;
            }
            if (i == x.length()) {
                out.add(j);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    @ParameterizedTest(name = "Case {0}: \"{1}\" / \"{2}\" -> {3} at [{4}]")
    @DisplayName("Professor's required cases (count and exact positions)")
    @CsvSource(delimiter = ';', value = {
            "1;  ABABAB;     AB;    3; 0 2 4",
            "2;  ABCDEFG;    XY;    0; ''",
            "3;  AAAAAA;     AA;    5; 0 1 2 3 4",
            "4;  ABCABCABC;  ABC;   3; 0 3 6",
            "5;  A;          A;     1; 0",
            "6;  ABC;        ABCDE; 0; ''",
            "7;  BANANA;     ANA;   2; 1 3",
            "8;  ABABABAB;   ABA;   3; 0 2 4",
            "9;  ABCABC;     ABC;   2; 0 3",
            "10; aAaAaA;     aA;    3; 0 2 4"
    })
    void professorCases(int id, String text, String pattern, int expectedCount, String expectedPositions) {
        int[] expected = expectedPositions.isBlank() ? new int[0]
                : java.util.Arrays.stream(expectedPositions.trim().split(" ")).mapToInt(Integer::parseInt).toArray();
        SearchStatistics s = new NotSoNaive(pattern).search(text);
        assertEquals(expectedCount, s.getMatchCount(), "count for case " + id);
        assertArrayEquals(expected, s.positionsArray(), "positions for case " + id);
    }

    @Test
    void preprocessingWhenFirstTwoCharactersAreEqual() {
        NotSoNaive nsn = new NotSoNaive("AAB");
        assertEquals(2, nsn.getK());
        assertEquals(1, nsn.getEll());
    }

    @Test
    void preprocessingWhenFirstTwoCharactersDiffer() {
        NotSoNaive nsn = new NotSoNaive("ABA");
        assertEquals(1, nsn.getK());
        assertEquals(2, nsn.getEll());
    }

    @Test
    void lecroqReferenceExample() {
        // Example used on the ESMAJ page: one occurrence at position 5.
        assertArrayEquals(new int[]{5}, find("GCATCGCAGAGAGTATACAGTACG", "GCAGAGAG"));
    }

    @Test
    void emptyPatternIsRejected() {
        assertThrows(InvalidPatternException.class, () -> new NotSoNaive(""));
        assertThrows(InvalidPatternException.class, () -> new NotSoNaive(null));
    }

    @Test
    void singleCharacterPattern() {
        assertArrayEquals(new int[]{1, 3, 5}, find("abababx", "b"));
        assertArrayEquals(new int[]{0, 1, 2}, find("zzz", "z"));
        assertArrayEquals(new int[0], find("abc", "q"));
    }

    @Test
    void patternLongerThanTextAndEmptyText() {
        assertEquals(0, new NotSoNaive("ABCDE").search("ABC").getMatchCount());
        assertEquals(0, new NotSoNaive("A").search("").getMatchCount());
        assertEquals(0, new NotSoNaive("AB").search("").getMatchCount());
    }

    @Test
    void patternEqualToText() {
        assertArrayEquals(new int[]{0}, find("Hello", "Hello"));
        assertArrayEquals(new int[]{0}, find("AB", "AB"));
    }

    @Test
    void matchesAtBeginningAndEnd() {
        assertArrayEquals(new int[]{0, 10}, find("xyz middlexyz", "xyz"));
    }

    @Test
    void consecutiveAndOverlappingMatches() {
        assertArrayEquals(new int[]{0, 2, 4}, find("ababab", "ab"));
        assertArrayEquals(new int[]{0, 1, 2, 3}, find("aaaaaa", "aaa"));
        assertArrayEquals(new int[]{0, 3, 6}, find("aabaabaab", "aab"));
        assertArrayEquals(new int[]{0, 2}, find("ababa", "aba"));
    }

    @Test
    void specialAsciiCharacters() {
        assertArrayEquals(new int[]{3, 11}, find("a+b*(c)?[d]*(c)?", "*(c)?"));
        assertArrayEquals(new int[]{4}, find("line\nnext", "\nn"));
        assertArrayEquals(new int[]{1}, find("$^.|\\", "^.|"));
    }

    @Test
    void searchIsCaseSensitive() {
        assertArrayEquals(new int[]{5}, find("Java java JAVA", "java"));
        assertArrayEquals(new int[0], find("ABC", "abc"));
    }

    @Test
    void worstCaseComparisonsAreQuadratic() {
        // y = a^n, x = a^m: every attempt matches x[1], compares x[2..m-1] and x[0],
        // shifting ell = 1. Attempts = n-m+1 and comparisons = (n-m+1) * m.
        int n = 2000, m = 50;
        SearchStatistics s = new NotSoNaive("a".repeat(m)).search("a".repeat(n));
        assertEquals(n - m + 1, s.getMatchCount());
        assertEquals(n - m + 1, s.getAttempts());
        assertEquals((long) (n - m + 1) * m, s.getComparisons());
    }

    @Test
    void traceRecordsEveryAttemptWithCorrectShift() {
        SearchStatistics s = new NotSoNaive("ANA").searchWithTrace("BANANA");
        assertNotNull(s.getTrace());
        assertEquals(s.getAttempts(), s.getTrace().getTamanio());
        int expectedJ = 0;
        for (TraceStep step : s.getTrace()) {
            assertEquals(expectedJ, step.j());
            expectedJ += step.shift();
        }
    }

    @Test
    @DisplayName("Randomized differential test against a test-only brute force")
    void randomizedAgainstReference() {
        Random rnd = new Random(20261112L);
        String[] alphabets = {"ab", "abc", "aA", "ACGT", "a b\n"};
        for (int iter = 0; iter < 20_000; iter++) {
            String alpha = alphabets[iter % alphabets.length];
            String y = randomString(rnd, alpha, rnd.nextInt(40));
            String x = randomString(rnd, alpha, 1 + rnd.nextInt(6));
            assertArrayEquals(reference(y, x), find(y, x), () -> "text=" + y + " pattern=" + x);
        }
    }

    private static String randomString(Random rnd, String alpha, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alpha.charAt(rnd.nextInt(alpha.length())));
        }
        return sb.toString();
    }

    private static int[] visitedJ(SearchStatistics s) {
        int[] out = new int[s.getTrace().getTamanio()];
        int k = 0;
        for (TraceStep t : s.getTrace()) {
            out[k++] = t.j();
        }
        return out;
    }

    @Test
    @DisplayName("Trace of the Lecroq example: visited j values, stop index and final j")
    void traceOfLecroqExample() {
        SearchStatistics s = new NotSoNaive("GCAGAGAG").searchWithTrace("GCATCGCAGAGAGTAT");
        assertArrayEquals(new int[]{5}, s.positionsArray());
        assertEquals(1, s.getK());
        assertEquals(2, s.getEll());
        assertArrayEquals(new int[]{0, 2, 3, 5, 7, 8}, visitedJ(s));
        assertEquals(9, s.getFinalJ());
        TraceStep first = s.getTrace().get(0);
        assertEquals(TraceStep.Outcome.MISMATCH_REST, first.outcome());
        assertEquals(3, first.stopI(), "block j = 0 stops with a mismatch at i = 3");
        TraceStep match = s.getTrace().get(3);
        assertEquals(5, match.j());
        assertEquals(TraceStep.Outcome.MATCH, match.outcome());
        assertEquals(8, match.stopI(), "i reaches m on a full match");
    }

    @Test
    void traceOfOverlappingAs() {
        SearchStatistics s = new NotSoNaive("AA").searchWithTrace("AAAAAA");
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, visitedJ(s));
        assertEquals(5, s.getFinalJ());
        for (TraceStep t : s.getTrace()) {
            assertEquals(TraceStep.Outcome.MATCH, t.outcome());
            assertEquals(2, t.stopI(), "m = 2: the inner loop does not run, i stays at 2 = m");
            assertEquals(1, t.shift());
        }
    }

    @Test
    void finalJWhenThePatternIsLongerThanTheText() {
        SearchStatistics s = new NotSoNaive("ABCDE").searchWithTrace("ABC");
        assertEquals(0, s.getTrace().getTamanio());
        assertEquals(0, s.getFinalJ());
    }

    @Test
    void traceOfOneCharacterPattern() {
        SearchStatistics s = new NotSoNaive("b").searchWithTrace("abcb");
        assertArrayEquals(new int[]{0, 1, 2, 3}, visitedJ(s));
        assertEquals(4, s.getFinalJ());
        assertArrayEquals(new int[]{1, 3}, s.positionsArray());
    }

    @Test
    @DisplayName("Randomized: the trace matches are exactly the reported positions and replay the search")
    void traceAgreesWithPositions() {
        Random rnd = new Random(7L);
        for (int iter = 0; iter < 5_000; iter++) {
            String y = randomString(rnd, "ab", rnd.nextInt(30));
            String x = randomString(rnd, "ab", 1 + rnd.nextInt(5));
            SearchStatistics s = new NotSoNaive(x).searchWithTrace(y);
            List<Integer> fromTrace = new ArrayList<>();
            int expectedJ = 0;
            for (TraceStep t : s.getTrace()) {
                assertEquals(expectedJ, t.j());
                expectedJ += t.shift();
                if (t.outcome() == TraceStep.Outcome.MATCH) {
                    fromTrace.add(t.j());
                }
                if (x.length() > 1 && t.outcome() != TraceStep.Outcome.MISMATCH_SECOND) {
                    // replay the inner loop from stopI: x[2..stopI-1] equal, x[stopI] different (if < m)
                    for (int i = 2; i < t.stopI(); i++) {
                        assertEquals(x.charAt(i), y.charAt(t.j() + i));
                    }
                    if (t.stopI() < x.length()) {
                        assertNotEquals(x.charAt(t.stopI()), y.charAt(t.j() + t.stopI()));
                    }
                }
            }
            assertEquals(expectedJ, s.getFinalJ());
            assertArrayEquals(fromTrace.stream().mapToInt(Integer::intValue).toArray(), s.positionsArray(),
                    () -> "text=" + y + " pattern=" + x);
        }
    }
}
