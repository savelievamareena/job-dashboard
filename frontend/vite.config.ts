import path from "path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8765";

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        // The Spring Boot app serves the data; the dev server only serves the UI.
        proxy: {
            "/api": { target: BACKEND, changeOrigin: true },
        },
    },
    resolve: {
        alias: {
            "@": path.resolve(__dirname, "./src"),
        },
    },
});
