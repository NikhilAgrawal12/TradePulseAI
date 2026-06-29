import { useEffect, useMemo, useRef, useState } from "react";
import "./SearchableDropdown.css";

type SearchableDropdownProps = {
  id: string;
  name: string;
  value: string;
  options: string[];
  disabled?: boolean;
  loading?: boolean;
  placeholder?: string;
  noOptionsText?: string;
  onChange: (nextValue: string) => void;
};

function normalize(value: string): string {
  return value.trim().toLowerCase();
}

export function SearchableDropdown({
  id,
  name,
  value,
  options,
  disabled = false,
  loading = false,
  placeholder,
  noOptionsText = "No matches found",
  onChange,
}: SearchableDropdownProps) {
  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);

  const filteredOptions = useMemo(() => {
    const query = normalize(value);
    if (!query) {
      return options;
    }
    return options.filter((option) => normalize(option).includes(query));
  }, [options, value]);

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
    };
  }, []);

  const showMenu = open && !disabled && (loading || filteredOptions.length > 0 || value.trim().length > 0);

  return (
    <div className={`searchable-dropdown${disabled ? " is-disabled" : ""}`} ref={wrapperRef}>
      <input
        id={id}
        name={name}
        type="text"
        value={value}
        disabled={disabled}
        autoComplete="off"
        className="searchable-dropdown-input"
        placeholder={placeholder}
        aria-autocomplete="list"
        aria-expanded={showMenu}
        aria-controls={`${id}-menu`}
        onFocus={() => {
          if (!disabled) {
            setOpen(true);
          }
        }}
        onChange={(event) => {
          setOpen(true);
          onChange(event.target.value);
        }}
      />
      <span className="searchable-dropdown-caret" aria-hidden="true">▾</span>

      {showMenu && (
        <div className="searchable-dropdown-menu" id={`${id}-menu`} role="listbox">
          {loading ? (
            <div className="searchable-dropdown-status">Loading...</div>
          ) : filteredOptions.length > 0 ? (
            filteredOptions.map((option) => (
              <button
                key={option}
                type="button"
                className={`searchable-dropdown-option${normalize(option) === normalize(value) ? " is-selected" : ""}`}
                onMouseDown={(event) => {
                  event.preventDefault();
                  onChange(option);
                  setOpen(false);
                }}
              >
                {option}
              </button>
            ))
          ) : (
            <div className="searchable-dropdown-status">{noOptionsText}</div>
          )}
        </div>
      )}
    </div>
  );
}

