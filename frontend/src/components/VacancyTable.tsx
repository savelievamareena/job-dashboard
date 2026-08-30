import { DebouncedInput } from "@/components/DebouncedInput";
import type { SortKey, Vacancy } from "@/types";

type Props = {
    vacancies: Vacancy[];
    statuses: string[];
    sortKey: SortKey;
    sortDir: 1 | -1;
    onSort: (key: SortKey) => void;
    onUpdate: (
        url: string,
        patch: Partial<Pick<Vacancy, "status" | "note" | "applyUrl" | "maySubmit">>,
    ) => void;
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
    { key: "source", label: "Source" },
    { key: "cv", label: "CV" },
    { key: "maySubmit", label: "May submit" },
    { key: "status", label: "Status" },
    { key: "applyUrl", label: "Apply link" },
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

/** A malformed address is shown as it stands: worth seeing, not worth failing the row over. */
const host = (url: string) => {
    try {
        return new URL(url).hostname.replace(/^www\./, "");
    } catch {
        return url;
    }
};

/** Where Apply leads outside LinkedIn, pasted by hand; the host shows above the box as a link. */
const ApplyLinkCell = ({
    applyUrl,
    company,
    onCommit,
}: {
    applyUrl: string;
    company: string;
    onCommit: (applyUrl: string) => void;
}) => (
    <div className="apply-cell">
        {applyUrl && (
            <a
                className="apply-link"
                href={applyUrl}
                target="_blank"
                rel="noopener noreferrer"
                title={applyUrl}
            >
                {host(applyUrl)}
            </a>
        )}
        <DebouncedInput
            className="apply-input"
            ariaLabel={`Apply link for ${company}`}
            placeholder="paste apply link"
            value={applyUrl}
            onCommit={onCommit}
        />
    </div>
);

/** Which CV goes out: tailored, the core sent unchanged, or nothing built yet. */
const CvChoice = ({ cv }: { cv: Vacancy["cv"] }) => {
    if (cv === null) {
        return <span className="tag unknown">not built</span>;
    }

    return cv === "tailored" ? (
        <span className="tag tailored">tailored</span>
    ) : (
        <span className="tag external">core CV</span>
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
                /* The url alone is not unique: one posting can appear under two dates or tracks. */
                <tr
                    key={`${vacancy.date}|${vacancy.source}|${vacancy.track}|${vacancy.url}`}
                    className={INACTIVE.has(vacancy.status) ? "done" : ""}
                >
                    <td className="date">{vacancy.date}</td>
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
                    <td className="source">{vacancy.source}</td>
                    <td>{vacancy.level}</td>
                    <td>
                        <CvChoice cv={vacancy.cv} />
                    </td>
                    <td>
                        <input
                            type="checkbox"
                            aria-label={`May submit for ${vacancy.company}`}
                            checked={vacancy.maySubmit}
                            onChange={(event) =>
                                onUpdate(vacancy.url, { maySubmit: event.target.checked })
                            }
                        />
                    </td>
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
                        <ApplyLinkCell
                            applyUrl={vacancy.applyUrl}
                            company={vacancy.company}
                            onCommit={(applyUrl) => onUpdate(vacancy.url, { applyUrl })}
                        />
                    </td>
                    <td>
                        <DebouncedInput
                            className="note"
                            ariaLabel={`Note for ${vacancy.company}`}
                            placeholder="..."
                            value={vacancy.note}
                            onCommit={(note) => onUpdate(vacancy.url, { note })}
                        />
                    </td>
                </tr>
            ))}
        </tbody>
    </table>
);
