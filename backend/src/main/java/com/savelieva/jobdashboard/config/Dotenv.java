package com.savelieva.jobdashboard.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/** Reads {@code .env} into system properties before Spring starts, searching upwards. */
public final class Dotenv {

    private static final String FILE_NAME = ".env";
    private static final int MAX_DEPTH = 5;

    private Dotenv() {
    }

    /** Loads {@code .env} found from the working directory upwards. */
    public static void load() {
        load(Paths.get("").toAbsolutePath());
    }

    /** The real environment and system properties win; a missing file is not an error. */
    static void load(Path startDirectory) {
        find(startDirectory).ifPresent(file -> read(file).forEach((key, value) -> {
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
        }));
    }

    private static Optional<Path> find(Path startDirectory) {
        Path dir = startDirectory;
        for (int i = 0; i <= MAX_DEPTH && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static java.util.Map<String, String> read(Path file) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            // An unreadable .env is not worth aborting on: the variables may well come from the
            // environment instead, and a database that cannot be reached is reported per request.
            return values;
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            values.put(trimmed.substring(0, eq).trim(), unquote(trimmed.substring(eq + 1).trim()));
        }
        return values;
    }

    private static String unquote(String value) {
        boolean quoted = value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")));
        return quoted ? value.substring(1, value.length() - 1) : value;
    }
}
