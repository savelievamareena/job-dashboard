import type { VacanciesResponse } from "@/types";

const failed = async (response: Response): Promise<never> => {
    throw new Error(`${response.status} ${response.statusText}: ${await response.text()}`);
};

export const fetchVacancies = async (signal?: AbortSignal): Promise<VacanciesResponse> => {
    const response = await fetch("/api/vacancies", { signal });
    return response.ok ? response.json() : failed(response);
};

export const saveStatus = async (url: string, status: string, note: string): Promise<void> => {
    const response = await fetch("/api/vacancies/status", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url, status, note }),
    });

    if (!response.ok) {
        await failed(response);
    }
};

/** Its own request: the status writes job_status, the apply link a column of vacancy. */
export const saveApplyUrl = async (url: string, applyUrl: string): Promise<void> => {
    const response = await fetch("/api/vacancies/apply-url", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url, applyUrl }),
    });

    if (!response.ok) {
        await failed(response);
    }
};

/** A column of `vacancy` like the apply link, so it is written on its own too. */
export const saveCvEmail = async (url: string, cvEmail: string): Promise<void> => {
    const response = await fetch("/api/vacancies/cv-email", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url, cvEmail }),
    });

    if (!response.ok) {
        await failed(response);
    }
};

export const saveMaySubmit = async (url: string, maySubmit: boolean): Promise<void> => {
    const response = await fetch("/api/vacancies/may-submit", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url, maySubmit }),
    });

    if (!response.ok) {
        await failed(response);
    }
};
