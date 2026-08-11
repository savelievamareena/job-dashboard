import { useEffect, useState } from "react";
import { fetchStatistics } from "@/api/statistics";
import type { Statistics } from "@/types";

type State = {
    statistics: Statistics | null;
    loading: boolean;
    error: string | null;
};

const INITIAL: State = { statistics: null, loading: true, error: null };

/** Loads the charts once. Nothing on this page writes, so there is nothing to refetch. */
export const useStatistics = (): State => {
    const [state, setState] = useState<State>(INITIAL);

    useEffect(() => {
        const controller = new AbortController();

        fetchStatistics(controller.signal)
            .then((statistics) => setState({ statistics, loading: false, error: null }))
            .catch((error: unknown) => {
                if (controller.signal.aborted) {
                    return;
                }
                setState({
                    ...INITIAL,
                    loading: false,
                    error: error instanceof Error ? error.message : "cannot load the statistics",
                });
            });

        return () => controller.abort();
    }, []);

    return state;
};
