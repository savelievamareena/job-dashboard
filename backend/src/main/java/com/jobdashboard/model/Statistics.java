package com.jobdashboard.model;

import java.util.List;

/** Three trends plus the days a search ran, so a day with no run is not drawn as a zero. */
public record Statistics(
        List<TrendPoint> language,
        List<TrendPoint> layer,
        List<TrendPoint> ai,
        List<String> scanDays) {
}
