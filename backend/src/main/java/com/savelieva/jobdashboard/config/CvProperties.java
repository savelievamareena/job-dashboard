package com.savelieva.jobdashboard.config;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the tailoring skill leaves its review queue. Read only, like every folder here except the
 * status file.
 *
 * <p>Nothing validates the path: a missing folder degrades to an empty CV column, which is a column
 * saying "nothing built yet" rather than a broken board. An unset {@code DASHBOARD_ROOT} is already
 * refused by {@link SearchProperties}, so it cannot reach this far unnoticed.
 *
 * @param root  folder holding {@code <core>/review-queue.csv}
 * @param cores core folder names; a core answers the track of the same name, Frontend to frontend
 */
@ConfigurationProperties(prefix = "dashboard.cv")
public record CvProperties(Path root, List<String> cores) {

    public CvProperties {
        cores = cores == null ? List.of() : List.copyOf(cores);
    }
}
