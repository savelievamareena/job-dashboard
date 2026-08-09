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

        const next = { ...current, ...patch };
        rows.current = rows.current.map((vacancy) => (vacancy.url === url ? next : vacancy));
        setState((state) => ({ ...state, vacancies: rows.current }));

        saveStatus(url, next.status, next.note).catch((error: unknown) =>
            setState((state) => ({
                ...state,
                error: error instanceof Error ? error.message : "cannot save the status",
            })),
        );
    }, []);

    return { ...state, update };
};
