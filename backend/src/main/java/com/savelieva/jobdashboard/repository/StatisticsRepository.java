package com.savelieva.jobdashboard.repository;

import com.savelieva.jobdashboard.model.Statistics;
import com.savelieva.jobdashboard.model.TrendPoint;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Reads the three trend views and the scan days; read only, as fresh as the last loader run. */
@Repository
public class StatisticsRepository {

    private static final String LANGUAGE =
            "select day, series, count from trend_language order by day, series";
    private static final String LAYER =
            "select day, series, count from trend_layer order by day, series";
    private static final String AI =
            "select day, series, count from trend_ai order by day, series";
    private static final String SCAN_DAYS = "select day from scan_day order by day";

    /** getString on a date column gives the ISO text Postgres stores, which is what JSON wants. */
    private static final RowMapper<TrendPoint> POINT = (rs, row) ->
            new TrendPoint(rs.getString("day"), rs.getString("series"), rs.getInt("count"));

    private final JdbcTemplate jdbc;

    public StatisticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Statistics find() {
        return new Statistics(
                jdbc.query(LANGUAGE, POINT),
                jdbc.query(LAYER, POINT),
                jdbc.query(AI, POINT),
                jdbc.queryForList(SCAN_DAYS, String.class));
    }
}
