import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { App } from "@/App";
import { Statistics } from "@/pages/Statistics";
import "@/styles/app.css";
import "@/styles/charts.css";

const container = document.getElementById("root");

if (!container) {
    throw new Error("#root is missing from index.html");
}

/**
 * Hash routing rather than a router dependency: there are two pages, both client side, and the
 * hash keeps the Vite dev server and the Spring Boot static handler out of it - neither has to
 * learn a second path that must fall through to index.html.
 */
const useHash = () => {
    const [hash, setHash] = useState(() => window.location.hash || "#/");

    useEffect(() => {
        const read = () => setHash(window.location.hash || "#/");
        window.addEventListener("hashchange", read);

        return () => window.removeEventListener("hashchange", read);
    }, []);

    return hash;
};

const PAGES = [
    { hash: "#/", title: "Board" },
    { hash: "#/statistics", title: "Job Statistic" },
];

const Shell = () => {
    const hash = useHash();
    const onStatistics = hash.startsWith("#/statistics");

    return (
        <>
            <nav className="pages">
                {PAGES.map((page) => (
                    <a
                        key={page.hash}
                        href={page.hash}
                        aria-current={
                            (page.hash === "#/statistics") === onStatistics ? "page" : undefined
                        }
                    >
                        {page.title}
                    </a>
                ))}
            </nav>

            {onStatistics ? (
                <main>
                    <h1>Job Statistic</h1>
                    <Statistics />
                </main>
            ) : (
                <App />
            )}
        </>
    );
};

createRoot(container).render(
    <StrictMode>
        <Shell />
    </StrictMode>,
);
