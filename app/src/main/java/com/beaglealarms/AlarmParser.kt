package com.beaglealarms

data class AlarmItem(val minutes: Int, val label: String)

/**
 * Finds every line that contains one of [names] and returns the alarms to set.
 *
 * - Section headers look like *Task name*. The section name becomes the alarm label.
 * - A line with several times (14:15 - 14:00) uses the earliest one.
 * - [defaultLead] minutes are subtracted from every alarm.
 * - [rules] maps a task name (matched by "contains") to its own lead time in minutes,
 *   which replaces [defaultLead] for that task.
 * - The result is sorted by time, without duplicates.
 */
object AlarmParser {
    private val header = Regex("^\\*+\\s*(.+?)\\s*\\*+$")
    private val time = Regex("(\\d{1,2}):(\\d{2})")
    private val rule = Regex("^(.+?)\\s*[=:]\\s*(\\d{1,3})$")

    /** Names separated by commas or new lines. */
    fun parseNames(text: String): List<String> =
        text.split(',', '\n', '،').map { it.trim() }.filter { it.isNotEmpty() }

    /** The task names found in the message, in order: every *Header* line. */
    fun sections(text: String): List<String> =
        text.lines().mapNotNull { header.find(it.trim())?.groupValues?.get(1) }.distinct()

    /** One rule per line, for example "Task name = 30". */
    fun parseRules(text: String): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()
        for (raw in text.lines()) {
            val m = rule.find(raw.trim()) ?: continue
            result[m.groupValues[1].trim()] = m.groupValues[2].toInt()
        }
        return result
    }

    fun parse(text: String, names: List<String>, rules: Map<String, Int>, defaultLead: Int): List<AlarmItem> {
        if (names.isEmpty()) return emptyList()
        var section = ""
        val result = LinkedHashSet<AlarmItem>()
        for (raw in text.lines()) {
            val line = raw.trim()
            val head = header.find(line)
            if (head != null) {
                section = head.groupValues[1]
                continue
            }
            if (names.none { line.contains(it) }) continue
            val minutes = time.findAll(line).mapNotNull { m ->
                val h = m.groupValues[1].toInt()
                val mi = m.groupValues[2].toInt()
                if (h < 24 && mi < 60) h * 60 + mi else null
            }.toList()
            val base = minutes.minOrNull() ?: continue
            val lead = rules.entries.firstOrNull { section.contains(it.key) }?.value ?: defaultLead
            val label = if (section.isNotEmpty()) section else names.first()
            result.add(AlarmItem(((base - lead) % 1440 + 1440) % 1440, label))
        }
        return result.sortedWith(compareBy({ it.minutes }, { it.label }))
    }
}
