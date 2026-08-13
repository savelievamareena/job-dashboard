import type { Period } from "@/types";

/**
 * The four windows offered, shortest first, and the one place that knows what each one means.
 *
 * `days` counts calendar days back from today, today included. A month is 30 days and half a year
 * 182 because the axis counts days and the left edge is a horizon, not a calendar boundary:
 * nothing downstream cares which date a month began on, and the day count needs no rule for what
 * "a month before the 31st" is.
 *
 * `caption` is how each window is written into the card captions, where it has to fit after a
 * number: "157 вакансий за последний месяц".
 */
export const PERIODS: { key: Period; label: string; days: number; caption: string }[] = [
    { key: "week", label: "неделя", days: 7, caption: "за последнюю неделю" },
    { key: "month", label: "месяц", days: 30, caption: "за последний месяц" },
    { key: "half-year", label: "полгода", days: 182, caption: "за последние полгода" },
    { key: "year", label: "год", days: 365, caption: "за последний год" },
];

const of = (period: Period) => PERIODS.find(({ key }) => key === period) ?? PERIODS[1];

export const caption = (period: Period) => of(period).caption;

/**
 * The first day the charts show, as the ISO text the trend rows are keyed by, so the window is
 * applied by plain string comparison.
 *
 * Today is built from the local parts and then walked back in UTC. Reading it in local time and
 * formatting with toISOString() would roll a late evening over to tomorrow and cut a day off the
 * far end of the window.
 */
export const since = (period: Period): string => {
    const now = new Date();
    const start = new Date(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()));
    start.setUTCDate(start.getUTCDate() - (of(period).days - 1));

    return start.toISOString().slice(0, 10);
};

type Props = {
    period: Period;
    onChange: (period: Period) => void;
};

/**
 * Four buttons rather than a select: there are four options, they are one word each, and the
 * chosen one has to stay readable while the charts below it are being compared - a select hides
 * the other three behind a click and shows the current one only in a box that reads as an input.
 */
export const PeriodPicker = ({ period, onChange }: Props) => (
    <div className="periods" role="group" aria-label="Период статистики">
        {PERIODS.map(({ key, label }) => (
            <button
                key={key}
                type="button"
                aria-pressed={key === period}
                onClick={() => onChange(key)}
            >
                {label}
            </button>
        ))}
    </div>
);
