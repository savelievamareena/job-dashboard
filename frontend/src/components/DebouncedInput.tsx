import { useEffect, useRef, useState } from "react";

type Props = {
    value: string;
    onCommit: (value: string) => void;
    className?: string;
    placeholder?: string;
    ariaLabel: string;
};

const DEBOUNCE_MS = 400;

/** Types locally and saves once she stops, so a note is not written on every keystroke. */
export const DebouncedInput = ({ value, onCommit, className, placeholder, ariaLabel }: Props) => {
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
            className={className}
            aria-label={ariaLabel}
            placeholder={placeholder}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onBlur={() => draft !== value && commit.current(draft)}
        />
    );
};
