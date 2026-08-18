# Project: ModelTest

Single-module Android app for on-device LLM inference via llama.cpp JNI bridge. arm64-v8a only, CPU only.

## Build & Run

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # install on connected device
./gradlew testDebugUnitTest      # unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests
```

## Architecture

- **GoActivity** is the launcher (not MainActivity). Uses XML layout.
- **LlamaNative.java** — JNI binding. Loads native libs in strict order: ggml-base → ggml → llama → ggml-cpu → modeltest.
- **llama_wrapper.cpp** — JNI bridge with 4 methods: initBackend, loadModel, generate, freeContext.
- **Model asset**: qwen2.5-coder-0.5b-instruct-q4_k_m.gguf (~400MB)

## Key Details

- compileSdk 35, minSdk 28, Java 11
- Context size: 1024 tokens
- CPU only (n_gpu_layers = 0)
