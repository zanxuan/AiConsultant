package com.zx.consultant.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads {@code .env} into system properties before Spring starts.
 * Searches common locations so IDE runs from the monorepo root still work.
 */
public final class LocalDotenvBootstrap {

    private static final List<Path> CANDIDATES = List.of(
            Path.of(".env"),
            Path.of("AiConsultant", ".env"),
            Path.of("backend", "AiConsultant", ".env")
    );

    private LocalDotenvBootstrap() {
    }

    public static void load() {
        for (Path path : CANDIDATES) {
            if (Files.isRegularFile(path)) {
                loadFile(path.toAbsolutePath().normalize());
                return;
            }
        }
    }

    private static void loadFile(Path path) {
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException ignored) {
            // Fall through to spring-dotenv / real environment variables.
        }
    }
}
