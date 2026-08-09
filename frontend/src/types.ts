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
    | "status";

export type Filters = {
    date: string;
    track: string;
    easyApply: "" | "yes" | "no";
    status: string;
    query: string;
};

export const EMPTY_FILTERS: Filters = { date: "", track: "", easyApply: "", status: "", query: "" };
