package com.savelieva.jobdashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** What the dashboard itself knows about a posting: how far it got, and a free note. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobStatus(String status, String note) {

    public static final JobStatus EMPTY = new JobStatus("", "");

    public JobStatus {
        status = status == null ? "" : status;
        note = note == null ? "" : note;
    }

    /** Dropped from the status file when untouched; not a field, so Jackson must not write it. */
    @JsonIgnore
    public boolean isEmpty() {
        return status.isBlank() && note.isBlank();
    }
}
