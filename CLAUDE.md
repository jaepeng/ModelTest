# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android app that generates daily "5-minute challenges" via on-device LLM inference. Loads a GGUF model (MiniCPM-V-4_6-Q4_0) through llama.cpp JNI, asks the model for JSON-formatted challenge lists keyed by category, persists them in Room, and tracks daily completion. Jetpack Compose UI, arm64-v8a only, CPU only.

## Build & Run

```bash
./gradlew assembleDebug              # build debug APK
./gradlew installDebug               # install on connected device
./gradlew testDebugUnitTest          # unit tests (only example tests exist)
./gradlew connectedDebugAndroidTest  # instrumented tests
```

No CI, no lint/format/typecheck beyond Gradle defaults. NDK builds native code via CMake (`app/src/main/cpp/CMakeLists.txt`); prebuilt `.so` files are vendored in `app/src/main/jniLibs/arm64-v8a/` and not rebuilt.

## Architecture

### Entry point

**`MainActivity.kt`** is the launcher (Compose). It reads `UserSettingRepository.isOnboardingCompleted()` and routes to either onboarding or `AppNavigation(startDestination = Home)`. `GoActivity` (XML layout) is a legacy standalone inference test bench — not part of the Compose flow, but still wired in the manifest and kept for debugging the native layer directly.

### Navigation / UI (`ui/`)

`ui/navigation/AppNavigation.kt` — NavHost with a bottom bar over Home / History / Settings; non-bottom routes `weekly_plan` and `onboarding` pushed on top. Each feature lives in its own package with a `*Screen.kt` composable + `*ViewModel.kt`:
- `ui/home` — daily challenge list, generation, toggle completion, confetti on all-done
- `ui/history` — past challenges + category distribution chart (Vico)
- `ui/settings`, `ui/settings/WeeklyPlanScreen.kt` — settings, weekly plan
- `ui/onboarding` — first-run setup (category selection, daily count)
- `ui/components` — `ProgressRing`, `ChallengeCard`, `ConfettiAnimation`

### LLM layer (`llm/`)

- **`LlamaNative.java`** — JNI binding. Static block loads native libs in strict dependency order: `ggml-base → ggml → llama → (ggml-cpu-android_armv8.2_1, try/catch) → modeltest`. Order matters. Exposes `initBackend`, `loadModel(path, nCtx)`, `generate`, `generateStreaming(...,TokenCallback)`, `freeContext`.
- **`LlmService.kt`** — singleton wrapper around `LlamaNative`. Lazy-initializes once (copies model asset → `getExternalFilesDir`, `initBackend`, `loadModel`). `N_CTX = 4096`, `MAX_TOKENS = 4096`. `generateStreaming` adapts the JNI `TokenCallback` into a Kotlin `Flow<String>` via `callbackFlow` (each token emitted on the generation thread; `onComplete` closes the flow). Call `release()` to free the native context.
- **`TokenCallback.java`** — interface called from native code (`onToken(String)`, `onComplete(String)`).
- **`ChallengeParser.kt`** — extracts JSON object from raw model output (strips `` reasoning blocks, finds first `{` → last `}`) into `Map<categoryName, List<challengeText>>`. Tolerates extra text the model emits around the JSON.

### Daily challenge generation flow (HomeViewModel)

1. On init, if no challenges exist for today (`challengeRepo.todayHasChallenges()`), generate.
2. Read user-selected categories + daily count from `UserSettingRepository`; split count across categories with remainder distributed across the first N.
3. Build a Chinese prompt using MiniCPM chat template `<|im_start|>user\n…<|im_end|>\n<|im_start|>assistant\n`, demanding strict JSON output with English category keys.
4. `llmService.generateStreaming(prompt).collect { … }` — tokens append to `_thinkingText` for live UI display.
5. `ChallengeParser.parse` → look up category DB rows by both `name` and `displayName` → `challengeRepo.archiveAndCleanupToday()` then `insertChallenges`.
6. Toggling completion fires confetti when `completedCount == totalCount`.

### Data layer (`data/`)

Room database `daily_challenge.db`, **version 3**, `fallbackToDestructiveMigration()` (schema changes wipe data). Entities:
- `Category` (7 seeded: health/mindfulness/learning/creativity/social/fitness/nature — seeded in `AppDatabase.SeedCallback.onOpen` if table empty, idempotent)
- `Challenge` (FK→Category CASCADE, unique index `[categoryId,date,text]`)
- `ChallengeCompletion`, `WeeklyPlan`, `UserSetting`

DAOs: `CategoryDao`, `ChallengeDao`, `WeeklyPlanDao`, `UserSettingDao`. Repositories wrap DAOs (`ChallengeRepository`, `WeeklyPlanRepository`, `UserSettingRepository`). `AppDatabase.getDatabase(context)` is a synchronized singleton.

### Native layer (`app/src/main/cpp/`)

- **`llama_wrapper.cpp`** — JNI bridge. `MyContext` holds `{model, ctx, vocab}`. Uses newer llama.cpp API (`llama_model_get_vocab`, `llama_vocab_eos`, `llama_init_from_model`, `llama_memory_clear`). `initBackend` manually registers the CPU backend. `generate` and `generateStreaming` both: clear KV cache → tokenize (vocab-first API, 512-token batch buf, add-special+parse-special) → decode → sample loop. Sampler chain: `top_k(40) → top_p(0.9) → temp(0.8) → penalties(64,1.1,0.8,1.0) → dist(seed=12345)` (penalties counter the small model's repetition). `generateStreaming` resolves `TokenCallback` method IDs via thread-attached `JNIEnv` (`getJNIEnv()` uses stored `g_jvm`) and calls back per token + `onComplete`.
- **`CMakeLists.txt`** — links `llama`/`ggml`/`ggml-base` as IMPORTED targets; `ggml-cpu-android_armv8.2_1` linked via `-L`/`-l` flags (avoids missing-SONAME issue with IMPORTED targets).
- **`include/`** — vendored llama.cpp + ggml headers, do not edit.

### Model assets

`app/src/main/assets/models/MiniCPM-V-4_6-Q4_0.gguf` (large, untracked-ish; model swapped from an earlier Qwen2.5-Coder-0.5B). Copied to `getExternalFilesDir(null)` on first launch (or first `LlmService.initialize()`). `tokenizer.json` also present but not used by the native path.

## Key Details

- `compileSdk 36`, `targetSdk 34`, `minSdk 28`, Java/Kotlin `JVM_11`, Compose enabled, KSP for Room, multidex on
- ABI: arm64-v8a only (`defaultConfig.ndk.abiFilters`)
- CPU only: `mparams.n_gpu_layers = 0` in `llama_wrapper.cpp`
- Context size: 4096 in `LlmService`; legacy 1024 in `GoActivity`
- Native lib load order is fragile — keep `ggml-base → ggml → llama`; the `ggml-cpu-android_armv8.2_1` load is wrapped in try/catch (graceful fallback) and its name carries an arch suffix
- Inference runs off the main thread: `LlmService` uses `Dispatchers.IO`; the JNI callback thread attaches to the JVM via stored `g_jvm`
- `android:largeHeap="true"` in manifest (model memory)
- ProGuard keep rules for `LlamaNative` + native methods live in `app/src/main/keepRules/rules.keep`
- Room uses destructive migration — bumping schema version wipes user data; write migrations instead if existing data matters
- Chat template is MiniCPM-style `<|im_start|>` (not Qwen ChatML); prompt and parser assume Chinese output with English JSON keys
