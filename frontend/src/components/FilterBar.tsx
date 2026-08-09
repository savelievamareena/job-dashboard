import { EMPTY_FILTERS, type Filters, type Vacancy } from "@/types";

type Props = {
    filters: Filters;
    onChange: (filters: Filters) => void;
    vacancies: Vacancy[];
    statuses: string[];
    shown: number;
};

/**
 * Always keeps the picked value in the list, even when today's board no longer holds it: a filter
 * restored from the last visit would otherwise hide behind a select that reads "all dates".
 */
const options = (values: string[], selected: string) =>
    [...new Set([...values, selected].filter(Boolean))];

const column = (vacancies: Vacancy[], key: "date" | "track", selected: string) =>
    options(
        vacancies.map((vacancy) => vacancy[key]),
        selected,
    ).sort();

export const FilterBar = ({ filters, onChange, vacancies, statuses, shown }: Props) => {
    const set = <K extends keyof Filters>(key: K, value: Filters[K]) =>
        onChange({ ...filters, [key]: value });

    const filtered = Object.values(filters).some(Boolean);

    return (
        <div className="bar">
            <select
                aria-label="Date"
                value={filters.date}
                onChange={(event) => set("date", event.target.value)}
            >
                <option value="">all dates</option>
                {column(vacancies, "date", filters.date).map((date) => (
                    <option key={date} value={date}>
                        {date}
                    </option>
                ))}
            </select>

            <select
                aria-label="Stack"
                value={filters.track}
                onChange={(event) => set("track", event.target.value)}
            >
                <option value="">all stacks</option>
                {column(vacancies, "track", filters.track).map((track) => (
                    <option key={track} value={track}>
                        {track}
                    </option>
                ))}
            </select>

            <select
                aria-label="Apply route"
                value={filters.easyApply}
                onChange={(event) => set("easyApply", event.target.value as Filters["easyApply"])}
            >
                <option value="">any apply route</option>
                <option value="yes">Easy Apply</option>
                <option value="no">external site</option>
            </select>

            <select
                aria-label="Status"
                value={filters.status}
                onChange={(event) => set("status", event.target.value)}
            >
                <option value="">any status</option>
                {options(statuses, filters.status).map((status) => (
                    <option key={status} value={status}>
                        {status}
                    </option>
                ))}
            </select>

            <input
                type="search"
                aria-label="Search"
                placeholder="search by company and job title"
                value={filters.query}
                onChange={(event) => set("query", event.target.value)}
            />

            <button
                type="button"
                className="clear"
                onClick={() => onChange(EMPTY_FILTERS)}
                disabled={!filtered}
            >
                clear filters
            </button>

            <span className="count">
                {shown} of {vacancies.length}
            </span>
        </div>
    );
};
