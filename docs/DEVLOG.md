# Development log

* Phase 0: checked the authoring sandbox. No Android SDK, Gradle or Google Maven reachable, so builds move
  to GitHub Actions. Chose target 28 (W^X), PRoot + Alpine, sora-editor, vendored Termux terminal.
* Verified against real sources (not memory): Termux v0.118.0 `TerminalSessionClient`, `TerminalViewClient`,
  `TerminalSession` constructor/exit handling, `TerminalView.setTextSize` renderer ordering; sora-editor 0.23.7
  `CodeEditor`, `EditorSearcher`, `ContentChangeEvent`, TextMate registry APIs and `languages.json` schema.
* Pinned toolchain to sora-editor 0.23.7's own: AGP 8.10.1, Kotlin 2.1.21, Gradle 8.11.1, compileSdk 36.
  (0.24.x requires compileSdk 37 / AGP 9.)
* Implemented Phases 1–4 scope. Bootstrap release-index parsers tested locally against sample indexes.
