import { useEffect, useRef, useState } from "react";

type Props = {
    value: string;
    onCommit: (note: string) => void;
};

const DEBOUNCE_MS = 400;

/**
 * Types locally and saves once she stops. Sending on every keystroke would rewrite the status file
 * a dozen times per note; waiting for blur loses the note if the tab is closed first.
 */
export const NoteInput = ({ value, onCommit }: Props) => {
    const [draft, setDraft] = useState(value);
    const commit = useRef(onCommit);
    commit.current = onCommit;

    useEffect(() => setDraft(value), [value]);

    useEffect(() => {
        if (draft === value) {
            return;
        }

        const timer = setTimeout(() => commit.current(draft), DEBOUNCE_MS);
        return () => clearTimeout(timer);
    }, [draft, value]);

    return (
        <input
            className="note"
            aria-label="Note"
            placeholder="..."
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onBlur={() => draft !== value && commit.current(draft)}
        />
    );
};
