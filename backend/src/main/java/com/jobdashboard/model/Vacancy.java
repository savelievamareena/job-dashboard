package com.jobdashboard.model;

/** One picked posting as the board shows it; every field but {@code url} may be missing. */
public record Vacancy(
        String date,
        String source,
        String track,
        String company,
        String title,
        String url,
        String stack,
        Boolean easyApply,
        String applyUrl,
        boolean maySubmit,
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
        return new Vacancy(date, source, track, company, title, url, stack, easyApply, applyUrl,
                maySubmit, level, jobType, location, applicants, gap, hasText, cv,
                jobStatus.status(), jobStatus.note());
    }

    /** Returns a copy carrying the CV built for this company, null while none has been. */
    public Vacancy withCv(CvKind kind) {
        return new Vacancy(date, source, track, company, title, url, stack, easyApply, applyUrl,
                maySubmit, level, jobType, location, applicants, gap, hasText, kind, status, note);
    }
}
