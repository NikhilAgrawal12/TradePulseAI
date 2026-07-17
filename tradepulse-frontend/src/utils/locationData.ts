import { City, Country, States } from "async-country-state-city";
import type { CCity, CCountry, CState } from "async-country-state-city/interface";

export type LocationCountryOption = Pick<CCountry, "name" | "isoCode" | "flag">;
export type LocationStateOption = Pick<CState, "name" | "isoCode" | "countryCode">;
export type LocationCityOption = Pick<CCity, "name" | "stateCode" | "countryCode">;

let countryInitPromise: Promise<void> | null = null;
const LOCATION_CACHE_TTL_MS = 24 * 60 * 60 * 1000;
const COUNTRY_CACHE_KEY = "tp_location_countries_v1";
const STATE_CACHE_PREFIX = "tp_location_states_v1_";
const CITY_CACHE_PREFIX = "tp_location_cities_v1_";

let countryOptionsCache: LocationCountryOption[] | null = null;
const stateOptionsCache = new Map<string, LocationStateOption[]>();
const cityOptionsCache = new Map<string, LocationCityOption[]>();
const stateRequestCache = new Map<string, Promise<LocationStateOption[]>>();
const cityRequestCache = new Map<string, Promise<LocationCityOption[]>>();

function normalizeValue(value: string): string {
  return value.trim().replace(/\s+/g, " ").toLowerCase();
}

function sortByName<T extends { name: string }>(items: T[]): T[] {
  return [...items].sort((left, right) => left.name.localeCompare(right.name));
}

function readCachedValue<T>(key: string): T | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as { savedAt: number; value: T };
    if (!parsed || typeof parsed.savedAt !== "number") {
      window.localStorage.removeItem(key);
      return null;
    }

    if (Date.now() - parsed.savedAt > LOCATION_CACHE_TTL_MS) {
      window.localStorage.removeItem(key);
      return null;
    }

    return parsed.value;
  } catch {
    return null;
  }
}

function writeCachedValue<T>(key: string, value: T): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.setItem(
      key,
      JSON.stringify({
        savedAt: Date.now(),
        value,
      }),
    );
  } catch {
    // Ignore storage failures (private mode/quota) and continue with memory cache only.
  }
}

export async function ensureLocationDataReady(): Promise<void> {
  if (!countryInitPromise) {
    countryInitPromise = Country.init();
  }
  await countryInitPromise;
}

export async function getCountryOptions(): Promise<LocationCountryOption[]> {
  if (countryOptionsCache) {
    return countryOptionsCache;
  }

  const cachedCountries = readCachedValue<LocationCountryOption[]>(COUNTRY_CACHE_KEY);
  if (cachedCountries) {
    countryOptionsCache = cachedCountries;
    return cachedCountries;
  }

  await ensureLocationDataReady();
  const countries = sortByName(Country.getAllCountries()).map(({ name, isoCode, flag }) => ({
    name,
    isoCode,
    flag,
  }));

  countryOptionsCache = countries;
  writeCachedValue(COUNTRY_CACHE_KEY, countries);
  return countries;
}

export async function findCountryByName(name: string): Promise<LocationCountryOption | null> {
  await ensureLocationDataReady();
  const normalizedTarget = normalizeValue(name);
  if (!normalizedTarget) {
    return null;
  }

  const match = Country.getAllCountries().find((country) => normalizeValue(country.name) === normalizedTarget);
  return match ? { name: match.name, isoCode: match.isoCode, flag: match.flag } : null;
}

export async function getStateOptions(countryCode: string): Promise<LocationStateOption[]> {
  if (!countryCode) {
    return [];
  }

  const normalizedCountryCode = countryCode.toUpperCase();
  const inMemoryStates = stateOptionsCache.get(normalizedCountryCode);
  if (inMemoryStates) {
    return inMemoryStates;
  }

  const stateCacheKey = `${STATE_CACHE_PREFIX}${normalizedCountryCode}`;
  const persistedStates = readCachedValue<LocationStateOption[]>(stateCacheKey);
  if (persistedStates) {
    stateOptionsCache.set(normalizedCountryCode, persistedStates);
    return persistedStates;
  }

  const inFlight = stateRequestCache.get(normalizedCountryCode);
  if (inFlight) {
    return inFlight;
  }

  const fetchStatesPromise = (async () => {
    await ensureLocationDataReady();
    const states = sortByName(await States.getAllStates(normalizedCountryCode)).map(({ name, isoCode, countryCode: optionCountryCode }) => ({
      name,
      isoCode,
      countryCode: optionCountryCode,
    }));

    stateOptionsCache.set(normalizedCountryCode, states);
    writeCachedValue(stateCacheKey, states);
    return states;
  })();

  stateRequestCache.set(normalizedCountryCode, fetchStatesPromise);
  try {
    return await fetchStatesPromise;
  } finally {
    stateRequestCache.delete(normalizedCountryCode);
  }
}

export async function findStateByName(countryCode: string, name: string): Promise<LocationStateOption | null> {
  if (!countryCode) {
    return null;
  }

  const normalizedTarget = normalizeValue(name);
  if (!normalizedTarget) {
    return null;
  }

  const states = await getStateOptions(countryCode);
  const match = states.find((state) => normalizeValue(state.name) === normalizedTarget);
  return match ?? null;
}

export async function getCityOptions(countryCode: string, stateCode: string): Promise<LocationCityOption[]> {
  if (!countryCode || !stateCode) {
    return [];
  }

  const normalizedCountryCode = countryCode.toUpperCase();
  const normalizedStateCode = stateCode.toUpperCase();
  const cityLookupKey = `${normalizedCountryCode}_${normalizedStateCode}`;

  const inMemoryCities = cityOptionsCache.get(cityLookupKey);
  if (inMemoryCities) {
    return inMemoryCities;
  }

  const cityCacheKey = `${CITY_CACHE_PREFIX}${cityLookupKey}`;
  const persistedCities = readCachedValue<LocationCityOption[]>(cityCacheKey);
  if (persistedCities) {
    cityOptionsCache.set(cityLookupKey, persistedCities);
    return persistedCities;
  }

  const inFlight = cityRequestCache.get(cityLookupKey);
  if (inFlight) {
    return inFlight;
  }

  const fetchCitiesPromise = (async () => {
    await ensureLocationDataReady();
    const dedupedCities = new Map<string, LocationCityOption>();
    const cities = await City.getAllCities(normalizedCountryCode, normalizedStateCode);

    for (const city of sortByName(cities)) {
      const key = normalizeValue(city.name);
      if (!dedupedCities.has(key)) {
        dedupedCities.set(key, {
          name: city.name,
          stateCode: city.stateCode,
          countryCode: city.countryCode,
        });
      }
    }

    const cityOptions = [...dedupedCities.values()];
    cityOptionsCache.set(cityLookupKey, cityOptions);
    writeCachedValue(cityCacheKey, cityOptions);
    return cityOptions;
  })();

  cityRequestCache.set(cityLookupKey, fetchCitiesPromise);
  try {
    return await fetchCitiesPromise;
  } finally {
    cityRequestCache.delete(cityLookupKey);
  }
}

export async function findCityByName(countryCode: string, stateCode: string, name: string): Promise<LocationCityOption | null> {
  if (!countryCode || !stateCode) {
    return null;
  }

  const normalizedTarget = normalizeValue(name);
  if (!normalizedTarget) {
    return null;
  }

  const cities = await getCityOptions(countryCode, stateCode);
  const match = cities.find((city) => normalizeValue(city.name) === normalizedTarget);
  return match ?? null;
}

