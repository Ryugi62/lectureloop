package io.github.ryugi62.lectureloop.domain

/** Second in the recording where the supporting sentence starts. */
@JvmInline
value class Timestamp(val seconds: Int) : Comparable<Timestamp> {
    init {
        require(seconds >= 0) { "Timestamp cannot be negative: $seconds" }
    }

    val label: String
        get() {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }

    override fun compareTo(other: Timestamp): Int = seconds.compareTo(other.seconds)

    companion object {
        private val pattern = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""")

        /** Accepts "MM:SS" or "H:MM:SS", optionally wrapped in brackets. Returns null for anything else. */
        fun parse(text: String): Timestamp? {
            val match = pattern.matchEntire(text.trim().removePrefix("[").removeSuffix("]").trim()) ?: return null
            val (h, m, s) = match.destructured
            val minutes = m.toInt()
            val secs = s.toInt()
            if (secs > 59 || (h.isNotEmpty() && minutes > 59)) return null
            return Timestamp((h.toIntOrNull() ?: 0) * 3600 + minutes * 60 + secs)
        }
    }
}
