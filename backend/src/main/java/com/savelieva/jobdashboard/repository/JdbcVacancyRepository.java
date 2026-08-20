package com.savelieva.jobdashboard.repository;

import com.savelieva.jobdashboard.model.Vacancy;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Reads the picked postings out of the database the loader fills, one row per posting.
 *
 * <p>This replaced a reader that walked the dated folders on every request. The board and the
 * statistics page now answer from the same table, so the two can no longer disagree about what was
 * found, and a posting is described by everything every sighting of it knew rather than by the one
 * day it happened to be picked on.
 *
 * <p>One posting is one row here, where the folders held one row per sighting. A posting picked on
 * two days was two rows on the old board; it is one now, dated by the later pick.
 *
 * <p>Read only but for two columns: apply_url, which the board lets her paste into because the paid
 * lookup that used to fill it stopped answering, and may_submit, which she raises by hand to tell
 * the apply agent it may submit without waiting for her review. Marks are written by
 * {@link StatusRepository}, and everything else in this table belongs to the loader.
 */
@Repository
public class JdbcVacancyRepository implements VacancyRepository {

    /**
     * The board shows a posting by when it went up, the same date the trend charts count by -
     * same first two steps of the coalesce, same reason: posted_at only arrived with the recent
     * columns, so an older posting has none of its own to show. Where the charts fall straight
     * back to found_date, the board takes one more step first: selected_date, the day the posting
     * was picked. posted_at only reaches back to 2026-08-09, so without that step a posting
     * picked before then showed the day the SCAN happened to run rather than any date about the
     * posting or the pick - worse than the pick date it replaced. See reset-schema.sql for why
     * selected_date exists again after being dropped earlier the same day.
     *
     * <p>The stack column is not read. It was always blank on the board, because the newer
     * jobs.csv dropped the column it came from, and the language the loader stores instead is not
     * a replacement: for every picked posting it simply repeats the track, frontend to javascript
     * and fullstack to java. Filling the cell with that would add a second track column.
     */
    private static final String SELECTED = """
            select coalesce(posted_at::date, selected_date, found_date) as date,
                   source, track, company, title, url,
                   easy_apply, apply_url, may_submit, level, job_type, location, applicants, gap,
                   has_text
            from vacancy
            where is_selected
            order by coalesce(posted_at::date, selected_date, found_date) desc, lower(company)
            """;

    /**
     * getString on a date gives the ISO text Postgres stores, which is the shape the board already
     * expects. getObject on easy_apply keeps a missing flag apart from a known "no": getBoolean
     * would turn both into false, and the board draws them differently.
     *
     * <p>Everything else that can be null becomes an empty string. company, title, gap and
     * apply_url are nullable columns, while the reader this replaced could only ever produce text,
     * so leaving the null through would put a JSON null where the board has always had "". Unlike
     * easy_apply, apply_url loses nothing that way: the column has one missing state, "nobody has
     * paid to ask yet", and no value for "there is no link".
     */
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

    /**
     * The one column of this table the board writes, and it survives the loader by luck of a
     * coalesce rather than by being off limits to it: migrate.py updates
     * {@code apply_url = coalesce(excluded.apply_url, v.apply_url)}, and `excluded` is null unless
     * a cached {@code _descriptions/<id>.json} carries an address. Nothing writes that file except
     * li_applyurl.py, so a pasted link stays until the paid lookup answers for the same posting -
     * at which point the provider's answer is the newer fact and should win.
     *
     * <p>Written straight to `vacancy` rather than to a table of its own because the board, the
     * loader and the paid lookup all mean the same thing by this column, and a second home for it
     * would need a rule about which one the page shows.
     */
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
