package com.savelieva.jobdashboard.model;

/**
 * One selected posting, assembled from the three files the parsing skills leave on disk:
 * selected.csv (company, title, gap), jobs.csv (stack) and the cached _descriptions record.
 *
 * <p>Every field except {@code url} may be missing: an older cached record uses different key
 * names, and some days were parsed before the cache existed at all. Missing reads as an empty
 * string, or as {@code null} for {@code easyApply} and {@code cv}, where "not known" and "no"
 * differ.
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
