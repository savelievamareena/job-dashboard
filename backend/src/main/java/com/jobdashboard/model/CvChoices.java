package com.jobdashboard.model;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** What the tailoring built, kept per core; a row is keyed by posting URL, or by company. */
public record CvChoices(Map<String, Queue> byCore) {

    public static final CvChoices EMPTY = new CvChoices(Map.of());

    /** One core's queue: the CV per posting URL, plus rows that name no URL keyed by company. */
    public record Queue(Map<String, CvKind> byUrl, Map<String, CvKind> byCompany) {

        public Queue {
            byUrl = byUrl == null ? Map.of() : Map.copyOf(byUrl);
            byCompany = byCompany == null ? Map.of() : Map.copyOf(byCompany);
        }

        CvKind find(String url, String company) {
            // The URL is the exact answer; the company is what a pre-URL row can still offer.
            CvKind forPosting = byUrl.get(url == null ? "" : url.trim());
            return forPosting != null ? forPosting : byCompany.get(key(company));
        }
    }

    public CvChoices {
        byCore = byCore == null ? Map.of() : Map.copyOf(byCore);
    }

    /**
     * The CV built for this posting, or null; a track names its core, unsorted takes any core.
     */
    public CvKind find(String track, String url, String company) {
        Queue ownCore = byCore.get(key(track));
        if (ownCore != null) {
            return ownCore.find(url, company);
        }
        return byCore.values().stream()
                .map(queue -> queue.find(url, company))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Folder names are written by hand in both files, so they agree on spelling, not on case. */
    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
