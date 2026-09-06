package com.jobdashboard.model;

/**
 * One point of a trend view: postings of one series on one day in one country, the day as text,
 * not a date. The page shows one country at a time and picks which.
 */
public record TrendPoint(String day, String country, String series, int count) {
}
