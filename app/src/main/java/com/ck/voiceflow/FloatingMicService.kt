package com.ck.voiceflow

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.AudioManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingMicService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var lp: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var accumulated = ""
    private var baseText = ""
    private var target: AccessibilityNodeInfo? = null

    /* ---------------- accessibility events ---------------- */

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (listening) return // don't move/hide the bubble mid-dictation
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val node = event.source ?: return
                if (node.isEditable && node.isFocused) {
                    target = node
                    showBubble()
                } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                    hideIfNoEditableFocus()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> hideIfNoEditableFocus()
            else -> {}
        }
    }

    private fun hideIfNoEditableFocus() {
        handler.postDelayed({
            if (listening) return@postDelayed
            val focus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focus == null || !focus.isEditable) hideBubble() else { target = focus; showBubble() }
        }, 250)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    /* ---------------- bubble ---------------- */

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showBubble() {
        if (bubble != null) return
        val b = TextView(this).apply {
            text = "🎙"
            textSize = 26f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0AA2C2"))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(6).toFloat()
        }
        val p = WindowManager.LayoutParams(
            dp(56), dp(56),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(72)
            y = resources.displayMetrics.heightPixels / 2
        }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        b.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = p.x; startY = p.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX; val dy = ev.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        p.x = startX + dx.toInt(); p.y = startY + dy.toInt()
                        try { wm.updateViewLayout(b, p) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) toggleDictation(); true }
                else -> false
            }
        }

        try { wm.addView(b, p); bubble = b; lp = p } catch (_: Exception) {}
    }

    private fun hideBubble() {
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
    }

    private fun setBubbleListening(on: Boolean) {
        (bubble?.background as? GradientDrawable)
            ?.setColor(Color.parseColor(if (on) "#EF4444" else "#0AA2C2"))
        bubble?.text = if (on) "■" else "🎙"
        bubble?.setTextColor(Color.WHITE)
    }

    /* ---------------- dictation ---------------- */

    private fun toggleDictation() {
        if (listening) stopDictation() else startDictation()
    }

    private fun startDictation() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "No speech recognizer on this device", Toast.LENGTH_LONG).show()
            return
        }
        target = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: target
        baseText = target?.text?.toString() ?: ""
        // many fields report their hint as text — don't treat hint as content
        if (target?.isShowingHintText == true) baseText = ""
        accumulated = ""
        listening = true
        setBubbleListening(true)
        muteBeeps() // the recognizer dings on every listen cycle — silence it for the session
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        startListeningOnce()
    }

    /* the recognizer restarts its listen cycle continuously, chiming each time —
       mute the beep streams while dictating, restore after */
    private var mutedStreams = mutableListOf<Int>()
    private fun muteBeeps() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        for (stream in intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_NOTIFICATION)) {
            try {
                am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                mutedStreams.add(stream)
            } catch (_: Exception) {} // some streams need DND access on some phones — skip those
        }
    }
    private fun unmuteBeeps() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        for (stream in mutedStreams) {
            try { am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
        }
        mutedStreams.clear()
    }

    private fun recIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
    }

    private fun startListeningOnce() {
        try { recognizer?.startListening(recIntent()) } catch (_: Exception) {}
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            val txt = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!txt.isNullOrBlank()) {
                // accumulate silently — nothing is typed until the user taps stop
                accumulated = applyScratchThat(accumulated + " " + txt)
            }
            if (listening) handler.postDelayed({ if (listening) startListeningOnce() }, 120)
            else finishDictation() // stop was tapped; this was the final flush
        }
        override fun onPartialResults(partial: Bundle) {}
        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                Toast.makeText(applicationContext, "Open VoiceFlow Mic and grant the microphone", Toast.LENGTH_LONG).show()
                listening = false
                setBubbleListening(false)
                finishDictation()
                return
            }
            // no-speech / busy / network: just restart while the user wants to listen
            if (listening) handler.postDelayed({ if (listening) startListeningOnce() }, 250)
            else finishDictation()
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rms: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    private var finishRunnable: Runnable? = null

    private fun stopDictation() {
        if (!listening) return
        listening = false
        setBubbleListening(false)
        // let the recognizer finalize what was just said; onResults delivers it,
        // with a timed fallback in case nothing more arrives
        try { recognizer?.stopListening() } catch (_: Exception) {}
        finishRunnable = Runnable { finishDictation() }
        handler.postDelayed(finishRunnable!!, 1800)
    }

    /* runs exactly once per session: cleanup grammar, insert text, restore sounds */
    private fun finishDictation() {
        finishRunnable?.let { handler.removeCallbacks(it) }
        finishRunnable = null
        recognizer?.let { try { it.destroy() } catch (_: Exception) {} }
        recognizer = null
        unmuteBeeps()
        if (accumulated.isNotBlank()) {
            writeToField(true)
            accumulated = ""
        }
    }

    /* ---------------- text cleanup + insertion ---------------- */

    private fun applyScratchThat(t: String): String {
        var s = t
        val cmd = Regex("(?i)\\b(?:scratch|strike|delete) that\\b[.,!?]?")
        while (cmd.containsMatchIn(s)) {
            val m = cmd.find(s)!!
            val before = s.substring(0, m.range.first)
            val after = s.substring(m.range.last + 1)
            val cut = before.replace(Regex("[^.!?\\n]+[.!?]?\\s*$"), "")
            s = cut + after
        }
        return s
    }

    private fun clean(t: String): String {
        var s = " $t "
        s = s.replace(Regex("(?i)(^|\\s)(?:um+|uh+|uhm+|erm+|err|ah+|hmm+|mhm+|huh)([,.!?]?)(?=\\s)"), "$1")
        s = s.replace(Regex("(?i)\\b(\\S+)(?:\\s+\\1\\b)+"), "$1")          // "I I went"
        s = s.replace(Regex("(?i)\\b(\\S+\\s+\\S+)(?:\\s+\\1\\b)+"), "$1")  // "I want I want"
        s = s.replace(Regex("(?i)\\bnew line\\b[.,]?"), "\n")
            .replace(Regex("(?i)\\bnew paragraph\\b[.,]?"), "\n\n")
        s = s.replace(Regex("\\s+([.,!?;:])"), "$1").replace(Regex("[ \\t]{2,}"), " ").trim()
        // capitalize sentence starts and lone "i"
        s = Regex("(^|[.!?]\\s+|\\n)([a-z])").replace(s) { it.groupValues[1] + it.groupValues[2].uppercase() }
        s = Regex("\\bi\\b").replace(s, "I")
        return s
    }

    private fun writeToField(final: Boolean) {
        val text = clean(accumulated)
        if (text.isBlank()) return
        var node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node == null || !node.isEditable) node = target

        // add a leading space when inserting right after existing text
        var toInsert = text
        if (node != null && node.isShowingHintText != true) {
            val existing = node.text?.toString() ?: ""
            val sel = node.textSelectionEnd
            if (sel > 0 && sel <= existing.length && !existing[sel - 1].isWhitespace()) toInsert = " $text"
        }

        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("VoiceFlow", toInsert))

        var ok = false
        if (node != null && node.isEditable) {
            // PASTE goes through the real input pipeline — apps like Google Keep
            // ignore ACTION_SET_TEXT for their save/dirty state and discard the note
            ok = try { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) } catch (_: Exception) { false }
            if (!ok) {
                val combined = if (baseText.isBlank()) text else baseText.trimEnd() + " " + text
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
                }
                ok = try { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) } catch (_: Exception) { false }
            }
        }
        if (final && !ok) {
            Toast.makeText(this, "Couldn't type here — text copied, long-press → Paste", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        listening = false
        finishDictation()
        hideBubble()
        super.onDestroy()
    }
}
