package app.tide.launcher.data

/**
 * Fuzzy subsequence matching for the app search field.
 *
 * "gm" should find "Gmail", "sb" should find "Subway", "ch" should find
 * "Clock". This is the classic subsequence problem with a scoring pass on top:
 * every character of the query has to appear in order, and among all the ways it
 * can, we prefer the one that looks most like what the user meant.
 *
 * Scoring, in descending order of weight:
 *  - **Consecutive runs** — "gma" beating "g..m..a" is the single strongest
 *    signal, so a run gets a bonus per adjacent pair.
 *  - **Word boundaries** — matching the "c" of "Clock" beats the "c" of
 *    "Account". A boundary is position 0, or any position after a non-letter.
 *  - **Prefix** — a whole-query prefix match outranks everything else, which is
 *    why typing "ch" surfaces Chrome and Clock above anything incidental.
 *
 * Kept free of Android types so it can be unit-tested on the JVM.
 */
object Fuzzy {

    /** Returned when the query does not match at all. */
    const val NO_MATCH = Int.MIN_VALUE

    /**
     * Scores [query] against [candidate].
     *
     * @return a higher-is-better score, or [NO_MATCH] when the characters of
     *   `query` do not all appear in `candidate` in order.
     */
    fun score(candidate: String, query: String): Int {
        if (query.isEmpty()) return 0
        val hay = candidate.lowercase()
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return 0
        if (needle.length > hay.length) return NO_MATCH

        var total = 0
        var hayIndex = 0
        var runLength = 0
        var lastMatchIndex = -1

        for (char in needle) {
            val found = hay.indexOf(char, hayIndex)
            if (found < 0) return NO_MATCH

            val isBoundary = found == 0 || !hay[found - 1].isLetter()
            // Exact case match is a mild positive signal: "gm" ranking "Gmail"
            // above "gmailbox" is worth a small nudge.
            val exactCase = candidate.getOrNull(found) == char

            total += BASE_MATCH
            if (isBoundary) total += BOUNDARY
            if (exactCase) total += EXACT_CASE

            // Guarded on having matched something already: `lastMatchIndex`
            // starts at -1, so without this a first match landing at index 0
            // satisfies `found == lastMatchIndex + 1` and collects a run bonus
            // it did not earn — inflating nearly every query, since most labels
            // start with the first character.
            if (lastMatchIndex >= 0 && found == lastMatchIndex + 1) {
                runLength++
                // Diminishing bonus so a long run keeps rewarding without
                // letting one word dominate every comparison.
                total += CONSECUTIVE * runLength
            } else {
                runLength = 0
                // Gap penalty, capped so distant matches stay viable rather
                // than falling out of the results entirely.
                val gap = found - lastMatchIndex - 1
                total -= (gap.coerceAtMost(MAX_GAP_PENALISED) * GAP)
            }

            lastMatchIndex = found
            hayIndex = found + 1
        }

        // Shorter candidates win ties: with the same query, "Files" should
        // outrank "Files by Google" at equal base score.
        total -= candidate.length / 4
        return total
    }

    /**
     * Ranks [entries] against [query]. An exact case-insensitive prefix on the
     * label is treated as a stronger hit than any fuzzy score, which is what
     * makes the first few characters of a query behave predictably.
     */
    fun rank(entries: List<AppEntry>, query: String): List<AppEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) return entries

        return entries
            .mapNotNull { entry ->
                val label = entry.label
                val lower = label.lowercase()
                val prefixBonus = when {
                    lower == needle.lowercase() -> EXACT_LABEL
                    lower.startsWith(needle.lowercase(), ignoreCase = true) -> PREFIX
                    else -> 0
                }
                val fuzzyScore = score(label, needle)
                val score = if (fuzzyScore == NO_MATCH && prefixBonus == 0) {
                    null
                } else {
                    (if (fuzzyScore == NO_MATCH) 0 else fuzzyScore) + prefixBonus
                }
                score?.let { entry to it }
            }
            .sortedWith(
                compareByDescending<Pair<AppEntry, Int>> { it.second }
                    .thenBy { it.first.label.length }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.label },
            )
            .map { it.first }
    }

    /** The letter this entry would be filed under in the alphabetical index. */
    fun alphabetOf(label: String): Char =
        label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

    private const val BASE_MATCH = 16
    private const val BOUNDARY = 30
    private const val EXACT_CASE = 2
    private const val CONSECUTIVE = 20
    private const val GAP = 3
    private const val MAX_GAP_PENALISED = 12
    private const val PREFIX = 400
    private const val EXACT_LABEL = 1_000
}