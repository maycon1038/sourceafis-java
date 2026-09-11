package com.machinezoo.sourceafis.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Private local directory, or a private Cloud Storage volume mounted by Cloud Run. */
@Service
public class PrivateImageStorage {
    private final Path root;

    public PrivateImageStorage(@Value("${app.storage.path}") String directory) throws IOException {
        root = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String save(byte[] image) throws IOException {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Image must not be empty");
        }
        String key = UUID.randomUUID().toString();
        Files.write(resolve(key), image, StandardOpenOption.CREATE_NEW);
        return key;
    }

    public byte[] read(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || !UUID.fromString(key).toString().equals(key)) {
            throw new IllegalArgumentException("Invalid image object key");
        }
        return root.resolve(key);
    }
}
