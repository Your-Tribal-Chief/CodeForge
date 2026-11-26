# Known limitations (0.1.0-mvp)

* **Not yet verified on a device.** The code has not been compiled or run by its author; the first CI run is
  the first compile. See QA.md.
* **Sideload only** (target SDK 28). Android may show an "built for an older version" notice.
* **ABIs:** arm64-v8a and x86_64 only. 32-bit-only phones are not supported.
* **Not in this build:** language servers (completion, hover, go-to-definition, diagnostics), extension
  marketplace, Git UI, debugger UI, project-wide search/replace, minimap, multi-cursor, code formatting,
  editor/terminal split view, settings import/export, SAF folder import.
  The editor's completion is the TextMate keyword/identifier completion from sora-editor, not IntelliSense.
* **Languages:** TypeScript runs only via `npm i -g typescript` in the terminal. Dart and Kotlin have
  highlighting but no compiler (no Alpine packages).
* **PRoot:** no real root, no kernel modules, no `mount`, no ports below 1024, `ptrace`-based so roughly
  1.2–2× slower for syscall-heavy builds. `ps`/`top` only see the app's own processes. Some devices need
  Settings → Disable PRoot seccomp.
* **Background:** sessions survive backgrounding via a foreground service, but Android can still kill the app
  under memory pressure; running processes are then lost (files are safe).
* **Shared storage** is mounted `noexec`; compiled programs are written to `/tmp` inside the Linux environment.
* **Java:** source file paths containing spaces break the `javac @argfile`.
* **Toolchain sizes** are large (GCC about 200 MB, OpenJDK about 200 MB, Rust about 500 MB installed).
