const EASTERN_TIME_ZONE = "America/New_York";

const easternDateTimeFormatter = new Intl.DateTimeFormat("en-US", {
  timeZone: EASTERN_TIME_ZONE,
  month: "short",
  day: "numeric",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

function parseApiDateTime(value: string | Date): Date {
  if (value instanceof Date) {
    return value;
  }

  const trimmed = value.trim();
  const hasExplicitZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(trimmed);
  if (hasExplicitZone) {
    return new Date(trimmed);
  }

  const normalized = trimmed.includes("T") ? `${trimmed}Z` : trimmed.replace(" ", "T") + "Z";
  return new Date(normalized);
}

export function formatEasternDateTime(value: string | Date | null | undefined): string {
  if (!value) {
    return "--";
  }

  const parsed = parseApiDateTime(value);
  if (Number.isNaN(parsed.getTime())) {
    return "--";
  }

  return `${easternDateTimeFormatter.format(parsed)} ET`;
}

