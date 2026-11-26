package dev.codeforge.ide.terminal

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.codeforge.ide.core.AppSettings

/** Sticky modifier state for the on-screen CTRL / ALT keys. */
class ModifierKeys {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
}

class TermViewClient(private val mods: ModifierKeys) : TerminalViewClient {
    var view: TerminalView? = null
    private var scaleAccum = 1f

    fun showKeyboard() {
        val v = view ?: return
        v.requestFocus()
        (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, 0)
    }

    private fun closeIfFinished(session: TerminalSession): Boolean {
        if (session.isRunning) return false
        TerminalHub.tabs.firstOrNull { it.session === session }?.let { TerminalHub.close(it) }
        return true
    }

    override fun onScale(scale: Float): Float {
        scaleAccum = scale
        if (scale < 0.9f || scale > 1.1f) {
            val delta = if (scale > 1f) 1 else -1
            AppSettings.update { it.copy(terminalFontSize = (it.terminalFontSize + delta).coerceIn(8, 32)) }
            return 1f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent?) = showKeyboard()
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession): Boolean =
        keyCode == KeyEvent.KEYCODE_ENTER && closeIfFinished(session)

    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
    override fun onLongPress(event: MotionEvent?): Boolean = false

    override fun readControlKey(): Boolean = mods.ctrl.also { if (it) mods.ctrl = false }
    override fun readAltKey(): Boolean = mods.alt.also { if (it) mods.alt = false }
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean =
        (codePoint == '\r'.code || codePoint == '\n'.code) && closeIfFinished(session)

    override fun onEmulatorSet() {}

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: "TermView", message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: "TermView", message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: "TermView", message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: "TermView", message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: "TermView", message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: "TermView", message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: "TermView", "error", e) }
}
