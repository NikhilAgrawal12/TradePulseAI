import { City, Country, States } from "async-country-state-city";
import type { CCity, CCountry, CState } from "async-country-state-city/interface";

export type LocationCountryOption = Pick<CCountry, "name" | "isoCode" | "flag">;
export type LocationStateOption = Pick<CState, "name" | "isoCode" | "countryCode">;
export type LocationCityOption = Pick<CCity, "name" | "stateCode" | "countryCode">;

let countryInitPromise: Promise<void> | null = null;

function normalizeValue(value: string): string {
  return value.trim().replace(/\s+/g, " ").toLowerCase();
}

function sortByName<T extends { name: string }>(items: T[]): T[] {
  return [...items].sort((left, right) => left.name.localeCompare(right.name));
}

export async function ensureLocationDataReady(): Promise<void> {
  if (!countryInitPromise) {
    countryInitPromise = Country.init();
  }
  await countryInitPromise;
}

export async function getCountryOptions(): Promise<LocationCountryOption[]> {
  await ensureLocationDataReady();
  return sortByName(Country.getAllCountries()).map(({ name, isoCode, flag }) => ({
    name,
    isoCode,
    flag,
  }));
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
  await ensureLocationDataReady();
  return sortByName(await States.getAllStates(countryCode)).map(({ name, isoCode, countryCode: optionCountryCode }) => ({
    name,
    isoCode,
    countryCode: optionCountryCode,
  }));
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

  await ensureLocationDataReady();
  const dedupedCities = new Map<string, LocationCityOption>();
  const cities = await City.getAllCities(countryCode, stateCode);

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

  return [...dedupedCities.values()];
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

