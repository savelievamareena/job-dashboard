package com.savelieva.jobdashboard.model;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What the tailoring left for each posting, kept per core because the two cores are deliberately
 * different documents rather than variants of one.
 *
 * <p>A queue row is a posting, keyed by its URL. Rows written before the queue carried a URL are
 * keyed by company instead and answer for every posting of it, which is the best they can say: the
 * file does not record which of that company's ads they were built for.
 *
 * @param byCore core folder name, lower case, to the rows of that core
 */
public record CvChoices(Map<String, Queue> byCore) {

    public static final CvChoices EMPTY = new CvChoices(Map.of());

    /**
     * One core's queue.
     *
     * @param byUrl    posting URL to the CV built for that posting
     * @param byCompany company, lower case, to the CV of a row that names no URL
     */
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
     * The CV built for this posting, or null when the tailoring has not reached it yet.
     *
     * <p>A track names its core: {@code frontend} is answered from the Frontend queue only. A
     * frontend posting must not be told "tailored" because a fullstack CV exists for that company,
     * since the two carry different job titles and a different employer name for 2022-2024.
     *
     * <p>The tracks that name no core, {@code other-stacks} and {@code unsorted}, are answered from
     * whichever core knows the posting: there the core is genuinely undecided, so any built CV is
     * more useful than a blank.
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
