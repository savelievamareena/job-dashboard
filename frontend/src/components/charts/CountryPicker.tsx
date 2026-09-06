import type { Country } from "@/types";

/**
 * The markets the charts can show, and the one place that knows how each is written in the
 * database and on the page. Poland is first because it is the market she actually applies in and
 * the one every posting collected before 2026-09-05 belongs to.
 */
export const COUNTRIES: { key: Country; label: string }[] = [
    { key: "poland", label: "польша" },
    { key: "germany", label: "германия" },
    { key: "uk", label: "британия" },
];

type Props = {
    country: Country;
    onChange: (country: Country) => void;
};

/**
 * Same buttons as PeriodPicker, for the same reason: three one-word options, and the chosen one
 * has to stay readable while the charts under it are being compared.
 *
 * One country at a time and no "all" option: a run scans one country, so consecutive days on a
 * combined chart would be different markets and a drop would read as the market falling when it
 * only means yesterday's scan was somewhere else.
 */
export const CountryPicker = ({ country, onChange }: Props) => (
    <div className="periods" role="group" aria-label="Страна">
        {COUNTRIES.map(({ key, label }) => (
            <button
                key={key}
                type="button"
                aria-pressed={key === country}
                onClick={() => onChange(key)}
            >
                {label}
            </button>
        ))}
    </div>
);
