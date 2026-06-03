# LSPosed Module Template

A minimal [LSPosed](https://github.com/LSPosed/LSPosed) module template using the modern [libxposed](https://github.com/libxposed) API.

## Features

- Kotlin with modern `XposedModule` API (no legacy `IXposedHook*` interfaces)
- Logging utility that outputs to both logcat and the LSPosed module logger
- Version catalog (`libs.versions.toml`) for dependency management
- ProGuard rules pre-configured for release builds

## Getting Started

### 1. Change module identity

- Rename the package from `com.example.template` to your own (use **Refactor → Rename** in Android Studio).
- Update `namespace` in `app/build.gradle.kts`.

### 2. Configure module metadata

- `app/src/main/res/values/strings.xml` — set `app_name` and `app_description` (shown in the LSPosed manager).
- `app/src/main/resources/META-INF/xposed/scope.list` — list target package names, one per line. Use `system` for system framework hooks.

### 3. Write your hooks

- `onPackageReady()` — most app hooks go here. The app's full classloader is ready.
- `onPackageLoaded()` — fires earlier, before `AppComponentFactory`. Use this to hook `AppComponentFactory` itself.
- `onSystemServerStarting()` — hook Android framework services in `system_server`.
- Use `logI(...)` / `logE(...)` / etc. for logging from anywhere.

### 4. Build

```bash
./gradlew assembleDebug    # debug build
./gradlew assembleRelease  # release build (minified)
```

Install the APK, enable the module in LSPosed, and reboot (or force-stop the target app).

## Project Structure

```
app/src/main/
├── java/com/example/template/
│   ├── MainHook.kt              # Module entry point
│   └── utils/Log.kt             # Logging helpers
├── resources/META-INF/xposed/
│   ├── java_init.list           # Entry class FQN
│   ├── module.prop              # API version requirements
│   └── scope.list               # Target packages
└── res/values/strings.xml       # Module name & description
```

## Credits

- [libxposed/api](https://github.com/libxposed/api) — modern Xposed API
- [re-zero001/XposedModule](https://github.com/re-zero001/XposedModule) — template targeting API 100
- [libxposed/example](https://github.com/libxposed/example) — official example
