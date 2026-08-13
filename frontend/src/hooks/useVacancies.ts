import { useCallback, useEffect, useRef, useState } from "react";
import { fetchVacancies, saveApplyUrl, saveStatus } from "@/api/vacancies";
import type { Vacancy } from "@/types";

type State = {
    vacancies: Vacancy[];
    statuses: string[];
    loading: boolean;
    error: string | null;
};

type Patch = Partial<Pick<Vacancy, "status" | "note" | "applyUrl">>;

const INITIAL: State = { vacancies: [], statuses: [], loading: true, error: null };

/**
 * Loads the board once and keeps the edits local: the backend rewrites the status file on every
 * change, so refetching the whole list after each keystroke would only cost a round trip.
 */
export const useVacancies = () => {
    const [state, setState] = useState<State>(INITIAL);
    // Mirrors the rows so an edit can be built and sent outside the state updater. Updating state
    // from inside the updater would fire the request twice under StrictMode.
    const rows = useRef<Vacancy[]>([]);

    useEffect(() => {
        const controller = new AbortController();

        fetchVacancies(controller.signal)
            .then(({ vacancies, statuses }) => {
                rows.current = vacancies;
                setState({ vacancies, statuses, loading: false, error: null });
            })
            .catch((error: unknown) => {
                if (controller.signal.aborted) {
                    return;
                }
                setState({
                    ...INITIAL,
                    loading: false,
                    error: error instanceof Error ? error.message : "cannot load the vacancies",
                });
            });

        return () => controller.abort();
    }, []);

    const update = useCallback((url: string, patch: Patch) => {
        const current = rows.current.find((vacancy) => vacancy.url === url);

        if (!current) {
            return;
        }

        // Patch each row on its own rather than building one row and assigning it to all of
        // them. The same posting can sit on the board more than once - found on two days, or
        // under two tracks - and those rows differ in date, track and stack. Sharing one built
        // row overwrites those fields with the first match's, so the others start claiming a day
        // they were never found on, and a date filter then shows them as identical twins.
        rows.current = rows.current.map((vacancy) =>
            vacancy.url === url ? { ...vacancy, ...patch } : vacancy,
        );
        setState((state) => ({ ...state, vacancies: rows.current }));

        // Status and note belong to the posting, not to the sighting, so either row answers.
        const next = { ...current, ...patch };

        const failed = (fallback: string) => (error: unknown) => {
            setState((state) => ({
                ...state,
                error: error instanceof Error ? error.message : fallback,
            }));
            // The row was already patched optimistically, so a rejected save would leave the table
            // showing a value the database does not hold. Put back what the row said before.
            rows.current = rows.current.map((vacancy) =>
                vacancy.url === url ? { ...vacancy, ...current } : vacancy,
            );
            setState((state) => ({ ...state, vacancies: rows.current }));
        };

        // Two writes, two tables: the apply link is a column of `vacancy`, the mark lives in
        // job_status. Sending one when only the other changed would rewrite a value nobody edited.
        if (patch.applyUrl !== undefined) {
            saveApplyUrl(url, next.applyUrl).catch(failed("cannot save the apply link"));
            return;
        }

        saveStatus(url, next.status, next.note).catch(failed("cannot save the status"));
    }, []);

    return { ...state, update };
};
