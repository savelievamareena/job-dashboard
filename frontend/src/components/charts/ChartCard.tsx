import type { ReactNode } from "react";

type Props = {
    title: string;
    /** What the chart counts, in one line. The reader should not have to infer the unit. */
    caption: string;
    children: ReactNode;
};

export const ChartCard = ({ title, caption, children }: Props) => (
    <section className="card viz">
        <h2>{title}</h2>
        <p className="caption">{caption}</p>
        {children}
    </section>
);
