import { useState } from "react";
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";

/**
 * null is a day the search did not run. It carries no dot - there is nothing to mark - but the
 * line is drawn across it, so the series stays readable instead of falling apart into isolated
 * points around every skipped day. The value at the gap is never asserted: no dot, no tooltip.
 */
export type TrendRow = Record<string, number | string | null>;

/**
 * The colour travels with the series, so dropping one never repaints the rest.
 *
 * name is the key in the data and stays a plain identifier; label is what the reader sees. The
 * two differ where a language is spelled one way in a column and another way by people.
 */
export type TrendSeries = { name: string; color: string; label: string };

type Props = {
    rows: TrendRow[];
    series: TrendSeries[];
};

const AXIS = { fill: "var(--muted-ink)", fontSize: 11 };

/** Enough to push a line into the background without making it vanish from the shape. */
const DIMMED = 0.16;

/** 2026-08-09 reads as 08-09: the year is the same on every tick and only costs width. */
const shortDay = (day: string) => String(day).slice(5);

export const TrendChart = ({ rows, series }: Props) => {
    // Pointing at a legend entry emphasises that one series and pushes the rest back. Seven
    // lines crossing each other are hard to follow individually, and emphasis answers "which one
    // is this" without hiding the others, so the highlighted line keeps its context.
    const [hovered, setHovered] = useState<string | null>(null);
    const opacity = (name: string) => (hovered === null || hovered === name ? 1 : DIMMED);

    return (
    <div className="chart">
        <ResponsiveContainer width="100%" height={230}>
            {/* Room at the top for the value labels the highlighted series puts above its points. */}
            <LineChart data={rows} margin={{ top: 18, right: 12, bottom: 0, left: -18 }}>
                {/* Solid hairlines: a dashed grid reads as a threshold when it is only a grid. */}
                <CartesianGrid vertical={false} stroke="var(--gridline)" strokeDasharray="0" />
                <XAxis
                    dataKey="day"
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
