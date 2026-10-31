package com.local.parallelvrgallerypro

data class CacheReplacementTarget(val version: String, val createdAt: Long)

/** A captured selection, not a fresh query of whichever version is current at completion. */
data class ImageWriteIntent(val replacements: List<CacheReplacementTarget> = emptyList(), val forceRender: Boolean = false) {
    fun encode(): String = "1|$forceRender|" + replacements.joinToString(";") { "${it.version}~${it.createdAt}" }

    companion object {
        fun decode(value: String?): ImageWriteIntent? = runCatching {
            val p = requireNotNull(value).split('|')
            require(p.size == 3 && p[0] == "1")
            val targets = if (p[2].isBlank()) emptyList() else p[2].split(';').map {
                val v = it.split('~'); require(v.size == 2 && v[0].matches(Regex("[A-Za-z0-9._-]+")))
                CacheReplacementTarget(v[0], v[1].toLong())
            }
            ImageWriteIntent(targets.distinct(), p[1].toBooleanStrict())
        }.getOrNull()
    }
}
