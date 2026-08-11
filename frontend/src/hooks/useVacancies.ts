import { useCallback, useEffect, useRef, useState } from "react";
import { fetchVacancies, saveStatus } from "@/api/vacancies";
import type { Vacancy } from "@/types";

type State = {
    vacancies: Vacancy[];
    statuses: string[];
    loading: boolean;
    error: string | null;
};

type Patch = Partial<Pick<Vacancy, "status" | "note">>;

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

        saveStatus(url, next.status, next.note).catch((error: unknown) =>
            setState((state) => ({
                ...state,
                error: error instanceof Error ? error.message : "cannot save the status",
            })),
        );
    }, []);

    return { ...state, update };
};
