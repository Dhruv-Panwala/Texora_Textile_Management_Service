import { useEffect, useReducer, useRef, useState } from 'react';
import { Mic, MicOff, Plus, RefreshCw, Trash } from 'lucide-react';
import type { ParsedTakaEntry } from '../utils/takaParser';
import { parseTakaEntries } from '../utils/takaParser';
import { validateTakaEntries, type TakaValue } from '../utils/takaRules';
import {
  createBrowserRecognitionProvider,
  type RecognitionProvider,
  type RecognitionSession,
} from '../services/recognitionProvider';

export type SpokenTaka = TakaValue;

interface EditableEntry {
  takaNo: string;
  meters: string;
  rawText: string;
}
type VoiceStatus = 'idle' | 'listening' | 'preview' | 'saving' | 'error';

interface VoiceState {
  status: VoiceStatus;
  transcript: string;
  entries: EditableEntry[];
  issues: string[];
  validationError: string;
  alternatives: string[];
  confidence?: number;
  message: string;
}

type VoiceAction =
  | { type: 'idle'; message?: string }
  | { type: 'listening' }
  | { type: 'transcript'; transcript: string; alternatives: string[]; confidence?: number }
  | { type: 'preview'; transcript: string; entries: EditableEntry[]; issues: string[]; alternatives: string[]; confidence?: number }
  | { type: 'update-entry'; index: number; field: 'takaNo' | 'meters'; value: string }
  | { type: 'delete-entry'; index: number }
  | { type: 'validation-error'; message: string }
  | { type: 'saving' }
  | { type: 'error'; message: string };

const initialState: VoiceState = {
  status: 'idle',
  transcript: '',
  entries: [],
  issues: [],
  validationError: '',
  alternatives: [],
  message: '',
};

function reducer(state: VoiceState, action: VoiceAction): VoiceState {
  switch (action.type) {
    case 'idle':
      return { ...initialState, message: action.message || '' };
    case 'listening':
      return { ...initialState, status: 'listening' };
    case 'transcript':
      return { ...state, transcript: action.transcript, alternatives: action.alternatives, confidence: action.confidence };
    case 'preview':
      return {
        ...state,
        status: 'preview',
        transcript: action.transcript,
        entries: action.entries,
        issues: action.issues,
        validationError: '',
        alternatives: action.alternatives,
        confidence: action.confidence,
      };
    case 'update-entry':
      return {
        ...state,
        entries: state.entries.map((entry, index) => index === action.index
          ? { ...entry, [action.field]: action.value, rawText: 'Edited manually' }
          : entry),
        validationError: '',
        issues: [],
      };
    case 'delete-entry':
      return { ...state, entries: state.entries.filter((_, index) => index !== action.index), validationError: '' };
    case 'validation-error':
      return { ...state, validationError: action.message };
    case 'saving':
      return { ...state, status: 'saving', validationError: '' };
    case 'error':
      return { ...state, status: 'error', message: action.message };
    default:
      return state;
  }
}

function editableEntries(entries: ParsedTakaEntry[]): EditableEntry[] {
  return entries.map((entry) => ({ takaNo: String(entry.takaNo), meters: String(entry.meters), rawText: entry.rawText }));
}

function numericEntries(entries: EditableEntry[]): TakaValue[] {
  return entries.map((entry) => ({ takaNo: Number(entry.takaNo), meters: Number(entry.meters) }));
}

export interface TakaVoiceInputProps {
  onEntriesConfirmed: (entries: SpokenTaka[]) => void | Promise<void>;
  onSaveToLibrary?: (entries: SpokenTaka[]) => void | Promise<void>;
  provider?: RecognitionProvider;
}

export function TakaVoiceInput({ onEntriesConfirmed, onSaveToLibrary, provider = createBrowserRecognitionProvider() }: TakaVoiceInputProps) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const [saveToLibrary, setSaveToLibrary] = useState(false);
  const [manualNo, setManualNo] = useState('');
  const [manualMeters, setManualMeters] = useState('');
  const [manualError, setManualError] = useState('');
  const sessionRef = useRef<RecognitionSession | null>(null);
  const transcriptRef = useRef('');
  const alternativesRef = useRef<string[]>([]);
  const confidenceRef = useRef<number | undefined>();

  useEffect(() => () => {
    sessionRef.current?.stop();
    sessionRef.current = null;
  }, []);

  const showPreview = (transcript: string) => {
    const parsed = parseTakaEntries(transcript);
    dispatch({
      type: 'preview',
      transcript,
      entries: editableEntries(parsed.entries),
      issues: parsed.issues,
      alternatives: alternativesRef.current,
      confidence: confidenceRef.current,
    });
  };

  const startListening = () => {
    if (!provider.isSupported()) {
      dispatch({ type: 'error', message: 'Voice capture is not supported in this browser. Use the manual entry below.' });
      return;
    }
    sessionRef.current?.stop();
    transcriptRef.current = '';
    alternativesRef.current = [];
    confidenceRef.current = undefined;
    dispatch({ type: 'listening' });
    try {
      sessionRef.current = provider.start({
        onUpdate: (update) => {
          transcriptRef.current = update.transcript;
          alternativesRef.current = Array.from(new Set(update.alternatives.map((alternative) => alternative.transcript)));
          confidenceRef.current = update.confidence;
          dispatch({
            type: 'transcript',
            transcript: update.transcript,
            alternatives: alternativesRef.current,
            confidence: update.confidence,
          });
        },
        onError: (message) => {
          sessionRef.current = null;
          dispatch({ type: 'error', message });
        },
        onEnd: () => {
          sessionRef.current = null;
          showPreview(transcriptRef.current.trim());
        },
      });
    } catch {
      sessionRef.current = null;
      dispatch({ type: 'error', message: 'Voice capture could not start. Use the manual entry below.' });
    }
  };

  const stopListening = () => {
    sessionRef.current?.stop();
    sessionRef.current = null;
  };

  const addManualEntry = () => {
    const candidate = { takaNo: Number(manualNo), meters: Number(manualMeters) };
    const currentEntries = state.status === 'preview' ? numericEntries(state.entries) : [];
    const validationError = validateTakaEntries([...currentEntries, candidate], false);
    if (validationError) {
      setManualError(validationError);
      return;
    }
    setManualError('');
    const entries = [...(state.status === 'preview' ? state.entries : []), {
      takaNo: String(candidate.takaNo),
      meters: String(candidate.meters),
      rawText: 'Entered manually',
    }];
    setManualNo('');
    setManualMeters('');
    dispatch({
      type: 'preview',
      transcript: state.transcript || 'Manual entry',
      entries,
      issues: [],
      alternatives: state.alternatives,
      confidence: state.confidence,
    });
  };

  const confirmEntries = async () => {
    if (state.status !== 'preview') {
      return;
    }
    if (state.issues.length > 0) {
      dispatch({ type: 'validation-error', message: state.issues.join(' ') });
      return;
    }
    const entries = numericEntries(state.entries);
    const validationError = validateTakaEntries(entries);
    if (validationError) {
      dispatch({ type: 'validation-error', message: validationError });
      return;
    }
    dispatch({ type: 'saving' });
    try {
      await onEntriesConfirmed(entries);
      if (saveToLibrary && onSaveToLibrary) {
        await onSaveToLibrary(entries);
      }
      setSaveToLibrary(false);
      dispatch({ type: 'idle', message: `${entries.length} taka ${entries.length === 1 ? 'entry' : 'entries'} added to the sale.` });
    } catch (reason: unknown) {
      dispatch({ type: 'error', message: reason instanceof Error ? reason.message : 'Could not confirm taka entries.' });
    }
  };

  const retry = () => {
    dispatch({ type: 'idle' });
    setManualError('');
  };

  const supported = provider.isSupported();
  const isListening = state.status === 'listening';
  const canEdit = state.status === 'preview';

  return (
    <div className="space-y-3 rounded-2xl border border-stone-200 bg-stone-50/80 p-4" data-testid="taka-voice-input">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-stone-900">Add taka entries by voice</p>
          <p className="mt-1 text-xs text-stone-500">Say “taka number 123 taka metres 450.5”. No comma or special pause is required; review before adding.</p>
        </div>
        <button type="button" className="btn-secondary" disabled={state.status === 'saving'} onClick={isListening ? stopListening : startListening}>
          {isListening ? <MicOff className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
          {isListening ? 'Stop and review' : 'Speak taka'}
        </button>
      </div>

      {!supported && <p className="text-xs text-amber-700">Voice capture is unavailable here. The manual entry below is always available.</p>}
      {state.status === 'listening' && <p className="text-xs text-stone-600" aria-live="polite">Listening… You can pause, then continue speaking.</p>}
      {state.transcript && <p className="text-xs text-stone-600" aria-live="polite">Heard: {state.transcript}</p>}
      {state.confidence != null && <p className="text-xs text-stone-500">Recognition confidence: {Math.round(state.confidence * 100)}%</p>}
      {state.alternatives.length > 1 && (
        <details className="text-xs text-stone-600">
          <summary>Other recognized phrases</summary>
          <ul className="mt-1 list-disc pl-5">{state.alternatives.slice(1).map((alternative, index) => <li key={`${alternative}-${index}`}>{alternative}</li>)}</ul>
        </details>
      )}

      {canEdit && (
        <div className="space-y-2 rounded-xl border border-stone-200 bg-white p-3">
          <p className="text-sm font-semibold text-stone-800">Review parsed entries</p>
          {state.entries.map((entry, index) => (
            <div className="grid grid-cols-[1fr_1fr_2rem] gap-2" key={`${entry.rawText}-${index}`}>
              <label className="sr-only" htmlFor={`voice-taka-no-${index}`}>Taka number {index + 1}</label>
              <input id={`voice-taka-no-${index}`} type="number" min="1" step="1" className="border rounded-md px-3 py-2" value={entry.takaNo} onChange={(event) => dispatch({ type: 'update-entry', index, field: 'takaNo', value: event.target.value })} />
              <label className="sr-only" htmlFor={`voice-taka-meters-${index}`}>Meters for taka {index + 1}</label>
              <input id={`voice-taka-meters-${index}`} type="number" min="0.01" step="0.01" className="border rounded-md px-3 py-2" value={entry.meters} onChange={(event) => dispatch({ type: 'update-entry', index, field: 'meters', value: event.target.value })} />
              <button type="button" className="flex items-center justify-center rounded-md bg-red-500 text-white" title="Delete parsed taka" onClick={() => dispatch({ type: 'delete-entry', index })}><Trash className="h-4 w-4" /></button>
            </div>
          ))}
          {state.issues.length > 0 && <p className="text-xs text-amber-700">{state.issues.join(' ')}</p>}
          {state.validationError && <p className="text-xs text-red-700" role="alert">{state.validationError}</p>}
          <div className="flex flex-wrap items-center gap-3">
            <button type="button" className="btn-primary" onClick={() => void confirmEntries()}>Add confirmed entries to sale</button>
            <button type="button" className="btn-secondary" onClick={retry}><RefreshCw className="h-4 w-4" /> Discard and retry</button>
            {onSaveToLibrary && <label className="flex items-center gap-2 text-xs text-stone-600"><input type="checkbox" checked={saveToLibrary} onChange={(event) => setSaveToLibrary(event.target.checked)} /> Also save to reusable library</label>}
          </div>
        </div>
      )}

      {state.status === 'saving' && <p className="text-sm text-stone-600" aria-live="polite">Adding entries…</p>}
      {state.status === 'error' && <div className="flex items-center justify-between gap-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert"><span>{state.message}</span><button type="button" className="btn-secondary" onClick={retry}>Try again</button></div>}
      {state.status === 'idle' && state.message && <p className="text-sm text-green-700" role="status">{state.message}</p>}

      <div className="border-t border-stone-200 pt-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-stone-500">Manual fallback</p>
        <div className="mt-2 flex flex-wrap gap-2">
          <label className="sr-only" htmlFor="manual-taka-number">Taka number</label>
          <input id="manual-taka-number" type="number" min="1" step="1" className="w-36 border rounded-md px-3 py-2" placeholder="Taka no" value={manualNo} onChange={(event) => setManualNo(event.target.value)} />
          <label className="sr-only" htmlFor="manual-taka-meters">Meters</label>
          <input id="manual-taka-meters" type="number" min="0.01" step="0.01" className="w-36 border rounded-md px-3 py-2" placeholder="Meters" value={manualMeters} onChange={(event) => setManualMeters(event.target.value)} />
          <button type="button" className="btn-secondary" onClick={addManualEntry}><Plus className="h-4 w-4" /> Add manually</button>
        </div>
        {manualError && <p className="mt-1 text-xs text-red-700" role="alert">{manualError}</p>}
      </div>
    </div>
  );
}
