package com.savelieva.jobdashboard.repository;

import com.savelieva.jobdashboard.model.JobStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The marks the board sets on a posting: how far it got, and a free note.
 *
 * <p>The one thing this application writes. Everything else in the database belongs to the loader,
 * which is why job_status is the only table migrate.py never truncates: a refill from a file would
 * throw away every mark made since the last import.
 *
 * <p>These used to live in a _status.json the loader mirrored into the table on every run, so the
 * same mark existed in two places and the file was the one that won. The file is no longer read or
 * written. Marks made before the move were carried into the table by
 * {@code db/alter-2026-08-11-board-on-db.sql}.
 *
 * <p>Read on every request rather than cached: the map is tiny, and a mark set in another tab
 * should show up on reload.
 */
@Repository
public class StatusRepository {

    /**
     * Keyed by url for the caller, stored by job_id. The board knows a posting by its url and the
     * table names it the way vacancy does, so the join is what translates between the two.
     */
    private static final String ALL = """
            select v.url, s.status, s.note
            from job_status s
            join vacancy v on v.job_id = s.job_id
            """;

    /**
     * Selecting the job_id out of vacancy rather than binding it directly is what enforces that a
     * mark names a posting the database actually holds. An unknown one writes no row and is
     * reported, instead of failing on the foreign key with a message about a constraint.
     */
    private static final String UPSERT = """
            insert into job_status (job_id, status, note)
            select v.job_id, ?, ? from vacancy v where v.job_id = ?
            on conflict (job_id) do update set status = excluded.status, note = excluded.note
            """;

    /** An emptied mark leaves no row, the same way it left no key in the file. */
    private static final String DELETE = "delete from job_status where job_id = ?";

    private final JdbcTemplate jdbc;

    public StatusRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, JobStatus> findAll() {
        Map<String, JobStatus> all = new LinkedHashMap<>();
        jdbc.query(ALL, rs -> {
            all.put(rs.getString("url"), new JobStatus(rs.getString("status"), rs.getString("note")));
        });
        return all;
    }

    public JobStatus find(String url) {
        return findAll().getOrDefault(url, JobStatus.EMPTY);
    }

    /**
     * Stores the mark for one posting, or removes it once it is empty again.
     *
     * @throws UnknownPostingException when no posting in the database carries this url
     */
    public void save(String url, JobStatus status) {
        String jobId = jobId(url);
        if (status.isEmpty()) {
            // Nothing to clear is not a failure: an untouched posting has no row to begin with,
            // so clearing it twice has to be as harmless as clearing it once.
            jdbc.update(DELETE, jobId);
            return;
        }
        if (jdbc.update(UPSERT, status.status(), status.note(), jobId) == 0) {
            throw new UnknownPostingException(url);
        }
    }

    /**
     * The trailing part of the url, which is how every part of this project names a posting: the
     * loader derives it the same way, and the alter script derived it the same way when it moved
     * the old marks over.
     */
    private String jobId(String url) {
        String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        int cut = trimmed.lastIndexOf('/');
        return cut < 0 ? trimmed : trimmed.substring(cut + 1);
    }

    /** A mark was sent for a posting the database does not hold, most likely from a stale tab. */
    public static class UnknownPostingException extends RuntimeException {
        public UnknownPostingException(String url) {
            super("no posting in the database has the url " + url);
        }
    }
}
