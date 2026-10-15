package com.nandu.mymusic.utils

/**
 * Normalized query matcher for song/artist/album search.
 *
 * Collapses internal whitespace and diacritics so that a query like "letgo"
 * matches a title "Let Go" or "Résumé" matches "resume". Plain substring
 * matching ("let g".contains) already worked; the gap was multi-word titles
 * typed without separators — "letgo" failed against "Let Go" because the
 * space is literal. Normalizing both sides collapses it.
 */
object SearchUtils {

    /** Collapse whitespace + lowercase + strip diacritics for matching. */
    fun normalizeQuery(query: String): String {
        return normalizeText(query)
    }

    /** Returns true if [query] matches any of the non-null text fields. */
    fun matches(query: String, title: String?, artist: String?, album: String?): Boolean {
        if (query.isEmpty()) return false
        val q = normalizeQuery(query)
        if (q.isEmpty()) return false
        return normalizeText(title).contains(q)
                || normalizeText(artist).contains(q)
                || normalizeText(album).contains(q)
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
}
