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
