import { useEffect, useState } from "react";
import { EMPTY_FILTERS, type Filters } from "@/types";

const KEY = "job-dashboard.filters";

/** Keeps only the keys the board knows: a stale or hand-edited entry must not filter by junk. */
const clean = (saved: unknown): Filters => {
    if (!saved || typeof saved !== "object") {
        return EMPTY_FILTERS;
    }

    const raw = saved as Record<string, unknown>;
    const text = (key: keyof Filters) => (typeof raw[key] === "string" ? (raw[key] as string) : "");

    return {
        date: text("date"),
        track: text("track"),
        source: text("source"),
        easyApply: raw.easyApply === "yes" || raw.easyApply === "no" ? raw.easyApply : "",
        status: text("status"),
        query: text("query"),
    };
};

const read = (): Filters => {
    try {
        const raw = localStorage.getItem(KEY);
        return raw ? clean(JSON.parse(raw)) : EMPTY_FILTERS;
    } catch {
        // Broken entry, or storage blocked in private mode: start clean instead of breaking the board.
        return EMPTY_FILTERS;
    }
};

/**
 * The filters survive a reload: the board is a working list reopened many times a day, and picking
 * the same date and stack again on every visit is the one thing that makes it feel disposable.
 */
export const useStoredFilters = () => {
    const [filters, setFilters] = useState<Filters>(read);

    useEffect(() => {
        try {
            localStorage.setItem(KEY, JSON.stringify(filters));
        } catch {
            // Storage is unavailable: the filters simply stay for this session only.
        }
    }, [filters]);

    return [filters, setFilters] as const;
};
