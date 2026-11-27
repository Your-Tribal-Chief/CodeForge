# Known limitations (0.1.0-mvp + feature batch 1)

## Inherent to the platform (cannot be "fixed" in app code)
* **Sideload only, target SDK 28.** Since Android 10, apps targeting API 29+ may not `execve()` files in
  app-writable storage. A local Linux userland must run binaries it installs or compiles, so the app targets 28
  (as Termux does). Raising targetSdk would break the terminal, toolchains and Run. Play Store requires newer targets.
* **PRoot:** no real root, kernel modules, `mount`, or ports below 1024; ptrace-based, so roughly 1.2–2× slower for
  syscall-heavy builds; `ps`/`top` only see the app's own processes. Some devices need Settings → Disable PRoot seccomp.
* **ABIs:** arm64-v8a and x86_64 only (no 32-bit-only phones).
* **Background:** sessions are kept alive by a foreground service, but Android can still kill the app under memory
  pressure; running processes are then lost (files are safe).
* **Shared storage is `noexec`:** compiled programs are written to `/tmp` inside the Linux environment.
* **Toolchain sizes** are large (GCC ≈ 200 MB, OpenJDK ≈ 200 MB, Rust ≈ 500 MB installed).

## Fixed in feature batch 1
* Java source paths with spaces (javac @argfile is now quoted).
* One broken grammar disabled all highlighting (grammars now load individually; Kotlin's plist grammar verified).
* No project-wide search/replace; no Git UI; no command palette; no breadcrumbs; no settings.json.
* Release builds were not minified; R8 keep rules added and the release APK is built and checked in CI.

## Not implemented yet (planned, in order)
1. **Extensions (Open VSX + .vsix):** declarative contributions only: themes, TextMate grammars, snippets,
   language configurations. Extensions whose features need a JavaScript extension host will not run natively.
2. **LSP + Problems panel** (sora editor-lsp; pylsp, typescript-language-server, clangd, rust-analyzer installed in
   the Linux environment). Until then completion is sora's identifier completion, not IntelliSense.
3. **code-server mode** (experimental): runs Code OSS in the Linux environment, shown in a WebView. Extensions come
   from Open VSX. **Pylance and GitHub Copilot are Microsoft-licensed for official VS Code only and are not on
   Open VSX**, so they will not be available in any Code OSS build, including this one. Alternatives: basedpyright /
   pylsp for Python.
4. Split editor, minimap, keybindings.json, multi-cursor, code formatting, debugger UI, side-by-side diff
   (the diff viewer is inline/unified).

## Other
* TypeScript runs only via `npm i -g typescript` in the terminal. Dart and Kotlin have highlighting but no compiler
  package in Alpine.
* Git pull/push/fetch/clone run in a terminal tab so credential prompts work; use a personal access token as the
  password for HTTPS remotes.
* **Not yet verified on a physical device.** CI builds and APK inspections pass; see QA.md.
