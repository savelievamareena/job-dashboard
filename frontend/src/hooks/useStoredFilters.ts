import { useEffect, useState } from "react";
import type { Filters } from "@/types";

const KEY = "job-dashboard.filters";

/** Keeps only the keys the board knows: a stale or hand-edited entry must not filter by junk. */
const clean = (saved: unknown): Filters | null => {
    if (!saved || typeof saved !== "object") {
        return null;
    }

    const raw = saved as Record<string, unknown>;
    const text = (key: keyof Filters) => (typeof raw[key] === "string" ? (raw[key] as string) : "");

    return {
        date: text("date"),
        track: text("track"),
        source: text("source"),
        country: text("country"),
        easyApply: raw.easyApply === "yes" || raw.easyApply === "no" ? raw.easyApply : "",
        status: text("status"),
        query: text("query"),
    };
};

const read = (): Filters | null => {
    try {
        const raw = localStorage.getItem(KEY);
        return raw ? clean(JSON.parse(raw)) : null;
    } catch {
        // Broken entry, or storage blocked in private mode: start clean instead of breaking the board.
        return null;
    }
};

/**
 * The filters survive a reload: the board is a working list reopened many times a day, and picking
 * the same date and stack again on every visit is the one thing that makes it feel disposable.
 */
export const useStoredFilters = (defaults: Filters) => {
    const [stored, setStored] = useState<Filters | null>(read);

    useEffect(() => {
        if (!stored) {
            return;
        }

        try {
            localStorage.setItem(KEY, JSON.stringify(stored));
        } catch {
            // Storage is unavailable: the filters simply stay for this session only.
        }
    }, [stored]);

    return [stored ?? defaults, setStored] as const;
};
