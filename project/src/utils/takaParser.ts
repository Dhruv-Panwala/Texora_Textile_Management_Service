import type { TakaValue } from './takaRules';

export interface ParsedTakaEntry extends TakaValue {
  rawText: string;
}

export interface TakaParseResult {
  normalizedTranscript: string;
  entries: ParsedTakaEntry[];
  issues: string[];
}

const numberWords: Record<string, number> = {
  zero: 0, oh: 0, o: 0, one: 1, two: 2, three: 3, four: 4, five: 5,
  six: 6, seven: 7, eight: 8, nine: 9, ten: 10, eleven: 11, twelve: 12,
  thirteen: 13, fourteen: 14, fifteen: 15, sixteen: 16, seventeen: 17,
  eighteen: 18, nineteen: 19, twenty: 20, thirty: 30, forty: 40,
  fifty: 50, sixty: 60, seventy: 70, eighty: 80, ninety: 90,
};

const scales: Record<string, number> = {
  hundred: 100,
  thousand: 1_000,
  lakh: 100_000,
  lac: 100_000,
  crore: 10_000_000,
};

const fillerWords = new Set([
  'a', 'an', 'the', 'please', 'add', 'record', 'register', 'entry', 'entries',
  'of', 'is', 'are', 'at', 'around', 'approximately', 'about', 'for', 'me', 'and',
  'um', 'uh', 'hmm', 'okay', 'ok',
]);

interface Label {
  start: number;
  end: number;
}

function normalizeTranscript(transcript: string): string {
  return transcript
    .toLowerCase()
    .replace(/[–—-]/g, ' ')
    .replace(/[,:;!?]/g, ' ')
    .replace(/\.(?=\s|$)/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function findLabels(transcript: string, pattern: RegExp): Label[] {
  return Array.from(transcript.matchAll(pattern)).map((match) => ({
    start: match.index ?? 0,
    end: (match.index ?? 0) + match[0].length,
  }));
}

function cleanNumberText(value: string): string {
  return value
    .split(/\s+/)
    .filter((part) => part && !fillerWords.has(part))
    .join(' ')
    .trim();
}

function digitValue(token: string): number | null {
  if (/^\d$/.test(token)) {
    return Number(token);
  }
  const value = numberWords[token];
  return value != null && value >= 0 && value <= 9 ? value : null;
}

function parseWholeNumber(value: string): number | null {
  const compact = value.replace(/,/g, '').trim();
  if (/^\d+$/.test(compact)) {
    return Number(compact);
  }

  const tokens = compact.replace(/-/g, ' ').split(/\s+/).filter(Boolean);
  if (tokens.length === 0) {
    return null;
  }

  const digitSequence = tokens.map(digitValue);
  if (digitSequence.every((digit) => digit != null)) {
    return Number(digitSequence.join(''));
  }

  // Indian-English speech commonly says “one twenty three” for 123.
  if (tokens.length >= 2 && tokens.length <= 3) {
    const first = digitValue(tokens[0]);
    const tens = numberWords[tokens[1]];
    const last = tokens.length === 3 ? digitValue(tokens[2]) : 0;
    if (first != null && first > 0 && tens >= 20 && tens % 10 === 0 && last != null) {
      return first * 100 + tens + last;
    }
  }

  let total = 0;
  let current = 0;
  let foundNumber = false;
  for (const token of tokens) {
    const valueForToken = numberWords[token];
    if (valueForToken != null) {
      current += valueForToken;
      foundNumber = true;
      continue;
    }
    const scale = scales[token];
    if (scale == null) {
      return null;
    }
    foundNumber = true;
    if (scale === 100) {
      current = (current || 1) * scale;
    } else {
      total += (current || 1) * scale;
      current = 0;
    }
  }
  return foundNumber ? total + current : null;
}

export function parseSpokenNumber(value: string): number | null {
  const cleaned = cleanNumberText(value);
  const direct = cleaned.replace(/,/g, '');
  if (/^\d+(?:\.\d+)?$/.test(direct)) {
    return Number(direct);
  }

  const decimalParts = cleaned.split(/\s+point\s+/);
  if (decimalParts.length === 2) {
    const whole = parseWholeNumber(decimalParts[0]);
    const fraction = decimalParts[1].split(/\s+/).filter(Boolean).map((token) => {
      if (/^\d+$/.test(token)) {
        return token;
      }
      const digit = digitValue(token);
      return digit == null ? null : String(digit);
    });
    if (whole != null && fraction.length > 0 && fraction.every((part) => part != null)) {
      return Number(`${whole}.${fraction.join('')}`);
    }
    return null;
  }
  return parseWholeNumber(cleaned);
}

export function parseTakaEntries(transcript: string): TakaParseResult {
  const normalizedTranscript = normalizeTranscript(transcript);
  // ponytail: Chrome can clip the first syllable of "taka" between phrases.
  const numberLabels = findLabels(normalizedTranscript, /\b(?:(?:taka|ka)\s+)?(?:number|no\.?|#)\b/g);
  const meterLabels = findLabels(normalizedTranscript, /\b(?:taka\s+)?met(?:er|re)s?\b/g);
  const entries: ParsedTakaEntry[] = [];
  const issues: string[] = [];

  if (numberLabels.length === 0 || meterLabels.length === 0) {
    return {
      normalizedTranscript,
      entries,
      issues: ['Use “taka number … taka metres …” for each entry; a comma or pause is optional.'],
    };
  }

  numberLabels.forEach((numberLabel, index) => {
    const nextNumber = numberLabels[index + 1];
    const meterLabel = meterLabels.find((candidate) => candidate.start > numberLabel.end
      && (!nextNumber || candidate.start < nextNumber.start));
    if (!meterLabel) {
      issues.push(`Could not find meters for taka entry ${index + 1}.`);
      return;
    }
    const numberText = cleanNumberText(normalizedTranscript.slice(numberLabel.end, meterLabel.start));
    const meterText = cleanNumberText(normalizedTranscript.slice(meterLabel.end, nextNumber?.start ?? normalizedTranscript.length));
    const takaNo = parseSpokenNumber(numberText);
    const meters = parseSpokenNumber(meterText);
    if (takaNo == null || meters == null || !Number.isFinite(takaNo) || !Number.isFinite(meters)) {
      issues.push(`Could not parse taka entry ${index + 1}.`);
      return;
    }
    entries.push({ takaNo, meters, rawText: `${numberText} / ${meterText}` });
  });

  return { normalizedTranscript, entries, issues };
}
