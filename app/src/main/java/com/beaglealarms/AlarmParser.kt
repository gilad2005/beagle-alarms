package com.beaglealarms

/**
 * Finds every line that contains [name] and returns the alarm times
 * (minutes since midnight), sorted, without duplicates.
 *
 * - Section headers look like *ריקוד קצר*.
 * - A line with two times (14:15 - 14:00) uses the earlier one.
 * - If the section name contains [ruleName], the alarm is set [ruleLead] minutes earlier.
 */
object AlarmParser {
    private val header = Regex("^\\*+\\s*(.+?)\\s*\\*+$")
    private val time = Regex("(\\d{1,2}):(\\d{2})")

    fun parse(text: String, name: String, ruleName: String, ruleLead: Int): List<Int> {
        if (name.isEmpty()) return emptyList()
        var section = ""
        val result = sortedSetOf<Int>()
        for (raw in text.lines()) {
            val line = raw.trim()
            val head = header.find(line)
            if (head != null) {
                section = head.groupValues[1]
                continue
            }
            if (!line.contains(name)) continue
            val minutes = time.findAll(line).mapNotNull { m ->
                val h = m.groupValues[1].toInt()
                val mi = m.groupValues[2].toInt()
                if (h < 24 && mi < 60) h * 60 + mi else null
            }.toList()
            val base = minutes.minOrNull() ?: continue
            val lead = if (ruleName.isNotEmpty() && section.contains(ruleName)) ruleLead else 0
            result.add(((base - lead) % 1440 + 1440) % 1440)
        }
        return result.toList()
    }
}
