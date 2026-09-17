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
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const [open, setOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);

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
        setHighlightedIndex(-1);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
    };
  }, []);

  const showMenu = open && !disabled && (loading || filteredOptions.length > 0 || value.trim().length > 0);

  useEffect(() => {
    if (!showMenu || loading || filteredOptions.length === 0) {
      setHighlightedIndex(-1);
      return;
    }

    const selectedIndex = filteredOptions.findIndex((option) => normalize(option) === normalize(value));
    setHighlightedIndex((current) => {
      if (current >= 0 && current < filteredOptions.length) {
        return current;
      }
      return selectedIndex >= 0 ? selectedIndex : 0;
    });
  }, [filteredOptions, loading, showMenu, value]);

  useEffect(() => {
    if (!showMenu || highlightedIndex < 0) {
      return;
    }

    optionRefs.current[highlightedIndex]?.scrollIntoView({ block: "nearest" });
  }, [highlightedIndex, showMenu]);

  const handleSelect = (option: string) => {
    onChange(option);
    setOpen(false);
    setHighlightedIndex(-1);
  };

  const handleKeyDown: React.KeyboardEventHandler<HTMLInputElement> = (event) => {
    if (disabled) {
      return;
    }

    if (event.key === "ArrowDown") {
      event.preventDefault();
      setOpen(true);
      if (loading || filteredOptions.length === 0) {
        return;
      }
      setHighlightedIndex((current) => (current < 0 ? 0 : Math.min(current + 1, filteredOptions.length - 1)));
      return;
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      setOpen(true);
      if (loading || filteredOptions.length === 0) {
        return;
      }
      setHighlightedIndex((current) => (current < 0 ? filteredOptions.length - 1 : Math.max(current - 1, 0)));
      return;
    }

    if (event.key === "Enter" && showMenu && highlightedIndex >= 0 && highlightedIndex < filteredOptions.length) {
      event.preventDefault();
      handleSelect(filteredOptions[highlightedIndex]);
      return;
    }

    if (event.key === "Escape") {
      setOpen(false);
      setHighlightedIndex(-1);
    }
  };

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
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={showMenu}
        aria-controls={`${id}-menu`}
        aria-activedescendant={showMenu && highlightedIndex >= 0 ? `${id}-option-${highlightedIndex}` : undefined}
        onFocus={() => {
          if (!disabled) {
            setOpen(true);
          }
        }}
        onKeyDown={handleKeyDown}
        onChange={(event) => {
          setOpen(true);
          setHighlightedIndex(0);
          onChange(event.target.value);
        }}
      />
      <span className="searchable-dropdown-caret" aria-hidden="true">▾</span>

      {showMenu && (
        <div className="searchable-dropdown-menu" id={`${id}-menu`} role="listbox">
          {loading ? (
            <div className="searchable-dropdown-status">Loading...</div>
          ) : filteredOptions.length > 0 ? (
            filteredOptions.map((option, index) => (
              <button
                key={option}
                id={`${id}-option-${index}`}
                type="button"
                role="option"
                aria-selected={index === highlightedIndex || normalize(option) === normalize(value)}
                ref={(element) => {
                  optionRefs.current[index] = element;
                }}
                className={`searchable-dropdown-option${normalize(option) === normalize(value) ? " is-selected" : ""}${index === highlightedIndex ? " is-active" : ""}`}
                onMouseEnter={() => {
                  setHighlightedIndex(index);
                }}
                onMouseDown={(event) => {
                  event.preventDefault();
                  handleSelect(option);
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

