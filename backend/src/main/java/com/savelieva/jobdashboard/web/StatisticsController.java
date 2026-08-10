package com.savelieva.jobdashboard.web;

import com.savelieva.jobdashboard.model.Statistics;
import com.savelieva.jobdashboard.repository.StatisticsRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatisticsController {

    private static final Logger log = LoggerFactory.getLogger(StatisticsController.class);

    private final StatisticsRepository statistics;

    public StatisticsController(StatisticsRepository statistics) {
        this.statistics = statistics;
    }

    @GetMapping("/statistics")
    public Statistics statistics() {
        return statistics.find();
    }

    /**
     * The database is optional to this application: the board reads files and does not touch it.
     * A stopped container therefore has to answer "this page is unavailable" rather than take the
     * request down as a server fault, and the message has to say which of the two it is, because
     * "no data" and "no database" look identical on an empty chart.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> unavailable(DataAccessException e) {
        log.warn("cannot read the statistics from the database", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.TEXT_PLAIN)
                .body("база недоступна: проверьте, что контейнер запущен (docker compose up -d)");
    }
}
