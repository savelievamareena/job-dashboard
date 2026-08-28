package com.savelieva.jobdashboard.web;

import com.savelieva.jobdashboard.model.Statistics;
import com.savelieva.jobdashboard.repository.StatisticsRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatisticsController {

    private final StatisticsRepository statistics;

    public StatisticsController(StatisticsRepository statistics) {
        this.statistics = statistics;
    }

    /** A stopped container is answered by {@link ApiExceptionHandler}, shared with the board. */
    @GetMapping("/statistics")
    public Statistics statistics() {
        return statistics.find();
    }
}
