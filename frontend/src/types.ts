/** One selected posting, exactly as the Spring Boot API returns it. */
export type Vacancy = {
    date: string;
    source: string;
    track: string;
    company: string;
    title: string;
    url: string;
    stack: string;
    /** null when the cached record predates the flag: unknown, which is not the same as "no". */
    easyApply: boolean | null;
    /**
     * Where the Apply button leads outside LinkedIn. Empty means nobody has filled it in yet, not
     * "there is no link". It used to come only from a paid lookup; that provider stopped answering
     * on 2026-08-13, so the board is now where it gets typed in by hand.
     */
    applyUrl: string;
    maySubmit: boolean;
    level: string;
    jobType: string;
    location: string;
    applicants: string;
    gap: string;
    hasText: boolean;
    /** Which CV to send. null while the tailoring has not reached this company. */
    cv: "tailored" | "base" | null;
    status: string;
    note: string;
};

export type VacanciesResponse = {
    vacancies: Vacancy[];
    statuses: string[];
};

/** Columns the table can be sorted by. */
export type SortKey =
    | "date"
    | "company"
    | "title"
    | "track"
    | "easyApply"
    | "level"
    | "cv"
    | "status"
    | "applyUrl"
    | "maySubmit";

export type Filters = {
    date: string;
    track: string;
    easyApply: "" | "yes" | "no";
    status: string;
    query: string;
};

export const EMPTY_FILTERS: Filters = { date: "", track: "", easyApply: "", status: "", query: "" };

export const NO_STATUS = "none";

/**
 * One point of a trend view. All three views share this shape, so one chart reads any of them.
 *
 * @see db/reset-schema.sql - trend_language, trend_layer, trend_ai
 */
export type TrendPoint = {
    /** The day a posting was first seen; a posting counts once, on that day. */
    day: string;
    series: string;
    count: number;
};

/**
 * How far back the statistics page looks. The window ends today, not on the last day the loader
 * ran, so "неделя" is the last seven calendar days and a week without a search run is an empty
 * chart rather than the last seven days that happen to carry data.
 */
export type Period = "week" | "month" | "half-year" | "year";

export type Statistics = {
    language: TrendPoint[];
    layer: TrendPoint[];
    ai: TrendPoint[];
    /**
     * Days a search actually ran. A day missing here is a day nobody looked, which is drawn as a
     * break in the line: a zero there would read as "nothing was posted".
     *
     * @see db/reset-schema.sql - scan_day
     */
    scanDays: string[];
};
