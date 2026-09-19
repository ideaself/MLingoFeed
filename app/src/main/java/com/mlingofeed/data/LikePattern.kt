package com.mlingofeed.data

/**
 * Escapes `%`, `_` and `\` so user input matches literally in a
 * `LIKE '%' || :query || '%' ESCAPE '\'` query.
 */
internal fun escapeLikePattern(value: String): String = value
    .replace("\\", "\\\\")
    .replace("%", "\\%")
    .replace("_", "\\_")
