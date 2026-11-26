package dev.codeforge.ide.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

/** All filesystem locations used by the app, plus host <-> guest path translation. */
object Env {
    lateinit var app: Context
        private set

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** ABIs this build ships a Linux userland for. */
    val abi: String?
        get() = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "x86_64" }

    val nativeLibDir: File get() = File(app.applicationInfo.nativeLibraryDir)
    val proot: File get() = File(nativeLibDir, "libproot.so")
    val prootLoader: File get() = File(nativeLibDir, "libproot-loader.so")
    val prootTmp: File get() = File(app.cacheDir, "proot-tmp").apply { mkdirs() }

    /** Alpine Linux root filesystem. Guest "/" maps here. */
    val rootfs: File get() = File(app.filesDir, "rootfs")
    /** Guest /root. Projects live in /root/projects by default. */
    val home: File get() = File(rootfs, "root")
    val projects: File get() = File(home, "projects")

    @Suppress("DEPRECATION")
    val sharedStorage: File get() = Environment.getExternalStorageDirectory()
    val sharedWorkspace: File get() = File(sharedStorage, "CodeForge")

    fun hasSharedStorage(): Boolean =
        app.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Translate a host path into the path the same file has inside the Linux environment. */
    fun toGuest(file: File): String {
        val p = file.absolutePath
        val r = rootfs.absolutePath
        val s = sharedStorage.absolutePath
        return when {
            p == r -> "/"
            p.startsWith("$r/") -> p.removePrefix(r)
            p == s -> "/sdcard"
            p.startsWith("$s/") -> "/sdcard" + p.removePrefix(s)
            else -> p
        }
    }

    /** Like File.exists() but true for symlinks too, without following them on the host. */
    fun lexists(file: File): Boolean = try {
        Os.lstat(file.path); true
    } catch (e: ErrnoException) {
        false
    }

    fun isSymlink(file: File): Boolean = try {
        OsConstants.S_ISLNK(Os.lstat(file.path).st_mode)
    } catch (e: ErrnoException) {
        false
    }
}
