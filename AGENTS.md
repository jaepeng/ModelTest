# AGENTS.md

Single-module Android app: on-device LLM inference via llama.cpp JNI bridge. arm64-v8a only, CPU only.

## Build & Run

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # install on connected device
./gradlew testDebugUnitTest      # unit tests (only example tests exist)
./gradlew connectedDebugAndroidTest  # instrumented tests
```

No CI, no lint/format/typecheck beyond Gradle defaults.

## Architecture

- **GoActivity** is the launcher (not MainActivity). Uses XML layout `activity_go.xml`.
- **MainActivity** is an empty Compose activity — unused, exists as placeholder.
- **LlamaNative.java** — JNI binding. Loads native libs in strict order: `ggml-base → ggml → llama → ggml-cpu-android_armv8.2_1 → modeltest`. Order matters.
- **llama_wrapper.cpp** — JNI bridge with 4 methods: `initBackend`, `loadModel`, `generate`, `freeContext`. Uses newer llama.cpp API (`llama_model_get_vocab`, `llama_vocab_eos`, separate model/context objects).
- **Prebuilt .so files** in `jniLibs/arm64-v8a/` — vendored from llama.cpp, do not edit/rebuild.
- **include/** headers — vendored, do not edit.
- **Model asset** `qwen2.5-coder-0.5b-instruct-q4_k_m.gguf` (~400MB) in `assets/models/`, copied to `getExternalFilesDir()` on first launch.

## Key Details

- `compileSdk 35`, `minSdk 28`, Java 11 source/target
- Context size: 1024 tokens (set in GoActivity `loadModel` call)
- `mparams.n_gpu_layers = 0` — CPU only
- Inference runs on `new Thread()` — no coroutine integration
- `android:largeHeap="true"` in manifest (needed for model memory)
- ProGuard keep rules exist for `LlamaNative` and native methods (`keepRules/rules.keep`)

## Watch Out For

- Native lib load order is fragile — ggml-base must load before ggml, ggml before llama
- The `ggml-cpu-android_armv8.2_1` lib is loaded in a try/catch (graceful fallback) — name includes arch suffix
- Model copy from assets happens synchronously in GoActivity.onCreate — blocks UI on first launch
- CMake links `ggml-cpu` via `-L`/`-l` flags (not IMPORTED target) to avoid missing SONAME issues
