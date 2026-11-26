package dev.codeforge.ide.core

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

object FileOps {
    private val badName = Regex("""[/\u0000]""")

    fun validateName(name: String): String? = when {
        name.isBlank() -> "Enter a name."
        name == "." || name == ".." -> "That name is reserved."
        badName.containsMatchIn(name) -> "Names can't contain '/'."
        name.length > 255 -> "That name is too long."
        else -> null
    }

    fun createFile(dir: File, name: String): File {
        val f = File(dir, name)
        if (Env.lexists(f)) throw IOException("${f.name} already exists.")
        f.parentFile?.mkdirs()
        if (!f.createNewFile()) throw IOException("Couldn't create ${f.name}.")
        return f
    }

    fun createDir(dir: File, name: String): File {
        val f = File(dir, name)
        if (Env.lexists(f)) throw IOException("${f.name} already exists.")
        if (!f.mkdirs()) throw IOException("Couldn't create folder ${f.name}.")
        return f
    }

    fun rename(file: File, newName: String): File {
        val target = File(file.parentFile, newName)
        if (Env.lexists(target)) throw IOException("$newName already exists.")
        if (!file.renameTo(target)) throw IOException("Couldn't rename ${file.name}.")
        return target
    }

    /** Recursive delete that never follows symlinks (rootfs symlinks point at absolute guest paths). */
    fun deleteTree(file: File) {
        val st = try {
            Os.lstat(file.path)
        } catch (e: ErrnoException) {
            return
        }
        if (OsConstants.S_ISDIR(st.st_mode)) file.listFiles()?.forEach { deleteTree(it) }
        if (!file.delete() && Env.lexists(file)) throw IOException("Couldn't delete ${file.name}.")
    }

    fun uniqueTarget(dir: File, name: String): File {
        var candidate = File(dir, name)
        var i = 1
        while (Env.lexists(candidate)) {
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            candidate = File(dir, if (ext.isEmpty() || base.isEmpty()) "$name copy $i" else "$base copy $i.$ext")
            i++
        }
        return candidate
    }

    fun copyInto(src: File, destDir: File): File {
        if (src.isDirectory && destDir.canonicalPath.startsWith(src.canonicalPath + "/")) {
            throw IOException("Can't copy a folder into itself.")
        }
        val target = uniqueTarget(destDir, src.name)
        if (Env.isSymlink(src)) {
            Os.symlink(Os.readlink(src.path), target.path)
        } else if (src.isDirectory) {
            target.mkdirs()
            src.listFiles()?.forEach { copyInto(it, target) }
        } else {
            src.copyTo(target)
        }
        return target
    }

    fun moveInto(src: File, destDir: File): File {
        if (src.isDirectory && destDir.canonicalPath.startsWith(src.canonicalPath + "/")) {
            throw IOException("Can't move a folder into itself.")
        }
        val target = File(destDir, src.name)
        if (Env.lexists(target)) throw IOException("${src.name} already exists there.")
        if (src.renameTo(target)) return target
        copyInto(src, destDir)
        deleteTree(src)
        return target
    }

    /** Heuristic binary check: NUL byte in the first 8 KB. */
    fun looksBinary(file: File): Boolean = file.inputStream().use { input ->
        val buf = ByteArray(8192)
        val n = input.read(buf)
        (0 until maxOf(n, 0)).any { buf[it] == 0.toByte() }
    }

    fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
