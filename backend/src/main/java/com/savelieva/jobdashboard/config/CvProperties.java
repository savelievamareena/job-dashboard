package com.savelieva.jobdashboard.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which CV cores exist. The queue rows themselves come from the database now, so no path is
 * configured here any more; only the list of cores is, and deliberately so.
 *
 * <p>It must not be replaced by reading the cores back out of the queue. A core with nothing built
 * yet still owns its track, and it has no rows to be found by: read the list from the data and
 * "fullstack" disappears the moment its queue is empty, which sends every fullstack posting
 * looking for an answer in the Frontend queue. That is the exact mix-up the per-core split exists
 * to prevent.
 *
 * @param cores core folder names; a core answers the track of the same name, Frontend to frontend
 */
@ConfigurationProperties(prefix = "dashboard.cv")
public record CvProperties(List<String> cores) {

    public CvProperties {
        cores = cores == null ? List.of() : List.copyOf(cores);
    }
}
