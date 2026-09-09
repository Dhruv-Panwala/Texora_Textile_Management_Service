import { useEffect, useRef, useState } from 'react';
import { Mic, MicOff } from 'lucide-react';

export type SpokenTaka = {
  takaNo: number;
  meters: number;
};

interface SpeechRecognitionAlternativeLike {
  transcript: string;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  [index: number]: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionResultListLike {
  length: number;
  [index: number]: SpeechRecognitionResultLike;
}

interface SpeechRecognitionEventLike extends Event {
  resultIndex: number;
  results: SpeechRecognitionResultListLike;
}

interface SpeechRecognitionErrorEventLike extends Event {
  error: string;
}

interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start: () => void;
  stop: () => void;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  }
}

const numberWords: Record<string, number> = {
  zero: 0,
  one: 1,
  two: 2,
  three: 3,
  four: 4,
  five: 5,
  six: 6,
  seven: 7,
  eight: 8,
  nine: 9,
  ten: 10,
  eleven: 11,
  twelve: 12,
  thirteen: 13,
  fourteen: 14,
  fifteen: 15,
  sixteen: 16,
  seventeen: 17,
  eighteen: 18,
  nineteen: 19,
  twenty: 20,
  thirty: 30,
  forty: 40,
  fifty: 50,
  sixty: 60,
  seventy: 70,
  eighty: 80,
  ninety: 90,
};

function parseNumber(value: string) {
  const cleaned = value.toLowerCase().replace(/[-,]/g, ' ').replace(/\s+/g, ' ').trim();
  if (/^\d+(?:\.\d+)?$/.test(cleaned)) {
    return Number(cleaned);
  }

  const decimalParts = cleaned.split(/\s+point\s+/);
  if (decimalParts.length === 2) {
    const whole = parseNumber(decimalParts[0]);
    const fraction = decimalParts[1].split(' ').map((part) => numberWords[part]);
    if (whole != null && fraction.length > 0 && fraction.every((digit) => digit >= 0 && digit <= 9)) {
      return Number(`${whole}.${fraction.join('')}`);
    }
    return null;
  }

  const parts = cleaned.split(' ').filter((part) => part !== 'and');
  if (parts.length > 1 && parts.every((part) => numberWords[part] >= 0 && numberWords[part] <= 9)) {
    return Number(parts.map((part) => numberWords[part]).join(''));
  }

  let total = 0;
  let current = 0;
  let foundNumber = false;
  for (const part of parts) {
    if (numberWords[part] != null) {
      current += numberWords[part];
      foundNumber = true;
    } else if (part === 'hundred') {
      current = (current || 1) * 100;
      foundNumber = true;
    } else if (part === 'thousand') {
      total += (current || 1) * 1000;
      current = 0;
      foundNumber = true;
    } else {
      return null;
    }
  }
  return foundNumber ? total + current : null;
}

function parseSpokenTaka(transcript: string): SpokenTaka | null {
  const normalized = transcript.toLowerCase().replace(/[,:;]/g, ' ').replace(/\s+/g, ' ').trim();
  const numberMatch = normalized.match(/(?:taka\s+)?(?:number|no)\s+(.+?)(?=\s+(?:taka\s+)?(?:meters?|metres?)\b|$)/);
  const metersMatch = normalized.match(/(?:taka\s+)?(?:meters?|metres?)\s+(.+)$/);
  if (!numberMatch || !metersMatch) {
    return null;
  }
  const takaNo = parseNumber(numberMatch[1]);
  const meters = parseNumber(metersMatch[1]);
  return takaNo != null && takaNo > 0 && meters != null && meters > 0 && Number.isFinite(takaNo) && Number.isFinite(meters)
    ? { takaNo, meters }
    : null;
}

function recognitionConstructor() {
  return window.SpeechRecognition || window.webkitSpeechRecognition;
}

export function TakaVoiceInput({ onEntry }: { onEntry: (entry: SpokenTaka) => Promise<void> }) {
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const handledFinalRef = useRef(false);
  const [listening, setListening] = useState(false);
  const [saving, setSaving] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [error, setError] = useState('');
  const supported = Boolean(recognitionConstructor());

  useEffect(() => () => recognitionRef.current?.stop(), []);

  const stopListening = () => {
    recognitionRef.current?.stop();
    setListening(false);
  };

  const startListening = () => {
    const Constructor = recognitionConstructor();
    if (!Constructor) {
      setError('Voice capture is not supported in this browser. Enter the taka below manually.');
      return;
    }
    setError('');
    setTranscript('');
    handledFinalRef.current = false;
    const recognition = new Constructor();
    recognition.lang = 'en-IN';
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.onresult = (event) => {
      let spoken = '';
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        spoken += event.results[index][0].transcript;
      }
      setTranscript(spoken.trim());
      const result = event.results[event.resultIndex];
      if (!result?.isFinal || handledFinalRef.current) {
        return;
      }
      handledFinalRef.current = true;
      const entry = parseSpokenTaka(spoken);
      if (!entry) {
        setError('Say: “taka number 123, taka metres 450.5”.');
        return;
      }
      setSaving(true);
      void onEntry(entry)
        .then(() => setTranscript(`Saved taka ${entry.takaNo} with ${entry.meters} metres.`))
        .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Could not save taka entry'))
        .finally(() => setSaving(false));
    };
    recognition.onerror = (event) => {
      setError(event.error === 'not-allowed' ? 'Microphone permission was denied.' : 'Voice capture failed. Please try again.');
      setListening(false);
    };
    recognition.onend = () => {
      recognitionRef.current = null;
      setListening(false);
    };
    recognitionRef.current = recognition;
    setListening(true);
    recognition.start();
  };

  return (
    <div className="rounded-2xl border border-stone-200 bg-stone-50/80 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-stone-900">Add by voice</p>
          <p className="mt-1 text-xs text-stone-500">Say “taka number 123, taka metres 450”.</p>
        </div>
        <button
          type="button"
          className="btn-secondary"
          disabled={saving}
          onClick={listening ? stopListening : startListening}
        >
          {listening ? <MicOff className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
          {listening ? 'Stop listening' : saving ? 'Saving...' : 'Speak taka'}
        </button>
      </div>
      {!supported && <p className="mt-2 text-xs text-amber-700">Voice capture is unavailable here; enter takas directly in the sale form.</p>}
      {transcript && <p className="mt-2 text-xs text-stone-600" aria-live="polite">Heard: {transcript}</p>}
      {error && <p className="mt-2 text-xs text-red-700" role="alert">{error}</p>}
    </div>
  );
}
