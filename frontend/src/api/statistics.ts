import type { Statistics } from "@/types";

/**
 * Reads the three trends and the scan days from the database, through the API.
 *
 * There is no fallback to bundled numbers on failure, on purpose: a snapshot that renders when
 * the request fails looks exactly like live data, so a stopped container or a broken query would
 * quietly show whatever the numbers were on the day the snapshot was taken. A failure has to
 * reach the page as a failure.
 */
export const fetchStatistics = async (signal?: AbortSignal): Promise<Statistics> => {
    const response = await fetch("/api/statistics", { signal });

    if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}: ${await response.text()}`);
    }

    return response.json();
};
