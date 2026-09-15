import { describe, expect, it } from 'vitest';
import { MAX_TAKA_ENTRIES, validateTakaEntries } from './takaRules';

describe('validateTakaEntries', () => {
  it('enforces positive, unique and two-decimal values', () => {
    expect(validateTakaEntries([{ takaNo: 1, meters: 0 }])).toMatch(/greater than zero/i);
    expect(validateTakaEntries([{ takaNo: 1, meters: 450.505 }])).toMatch(/decimal places/i);
    expect(validateTakaEntries([{ takaNo: 1, meters: 10 }, { takaNo: 1, meters: 11 }])).toMatch(/duplicated/i);
  });

  it('enforces the shared maximum count', () => {
    const entries = Array.from({ length: MAX_TAKA_ENTRIES + 1 }, (_, index) => ({ takaNo: index + 1, meters: 1 }));
    expect(validateTakaEntries(entries)).toMatch(/at most/i);
  });
});
