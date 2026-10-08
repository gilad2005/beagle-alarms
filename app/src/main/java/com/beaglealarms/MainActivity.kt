package com.beaglealarms

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var messageBox: EditText
    private lateinit var nameBox: EditText
    private lateinit var ruleNameBox: EditText
    private lateinit var ruleLeadBox: EditText
    private lateinit var resultView: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handleShare()
    }

    private fun handleShare() {
        val i = intent ?: return
        if (i.action == Intent.ACTION_SEND && i.type == "text/plain") {
            val shared = i.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) {
                messageBox.setText(shared)
                setAlarms()
            }
        }
    }

    private fun setAlarms() {
        val name = nameBox.text.toString().trim()
        val ruleName = ruleNameBox.text.toString().trim()
        val ruleLead = ruleLeadBox.text.toString().toIntOrNull() ?: 0
        val times = AlarmParser.parse(messageBox.text.toString(), name, ruleName, ruleLead)

        if (times.isEmpty()) {
            resultView.text = "לא נמצאו שורות עם \"$name\"."
            return
        }

        handler.removeCallbacksAndMessages(null)
        times.forEachIndexed { index, m ->
            handler.postDelayed({
                val alarm = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, m / 60)
                    putExtra(AlarmClock.EXTRA_MINUTES, m % 60)
                    putExtra(AlarmClock.EXTRA_MESSAGE, name)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                try {
                    startActivity(alarm)
                } catch (e: Exception) {
                    resultView.append("\nנכשל: ${fmt(m)}")
                }
            }, index * 1200L)
        }

        resultView.text = "קובע ${times.size} שעונים:\n" + times.joinToString("\n") { fmt(it) } +
            "\n\nכדאי לבדוק באפליקציית השעון."
    }

    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setPadding(0, dp(14), 0, dp(2))
    }

    private fun field(value: String, number: Boolean = false) = EditText(this).apply {
        setText(value)
        inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
    }

    private fun buildUi() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        column.addView(TextView(this).apply {
            text = "שעוני ביגל"
            textSize = 26f
        })

        column.addView(label("ההודעה (או שתף אליה מוואטסאפ)"))
        messageBox = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
        }
        column.addView(messageBox)

        column.addView(label("מחפש את השם"))
        nameBox = field("ביגל")
        column.addView(nameBox)

        column.addView(label("חריג: משימה"))
        ruleNameBox = field("ריקוד קצר")
        column.addView(ruleNameBox)

        column.addView(label("חריג: דקות לפני"))
        ruleLeadBox = field("30", number = true)
        column.addView(ruleLeadBox)

        column.addView(Button(this).apply {
            text = "קבע שעונים"
            setOnClickListener { setAlarms() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })

        resultView = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(16), 0, 0)
        }
        column.addView(resultView)

        val scroll = ScrollView(this)
        scroll.layoutDirection = View.LAYOUT_DIRECTION_RTL
        scroll.addView(column)
        setContentView(scroll)
    }
}
