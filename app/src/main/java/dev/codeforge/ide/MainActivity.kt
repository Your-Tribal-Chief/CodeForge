package dev.codeforge.ide

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.codeforge.ide.core.AppSettings
import dev.codeforge.ide.ui.Actions
import dev.codeforge.ide.ui.AppRoot
import dev.codeforge.ide.ui.CodeForgeTheme
import dev.codeforge.ide.ui.Dest
import dev.codeforge.ide.ui.UiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by AppSettings.state.collectAsState()
            CodeForgeTheme(light = prefs.lightTheme) { AppRoot() }
        }
    }

    /** Hardware keyboard shortcuts. Ctrl+R/F/S are left to the terminal when it has focus. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val inCode = UiState.dest == Dest.CODE
            val handled = when {
                event.keyCode == KeyEvent.KEYCODE_F5 -> { Actions.runActive(); true }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_GRAVE -> {
                    UiState.dest = if (UiState.dest == Dest.TERMINAL) Dest.CODE else Dest.TERMINAL; true
                }
                inCode && event.isCtrlPressed && event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_S -> { Actions.saveAll(); true }
                inCode && event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_S -> { Actions.saveActive(); true }
                inCode && event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_F -> { UiState.findOpen = !UiState.findOpen; true }
                inCode && event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_R -> { Actions.runActive(); true }
                event.isCtrlPressed && event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_E -> { UiState.dest = Dest.FILES; true }
                else -> false
            }
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }
}
