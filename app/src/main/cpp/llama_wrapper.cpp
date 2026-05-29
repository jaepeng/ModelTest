#include <jni.h>
#include <android/log.h>
#include <string>
#include "llama.h"
#include "ggml-backend.h"
#include "ggml-cpu.h"

#define LOG_TAG "LLAMA_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct MyContext {
    llama_model* model;
    llama_context* ctx;
    const llama_vocab* vocab;
};

static void llama_log_callback_android(enum ggml_log_level level, const char * text, void * user_data) {
    (void)level;
    (void)user_data;
    __android_log_print(ANDROID_LOG_INFO, "LLAMA_CPP", "%s", text);
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_modeltest_LlamaNative_initBackend(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    llama_log_set(llama_log_callback_android, nullptr);

    // Explicitly register CPU backend if possible
    ggml_backend_reg_t cpu_reg = ggml_backend_cpu_reg();
    if (cpu_reg) {
        ggml_backend_register(cpu_reg);
        LOGD("CPU backend registered manually");
    } else {
        LOGE("Failed to get CPU backend registry");
    }

    llama_backend_init();
    LOGD("llama backend initialized");
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_example_modeltest_LlamaNative_loadModel(
        JNIEnv* env,
        jobject thiz,
        jstring modelPath,
        jint nCtx) {
    (void)thiz;

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("model path is null");
        return 0;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    llama_model* model = llama_model_load_from_file(path, mparams);
    if (!model) {
        LOGE("failed to load model");
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_threads = 4;

    llama_context* ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("failed to create context");
        llama_model_free(model);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }

    // 获取词表（新版API必须用它来调用llama_tokenize）
    const llama_vocab* vocab = llama_model_get_vocab(model);

    // 保存模型、上下文和词表
    MyContext* myCtx = new MyContext();
    myCtx->model = model;
    myCtx->ctx = ctx;
    myCtx->vocab = vocab;

    env->ReleaseStringUTFChars(modelPath, path);
    LOGD("model loaded successfully");
    return (jlong)myCtx;
}

JNIEXPORT jstring JNICALL
Java_com_example_modeltest_LlamaNative_generate(
        JNIEnv* env,
        jobject thiz,
        jlong myCtxPtr,
        jstring prompt,
        jint maxTokens) {
    (void)thiz;

    MyContext* myCtx = reinterpret_cast<MyContext*>(myCtxPtr);
    if (!myCtx || !myCtx->ctx || !myCtx->vocab) {
        LOGE("context or vocab is null (ptr=%p)", myCtx);
        if (myCtx) {
             LOGE("ctx=%p, vocab=%p", myCtx->ctx, myCtx->vocab);
        }
        return env->NewStringUTF("");
    }

    // Clear KV cache from previous generate() call
    llama_memory_clear(llama_get_memory(myCtx->ctx), true);

    llama_context* ctx = myCtx->ctx;
    const llama_vocab* vocab = myCtx->vocab;

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    if (!promptStr) {
        LOGE("prompt is null");
        return env->NewStringUTF("");
    }
    LOGD("Prompt: %s", promptStr);

    std::string result;
    llama_batch batch = llama_batch_init(512, 0, 1);

    // ✅ 新版API：用 vocab 作为第一个参数
    int n_prompt = llama_tokenize(
            vocab,
            promptStr,
            (int32_t)strlen(promptStr),
            batch.token,
            512,
            true,
            true
    );

    if (n_prompt < 0) {
        LOGE("tokenize failed with code %d", n_prompt);
        llama_batch_free(batch);
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("");
    }
    LOGD("Tokenized prompt: %d tokens", n_prompt);

    batch.n_tokens = n_prompt;
    for (int i = 0; i < n_prompt; i++) {
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_prompt - 1);
    }

    int decode_res = llama_decode(ctx, batch);
    if (decode_res != 0) {
        LOGE("decode failed with code %d", decode_res);
        llama_batch_free(batch);
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("");
    }
    LOGD("Decoded prompt successfully");

    // Build a sampler chain: top_k -> top_p -> temp -> penalties -> dist
    // to prevent repetitive output from the small 0.5B model
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        64,    // penalize last 64 tokens
        1.1f,  // repeat penalty (>1.0 = penalize repeats)
        0.8f,  // frequency penalty
        1.0f   // present penalty
    ));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(12345));

    int cur = n_prompt;
    while (cur < n_prompt + maxTokens) {
        int token = llama_sampler_sample(smpl, ctx, -1);

        if (token == llama_vocab_eos(vocab)) {
            LOGD("Sampled EOS token at step %d", cur);
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(
                vocab,
                token,
                buf,
                sizeof(buf),
                0,
                false
        );

        if (n > 0) {
            result.append(buf, n);
        }

        if (cur == n_prompt) {
             LOGD("First generated token id: %d, piece length: %d", token, n);
        }

        batch.n_tokens = 1;
        batch.token[0] = token;
        batch.pos[0] = cur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;

        if (llama_decode(ctx, batch) != 0) {
            break;
        }
        cur++;
    }

    llama_sampler_free(smpl);
    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, promptStr);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_modeltest_LlamaNative_freeContext(
        JNIEnv* env,
        jobject thiz,
        jlong myCtxPtr) {
    (void)env;
    (void)thiz;

    MyContext* myCtx = reinterpret_cast<MyContext*>(myCtxPtr);
    if (myCtx) {
        if (myCtx->ctx) llama_free(myCtx->ctx);
        if (myCtx->model) llama_model_free(myCtx->model);
        delete myCtx;
        LOGD("context freed");
    }
}

}