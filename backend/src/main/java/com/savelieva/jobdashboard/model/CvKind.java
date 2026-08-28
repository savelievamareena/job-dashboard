package com.savelieva.jobdashboard.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Tailored for the company, or the core sent as it is; nothing built yet is a missing value. */
public enum CvKind {
    TAILORED,
    BASE;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
