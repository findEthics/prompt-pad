package me.pompel.elauncher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/** Downloads and verifies the optional on-device model without bundling it in the APK. */
public final class ModelDownloader implements AutoCloseable {
    public static final String MANIFEST_URL =
            "https://huggingface.co/findethics-labs/prompt-pad-gemma3-270m/resolve/main/model-manifest.json";
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final Pattern JSON_STRING_FIELD = Pattern.compile(
            "\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");

    public interface Listener {
        void onProgress(int percent);

        void onSuccess();

        void onError(String message);

        void onCancelled();
    }

    public static final class Manifest {
        public final String url;
        public final String sha256;

        private Manifest(String url, String sha256) {
            this.url = url;
            this.sha256 = sha256;
        }
    }

    private final Context context;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private volatile boolean closed;
    private volatile HttpURLConnection activeConnection;
    private Future<?> task;

    public ModelDownloader(Context context, Listener listener) {
        if (context == null || listener == null) {
            throw new IllegalArgumentException("context and listener are required");
        }
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized void start() {
        if (closed || task != null) {
            return;
        }
        task = executor.submit(() -> {
            try {
                download();
                postSuccess();
            } catch (CancelledException exception) {
                postCancelled();
            } catch (Exception exception) {
                if (cancelled || Thread.currentThread().isInterrupted()) {
                    postCancelled();
                } else {
                    postError(errorMessage(exception));
                }
            } finally {
                executor.shutdown();
            }
        });
    }

    public synchronized void cancel() {
        cancelled = true;
        if (activeConnection != null) {
            activeConnection.disconnect();
        }
        if (task != null) {
            task.cancel(true);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        cancel();
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    public static File partFile(File modelFile) {
        return new File(modelFile.getParentFile(), modelFile.getName() + ".part");
    }

    public static long resumeOffset(File partFile) {
        return partFile.isFile() ? partFile.length() : 0L;
    }

    public static Manifest parseManifest(String json) throws IOException {
        if (json == null) {
            throw new IOException("Manifest is empty");
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IOException("Manifest JSON is invalid");
        }
        String url = jsonField(trimmed, "url");
        String sha256 = jsonField(trimmed, "sha256").toLowerCase(Locale.ROOT);
        if (!url.startsWith("https://")) {
            throw new IOException("Manifest URL must use HTTPS");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IOException("Manifest SHA-256 is invalid");
        }
        return new Manifest(url, sha256);
    }

    public static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    public static boolean verifySha256(File file, String expected) throws IOException {
        return expected != null && expected.equalsIgnoreCase(sha256(file));
    }

    private void download() throws IOException, CancelledException {
        checkCancelled();
        Manifest manifest = parseManifest(readText(MANIFEST_URL));
        File modelFile = MediaPipeLlmInterpreter.modelFile(context);
        File parent = modelFile.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Cannot create model directory");
        }
        File part = partFile(modelFile);
        long existing = resumeOffset(part);
        HttpURLConnection connection = openConnection(manifest.url, existing);
        try {
            int status = connection.getResponseCode();
            if (status == 416 && existing > 0) {
                connection.disconnect();
                existing = 0;
                connection = openConnection(manifest.url, -1);
                status = connection.getResponseCode();
            }
            if (status < 200 || status >= 300) {
                throw new IOException("Model download failed with HTTP " + status);
            }

            boolean append = existing > 0 && status == HttpURLConnection.HTTP_PARTIAL;
            long offset = append ? existing : 0L;
            long total = totalBytes(connection, offset);
            MessageDigest digest = newDigest();
            if (append) {
                updateDigest(digest, part);
            }
            postProgress(total > 0 ? percent(offset, total) : -1);
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(part, append)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long downloaded = offset;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled();
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    downloaded += count;
                    if (total > 0) {
                        postProgress(percent(downloaded, total));
                    }
                }
            } finally {
                activeConnection = null;
                connection.disconnect();
            }

            checkCancelled();
            if (!manifest.sha256.equalsIgnoreCase(hex(digest.digest()))) {
                part.delete();
                throw new IOException("Model checksum mismatch; partial file was deleted");
            }
            if (modelFile.exists() || !part.renameTo(modelFile)) {
                throw new IOException("Cannot install verified model");
            }
        } finally {
            if (activeConnection == connection) {
                activeConnection = null;
            }
            connection.disconnect();
        }
    }

    private String readText(String url) throws IOException, CancelledException {
        HttpURLConnection connection = openConnection(url, -1);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Manifest request failed with HTTP " + status);
            }
            StringBuilder result = new StringBuilder();
            try (Reader reader = new InputStreamReader(connection.getInputStream(), "UTF-8")) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    checkCancelled();
                    if (result.length() + count > MAX_MANIFEST_BYTES) {
                        throw new IOException("Manifest is too large");
                    }
                    result.append(buffer, 0, count);
                }
            }
            return result.toString();
        } finally {
            activeConnection = null;
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String initialUrl, long rangeStart) throws IOException {
        URL current = new URL(initialUrl);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            if (!"https".equalsIgnoreCase(current.getProtocol())) {
                throw new IOException("Download URL must use HTTPS");
            }
            HttpsURLConnection connection = (HttpsURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setUseCaches(false);
            if (rangeStart > 0) {
                connection.setRequestProperty("Range", "bytes=" + rangeStart + "-");
            }
            activeConnection = connection;
            int status = connection.getResponseCode();
            if (status < 300 || status >= 400) {
                return connection;
            }
            // ponytail: manual redirects preserve Range because auto-redirect drops custom headers.
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.trim().isEmpty()) {
                throw new IOException("Redirect has no Location header");
            }
            current = new URL(current, location);
        }
        throw new IOException("Too many redirects");
    }

    private static long totalBytes(HttpURLConnection connection, long offset) {
        String contentRange = connection.getHeaderField("Content-Range");
        if (contentRange != null) {
            int slash = contentRange.lastIndexOf('/');
            if (slash >= 0) {
                try {
                    return Long.parseLong(contentRange.substring(slash + 1).trim());
                } catch (NumberFormatException ignored) {
                    // Fall back to Content-Length below.
                }
            }
        }
        String contentLength = connection.getHeaderField("Content-Length");
        if (contentLength != null) {
            try {
                return offset + Long.parseLong(contentLength);
            } catch (NumberFormatException ignored) {
                // Unknown length; progress will be omitted.
            }
        }
        return -1L;
    }

    private static void updateDigest(MessageDigest digest, File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int percent(long current, long total) {
        return (int) Math.min(100L, current * 100L / total);
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = digits[value >>> 4];
            result[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }

    private static String jsonField(String json, String wantedKey) throws IOException {
        Matcher matcher = JSON_STRING_FIELD.matcher(json);
        while (matcher.find()) {
            if (wantedKey.equals(matcher.group(1))) {
                return unescapeJson(matcher.group(2)).trim();
            }
        }
        throw new IOException("Manifest field is missing: " + wantedKey);
    }

    private static String unescapeJson(String value) throws IOException {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++index >= value.length()) {
                throw new IOException("Manifest JSON escape is invalid");
            }
            char escaped = value.charAt(index);
            switch (escaped) {
                case '"': result.append('"'); break;
                case '\\': result.append('\\'); break;
                case '/': result.append('/'); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'u':
                    if (index + 4 >= value.length()) {
                        throw new IOException("Manifest JSON escape is invalid");
                    }
                    try {
                        result.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    } catch (NumberFormatException exception) {
                        throw new IOException("Manifest JSON escape is invalid", exception);
                    }
                    index += 4;
                    break;
                default:
                    throw new IOException("Manifest JSON escape is invalid");
            }
        }
        return result.toString();
    }

    private void checkCancelled() throws CancelledException {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new CancelledException();
        }
    }

    private void postProgress(int percent) {
        post(() -> listener.onProgress(percent));
    }

    private void postSuccess() {
        post(listener::onSuccess);
    }

    private void postError(String message) {
        post(() -> listener.onError(message));
    }

    private void postCancelled() {
        post(listener::onCancelled);
    }

    private void post(Runnable callback) {
        mainHandler.post(() -> {
            if (!closed) {
                callback.run();
            }
        });
    }

    private static String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "Model download failed" : message;
    }

    private static final class CancelledException extends Exception {
    }
}
