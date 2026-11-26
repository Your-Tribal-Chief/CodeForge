package dev.codeforge.ide.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Builds PRoot command lines. Every guest command runs as:
 *   libproot.so [options] -r <rootfs> -w <cwd> /usr/bin/env -i <guest env> <command...>
 * Arguments are passed as an argv array (no host shell), so file names are never re-parsed on the host.
 */
object Proot {
    const val GUEST_PATH =
        "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/jvm/default-jvm/bin"

    fun hostEnv(): Array<String> {
        val env = mutableListOf(
            "PROOT_LOADER=${Env.prootLoader.absolutePath}",
            "PROOT_TMP_DIR=${Env.prootTmp.absolutePath}",
            "LD_LIBRARY_PATH=${Env.nativeLibDir.absolutePath}",
            "HOME=${Env.app.filesDir.absolutePath}",
            "TMPDIR=${Env.prootTmp.absolutePath}",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT") ?: "/system"}",
            "ANDROID_DATA=${System.getenv("ANDROID_DATA") ?: "/data"}",
        )
        if (AppSettings.current.prootNoSeccomp) env += "PROOT_NO_SECCOMP=1"
        return env.toTypedArray()
    }

    private fun guestEnv(): List<String> = listOf(
        "HOME=/root", "USER=root", "LOGNAME=root", "PATH=$GUEST_PATH", "LANG=C.UTF-8",
        "TMPDIR=/tmp", "TERM=xterm-256color", "COLORTERM=truecolor", "SHELL=/bin/sh",
    )

    fun argv(cwdGuest: String, command: List<String>): Array<String> {
        val root = Env.rootfs.absolutePath
        val a = mutableListOf(
            Env.proot.absolutePath,
            "--kill-on-exit", "--link2symlink", "-0",
            "-r", root,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "/dev/urandom:/dev/random",
            "-b", "$root/tmp:/dev/shm",
        )
        if (Env.hasSharedStorage()) a += listOf("-b", "${Env.sharedStorage.absolutePath}:/sdcard")
        a += listOf("-w", cwdGuest, "/usr/bin/env", "-i")
        a += guestEnv()
        a += command
        return a.toTypedArray()
    }

    data class ExecResult(val exitCode: Int, val output: String, val timedOut: Boolean)

    /** Run a short, non-interactive command in the guest and capture its output. */
    suspend fun exec(script: String, cwdGuest: String = "/root", timeoutMs: Long = 30_000): ExecResult =
        withContext(Dispatchers.IO) {
            val pb = ProcessBuilder(*argv(cwdGuest, listOf("/bin/sh", "-c", script)))
            val env = pb.environment()
            env.clear()
            for (kv in hostEnv()) {
                val i = kv.indexOf('=')
                env[kv.substring(0, i)] = kv.substring(i + 1)
            }
            pb.directory(Env.app.filesDir)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = StringBuilder()
            val reader = thread(isDaemon = true, name = "proot-exec-reader") {
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(out) { if (out.length < 64_000) out.appendLine(line) }
                    }
                } catch (_: Exception) {
                }
            }
            val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) proc.destroyForcibly()
            reader.join(2_000)
            ExecResult(if (finished) proc.exitValue() else -1, synchronized(out) { out.toString() }, !finished)
        }

    fun shellCommand(): List<String> =
        if (Env.lexists(File(Env.rootfs, "bin/bash"))) listOf("/bin/bash", "-l") else listOf("/bin/sh", "-l")
}
