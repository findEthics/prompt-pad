package me.pompel.elauncher;

import android.content.Context;
import android.util.Log;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;

import java.io.File;

/** MediaPipe-backed natural-language command interpreter. */
public final class MediaPipeLlmInterpreter implements LlmCommandInterpreter, AutoCloseable {
    public static final String MODEL_FILE_NAME = "prompt-pad-gemma3-270m.task";
    private static final String TAG = "MediaPipeLlmInterpreter";
    // MediaPipe counts prompt and generated tokens together.
    private static final int MAX_TOKENS = 256;

    private final Context context;
    private LlmInference inference;

    public MediaPipeLlmInterpreter(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context.getApplicationContext();
    }

    public static File modelFile(Context context) {
        File directory = context.getExternalFilesDir(null);
        return new File(directory == null ? context.getFilesDir() : directory, MODEL_FILE_NAME);
    }

    public static boolean isModelPresent(Context context) {
        return modelFile(context).isFile();
    }

    public boolean isAvailable() {
        return isModelPresent(context);
    }

    @Override
    public synchronized String interpret(String text) {
        if (text == null || text.trim().isEmpty() || !isModelPresent(context)) {
            return null;
        }
        try {
            if (inference == null) {
                inference = createInference();
            }
            try (LlmInferenceSession session = LlmInferenceSession.createFromOptions(
                    inference,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                            .setTopK(1)
                            .setTopP(1.0f)
                            .setTemperature(0.0f)
                            .build())) {
                session.addQueryChunk(text);
                return session.generateResponse();
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Natural-language inference failed", exception);
            return null;
        }
    }

    private LlmInference createInference() {
        return LlmInference.createFromOptions(context,
                LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile(context).getAbsolutePath())
                        .setMaxTokens(MAX_TOKENS)
                        .setMaxTopK(1)
                        .setPreferredBackend(LlmInference.Backend.CPU)
                        .build());
    }

    @Override
    public synchronized void close() {
        if (inference != null) {
            inference.close();
            inference = null;
        }
    }
}
