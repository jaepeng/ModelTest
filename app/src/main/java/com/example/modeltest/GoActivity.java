package com.example.modeltest;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class GoActivity extends AppCompatActivity {

    private static final String SYSTEM_PROMPT = "你是一个中文AI助手，必须用中文回答所有问题，不要使用英文。";
    private LlamaNative llama;
    private long ctxPtr = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_go);

        copyAssetToFilesIfNotExists("models/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf", "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf");

        try {
            llama = new LlamaNative();
        } catch (Throwable e) {
            Log.e("QWEN", "Failed to initialize LlamaNative", e);
            return;
        }

        new Thread(() -> {
            // 1. 初始化后端
            boolean ok = llama.initBackend();
            Log.d("QWEN", "initBackend = " + ok);

            // 2. 获取模型路径
            File externalFilesDir = getExternalFilesDir(null);
            if (externalFilesDir == null) {
                Log.e("QWEN", "getExternalFilesDir is null");
                return;
            }
            File modelFile = new File(externalFilesDir, "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf");
            if (!modelFile.exists()) {
                Log.e("QWEN", "Model file does not exist: " + modelFile.getAbsolutePath());
                return;
            }
            Log.d("QWEN", "Model file size: " + modelFile.length() + " bytes");
            String modelPath = modelFile.getAbsolutePath();

            // 3. 加载模型
            ctxPtr = llama.loadModel(modelPath, 1024);
            if (ctxPtr == 0L) {
                Log.e("QWEN", "loadModel failed");
                return;
            }
            Log.d("QWEN", "loadModel success, ctxPtr=" + ctxPtr);

            // 4. 推理测试
            String prompt = "<|im_start|>user\n你好，介绍一下自己,你是什么模型<|im_end|>\n<|im_start|>assistant\n";
            String result = llama.generate(ctxPtr, prompt, 512);

            runOnUiThread(() -> {
                Log.i("jae", "onCreate: result==>" + result);

            });
            String prompt1 = "<|im_start|>user\n帮我生成3个提升个人专注力的小任务，用json格式返回给我<|im_end|>\n<|im_start|>assistant\n";
            String result1 = llama.generate(ctxPtr, prompt1, 512);
            Log.i("jae", "onCreate: "+result1);
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ctxPtr != 0L && llama != null) {
            llama.freeContext(ctxPtr);
        }
    }

    private void copyAssetToFilesIfNotExists(String assetName, String destName) {
        File destFile = new File(getExternalFilesDir(null), destName);
        if (destFile.exists()) return;

        try (InputStream in = getAssets().open(assetName);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            Log.d("MODEL", "Copied to " + destFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("MODEL", "Failed to copy asset", e);
        }
    }
}
