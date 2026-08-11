import { useMemo } from "react";
import { ChartCard } from "@/components/charts/ChartCard";
import { TrendChart, type TrendRow, type TrendSeries } from "@/components/charts/TrendChart";
import { useStatistics } from "@/hooks/useStatistics";
import type { TrendPoint } from "@/types";

/**
 * Fixed series lists, in palette order. A series keeps its colour whatever the data does, and a
 * value outside the list folds into the last slot rather than taking a ninth hue nobody can tell
 * from the eight already on screen.
 */
const LANGUAGES = ["javascript", "java", "php", "go", "python", "csharp", "other"];
/**
 * Must name every value `trend_layer` can return, and the view is the authority on that list.
 * Not a style rule: `bucket()` below folds anything unnamed into the LAST entry and ADDS its
 * count there, so a layer missing from this array is not absent from the chart, it is drawn
 * under a neighbour's name. `devops` and `back-ops` arrived 2026-08-11 and spent an afternoon
 * being counted as fullstack, inflating that curve by 114 postings.
 */
const LAYERS = ["frontend", "backend", "fullstack", "devops", "back-ops"];
const AI_KINDS = ["mlops", "llm-app", "ml", "other"];

/**
 * How a series is written for the reader, where that differs from its key in the data. The key
 * stays a plain identifier so it survives SQL and JSON unquoted; only the label is prettified.
 */
const LABELS: Record<string, string> = { csharp: "c#" };

/**
 * The validated categorical order. Eight is the ceiling: the order itself is what clears the
 * colourblind separation gates, so a ninth series would be a new hue that fails them.
 */
const SLOTS = [
    "var(--series-1)",
    "var(--series-2)",
    "var(--series-3)",
    "var(--series-4)",
    "var(--series-5)",
    "var(--series-6)",
    "var(--series-7)",
    "var(--series-8)",
];

/** Every date from the first to the last the data touches, so a skipped day has a slot to be. */
const calendar = (points: TrendPoint[]) => {
    const days = points.map((point) => point.day).sort();

    if (days.length === 0) {
        return [];
    }

    const last = new Date(`${days[days.length - 1]}T00:00:00Z`);
    const range: string[] = [];

    for (
        let day = new Date(`${days[0]}T00:00:00Z`);
        day <= last;
        day.setUTCDate(day.getUTCDate() + 1)
    ) {
        range.push(day.toISOString().slice(0, 10));
    }

    return range;
};

/** Anything the fixed list does not name belongs to the tail bucket, which is its last entry. */
const bucket = (series: string[], name: string) =>
    series.includes(name) ? name : series[series.length - 1];

/**
 * One row per day, where the value carries which of two different silences this day is.
 *
 * A day the search ran and this series had nothing is a zero: it was looked at and there was
 * none. A day the search did not run is null, which breaks the line, because a zero there would
 * claim nothing was posted when the truth is that nobody looked. group by cannot tell them
 * apart - it just returns no row - so the difference is put back here, from scan_day.
 *
 * A series that never appears at all is dropped: a flat line along the axis says nothing and
 * costs a colour and a legend entry. Its colour comes from its place in the full list, so
 * dropping one never repaints the others.
 */
const toChart = (points: TrendPoint[], series: string[], scanDays: string[]) => {
    const counts = new Map<string, number>();
    const seen = new Set<string>();
    const scanned = new Set(scanDays);

    for (const point of points) {
        const name = bucket(series, point.series);
        const key = `${point.day}|${name}`;
        counts.set(key, (counts.get(key) ?? 0) + point.count);
        seen.add(name);
    }

    const present: TrendSeries[] = series
        .map((name, index) => ({
            name,
            color: SLOTS[index % SLOTS.length],
            label: LABELS[name] ?? name,
        }))
        .filter(({ name }) => seen.has(name));

    const days = calendar(points);
    const rows: TrendRow[] = days.map((day) => {
        const row: TrendRow = { day };

        for (const { name } of present) {
            row[name] = scanned.has(day) ? (counts.get(`${day}|${name}`) ?? 0) : null;
        }

        return row;
    });

    return { rows, series: present };
};

const total = (points: TrendPoint[]) => points.reduce((sum, point) => sum + point.count, 0);

/**
 * One trend has data while the others do not - ai_kind, say, which the parser writes on far
 * fewer postings than language. An empty plot with axes still reads as a measured zero, so that
 * card says it has nothing rather than drawing nothing.
 */
const Chart = ({ chart }: { chart: ReturnType<typeof toChart> }) =>
    chart.series.length === 0 ? (
        <p className="empty">по этому срезу в базе пока нет размеченных вакансий</p>
    ) : (
        <TrendChart rows={chart.rows} series={chart.series} />
    );

export const Statistics = () => {
    const { statistics, loading, error } = useStatistics();

    const view = useMemo(
        () =>
            statistics && {
                language: toChart(statistics.language, LANGUAGES, statistics.scanDays),
                layer: toChart(statistics.layer, LAYERS, statistics.scanDays),
                ai: toChart(statistics.ai, AI_KINDS, statistics.scanDays),
                counted: {
                    language: total(statistics.language),
                    layer: total(statistics.layer),
                    ai: total(statistics.ai),
                },
            },
        [statistics],
    );

    if (loading) {
        return <p className="empty">loading...</p>;
    }

    if (error || !view) {
        return <p className="error">{error ?? "cannot load the statistics"}</p>;
    }

    // A 200 with three empty arrays is a real state, not a hypothetical one: the schema can be
    // applied without the loader having run since. Three blank charts would read as "nothing was
    // posted", so it has to be said in words instead.
    if (view.counted.language + view.counted.layer + view.counted.ai === 0) {
        return (
            <p className="banner">
                В базе нет данных для графиков. Прогоните загрузчик:{" "}
                <code>python3 db/migrate.py | docker compose exec -T db psql -U postgres -d
                jobdashboard -v ON_ERROR_STOP=1</code>
            </p>
        );
    }

    return (
        <div className="charts">
            <ChartCard
                title="Языки программирования"
                caption={`${view.counted.language} вакансий, по дню публикации.`}
            >
                <Chart chart={view.language} />
            </ChartCard>

            <ChartCard
                title="Frontend, backend, fullstack"
                caption={`${view.counted.layer} вакансий с определённым слоем, по дню публикации.`}
            >
                <Chart chart={view.layer} />
            </ChartCard>

            <ChartCard
                title="AI и ML"
                caption={`${view.counted.ai} вакансий с проставленным ai_kind, по дню публикации.`}
            >
                <Chart chart={view.ai} />
            </ChartCard>
        </div>
    );
};
