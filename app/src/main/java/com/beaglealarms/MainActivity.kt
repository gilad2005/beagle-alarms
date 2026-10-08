package com.beaglealarms

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

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

    private lateinit var messageBox: EditText
    private lateinit var nameBox: EditText
    private lateinit var ruleNameBox: EditText
    private lateinit var ruleLeadBox: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var countView: TextView
    private lateinit var setButton: Button
    private lateinit var deleteSwitch: Switch
    private lateinit var statusView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var items: List<AlarmItem> = emptyList()
    private var lastClip: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildUi()
        handleIncoming()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) pasteFromClipboard()
    }

    /** Whatever was copied (for example from WhatsApp) is pasted automatically when the app opens. */
    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this).toString()
        val name = nameBox.text.toString().trim()
        if (text.isBlank() || text == lastClip || name.isEmpty() || !text.contains(name)) return
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

    private fun refresh() {
        val name = nameBox.text.toString().trim()
        val ruleName = ruleNameBox.text.toString().trim()
        val ruleLead = ruleLeadBox.text.toString().toIntOrNull() ?: 0
        items = AlarmParser.parse(messageBox.text.toString(), name, ruleName, ruleLead)
        highlightLines(name)

        listBox.removeAllViews()
        if (items.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = if (name.isEmpty()) "הקלד שם לחיפוש." else
                    "אין עדיין שורות עם \"$name\".\nהעתק את ההודעה בוואטסאפ ופתח את האפליקציה."
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

    /** Marks every line of the message that contains the chosen name. */
    private fun highlightLines(name: String) {
        val editable = messageBox.text
        editable.getSpans(0, editable.length, BackgroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }
        if (name.isEmpty()) return
        var start = 0
        for (line in editable.toString().split("\n")) {
            if (line.contains(name)) {
                editable.setSpan(
                    BackgroundColorSpan(HIGHLIGHT), start, start + line.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            start += line.length + 1
        }
    }

    private fun row(item: AlarmItem): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
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
            text = item.label
            textSize = 18f
            setTextColor(TEXT)
        })
        return r
    }

    private fun loadLast(): List<AlarmItem> {
        val raw = getSharedPreferences("alarms", Context.MODE_PRIVATE).getString("last", "") ?: ""
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            val m = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            AlarmItem(m, parts.getOrElse(1) { "" })
        }
    }

    private fun saveLast(list: List<AlarmItem>) {
        getSharedPreferences("alarms", Context.MODE_PRIVATE).edit()
            .putString("last", list.joinToString("\n") { "${it.minutes}|${it.label}" })
            .apply()
    }

    private fun setAlarms() {
        if (items.isEmpty()) return
        handler.removeCallbacksAndMessages(null)
        val toSet = items
        val old = if (deleteSwitch.isChecked) loadLast() else emptyList()
        var delay = 0L

        // First remove the alarms this app set last time.
        old.forEach { item ->
            handler.postDelayed({
                val del = Intent(AlarmClock.ACTION_DELETE_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                    putExtra(AlarmClock.EXTRA_HOUR, item.minutes / 60)
                    putExtra(AlarmClock.EXTRA_MINUTES, item.minutes % 60)
                    putExtra(AlarmClock.EXTRA_IS_PM, item.minutes / 60 >= 12)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                try {
                    startActivity(del)
                } catch (e: Exception) {
                    // The clock app does not support deleting; the new alarms are still set.
                }
            }, delay)
            delay += 1200L
        }

        toSet.forEach { item ->
            handler.postDelayed({
                val alarm = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, item.minutes / 60)
                    putExtra(AlarmClock.EXTRA_MINUTES, item.minutes % 60)
                    putExtra(AlarmClock.EXTRA_MESSAGE, item.label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                try {
                    startActivity(alarm)
                } catch (e: Exception) {
                    statusView.append("\nנכשל: ${fmt(item.minutes)} ${item.label}")
                }
            }, delay)
            delay += 1200L
        }

        saveLast(if (deleteSwitch.isChecked) toSet else (loadLast() + toSet).distinct())

        val deleted = if (old.isNotEmpty()) "ביקשתי למחוק ${old.size} שעונים מהפעם הקודמת. " else ""
        statusView.text = deleted + "נשלחו ${toSet.size} שעונים חדשים לאפליקציית השעון.\nכדאי לבדוק שם שהכול נקבע."
    }

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
        setPadding(0, dp(12), 0, dp(4))
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

    private fun buildUi() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }

        column.addView(TextView(this).apply {
            text = "שעוני ביגל"
            textSize = 32f
            setTextColor(TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        column.addView(TextView(this).apply {
            text = "מעתיקים את ההודעה בוואטסאפ, פותחים כאן, ולוחצים פעם אחת."
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
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        listCard.addView(listBox)
        column.addView(listCard, cardParams())

        // Settings card
        val settingsCard = card()
        settingsCard.addView(sectionTitle("הגדרות"))
        settingsCard.addView(smallLabel("שם לחיפוש"))
        nameBox = field("ביגל")
        settingsCard.addView(nameBox)

        val ruleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val ruleLeft = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ruleLeft.addView(smallLabel("משימה עם שעון מוקדם"))
        ruleNameBox = field("ריקוד קצר")
        ruleLeft.addView(ruleNameBox)
        val ruleRight = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ruleRight.addView(smallLabel("דקות לפני"))
        ruleLeadBox = field("30", number = true)
        ruleRight.addView(ruleLeadBox)
        ruleRow.addView(ruleLeft, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
        ruleRow.addView(ruleRight, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = dp(12) })
        settingsCard.addView(ruleRow)

        deleteSwitch = Switch(this).apply {
            text = "מחק קודם את השעונים שהאפליקציה קבעה בפעם הקודמת"
            textSize = 14f
            setTextColor(TEXT)
            isChecked = true
            setPadding(0, dp(16), 0, 0)
        }
        settingsCard.addView(deleteSwitch)
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

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refresh() }
        }
        listOf(messageBox, nameBox, ruleNameBox, ruleLeadBox).forEach { it.addTextChangedListener(watcher) }
    }
}
