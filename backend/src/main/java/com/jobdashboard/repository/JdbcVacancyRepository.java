package com.jobdashboard.repository;

import com.jobdashboard.model.Vacancy;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Reads the picked postings, one row each; only apply_url and may_submit are written here. */
@Repository
public class JdbcVacancyRepository implements VacancyRepository {

    /** Dated by posted_at, then the pick day, then found_date; stack only repeated the track.
     *  A selected posting with no title renders as a bare "open the posting" link - nothing to
     *  screen and nothing to apply to - so it stays off the list (her call, 2026-09-01, after the
     *  portal-scan ghosts that reached selection through the old unsorted leak). */
    private static final String SELECTED = """
            select coalesce(posted_at::date, selected_date, found_date) as date,
                   source, track, company, title, url,
                   easy_apply, apply_url, may_submit, level, job_type, location, applicants, gap,
                   has_text
            from vacancy
            where is_selected
              and coalesce(title, '') <> ''
            order by coalesce(posted_at::date, selected_date, found_date) desc, lower(company)
            """;

    /** getObject keeps a missing easy_apply apart from a known "no"; other nulls become "". */
    private static final RowMapper<Vacancy> VACANCY = (rs, row) -> new Vacancy(
            rs.getString("date"),
            text(rs.getString("source")),
            text(rs.getString("track")),
            text(rs.getString("company")),
            text(rs.getString("title")),
            text(rs.getString("url")),
            "",
            rs.getObject("easy_apply", Boolean.class),
            text(rs.getString("apply_url")),
            rs.getBoolean("may_submit"),
            text(rs.getString("level")),
            text(rs.getString("job_type")),
            text(rs.getString("location")),
            text(rs.getString("applicants")),
            text(rs.getString("gap")),
            rs.getBoolean("has_text"),
            null,
            "",
            "");

    private static final String SAVE_APPLY_URL = "update vacancy set apply_url = ? where job_id = ?";

    private static final String SAVE_MAY_SUBMIT =
            "update vacancy set may_submit = ? where job_id = ?";

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private final JdbcTemplate jdbc;

    public JdbcVacancyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Vacancy> findSelected() {
        return jdbc.query(SELECTED, VACANCY);
    }

    /** The one column here the board writes; the loader's coalesce leaves a pasted link alone. */
    @Override
    public void saveApplyUrl(String url, String applyUrl) {
        String value = applyUrl == null || applyUrl.isBlank() ? null : applyUrl.strip();
        if (jdbc.update(SAVE_APPLY_URL, value, StatusRepository.jobId(url)) == 0) {
            throw new StatusRepository.UnknownPostingException(url);
        }
    }

    @Override
    public void saveMaySubmit(String url, boolean maySubmit) {
        if (jdbc.update(SAVE_MAY_SUBMIT, maySubmit, StatusRepository.jobId(url)) == 0) {
            throw new StatusRepository.UnknownPostingException(url);
        }
    }
}
