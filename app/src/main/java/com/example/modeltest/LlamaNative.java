package com.example.modeltest;

import android.util.Log;

public class LlamaNative {
    static {
        try {
            // Load base dependencies first
            System.loadLibrary("ggml-base");
            System.loadLibrary("ggml");
            System.loadLibrary("llama");
            // Load the ggml-cpu backend if it exists
            try {
                System.loadLibrary("ggml-cpu-android_armv8.2_1");
            } catch (UnsatisfiedLinkError e) {
                Log.w("LLAMA", "ggml-cpu backend not loaded: " + e.getMessage());
            }
            // Load the JNI wrapper
            System.loadLibrary("modeltest");
            Log.d("LLAMA", "All native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e("LLAMA", "Native library loading failed: " + e.getMessage());
        } catch (Throwable t) {
            Log.e("LLAMA", "Unexpected error during native library loading", t);
        }
    }

    // ----------- JNI Methods -----------
    public native boolean initBackend();
    public native long loadModel(String modelPath, int nCtx);
    public native String generate(long ctxPtr, String prompt, int maxTokens);
    public native void freeContext(long ctxPtr);
}
