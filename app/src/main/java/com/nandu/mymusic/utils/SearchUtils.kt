package com.nandu.mymusic.utils

/**
 * Intelligent query matcher for song/artist/album search.
 *
 * Matching strategy (Spotify-style):
 * 1. Both the query and each field are normalized — diacritics stripped,
 *    whitespace collapsed/removed, lowercased — so "letgo" matches "Let Go"
 *    and "résumé" matches "Resume".
 * 2. The query is split into **tokens** on whitespace. ALL tokens must match
 *    (AND) so a multi-word query like "sun flower" matches "Sunflower".
 * 3. Each token matches a field if it is a substring of the field OR is within
 *    a small [Levenshtein edit distance] of any whitespace-delimited word in
 *    the field — giving typo tolerance: "sunflowr" matches "sunflower".
 */
object SearchUtils {

    /** Max edit distance for a token to still match a word. Scaled slightly for longer tokens. */
    private fun maxEdits(token: String): Int = if (token.length <= 4) 1 else 2

    /** Collapse whitespace + lowercase + strip diacritics for matching. */
    fun normalizeQuery(query: String): String = normalizeText(query)

    /** Returns true if [query] matches any of the non-null text fields. */
    fun matches(query: String, title: String?, artist: String?, album: String?): Boolean {
        if (query.isEmpty()) return false
        val q = normalizeQuery(query)
        if (q.isEmpty()) return false
        return normalizeText(title).let { matchesField(q, it) }
            || normalizeText(artist).let { matchesField(q, it) }
            || normalizeText(album).let { matchesField(q, it) }
    }

    /** True if the normalized query matches the normalized field via tokens. */
    private fun matchesField(query: String, field: String): Boolean {
        val tokens = query.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        return tokens.all { token -> matchesToken(token, field) }
    }

    /** True if a single token is a substring of, or within edit distance of, any word in [field]. */
    private fun matchesToken(token: String, field: String): Boolean {
        if (token in field) return true
        val words = field.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val cap = maxEdits(token)
        return words.any { word -> levenshtein(token, word, cap) <= cap }
    }

    /** Collapse whitespace + lowercase + strip diacritics — for both indexing and queries. */
    fun normalizeText(value: String?): String {
        if (value == null) return ""
        // Strip diacritics, collapse all whitespace to nothing, lowercase.
        val noMarks = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (i in noMarks.indices) {
            val ch = noMarks[i]
            // NON_SPACING_MARK (6) — skip combining diacritics in one go.
            if (java.lang.Character.getType(ch) == 6) continue
            if (ch.isWhitespace()) continue
            sb.append(ch)
        }
        return sb.toString().lowercase()
    }

    /** Bounded Levenshtein distance — returns [cap + 1] if distance exceeds [cap] for early exit. */
    private fun levenshtein(a: String, b: String, cap: Int): Int {
        if (a.isEmpty()) return if (b.length <= cap) b.length else cap + 1
        if (b.isEmpty()) return if (a.length <= cap) a.length else cap + 1
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            var minRow = curr[0]
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost)
                minRow = minOf(minRow, curr[j + 1])
            }
            if (minRow > cap) return cap + 1 // early exit — too far
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
