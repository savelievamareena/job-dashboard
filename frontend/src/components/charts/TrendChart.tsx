import { useState } from "react";
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ReferenceArea,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";

export type TrendRow = Record<string, number | string | null>;

export type TrendSeries = { name: string; color: string; label: string };

type Props = {
    rows: TrendRow[];
    series: TrendSeries[];
};

const AXIS = { fill: "var(--muted-ink)", fontSize: 11 };

const DIMMED = 0.16;

const shortDay = (day: string) => String(day).slice(5);

const isWeekend = (day: string) => {
    const [year, month, date] = day.split("-").map(Number);
    const weekday = new Date(year, month - 1, date).getDay();
    return weekday === 0 || weekday === 6;
};

export const TrendChart = ({ rows, series }: Props) => {
    const [hovered, setHovered] = useState<string | null>(null);
    const opacity = (name: string) => (hovered === null || hovered === name ? 1 : DIMMED);

    const weekendDays = rows
        .map((row) => row.day)
        .filter((day): day is string => typeof day === "string" && isWeekend(day));

    return (
    <div className="chart">
        <ResponsiveContainer width="100%" height={300}>
            <LineChart data={rows} margin={{ top: 18, right: 12, bottom: 0, left: -18 }}>
                {weekendDays.map((day) => (
                    <ReferenceArea
                        key={day}
                        x1={day}
                        x2={day}
                        fill="var(--weekend-fill)"
                        stroke="none"
                    />
                ))}
                <CartesianGrid vertical={false} stroke="var(--gridline)" strokeDasharray="0" />
                <XAxis
                    dataKey="day"
                    scale="band"
                    tickFormatter={shortDay}
                    tick={AXIS}
                    tickLine={false}
                    stroke="var(--baseline)"
                    // Keeps the first and last points off the edges, so their value labels do
                    // not land on the y-axis ticks or run out of the plot.
                    padding={{ left: 14, right: 14 }}
                />
                <YAxis
                    allowDecimals={false}
                    tick={AXIS}
                    tickLine={false}
                    axisLine={false}
                    width={44}
                />
                <Tooltip
                    contentStyle={{
                        background: "var(--surface-1)",
                        border: "1px solid var(--ring)",
                        borderRadius: 8,
                        fontSize: 12,
                    }}
                    labelStyle={{ color: "var(--text-primary)" }}
                    itemStyle={{ padding: 0 }}
                />
                <Legend
                    iconType="plainline"
                    wrapperStyle={{ fontSize: 12, color: "var(--text-secondary)", cursor: "default" }}
                    onMouseEnter={(entry: { dataKey?: unknown }) =>
                        setHovered(typeof entry.dataKey === "string" ? entry.dataKey : null)
                    }
                    onMouseLeave={() => setHovered(null)}
                />
                {series.map(({ name, color, label }) => (
                    <Line
                        key={name}
                        // Legend and tooltip show this; the hover still keys off dataKey.
                        name={label}
                        // Straight segments: a daily count does not ease between days, and a
                        // spline invents a curve through values that were never observed.
                        type="linear"
                        dataKey={name}
                        stroke={color}
                        strokeWidth={hovered === name ? 3 : 2}
                        strokeOpacity={opacity(name)}
                        // The ring is the surface colour, so crossing lines separate without a
                        // border being drawn around either of them.
                        dot={{
                            r: 3,
                            strokeWidth: 2,
                            stroke: "var(--surface-1)",
                            fillOpacity: opacity(name),
                            strokeOpacity: opacity(name),
                        }}
                        activeDot={{ r: 5, strokeWidth: 2, stroke: "var(--surface-1)" }}
                        // Only the highlighted series is numbered: a value on every point of
                        // seven lines is unreadable, but on one line it answers the question
                        // the hover asked. The label wears the text token and carries a surface
                        // halo, so it stays legible where it crosses another line - and it is
                        // what lets a value be read without relying on the hue, which matters
                        // for the light-mode series that sit below the contrast floor.
                        label={
                            hovered === name
                                ? {
                                      position: "top",
                                      offset: 8,
                                      fontSize: 11,
                                      fill: "var(--text-primary)",
                                      stroke: "var(--surface-1)",
                                      strokeWidth: 3,
                                      paintOrder: "stroke",
                                  }
                                : false
                        }
                        // The days with no search run keep their slot on the axis, so the gap
                        // is still visible in the spacing, but the line spans it.
                        connectNulls
                        isAnimationActive={false}
                    />
                ))}
            </LineChart>
        </ResponsiveContainer>
    </div>
    );
};
