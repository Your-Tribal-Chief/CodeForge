package dev.codeforge.ide.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Prefs(
    val editorFontSize: Int = 14,
    val terminalFontSize: Int = 13,
    val tabSize: Int = 4,
    val wordWrap: Boolean = false,
    val lineNumbers: Boolean = true,
    val autoSave: Boolean = false,
    val lightTheme: Boolean = false,
    val showHidden: Boolean = false,
    val prootNoSeccomp: Boolean = false,
)

object AppSettings {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Prefs())
    val state: StateFlow<Prefs> = _state
    val current: Prefs get() = _state.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("codeforge_settings", Context.MODE_PRIVATE)
        val d = Prefs()
        _state.value = Prefs(
            editorFontSize = sp.getInt("editorFontSize", d.editorFontSize),
            terminalFontSize = sp.getInt("terminalFontSize", d.terminalFontSize),
            tabSize = sp.getInt("tabSize", d.tabSize),
            wordWrap = sp.getBoolean("wordWrap", d.wordWrap),
            lineNumbers = sp.getBoolean("lineNumbers", d.lineNumbers),
            autoSave = sp.getBoolean("autoSave", d.autoSave),
            lightTheme = sp.getBoolean("lightTheme", d.lightTheme),
            showHidden = sp.getBoolean("showHidden", d.showHidden),
            prootNoSeccomp = sp.getBoolean("prootNoSeccomp", d.prootNoSeccomp),
        )
    }

    fun update(transform: (Prefs) -> Prefs) {
        val p = transform(_state.value)
        _state.value = p
        sp.edit()
            .putInt("editorFontSize", p.editorFontSize.coerceIn(8, 32))
            .putInt("terminalFontSize", p.terminalFontSize.coerceIn(8, 32))
            .putInt("tabSize", p.tabSize)
            .putBoolean("wordWrap", p.wordWrap)
            .putBoolean("lineNumbers", p.lineNumbers)
            .putBoolean("autoSave", p.autoSave)
            .putBoolean("lightTheme", p.lightTheme)
            .putBoolean("showHidden", p.showHidden)
            .putBoolean("prootNoSeccomp", p.prootNoSeccomp)
            .apply()
    }

    fun reset() = update { Prefs() }

    /** Open editor tabs, restored after the app restarts. */
    var openFiles: List<String>
        get() = sp.getString("openFiles", "")!!.split('\n').filter { it.isNotBlank() }
        set(value) = sp.edit().putString("openFiles", value.joinToString("\n")).apply()

    var activeFile: String?
        get() = sp.getString("activeFile", null)
        set(value) = sp.edit().putString("activeFile", value).apply()
}
