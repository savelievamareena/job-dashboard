package com.savelieva.jobdashboard.model;

/**
 * One point of a trend view: how many postings of one series landed on one day.
 *
 * <p>The day is carried as text rather than as a {@code LocalDate} because nothing here does date
 * arithmetic: it is read out of the view, put in JSON and used as an axis label. Text keeps the
 * wire format independent of how Jackson is configured to write dates.
 *
 * @param day    the day a posting was published, or the day it was found where it carries no
 *               publication date of its own
 * @param series the language, layer or AI category, already normalised by the loader
 * @param count  postings, counted once each
 */
public record TrendPoint(String day, String series, int count) {
}
