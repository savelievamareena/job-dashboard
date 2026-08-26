import { useMemo, useState } from "react";
import { FilterBar } from "@/components/FilterBar";
import { VacancyTable } from "@/components/VacancyTable";
import { useStoredFilters } from "@/hooks/useStoredFilters";
import { useVacancies } from "@/hooks/useVacancies";
import { NO_STATUS, type Filters, type SortKey, type Vacancy } from "@/types";

const matches = (vacancy: Vacancy, filters: Filters) => {
    const haystack = `${vacancy.company} ${vacancy.title}`.toLowerCase();
    const status = filters.status === NO_STATUS ? "" : filters.status;

    return (
        (!filters.date || vacancy.date === filters.date) &&
        (!filters.track || vacancy.track === filters.track) &&
        (!filters.status || vacancy.status === status) &&
        (!filters.easyApply || vacancy.easyApply === (filters.easyApply === "yes")) &&
        (!filters.query || haystack.includes(filters.query.toLowerCase()))
    );
};

const compare = (a: Vacancy, b: Vacancy, key: SortKey) => {
    const left = a[key] ?? "";
    const right = b[key] ?? "";

    return left > right ? 1 : left < right ? -1 : 0;
};

export const App = () => {
    const { vacancies, statuses, loading, error, update } = useVacancies();
    const [filters, setFilters] = useStoredFilters();
    const [sortKey, setSortKey] = useState<SortKey>("date");
    const [sortDir, setSortDir] = useState<1 | -1>(-1);

    const shown = useMemo(
        () =>
            vacancies
                .filter((vacancy) => matches(vacancy, filters))
                .sort((a, b) => compare(a, b, sortKey) * sortDir),
        [vacancies, filters, sortKey, sortDir],
    );

    const sortBy = (key: SortKey) => {
        setSortDir(key === sortKey ? ((sortDir * -1) as 1 | -1) : 1);
        setSortKey(key);
    };

    return (
        <main>
            <h1>Selected vacancies</h1>

            {error && <p className="error">{error}</p>}

            {loading ? (
                <p className="empty">loading...</p>
            ) : (
                <>
                    <FilterBar
                        filters={filters}
                        onChange={setFilters}
                        vacancies={vacancies}
                        statuses={statuses}
                        shown={shown.length}
                    />
                    {shown.length === 0 ? (
                        <p className="empty">
                            {vacancies.length === 0
                                ? "nothing selected yet: run the search and the selection first"
                                : "no vacancy matches these filters"}
                        </p>
                    ) : (
                        <VacancyTable
                            vacancies={shown}
                            statuses={statuses}
                            sortKey={sortKey}
                            sortDir={sortDir}
                            onSort={sortBy}
                            onUpdate={update}
                        />
                    )}
                </>
            )}
        </main>
    );
};
