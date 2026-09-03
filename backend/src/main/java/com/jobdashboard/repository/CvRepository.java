package com.jobdashboard.repository;

import com.jobdashboard.config.CvProperties;
import com.jobdashboard.model.CvChoices;
import com.jobdashboard.model.CvKind;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Which CV was built for each posting, from the queue the loader imports from review-queue.csv. */
@Repository
public class CvRepository {

    /** Ordered by id so a repeated row still wins the way a later line in the file did. */
    private static final String QUEUE = "select core, url, company, kind from cv_queue order by id";

    private final CvProperties properties;
    private final JdbcTemplate jdbc;

    public CvRepository(CvProperties properties, JdbcTemplate jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    public CvChoices findAll() {
        // Every configured core is listed, queue or not: an empty core still owns its track.
        Map<String, Map<String, CvKind>> byUrl = new LinkedHashMap<>();
        Map<String, Map<String, CvKind>> byCompany = new LinkedHashMap<>();
        for (String core : properties.cores()) {
            byUrl.put(key(core), new LinkedHashMap<>());
            byCompany.put(key(core), new LinkedHashMap<>());
        }

        jdbc.query(QUEUE, rs -> {
            String core = key(rs.getString("core"));
            Map<String, CvKind> urls = byUrl.get(core);
            if (urls == null) {
                return;   // a queue for a core the configuration does not list is not ours to read
            }
            CvKind kind = CvKind.valueOf(rs.getString("kind").toUpperCase(Locale.ROOT));
            String url = rs.getString("url");
            if (url == null || url.isBlank()) {
                byCompany.get(core).put(key(rs.getString("company")), kind);
            } else {
                urls.put(url.trim(), kind);
            }
        });

        Map<String, CvChoices.Queue> queues = new LinkedHashMap<>();
        byUrl.forEach((core, urls) ->
                queues.put(core, new CvChoices.Queue(urls, byCompany.get(core))));
        return new CvChoices(queues);
    }

    /** Folder names are written by hand in both places, so they agree on spelling, not on case. */
    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
