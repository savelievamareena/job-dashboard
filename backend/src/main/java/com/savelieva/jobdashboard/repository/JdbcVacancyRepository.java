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
 * <p>Read only. Marks are written by {@link StatusRepository}, and everything else in this table
 * belongs to the loader.
 */
@Repository
public class JdbcVacancyRepository implements VacancyRepository {

    /**
     * selected_date is the day the posting was picked, which is the date the board has always
     * shown. found_date is the day it was first seen, which is what the trend charts count by and
     * is often a day earlier. The coalesce covers a posting whose selected.csv line is gone: the
     * flag outlives the line, so the first sighting is the only date left to show.
     *
     * <p>The stack column is not read. It was always blank on the board, because the newer
     * jobs.csv dropped the column it came from, and the language the loader stores instead is not
     * a replacement: for every picked posting it simply repeats the track, frontend to javascript
     * and fullstack to java. Filling the cell with that would add a second track column.
     */
    private static final String SELECTED = """
            select coalesce(selected_date, found_date) as date,
                   source, track, company, title, url,
                   easy_apply, level, job_type, location, applicants, gap, has_text
            from vacancy
            where is_selected
            order by coalesce(selected_date, found_date) desc, lower(company)
            """;

    /**
     * getString on a date gives the ISO text Postgres stores, which is the shape the board already
     * expects. getObject on easy_apply keeps a missing flag apart from a known "no": getBoolean
     * would turn both into false, and the board draws them differently.
     *
     * <p>Everything else that can be null becomes an empty string. company, title and gap are
     * nullable columns, while the reader this replaced could only ever produce text, so leaving
     * the null through would put a JSON null where the board has always had "".
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
            text(rs.getString("level")),
            text(rs.getString("job_type")),
            text(rs.getString("location")),
            text(rs.getString("applicants")),
            text(rs.getString("gap")),
            rs.getBoolean("has_text"),
            null,
            "",
            "");

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
}
