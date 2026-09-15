export const MAX_TAKA_ENTRIES = 200;
export const MAX_TAKA_DECIMAL_PLACES = 2;

export interface TakaValue {
  takaNo: number;
  meters: number;
}

export function decimalPlaces(value: number): number {
  if (!Number.isFinite(value)) {
    return Number.POSITIVE_INFINITY;
  }
  const [coefficient, exponentText] = value.toString().toLowerCase().split('e');
  const fractionLength = coefficient.includes('.') ? coefficient.length - coefficient.indexOf('.') - 1 : 0;
  return Math.max(0, fractionLength - Number(exponentText || 0));
}

export function validateTakaEntries(entries: TakaValue[], requireAtLeastOne = true): string | null {
  if (requireAtLeastOne && entries.length === 0) {
    return 'At least one taka entry is required.';
  }
  if (entries.length > MAX_TAKA_ENTRIES) {
    return `You can add at most ${MAX_TAKA_ENTRIES} taka entries.`;
  }

  const takaNumbers = new Set<number>();
  for (const entry of entries) {
    if (!Number.isInteger(entry.takaNo) || entry.takaNo <= 0) {
      return 'Taka numbers must be positive whole numbers.';
    }
    if (takaNumbers.has(entry.takaNo)) {
      return `Taka number ${entry.takaNo} is duplicated.`;
    }
    takaNumbers.add(entry.takaNo);
    if (!Number.isFinite(entry.meters) || entry.meters <= 0) {
      return 'Taka meters must be greater than zero.';
    }
    if (decimalPlaces(entry.meters) > MAX_TAKA_DECIMAL_PLACES) {
      return `Meters can have at most ${MAX_TAKA_DECIMAL_PLACES} decimal places.`;
    }
  }
  return null;
}
