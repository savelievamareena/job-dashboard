import type { Statistics } from "@/types";

/** Reads the three trends and the scan days. No fallback data: a failure must reach the page. */
export const fetchStatistics = async (signal?: AbortSignal): Promise<Statistics> => {
    const response = await fetch("/api/statistics", { signal });

    if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}: ${await response.text()}`);
    }

    return response.json();
};
