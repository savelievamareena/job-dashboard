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

    /**
     * An untouched entry is dropped from the status file rather than stored as two empty strings.
     * Ignored by Jackson: this is a question about the record, not a field of it, and serialising
     * it would put a stray "empty" key into the status file and the API response.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return status.isBlank() && note.isBlank();
    }
}
