import { useMemo, useState } from "react";
import { ChartCard } from "@/components/charts/ChartCard";
import { caption, PeriodPicker, since } from "@/components/charts/PeriodPicker";
import { TrendChart, type TrendRow, type TrendSeries } from "@/components/charts/TrendChart";
import { useStatistics } from "@/hooks/useStatistics";
import type { Period, TrendPoint } from "@/types";

const LANGUAGES = ["javascript", "java", "php", "go", "python", "csharp", "other"];
const LAYERS = ["frontend", "backend", "fullstack", "devops", "back-ops"];
const AI_KINDS = ["mlops", "llm-app", "ml", "other"];

const LABELS: Record<string, string> = { csharp: "c#" };

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

const bucket = (series: string[], name: string) =>
    series.includes(name) ? name : series[series.length - 1];

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

const PLURAL = new Intl.PluralRules("ru-RU");
const FORMS: Record<string, string> = { one: "вакансия", few: "вакансии" };
const counted = (count: number) => `${count} ${FORMS[PLURAL.select(count)] ?? "вакансий"}`;

const cardCaption = (count: number, period: Period, qualifier = "") =>
    `${counted(count)}${qualifier} ${caption(period)}, по дню публикации.`;

const Chart = ({ chart }: { chart: ReturnType<typeof toChart> }) =>
    chart.series.length === 0 ? (
        <p className="empty">по этому срезу за выбранный период нет размеченных вакансий</p>
    ) : (
        <TrendChart rows={chart.rows} series={chart.series} />
    );

export const Statistics = () => {
    const { statistics, loading, error } = useStatistics();
    const [period, setPeriod] = useState<Period>("month");

    const view = useMemo(() => {
        if (!statistics) {
            return null;
        }

        const from = since(period);
        const within = (points: TrendPoint[]) => points.filter((point) => point.day >= from);
        const scanDays = statistics.scanDays.filter((day) => day >= from);
        const language = within(statistics.language);
        const layer = within(statistics.layer);
        const ai = within(statistics.ai);

        return {
            language: toChart(language, LANGUAGES, scanDays),
            layer: toChart(layer, LAYERS, scanDays),
            ai: toChart(ai, AI_KINDS, scanDays),
            counted: {
                language: total(language),
                layer: total(layer),
                ai: total(ai),
            },
        };
    }, [statistics, period]);

    if (loading) {
        return (
            <div className="stats">
                <PeriodPicker period={period} onChange={setPeriod} />
                <div className="loader" role="status" aria-label="Загрузка">
                    <span className="spinner" />
                </div>
            </div>
        );
    }

    if (error || !view || !statistics) {
        return <p className="error">{error ?? "cannot load the statistics"}</p>;
    }

    if (total(statistics.language) + total(statistics.layer) + total(statistics.ai) === 0) {
        return (
            <p className="banner">
                В базе нет данных для графиков. Прогоните загрузчик:{" "}
                <code>python3 db/migrate.py | docker compose exec -T db psql -U postgres -d
                jobdashboard -v ON_ERROR_STOP=1</code>
            </p>
        );
    }

    const shown = view.counted.language + view.counted.layer + view.counted.ai;

    return (
        <div className="stats">
            <PeriodPicker period={period} onChange={setPeriod} />

            {shown === 0 ? (
                <p className="banner">
                    За выбранный период вакансий нет. Возьмите период подлиннее или прогоните
                    загрузчик.
                </p>
            ) : (
                <div className="charts">
                    <ChartCard
                        title="Языки программирования"
                        caption={cardCaption(view.counted.language, period)}
                    >
                        <Chart chart={view.language} />
                    </ChartCard>

                    <ChartCard
                        title="Frontend, backend, fullstack"
                        caption={cardCaption(view.counted.layer, period, " с определённым слоем")}
                    >
                        <Chart chart={view.layer} />
                    </ChartCard>

                    <ChartCard
                        title="AI и ML"
                        caption={cardCaption(view.counted.ai, period, " с проставленным ai_kind")}
                    >
                        <Chart chart={view.ai} />
                    </ChartCard>
                </div>
            )}
        </div>
    );
};
