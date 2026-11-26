package dev.codeforge.ide.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.codeforge.ide.core.AppSettings
import dev.codeforge.ide.core.FileOps
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class OpenFile(file: File, val content: Content) {
    var file by mutableStateOf(file)
    var dirty by mutableStateOf(false)
    var lastSavedAt by mutableStateOf(0L)
}

/** Open editor tabs. Each tab owns its sora [Content], which also holds its own undo history. */
object Workspace {
    const val MAX_FILE_BYTES = 8L * 1024 * 1024

    val files = mutableStateListOf<OpenFile>()
    var activeIndex by mutableIntStateOf(-1)
    /** Bumped whenever the file tree changes on disk, so the explorer re-lists. */
    var treeVersion by mutableIntStateOf(0)

    val active: OpenFile? get() = files.getOrNull(activeIndex)

    sealed interface OpenResult {
        data object Ok : OpenResult
        data class Error(val message: String) : OpenResult
    }

    suspend fun open(file: File): OpenResult {
        val existing = files.indexOfFirst { it.file.absolutePath == file.absolutePath }
        if (existing >= 0) {
            activeIndex = existing
            persist()
            return OpenResult.Ok
        }
        val content = withContext(Dispatchers.IO) {
            when {
                !file.isFile -> null
                file.length() > MAX_FILE_BYTES -> null
                FileOps.looksBinary(file) -> null
                else -> Content(file.readText())
            }
        } ?: return OpenResult.Error(
            when {
                !file.isFile -> "${file.name} can't be opened."
                file.length() > MAX_FILE_BYTES -> "${file.name} is larger than 8 MB, too big to edit here."
                else -> "${file.name} looks like a binary file."
            },
        )
        files += OpenFile(file, content)
        activeIndex = files.lastIndex
        persist()
        return OpenResult.Ok
    }

    suspend fun save(of: OpenFile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val text = withContext(Dispatchers.Main) { of.content.toString() }
            val tmp = File(of.file.parentFile, ".${of.file.name}.codeforge-tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(of.file)) {
                of.file.writeText(text)
                tmp.delete()
            }
            withContext(Dispatchers.Main) {
                of.dirty = false
                of.lastSavedAt = System.currentTimeMillis()
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    suspend fun saveAll(): Int {
        var failed = 0
        for (f in files.toList()) if (f.dirty && save(f).isFailure) failed++
        return failed
    }

    fun close(of: OpenFile) {
        val idx = files.indexOf(of)
        if (idx < 0) return
        files.removeAt(idx)
        activeIndex = when {
            files.isEmpty() -> -1
            activeIndex > idx -> activeIndex - 1
            activeIndex >= files.size -> files.lastIndex
            else -> activeIndex
        }
        persist()
    }

    /** Keep tabs consistent after a rename/move/delete in the explorer. */
    fun onPathChanged(old: File, new: File?) {
        val oldPath = old.absolutePath
        for (f in files.toList()) {
            val p = f.file.absolutePath
            if (p == oldPath || p.startsWith("$oldPath/")) {
                if (new == null) close(f) else f.file = File(new.absolutePath + p.removePrefix(oldPath))
            }
        }
        treeVersion++
        persist()
    }

    fun select(index: Int) {
        if (index in files.indices) {
            activeIndex = index
            persist()
        }
    }

    private fun persist() {
        AppSettings.openFiles = files.map { it.file.absolutePath }
        AppSettings.activeFile = active?.file?.absolutePath
    }

    suspend fun restoreSession() {
        if (files.isNotEmpty()) return
        val active = AppSettings.activeFile
        for (path in AppSettings.openFiles) {
            val f = File(path)
            if (f.isFile) open(f)
        }
        val idx = files.indexOfFirst { it.file.absolutePath == active }
        if (idx >= 0) activeIndex = idx
    }
}
