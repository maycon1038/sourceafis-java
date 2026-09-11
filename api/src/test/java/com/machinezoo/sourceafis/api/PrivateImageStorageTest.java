package com.machinezoo.sourceafis.api;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PrivateImageStorageTest {
    @TempDir
    Path directory;

    @Test
    void persistsOriginalImageAcrossInstances() throws Exception {
        var storage = new PrivateImageStorage(directory.toString());
        byte[] original = {1, 2, 3};
        String key = storage.save(original);
        assertArrayEquals(original, new PrivateImageStorage(directory.toString()).read(key));
        assertNotEquals(key, storage.save(original));
    }

    @Test
    void rejectsPathsOutsidePrivateDirectory() throws Exception {
        var storage = new PrivateImageStorage(directory.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.read("../secret"));
        assertThrows(IllegalArgumentException.class, () -> storage.read("C:/secret"));
        assertThrows(IllegalArgumentException.class, () -> storage.save(new byte[0]));
    }
}
