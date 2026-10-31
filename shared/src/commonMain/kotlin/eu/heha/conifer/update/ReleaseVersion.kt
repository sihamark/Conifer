package eu.heha.conifer.update

/** A `MAJOR.MINOR.PATCH` version, the bare form release tags take (`1.3.1`, no `v`). */
data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<ReleaseVersion> {

    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Null for anything but three plain numbers — a suffix like `-rc1` included, so such a tag
         * is never taken for a newer release.
         */
        fun parse(text: String): ReleaseVersion? {
            val parts = text.trim().split('.')
            if (parts.size != 3) return null
            val numbers = parts.map { part ->
                if (part.isEmpty() || !part.all(Char::isDigit)) return null
                part.toIntOrNull() ?: return null
            }
            return ReleaseVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}
