package com.zx.consultant.common.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads {@code .env} into system properties before Spring starts (local/dev).
 * Production should inject real environment variables instead of relying on this file.
 */
public final class LocalDotenvBootstrap {

    private LocalDotenvBootstrap() {
    }

    public static void load() {
        for (Path path : candidatePaths()) {
            if (Files.isRegularFile(path)) {
                loadFile(path.toAbsolutePath().normalize());
                return;
            }
        }
    }

    private static List<Path> candidatePaths() {
        Set<Path> paths = new LinkedHashSet<>();

        // Relative to process working directory (IDE / manual start).
        paths.add(Path.of(".env").toAbsolutePath().normalize());
        paths.add(Path.of("AiConsultant", ".env").toAbsolutePath().normalize());
        paths.add(Path.of("backend", "AiConsultant", ".env").toAbsolutePath().normalize());
        paths.add(Path.of("backend", ".env").toAbsolutePath().normalize());

        // Relative to code location: jar dir, project root (parent of target/), classes dir parents.
        for (Path anchor : codeLocationAnchors()) {
            paths.add(anchor.resolve(".env").normalize());
            Path parent = anchor.getParent();
            if (parent != null) {
                paths.add(parent.resolve(".env").normalize());
            }
        }

        return new ArrayList<>(paths);
    }

    private static List<Path> codeLocationAnchors() {
        List<Path> anchors = new ArrayList<>();
        try {
            CodeSource codeSource = LocalDotenvBootstrap.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return anchors;
            }
            URL location = codeSource.getLocation();
            if (location == null) {
                return anchors;
            }
            Path codePath = Path.of(location.toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(codePath)) {
                // Running from jar: .../backend/target/app.jar
                Path jarDir = codePath.getParent();
                if (jarDir != null) {
                    anchors.add(jarDir);
                    Path projectRoot = jarDir.getParent();
                    if (projectRoot != null) {
                        anchors.add(projectRoot);
                    }
                }
            } else {
                // Running from IDE: .../target/classes
                anchors.add(codePath);
                Path targetDir = codePath.getParent();
                if (targetDir != null) {
                    anchors.add(targetDir);
                    Path projectRoot = targetDir.getParent();
                    if (projectRoot != null) {
                        anchors.add(projectRoot);
                    }
                }
            }
        } catch (URISyntaxException ignored) {
            // Fall through to cwd candidates / real environment variables.
        }
        return anchors;
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
                // Never override real OS env vars / existing system properties (prod-safe).
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException ignored) {
            // Fall through to spring-dotenv / real environment variables.
        }
    }
}
