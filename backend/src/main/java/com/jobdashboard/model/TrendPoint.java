package com.jobdashboard.model;

/**
 * One point of a trend view: postings of one series on one day, the day as text, not a date.
 */
public record TrendPoint(String day, String series, int count) {
}
