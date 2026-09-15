import { describe, expect, it } from 'vitest';
import { parseSpokenNumber, parseTakaEntries } from './takaParser';

describe('parseSpokenNumber', () => {
  it.each([
    ['123', 123],
    ['450.5', 450.5],
    ['one two three', 123],
    ['one twenty three', 123],
    ['five hundred seventy four', 574],
    ['one lakh twenty three thousand five hundred', 123500],
  ])('parses %s as %s', (spoken, expected) => {
    expect(parseSpokenNumber(spoken)).toBe(expected);
  });

  it('parses spoken decimals', () => {
    expect(parseSpokenNumber('four hundred fifty point five')).toBe(450.5);
    expect(parseSpokenNumber('four five zero point five zero')).toBe(450.5);
  });
});

describe('parseTakaEntries', () => {
  it('does not require a spoken comma', () => {
    expect(parseTakaEntries('taka number 123 taka metres 574').entries).toEqual([
      { takaNo: 123, meters: 574, rawText: '123 / 574' },
    ]);
  });

  it('accepts the no-break meters wording from speech recognition', () => {
    expect(parseTakaEntries('taka number 123 taka meters 450').entries).toEqual([
      { takaNo: 123, meters: 450, rawText: '123 / 450' },
    ]);
  });

  it('accepts the punctuation browsers commonly add after a pause', () => {
    expect(parseTakaEntries('Taka number 123. Taka meters 450.').entries).toEqual([
      { takaNo: 123, meters: 450, rawText: '123 / 450' },
    ]);
  });

  it('parses multiple entries and meter variants with fillers', () => {
    expect(parseTakaEntries('please record taka number one twenty three, taka meter four hundred fifty point five and taka no one two four taka metres 451').entries).toEqual([
      { takaNo: 123, meters: 450.5, rawText: 'one twenty three / four hundred fifty point five' },
      { takaNo: 124, meters: 451, rawText: 'one two four / 451' },
    ]);
  });

  it('accepts Chrome clipping "taka" to "ka" between entries', () => {
    expect(parseTakaEntries('Taka number 584. Taka metres 197. Ka number 119. Taka meters 450.25.')).toMatchObject({
      entries: [
        { takaNo: 584, meters: 197, rawText: '584 / 197' },
        { takaNo: 119, meters: 450.25, rawText: '119 / 450.25' },
      ],
      issues: [],
    });
  });

  it('reports an issue when the taka format is missing', () => {
    expect(parseTakaEntries('hello there').entries).toHaveLength(0);
    expect(parseTakaEntries('hello there').issues).not.toHaveLength(0);
  });
});
