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

/**
 * Its own request rather than a field on the status one: these two write different tables. The
 * status lives in job_status, which belongs to this application; the apply link is a column of
 * vacancy, which the loader owns and only leaves alone because it has nothing to say about it.
 */
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
