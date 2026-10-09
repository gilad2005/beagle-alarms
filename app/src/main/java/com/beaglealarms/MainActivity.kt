package com.beaglealarms

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

private val BG = Color.parseColor("#0E1A21")
private val CARD = Color.parseColor("#172730")
private val FIELD = Color.parseColor("#0E1A21")
private val LINE = Color.parseColor("#26404C")
private val TEXT = Color.parseColor("#E8F0F3")
private val MUTED = Color.parseColor("#8DA4AF")
private val ACCENT = Color.parseColor("#F29A4A")
private val ON_ACCENT = Color.parseColor("#1D1105")
private val BLUE = Color.parseColor("#6FB3D2")
private val HIGHLIGHT = Color.parseColor("#66F29A4A")

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var messageBox: EditText
    private lateinit var nameBox: EditText
    private lateinit var leadBox: EditText
    private lateinit var rulesList: LinearLayout
    private lateinit var chipsWrap: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var countView: TextView
    private lateinit var dayView: TextView
    private lateinit var setButton: Button
    private lateinit var statusView: TextView
    private val segViews = ArrayList<TextView>()

    private val handler = Handler(Looper.getMainLooper())
    private var items: List<AlarmItem> = emptyList()
    private var lastClip: String = ""

    /** 0 = automatic, 1 = today, 2 = tomorrow */
    private var dayMode = 0

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { saveAndRefresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        dayMode = prefs.getInt("dayMode", 0)
        buildUi()
        handleIncoming()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) pasteFromClipboard()
    }

    // ---------------------------------------------------------------- input

    /** Whatever was copied (for example from WhatsApp) is pasted automatically when the app opens. */
    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this).toString()
        val names = AlarmParser.parseNames(nameBox.text.toString())
        if (text.isBlank() || text == lastClip) return
        if (names.isNotEmpty() && names.none { text.contains(it) }) return
        lastClip = text
        messageBox.setText(text)
        statusView.text = ""
    }

    private fun handleIncoming() {
        val i = intent ?: return
        val text: String? = when (i.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) {
            lastClip = text
            messageBox.setText(text)
        }
    }

    // ------------------------------------------------------------- settings

    private fun rulesText(): String =
        (0 until rulesList.childCount).mapNotNull { i ->
            val row = rulesList.getChildAt(i) as LinearLayout
            val n = (row.getChildAt(0) as EditText).text.toString().replace(Regex("[=:]"), " ").trim()
            val m = (row.getChildAt(1) as EditText).text.toString().trim()
            if (n.isEmpty() || m.isEmpty()) null else "$n = $m"
        }.joinToString("\n")

    private fun saveAndRefresh() {
        prefs.edit()
            .putString("names", nameBox.text.toString())
            .putString("lead", leadBox.text.toString())
            .putString("rules", rulesText())
            .putInt("dayMode", dayMode)
            .apply()
        refresh()
    }

    private fun addRuleRow(name: String, minutes: String, focusMinutes: Boolean = false) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameField = field(name).apply { hint = "שם המשימה" }
        val minField = field(minutes, number = true).apply {
            hint = "דקות"
            gravity = Gravity.CENTER
        }
        val remove = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(4), dp(8))
            setOnClickListener {
                rulesList.removeView(row)
                saveAndRefresh()
            }
        }
        row.addView(nameField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
        row.addView(minField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = dp(8) })
        row.addView(remove)
        nameField.addTextChangedListener(watcher)
        minField.addTextChangedListener(watcher)
        rulesList.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        if (focusMinutes) minField.requestFocus()
    }

    /** Task names found in the message that do not have an early-alarm rule yet. */
    private fun updateChips() {
        val existing = AlarmParser.parseRules(rulesText()).keys
        val free = AlarmParser.sections(messageBox.text.toString()).filter { s -> existing.none { it == s } }
        chipsRow.removeAllViews()
        free.forEach { name ->
            chipsRow.addView(TextView(this).apply {
                text = "＋ $name"
                textSize = 14f
                setTextColor(BLUE)
                background = rounded(FIELD, 20)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    addRuleRow(name, "", focusMinutes = true)
                    saveAndRefresh()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
        chipsWrap.visibility = if (free.isEmpty()) View.GONE else View.VISIBLE
    }

    // ------------------------------------------------------------------ day

    private fun detectedDay(): Pair<Int, String> {
        val head = messageBox.text.toString().take(200)
        return when {
            head.contains("מחר") -> 1 to "לפי ההודעה"
            head.contains("היום") -> 0 to "לפי ההודעה"
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 17 -> 1 to "לפי השעה"
            else -> 0 to "לפי השעה"
        }
    }

    /** 0 = today, 1 = tomorrow */
    private fun dayOffset(): Int = when (dayMode) {
        1 -> 0
        2 -> 1
        else -> detectedDay().first
    }

    private fun targetTime(item: AlarmItem): Calendar = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, dayOffset())
        set(Calendar.HOUR_OF_DAY, item.minutes / 60)
        set(Calendar.MINUTE, item.minutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun isPast(item: AlarmItem) = targetTime(item).timeInMillis <= System.currentTimeMillis()

    private fun updateSegments() {
        segViews.forEachIndexed { i, tv ->
            val on = i == dayMode
            tv.background = if (on) rounded(ACCENT, 10) else null
            tv.setTextColor(if (on) ON_ACCENT else MUTED)
        }
        val offset = dayOffset()
        val name = if (offset == 0) "היום" else "מחר"
        dayView.text = if (dayMode == 0) "אוטומטי: $name (${detectedDay().second})" else "השעונים ייקבעו ל$name"
    }

    // -------------------------------------------------------------- refresh

    private fun refresh() {
        val names = AlarmParser.parseNames(nameBox.text.toString())
        val rules = AlarmParser.parseRules(rulesText())
        val lead = leadBox.text.toString().toIntOrNull() ?: 0
        items = AlarmParser.parse(messageBox.text.toString(), names, rules, lead)
        highlightLines(names)
        updateChips()
        updateSegments()

        listBox.removeAllViews()
        if (items.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = if (names.isEmpty()) "הקלד בהגדרות את השם (או השמות) שלך." else
                    "אין עדיין שורות עם ${names.joinToString(", ") { "\"$it\"" }}.\nהעתק את ההודעה ופתח את האפליקציה."
                setTextColor(MUTED)
                textSize = 14f
            })
        } else {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    listBox.addView(View(this).apply { setBackgroundColor(LINE) },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
                }
                listBox.addView(row(item))
            }
        }
        countView.text = if (items.isEmpty()) "" else "${items.size} שעונים"
        setButton.isEnabled = items.isNotEmpty()
        setButton.alpha = if (items.isEmpty()) 0.4f else 1f
    }

    /** Marks every line of the message that contains one of the chosen names. */
    private fun highlightLines(names: List<String>) {
        val editable = messageBox.text
        editable.getSpans(0, editable.length, BackgroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }
        if (names.isEmpty()) return
        var start = 0
        for (line in editable.toString().split("\n")) {
            if (names.any { line.contains(it) }) {
                editable.setSpan(
                    BackgroundColorSpan(HIGHLIGHT), start, start + line.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            start += line.length + 1
        }
    }

    private fun row(item: AlarmItem): View {
        val past = isPast(item)
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            alpha = if (past) 0.4f else 1f
        }
        r.addView(TextView(this).apply {
            text = fmt(item.minutes)
            textSize = 26f
            setTextColor(ACCENT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textDirection = View.TEXT_DIRECTION_LTR
            minWidth = dp(88)
        })
        r.addView(TextView(this).apply {
            text = if (past) "${item.label} (עבר)" else item.label
            textSize = 18f
            setTextColor(TEXT)
        })
        return r
    }

    // --------------------------------------------------------------- alarms

    private fun setAlarms() {
        if (items.isEmpty()) return
        handler.removeCallbacksAndMessages(null)
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        var sent = 0
        var skipped = 0
        var weekly = 0

        items.forEach { item ->
            val t = targetTime(item)
            val diff = t.timeInMillis - now
            if (diff <= 0) {
                skipped++
                return@forEach
            }
            // The clock app rings at the next occurrence of the time. Further than 24 hours away
            // that would be the wrong day, so the weekday is passed as well.
            val needsDay = diff > dayMs
            if (needsDay) weekly++
            handler.postDelayed({
                val alarm = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, item.minutes / 60)
                    putExtra(AlarmClock.EXTRA_MINUTES, item.minutes % 60)
                    putExtra(AlarmClock.EXTRA_MESSAGE, item.label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    if (needsDay) {
                        putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, arrayListOf(t.get(Calendar.DAY_OF_WEEK)))
                    }
                }
                try {
                    startActivity(alarm)
                } catch (e: Exception) {
                    statusView.append("\nנכשל: ${fmt(item.minutes)} ${item.label}")
                }
            }, sent * 1200L)
            sent++
        }

        val day = if (dayOffset() == 0) "להיום" else "למחר"
        val sb = StringBuilder("נשלחו $sent שעונים $day לאפליקציית השעון.")
        if (skipped > 0) sb.append("\n$skipped שעות כבר עברו ולא נקבעו.")
        if (weekly > 0) sb.append("\n$weekly שעונים רחוקים מ-24 שעות, אז הם נקבעו כשעון שחוזר כל שבוע. כדאי למחוק אותם אחרי.")
        sb.append("\nכדאי לבדוק באפליקציית השעון שהכול נקבע.")
        statusView.text = sb.toString()
    }

    private fun openAlarmList() {
        try {
            startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        } catch (e: Exception) {
            statusView.text = "לא הצלחתי לפתוח את אפליקציית השעון."
        }
    }

    // ------------------------------------------------------------------ ui

    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        letterSpacing = 0.05f
        setTextColor(MUTED)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun smallLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(MUTED)
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun styled(e: EditText): EditText {
        e.setTextColor(TEXT)
        e.setHintTextColor(MUTED)
        e.background = rounded(FIELD, 12)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        return e
    }

    private fun field(value: String, number: Boolean = false) = styled(EditText(this)).apply {
        setText(value)
        inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 18)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun cardParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(14) }

    private fun headerRow(title: TextView, trailing: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(trailing)
        }

    private fun segmented(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(FIELD, 12)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        listOf("אוטומטי", "היום", "מחר").forEachIndexed { i, label ->
            val tv = TextView(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(9), 0, dp(9))
                setOnClickListener {
                    dayMode = i
                    saveAndRefresh()
                }
            }
            segViews.add(tv)
            box.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return box
    }

    private fun buildUi() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }

        column.addView(TextView(this).apply {
            text = "שעוני משמרות"
            textSize = 32f
            setTextColor(TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        column.addView(TextView(this).apply {
            text = "מעתיקים את ההודעה, פותחים כאן, ולוחצים פעם אחת. ההגדרות נשמרות."
            textSize = 14f
            setTextColor(MUTED)
            setPadding(0, dp(4), 0, dp(20))
        })

        // Message card
        val messageCard = card()
        val clear = TextView(this).apply {
            text = "נקה"
            textSize = 14f
            setTextColor(BLUE)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                messageBox.setText("")
                statusView.text = ""
            }
        }
        messageCard.addView(headerRow(sectionTitle("ההודעה"), clear))
        messageBox = styled(EditText(this)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            maxHeight = dp(280)
            gravity = Gravity.TOP or Gravity.START
            hint = "ההודעה תודבק כאן אוטומטית"
            isVerticalScrollBarEnabled = true
            setHorizontallyScrolling(false)
            // Let the message scroll by itself inside the page scroll.
            setOnTouchListener { v, event ->
                if (v.canScrollVertically(1) || v.canScrollVertically(-1)) {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
        }
        messageCard.addView(messageBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
        column.addView(messageCard, cardParams())

        // Alarms preview card
        val listCard = card()
        countView = TextView(this).apply {
            textSize = 13f
            setTextColor(ACCENT)
        }
        listCard.addView(headerRow(sectionTitle("השעונים שייקבעו"), countView))
        listCard.addView(segmented(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })
        dayView = TextView(this).apply {
            textSize = 12f
            setTextColor(MUTED)
            setPadding(dp(4), dp(6), 0, 0)
        }
        listCard.addView(dayView)
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        listCard.addView(listBox)
        column.addView(listCard, cardParams())

        // Settings card
        val settingsCard = card()
        settingsCard.addView(sectionTitle("הגדרות"))

        settingsCard.addView(smallLabel("השמות שלי (אפשר כמה, מופרדים בפסיק)"))
        nameBox = field(prefs.getString("names", "") ?: "").apply { hint = "למשל: דנה, דני" }
        settingsCard.addView(nameBox)

        settingsCard.addView(smallLabel("כמה דקות לפני המשמרת לצלצל (לכל השעונים)"))
        leadBox = field(prefs.getString("lead", "0") ?: "0", number = true)
        settingsCard.addView(leadBox)

        settingsCard.addView(smallLabel("משימות שצריכות שעון מוקדם יותר"))
        rulesList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        settingsCard.addView(rulesList)
        AlarmParser.parseRules(prefs.getString("rules", "") ?: "").forEach { (n, m) ->
            addRuleRow(n, m.toString())
        }

        chipsWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chipsWrap.addView(smallLabel("משימות בהודעה, הקש להוספה"))
        chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipsWrap.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipsRow)
        })
        settingsCard.addView(chipsWrap)

        settingsCard.addView(TextView(this).apply {
            text = "＋ הוסף משימה"
            textSize = 15f
            setTextColor(BLUE)
            setPadding(0, dp(14), 0, dp(2))
            setOnClickListener { addRuleRow("", "") }
        })
        column.addView(settingsCard, cardParams())

        // Action
        setButton = Button(this).apply {
            text = "קבע שעונים"
            textSize = 18f
            isAllCaps = false
            setTextColor(ON_ACCENT)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = rounded(ACCENT, 16)
            stateListAnimator = null
            minHeight = dp(58)
            setOnClickListener { setAlarms() }
        }
        column.addView(setButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val listButton = Button(this).apply {
            text = "פתח את רשימת השעונים"
            textSize = 15f
            isAllCaps = false
            setTextColor(BLUE)
            background = rounded(CARD, 16)
            stateListAnimator = null
            minHeight = dp(50)
            setOnClickListener { openAlarmList() }
        }
        column.addView(listButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(BLUE)
            setPadding(0, dp(14), 0, 0)
        }
        column.addView(statusView)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(column)
        }
        setContentView(scroll)

        messageBox.addTextChangedListener(watcher)
        nameBox.addTextChangedListener(watcher)
        leadBox.addTextChangedListener(watcher)
    }
}
