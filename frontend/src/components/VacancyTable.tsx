import { NoteInput } from "@/components/NoteInput";
import type { SortKey, Vacancy } from "@/types";

type Props = {
    vacancies: Vacancy[];
    statuses: string[];
    sortKey: SortKey;
    sortDir: 1 | -1;
    onSort: (key: SortKey) => void;
    onUpdate: (url: string, patch: Partial<Pick<Vacancy, "status" | "note">>) => void;
};

/** A posting with one of these is out of play: rejected, or gone from LinkedIn entirely. */
const INACTIVE = new Set(["not a fit", "closed"]);

const COLUMNS: { key: SortKey; label: string }[] = [
    { key: "date", label: "Date" },
    { key: "company", label: "Company" },
    { key: "title", label: "Job title" },
    { key: "track", label: "Stack" },
    { key: "easyApply", label: "Apply" },
    { key: "level", label: "Level" },
    { key: "status", label: "Status" },
];

const ApplyRoute = ({ easyApply }: { easyApply: boolean | null }) => {
    if (easyApply === null) {
        return <span className="tag unknown">?</span>;
    }

    return easyApply ? (
        <span className="tag easy">Easy Apply</span>
    ) : (
        <span className="tag external">external site</span>
    );
};

export const VacancyTable = ({
    vacancies,
    statuses,
    sortKey,
    sortDir,
    onSort,
    onUpdate,
}: Props) => (
    <table>
        <thead>
            <tr>
                {COLUMNS.map(({ key, label }) => (
                    <th
                        key={key}
                        onClick={() => onSort(key)}
                        aria-sort={
                            sortKey === key
                                ? sortDir === 1
                                    ? "ascending"
                                    : "descending"
                                : "none"
                        }
                    >
                        {label}
                        {sortKey === key && <span className="arrow">{sortDir === 1 ? "^" : "v"}</span>}
                    </th>
                ))}
                <th>Note</th>
            </tr>
        </thead>
        <tbody>
            {vacancies.map((vacancy) => (
                <tr key={vacancy.url} className={INACTIVE.has(vacancy.status) ? "done" : ""}>
                    <td>{vacancy.date}</td>
                    <td className="company" title={vacancy.company}>
                        {vacancy.company}
                    </td>
                    <td>
                        <a href={vacancy.url} target="_blank" rel="noopener noreferrer">
                            {vacancy.title || "open the posting"}
                        </a>
                        {vacancy.gap && <div className="gap">gap: {vacancy.gap}</div>}
                        {vacancy.location && <div className="location">{vacancy.location}</div>}
                    </td>
                    <td>
                        {vacancy.track}
                        {vacancy.stack && ` / ${vacancy.stack}`}
                    </td>
                    <td>
                        <ApplyRoute easyApply={vacancy.easyApply} />
                    </td>
                    <td>{vacancy.level}</td>
                    <td>
                        <select
                            aria-label={`Status for ${vacancy.company}`}
                            value={vacancy.status}
                            onChange={(event) =>
                                onUpdate(vacancy.url, { status: event.target.value })
                            }
                        >
                            {statuses.map((status) => (
                                <option key={status} value={status}>
                                    {status}
                                </option>
                            ))}
                        </select>
                    </td>
                    <td>
                        <NoteInput
                            value={vacancy.note}
                            onCommit={(note) => onUpdate(vacancy.url, { note })}
                        />
                    </td>
                </tr>
            ))}
        </tbody>
    </table>
);
