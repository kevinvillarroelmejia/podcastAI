package com.podcastai.podcast;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.podcastai.config.AppProperties;

/**
 * Guarda el audio de cada bloque en disco. En la fase 2 se cambia por Supabase Storage (cifrado, región UE).
 */
@Component
public class AudioStorage {

    private final Path root;

    public AudioStorage(AppProperties props) {
        this.root = Path.of(props.storageDir()).toAbsolutePath().normalize();
    }

    public String save(UUID podcastId, int blockIndex, byte[] audio) {
        Path dir = root.resolve(podcastId.toString());
        Path file = dir.resolve("block-" + blockIndex + ".mp3");
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "block-", ".tmp");
            Files.write(tmp, audio);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return root.relativize(file).toString();
    }

    public Path resolve(String relativePath) {
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Ruta fuera del almacenamiento");
        }
        return file;
    }

    public void deletePodcast(UUID podcastId) {
        Path dir = root.resolve(podcastId.toString());
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(AudioStorage::deleteQuietly);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
