package com.jobdashboard.model;

/**
 * A day a search actually ran, and the country it scanned. One run scans one country, so a Polish
 * day is still a gap in the German line rather than a zero.
 */
public record ScanDay(String day, String country) {
}
