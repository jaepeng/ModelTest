# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android app that runs on-device LLM inference via llama.cpp. Loads GGUF models (bundled Qwen2.5-Coder-0.5B) through JNI and generates text on CPU (arm64-v8a only).

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew connectedDebugAndroidTest
```

Requires Android SDK with compileSdk 35, minSdk 28. NDK builds native code via CMake.

## Architecture

### Native Layer (`app/src/main/cpp/`)

- **llama_wrapper.cpp** — JNI bridge exposing 4 methods to Java: `initBackend`, `loadModel`, `generate`, `freeContext`
- **CMakeLists.txt** — Links against prebuilt `.so` files in `jniLibs/arm64-v8a/` (libllama, libggml, libggml-base, libggml-cpu)
- **include/** — llama.cpp and ggml headers (vendored, do not edit)

### Java Layer (`app/src/main/java/com/example/modeltest/`)

- **LlamaNative.java** — JNI binding class. Loads native libs in correct dependency order (ggml-base → ggml → llama → ggml-cpu → modeltest). Exposes `initBackend()`, `loadModel(path, nCtx)`, `generate(ctxPtr, prompt, maxTokens)`, `freeContext(ctxPtr)`.
- **GoActivity.java** — Entry point. Copies model from assets to external files dir, runs inference on background thread. Uses XML layout (`activity_go.xml`).
- **MainActivity.kt** — Empty Compose activity (unused currently).
- **ui/theme/** — Compose theme files (Color, Type, Theme). Only relevant if migrating UI to Compose.

### Model Assets

- `app/src/main/assets/models/` — Contains `qwen2.5-coder-0.5b-instruct-q4_k_m.gguf` (~400MB) and `tokenizer.json`
- Model copied to `getExternalFilesDir()` on first launch

### Prebuilt Native Libraries

All in `app/src/main/jniLibs/arm64-v8a/`: libllama.so, libggml.so, libggml-base.so, libggml-cpu-android_armv8.2_1.so, libmtmd.so. These are prebuilt from llama.cpp — do not rebuild unless upgrading llama.cpp.

## Key Details

- **ABI**: arm64-v8a only (configured in `defaultConfig.ndk.abiFilters`)
- **CPU only**: `mparams.n_gpu_layers = 0` in llama_wrapper.cpp
- **Context size**: 1024 tokens (set in GoActivity.java `loadModel` call)
- **Library load order** matters: ggml-base must load before ggml, ggml before llama
- **Thread safety**: Inference runs on `new Thread()` — no coroutine integration yet
- **llama.cpp API version**: Uses newer API with `llama_model_get_vocab()`, `llama_vocab_eos()`, separate model/context objects
