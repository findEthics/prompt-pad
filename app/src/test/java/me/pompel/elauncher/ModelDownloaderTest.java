package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ModelDownloaderTest {
    @Test
    public void resumeOffsetUsesExistingPartLength() throws Exception {
        File part = File.createTempFile("prompt-pad-model", ".part");
        try {
            try (FileOutputStream output = new FileOutputStream(part)) {
                output.write("partial model".getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(13L, ModelDownloader.resumeOffset(part));
            assertEquals("model.task.part", ModelDownloader.partFile(
                    new File(part.getParentFile(), "model.task")).getName());
        } finally {
            assertTrue(part.delete());
        }
    }

    @Test
    public void sha256VerificationAcceptsMatchingFileAndRejectsMismatch() throws Exception {
        File file = File.createTempFile("prompt-pad-model", ".task");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write("prompt-pad".getBytes(StandardCharsets.UTF_8));
            }
            assertEquals("871793761724569cf949ee6e3762daf9059d78f29d3d4c96339f552db02f420a",
                    ModelDownloader.sha256(file));
            assertTrue(ModelDownloader.verifySha256(file,
                    "871793761724569cf949ee6e3762daf9059d78f29d3d4c96339f552db02f420a"));
            assertFalse(ModelDownloader.verifySha256(file, "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"));
        } finally {
            assertTrue(file.delete());
        }
    }

    @Test
    public void parsesAndValidatesManifest() throws Exception {
        ModelDownloader.Manifest manifest = ModelDownloader.parseManifest("{"
                + "\"version\":\"commit\","
                + "\"url\":\"https://huggingface.co/model/resolve/commit/model.task\","
                + "\"sha256\":\"871793761724569cf949ee6e3762daf9059d78f29d3d4c96339f552db02f420a\""
                + "}");

        assertEquals("https://huggingface.co/model/resolve/commit/model.task", manifest.url);
        assertEquals("871793761724569cf949ee6e3762daf9059d78f29d3d4c96339f552db02f420a",
                manifest.sha256);
    }

    @Test(expected = IOException.class)
    public void rejectsManifestWithNonHttpsUrl() throws Exception {
        ModelDownloader.parseManifest("{"
                + "\"url\":\"http://example.test/model.task\","
                + "\"sha256\":\"871793761724569cf949ee6e3762daf9059d78f29d3d4c96339f552db02f420a\""
                + "}");
    }
}
