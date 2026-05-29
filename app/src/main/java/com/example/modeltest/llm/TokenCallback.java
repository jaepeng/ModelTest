package com.example.modeltest.llm;

/**
 * Callback interface for streaming LLM token generation.
 * Called from native code on the generation thread.
 */
public interface TokenCallback {
    /** Called for each generated token. */
    void onToken(String token);

    /** Called when generation is complete with the full result. */
    void onComplete(String fullResult);
}
