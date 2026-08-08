package com.savelieva.jobdashboard.config;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the parsing skills leave their output. Nothing here writes into those folders except the
 * status file, so a wrong path degrades to an empty dashboard rather than to lost data.
 *
 * @param roots      folders holding {@code <date>/<track>/jobs.csv}, newest date first in the UI
 * @param statusFile JSON file the dashboard owns: url to status and note
 * @param tracks     track folder names, in the order they should be scanned
 */
@ConfigurationProperties(prefix = "dashboard")
public record SearchProperties(List<Path> roots, Path statusFile, List<String> tracks) {

    public SearchProperties {
        roots = roots == null ? List.of() : List.copyOf(roots);
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        roots.forEach(SearchProperties::requireResolved);
        requireResolved(statusFile);
    }

    /**
     * Spring's binder leaves an unresolved placeholder in the value instead of failing, so a
     * missing DASHBOARD_ROOT would otherwise reach the file reader as the literal path
     * "${DASHBOARD_ROOT}/DailySearch", which simply does not exist and shows an empty board that
     * looks like a quiet day rather than a broken setup. Refusing to start says what to fix.
     */
    private static void requireResolved(Path path) {
        if (path != null && path.toString().contains("${")) {
            throw new IllegalStateException("""
                    DASHBOARD_ROOT is not set, so %s stayed unresolved.
                    Set it to the folder holding DailySearch, either in a .env file at the root of \
                    this repository (copy .env.example) or as an environment variable."""
                    .formatted(path));
        }
    }
}
