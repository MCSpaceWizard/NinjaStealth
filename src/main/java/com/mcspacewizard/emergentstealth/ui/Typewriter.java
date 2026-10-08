package com.mcspacewizard.emergentstealth.ui;

/**
 * Typewriter timing for dialogue (design doc 31 §2, Dialogue): how many characters of a line are visible
 * after a given time. Sentence ends pause longer than commas, so speech reads naturally.
 */
public final class Typewriter {
    private Typewriter() {}

    /** Extra pause after a sentence end, in characters' worth of time. */
    static final int SENTENCE_PAUSE = 7;
    /** Extra pause after a comma, colon or semicolon. */
    static final int CLAUSE_PAUSE = 3;

    /** Visible characters of {@code text} after {@code elapsedMs} at {@code charsPerSecond}. */
    public static int visible(String text, long elapsedMs, float charsPerSecond) {
        if (charsPerSecond <= 0.0F || elapsedMs < 0L) {
            return elapsedMs < 0L ? 0 : text.length();
        }
        float budget = elapsedMs / 1000.0F * charsPerSecond;
        float used = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            used += 1.0F;
            if (used > budget) {
                return i;
            }
            used += pauseAfter(text, i);
        }
        return text.length();
    }

    /** Time until the whole line is visible. */
    public static long durationMs(String text, float charsPerSecond) {
        if (charsPerSecond <= 0.0F) {
            return 0L;
        }
        float cost = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            cost += 1.0F;
            if (i < text.length() - 1) {
                cost += pauseAfter(text, i);
            }
        }
        return (long) Math.ceil(cost / charsPerSecond * 1000.0F);
    }

    private static int pauseAfter(String text, int i) {
        char c = text.charAt(i);
        boolean followedBySpace = i + 1 < text.length() && text.charAt(i + 1) == ' ';
        if ((c == '.' || c == '!' || c == '?') && followedBySpace) {
            return SENTENCE_PAUSE;
        }
        if ((c == ',' || c == ';' || c == ':') && followedBySpace) {
            return CLAUSE_PAUSE;
        }
        return 0;
    }
}
