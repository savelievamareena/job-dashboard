package com.savelieva.jobdashboard.repository;

import com.savelieva.jobdashboard.model.JobStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The marks the board sets, the one thing this app writes; migrate.py never truncates them. */
@Repository
public class StatusRepository {

    /** Keyed by url for the caller, stored by job_id; the join translates between the two. */
    private static final String ALL = """
            select v.url, s.status, s.note
            from job_status s
            join vacancy v on v.job_id = s.job_id
            """;

    /** Selecting job_id from vacancy makes an unknown posting write no row, not fail. */
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

    /** Stores the mark for one posting, or removes it once it is empty again. */
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

    /** The trailing part of the url, the way every part of this project names a posting. */
    static String jobId(String url) {
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
