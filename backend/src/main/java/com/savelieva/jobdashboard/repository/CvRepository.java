package com.savelieva.jobdashboard.repository;

import com.savelieva.jobdashboard.config.CvProperties;
import com.savelieva.jobdashboard.model.CvChoices;
import com.savelieva.jobdashboard.model.CvKind;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads which CV was built for each posting out of the queue the loader imports from
 * {@code <core>/review-queue.csv}.
 *
 * <p>A row is one posting: two ads from the same agency often get different answers, one tailored
 * and one sent the core, so keying by company would let the first one speak for the second. Rows
 * written before the queue carried a URL are kept under their company, where they answer for every
 * posting of it. That is all such a row can honestly say.
 *
 * <p>Whether a row is a tailored CV or the core one is decided by the loader and stored in the
 * kind column. It reads that off the path: a CV tailored for a company lives in a folder named
 * after that company, and anything else under the core is the core CV queued under the company it
 * will be sent to. That naming is what the tailoring skill guarantees, and it is the only marker
 * the queue carries.
 */
@Repository
public class CvRepository {

    /**
     * Ordered by id so a repeated row still wins the way it did in the file, where a later line
     * overwrote an earlier one. Without the order Postgres is free to hand back either, and the
     * queue is appended to by hand often enough for that to matter.
     */
    private static final String QUEUE = "select core, url, company, kind from cv_queue order by id";

    private final CvProperties properties;
    private final JdbcTemplate jdbc;

    public CvRepository(CvProperties properties, JdbcTemplate jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    public CvChoices findAll() {
        // Every configured core is listed, queue or not. The list comes from the configuration and
        // must not be read back out of this table with a select distinct: a core with nothing built
        // yet still owns its track, and dropping it would send that track looking for an answer in
        // the other core, which is exactly the mix-up the per-core split exists to prevent.
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
