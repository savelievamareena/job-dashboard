package com.jobdashboard.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Which CV cores exist. Configured, not read back from the queue: an empty core would vanish. */
@ConfigurationProperties(prefix = "dashboard.cv")
public record CvProperties(List<String> cores) {

    public CvProperties {
        cores = cores == null ? List.of() : List.copyOf(cores);
    }
}
