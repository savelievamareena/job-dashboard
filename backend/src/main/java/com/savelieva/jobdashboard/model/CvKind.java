package com.savelieva.jobdashboard.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Which CV goes to a company: the one tailored for it, or the core sent as it is.
 *
 * <p>There is no third constant for "nothing built yet". That case is a missing value, the same way
 * a missing Easy Apply flag is: "not decided" and "send the core" are different answers, and only
 * one of them means the tailoring already ran.
 */
public enum CvKind {
    TAILORED,
    BASE;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
