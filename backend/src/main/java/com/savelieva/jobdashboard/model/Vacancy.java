package com.savelieva.jobdashboard.model;

/**
 * One picked posting, as the board shows it: a row of the vacancy table joined with the mark set
 * on it and the CV built for it.
 *
 * <p>Every field except {@code url} may be missing. The loader fills them from whatever the
 * parsing knew, and it knew different things on different days: an older cached record uses other
 * key names, and some days were parsed before the cache existed at all. Missing reads as an empty
 * string, or as {@code null} for {@code easyApply} and {@code cv}, where "not known" and "no"
 * differ.
 *
 * <p>{@code date} is the day the posting was picked, not the day it was found. The two are a day
 * apart often enough to matter, and the charts count by the other one.
 *
 * <p>{@code stack} is always empty and kept only because the board still renders the field. The
 * column it came from disappeared from jobs.csv, and the language the loader stores instead just
 * repeats the track for every picked posting.
 */
public record Vacancy(
        String date,
        String source,
        String track,
        String company,
        String title,
        String url,
        String stack,
        Boolean easyApply,
        String level,
        String jobType,
        String location,
        String applicants,
        String gap,
        boolean hasText,
        CvKind cv,
        String status,
        String note) {

    /** Returns a copy carrying the status and note the dashboard has stored for this posting. */
    public Vacancy withStatus(JobStatus jobStatus) {
        return new Vacancy(date, source, track, company, title, url, stack, easyApply, level,
                jobType, location, applicants, gap, hasText, cv,
                jobStatus.status(), jobStatus.note());
    }

    /** Returns a copy carrying the CV built for this company, null while none has been. */
    public Vacancy withCv(CvKind kind) {
        return new Vacancy(date, source, track, company, title, url, stack, easyApply, level,
                jobType, location, applicants, gap, hasText, kind, status, note);
    }
}
