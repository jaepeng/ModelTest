package com.example.modeltest.llm

import android.content.Context
import android.util.Log
import com.example.modeltest.LlamaNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Singleton service that wraps LlamaNative for on-device LLM inference.
 * Handles model loading (lazy, once) and text generation.
 */
class LlmService(private val context: Context) {

    private val llama = LlamaNative()
    private var ctxPtr = 0L
    private var initialized = false

    companion object {
        private const val TAG = "LlmService"
        private const val MODEL_FILENAME = "MiniCPM-V-4_6-Q4_0.gguf"
        private const val ASSET_PATH = "models/$MODEL_FILENAME"
        private const val N_CTX = 2048
        private const val MAX_TOKENS = 1024
    }

    /**
     * Initialize the LLM backend and load model. Safe to call multiple times - only initializes once.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        try {
            copyModelIfNeeded()
            llama.initBackend()
            val modelPath = File(context.getExternalFilesDir(null), MODEL_FILENAME).absolutePath
            ctxPtr = llama.loadModel(modelPath, N_CTX)
            if (ctxPtr == 0L) {
                Log.e(TAG, "loadModel returned 0")
                return@withContext
            }
            initialized = true
            Log.d(TAG, "LLM initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
        }
    }

    /**
     * Generate text from a prompt. Returns empty string if not initialized.
     */
    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        if (!initialized || ctxPtr == 0L) {
            Log.e(TAG, "LLM not initialized: initialized=$initialized, ctxPtr=$ctxPtr")
            return@withContext ""
        }
        Log.d(TAG, "Generating with prompt length=${prompt.length}")
        val result = llama.generate(ctxPtr, prompt, MAX_TOKENS)
        Log.d(TAG, "Generate result length=${result.length}")
        if (result.isNotEmpty()) {
            Log.d(TAG, "Generate output: $result")
        } else {
            Log.w(TAG, "Generate returned empty string")
        }
        result
    }

    /**
     * Free native resources. Call when app is destroyed.
     */
    fun release() {
        if (ctxPtr != 0L) {
            llama.freeContext(ctxPtr)
            ctxPtr = 0L
            initialized = false
        }
    }

    private fun copyModelIfNeeded() {
        val destFile = File(context.getExternalFilesDir(null), MODEL_FILENAME)
        if (destFile.exists()) return

        try {
            context.assets.open(ASSET_PATH).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            Log.d(TAG, "Model copied to ${destFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy model asset", e)
        }
    }
}
