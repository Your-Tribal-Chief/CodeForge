package dev.codeforge.ide.core

import android.content.res.AssetManager
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties

/**
 * First-run installer for the bundled Alpine Linux userland (extracted from APK assets,
 * checksum-verified at build time). User files under /root are never deleted by a reinstall.
 */
object Bootstrap {
    sealed interface State {
        data object Checking : State
        data class Installing(val fraction: Float?, val message: String) : State
        data object Ready : State
        data class Failed(val message: String, val detail: String = "") : State
    }

    private const val MARKER = ".codeforge-bootstrap"
    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state
    private val mutex = Mutex()

    var alpineVersion: String = "?"
        private set
    var alpineBranch: String = "?"
        private set

    suspend fun ensure(forceReinstall: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.IO) {
            _state.value = State.Checking
            _state.value = try {
                run(forceReinstall)
            } catch (t: Throwable) {
                State.Failed("Setting up the Linux environment failed.", t.toString())
            }
        }
    }

    private suspend fun run(forceReinstall: Boolean): State {
        val abi = Env.abi ?: return State.Failed(
            "This device's processor isn't supported by this build.",
            "Device ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}. This APK ships arm64-v8a and x86_64.",
        )
        val props = loadProps(abi) ?: return State.Failed(
            "This APK was built without the Linux environment.",
            "Missing assets/bootstrap/rootfs-$abi.*. Build the APK with scripts/fetch-bootstrap.sh (the CI workflow does this).",
        )
        if (!Env.proot.exists() || !Env.prootLoader.exists()) return State.Failed(
            "PRoot is missing from this APK.",
            "Expected ${Env.proot} and ${Env.prootLoader}.",
        )
        alpineVersion = props.getProperty("version", "?")
        alpineBranch = props.getProperty("branch", "latest-stable")

        val marker = File(Env.rootfs, MARKER)
        if (forceReinstall || !marker.exists()) install(abi)
        configure()
        marker.writeText(alpineVersion)

        _state.value = State.Installing(null, "Checking the Linux environment…")
        val probe = Proot.exec("echo codeforge-ok; cat /etc/alpine-release", timeoutMs = 20_000)
        if (!probe.output.contains("codeforge-ok")) {
            return State.Failed(
                "The Linux environment didn't start.",
                "Exit code ${probe.exitCode}${if (probe.timedOut) " (timed out)" else ""}.\n${probe.output.take(2000)}\n" +
                    "If this persists, turn on Settings → Disable PRoot seccomp and retry.",
            )
        }
        return State.Ready
    }

    private fun loadProps(abi: String): Properties? = try {
        Env.app.assets.open("bootstrap/rootfs-$abi.properties").use { Properties().apply { load(it) } }
    } catch (e: IOException) {
        null
    }

    private class CountingStream(input: InputStream) : FilterInputStream(input) {
        @Volatile var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }
    }

    private fun install(abi: String) {
        val root = Env.rootfs
        root.mkdirs()
        // Wipe everything except the user's home directory.
        root.listFiles()?.forEach { if (it.name != "root") FileOps.deleteTree(it) }

        val asset = "bootstrap/rootfs-$abi.cfrootfs" // gzip-compressed tar (see fetch-bootstrap.sh)
        val am = Env.app.assets
        val total = try {
            am.openFd(asset).use { it.length }
        } catch (e: IOException) {
            -1L
        }
        val counting = CountingStream(am.open(asset, AssetManager.ACCESS_STREAMING))
        val rootPath = root.absolutePath
        val dirModes = mutableListOf<Pair<File, Int>>()
        var n = 0

        TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(counting, 1 shl 16))).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                val name = e.name.removePrefix("./").trimEnd('/')
                if (name.isEmpty()) continue
                if (name.split('/').any { it == ".." }) throw IOException("Unsafe path in archive: ${e.name}")
                val out = File(root, name)
                if (!out.absolutePath.startsWith("$rootPath/")) throw IOException("Unsafe path in archive: ${e.name}")
                // Preserve the user's existing home directory contents.
                if (name == "root" && out.isDirectory) continue
                out.parentFile?.mkdirs()
                when {
                    e.isDirectory -> {
                        out.mkdirs()
                        dirModes += out to e.mode
                    }
                    e.isSymbolicLink -> {
                        if (Env.lexists(out)) FileOps.deleteTree(out)
                        Os.symlink(e.linkName, out.path)
                    }
                    e.isLink -> {
                        val target = File(root, e.linkName.removePrefix("./"))
                        if (Env.lexists(out)) FileOps.deleteTree(out)
                        target.copyTo(out, overwrite = true)
                        Os.chmod(out.path, (e.mode and 4095) or 384)
                    }
                    else -> {
                        if (Env.isSymlink(out)) FileOps.deleteTree(out)
                        out.outputStream().use { tar.copyTo(it, 1 shl 16) }
                        Os.chmod(out.path, (e.mode and 4095) or 384) // keep owner rw
                    }
                }
                if (++n % 150 == 0) {
                    val f = if (total > 0) (counting.count.toFloat() / total).coerceIn(0f, 1f) else null
                    _state.value = State.Installing(f, "Unpacking Alpine Linux $alpineVersion…")
                }
            }
        }
        for ((dir, mode) in dirModes) Os.chmod(dir.path, (mode and 4095) or 448) // owner rwx
    }

    private fun writeSafely(file: File, text: String) {
        if (Env.isSymlink(file)) FileOps.deleteTree(file)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun configure() {
        val root = Env.rootfs
        _state.value = State.Installing(null, "Configuring…")
        writeSafely(File(root, "etc/resolv.conf"), "nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        writeSafely(File(root, "etc/hosts"), "127.0.0.1 localhost\n::1 localhost ip6-localhost\n")
        val repo = "https://dl-cdn.alpinelinux.org/alpine/$alpineBranch"
        writeSafely(File(root, "etc/apk/repositories"), "$repo/main\n$repo/community\n")
        writeSafely(
            File(root, "etc/profile.d/codeforge.sh"),
            """
            |export PS1='\[\e[38;5;215m\]\w\[\e[0m\] \$ '
            |alias ll='ls -la'
            |alias la='ls -A'
            |if [ -z "${'$'}CODEFORGE_QUIET" ]; then
            |  echo "CodeForge terminal - Alpine Linux ${'$'}(cat /etc/alpine-release)"
            |  echo "Install tools from the Tools tab, or run: apk add <package>"
            |  export CODEFORGE_QUIET=1
            |fi
            |
            """.trimMargin(),
        )
        File(root, "tmp").mkdirs()
        Os.chmod(File(root, "tmp").path, 1023) // 01777
        Env.projects.mkdirs()
        if (Env.projects.list().isNullOrEmpty()) SampleProject.create(File(Env.projects, "hello-codeforge"))
    }
}
