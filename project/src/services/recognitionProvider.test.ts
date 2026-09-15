import { afterEach, describe, expect, it, vi } from 'vitest';
import { createBrowserRecognitionProvider } from './recognitionProvider';

type FakeResult = {
  isFinal: boolean;
  length: number;
  [index: number]: { transcript: string; confidence?: number };
};

type FakeResultEvent = {
  resultIndex: number;
  results: FakeResult[];
};

class FakeRecognition {
  static instances: FakeRecognition[] = [];
  lang = '';
  continuous = false;
  interimResults = false;
  onresult: ((event: FakeResultEvent) => void) | null = null;
  onerror: ((event: { error: string }) => void) | null = null;
  onend: (() => void) | null = null;
  start = vi.fn();
  stop = vi.fn(() => this.onend?.());

  constructor() {
    FakeRecognition.instances.push(this);
  }
}

describe('createBrowserRecognitionProvider', () => {
  afterEach(() => {
    FakeRecognition.instances = [];
    vi.unstubAllGlobals();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('preserves recognition across a browser pause and ignores a manual-stop error', () => {
    vi.useFakeTimers();
    vi.stubGlobal('window', {
      SpeechRecognition: FakeRecognition,
      setTimeout,
    });
    const provider = createBrowserRecognitionProvider();
    const updates: string[] = [];
    const onError = vi.fn();
    const session = provider.start({
      onUpdate: (update) => updates.push(update.transcript),
      onError,
    });
    const first = FakeRecognition.instances[0];
    first.onerror?.({ error: 'no-speech' });
    first.onresult?.({
      resultIndex: 0,
      results: [{ isFinal: true, length: 1, 0: { transcript: 'taka number 123' } }],
    });
    first.onend?.();
    vi.advanceTimersByTime(150);
    expect(FakeRecognition.instances).toHaveLength(2);
    expect(FakeRecognition.instances[1].continuous).toBe(true);

    session.stop();
    FakeRecognition.instances[1].onerror?.({ error: 'aborted' });
    expect(updates).toContain('taka number 123');
    expect(onError).not.toHaveBeenCalled();
  });

  it('treats an active aborted segment as a pause instead of a capture failure', () => {
    vi.useFakeTimers();
    vi.stubGlobal('window', {
      SpeechRecognition: FakeRecognition,
      setTimeout,
    });
    const provider = createBrowserRecognitionProvider();
    const onError = vi.fn();
    provider.start({ onUpdate: vi.fn(), onError });
    const first = FakeRecognition.instances[0];

    first.onerror?.({ error: 'aborted' });
    first.onend?.();
    vi.advanceTimersByTime(150);

    expect(FakeRecognition.instances).toHaveLength(2);
    expect(onError).not.toHaveBeenCalled();
  });

  it('keeps interim text when a pause ends one segment before the next phrase', () => {
    vi.useFakeTimers();
    vi.stubGlobal('window', {
      SpeechRecognition: FakeRecognition,
      setTimeout,
      clearTimeout,
    });
    const provider = createBrowserRecognitionProvider();
    const updates: string[] = [];
    const onEnd = vi.fn();
    const session = provider.start({
      onUpdate: (update) => updates.push(update.transcript),
      onError: vi.fn(),
      onEnd,
    });
    const first = FakeRecognition.instances[0];

    first.onresult?.({
      resultIndex: 0,
      results: [{ isFinal: false, length: 1, 0: { transcript: 'taka number 123' } }],
    });
    first.onend?.();
    vi.advanceTimersByTime(150);

    const second = FakeRecognition.instances[1];
    second.onresult?.({
      resultIndex: 0,
      results: [{ isFinal: true, length: 1, 0: { transcript: 'taka metres 574' } }],
    });
    session.stop();

    expect(updates[updates.length - 1]).toBe('taka number 123 taka metres 574');
    expect(onEnd).toHaveBeenCalledOnce();
  });

  it('treats a transient network error during a pause as recoverable', () => {
    vi.useFakeTimers();
    vi.stubGlobal('window', {
      SpeechRecognition: FakeRecognition,
      setTimeout,
      clearTimeout,
    });
    const provider = createBrowserRecognitionProvider();
    const onError = vi.fn();
    provider.start({ onUpdate: vi.fn(), onError });
    const first = FakeRecognition.instances[0];

    first.onerror?.({ error: 'network' });
    first.onend?.();
    vi.advanceTimersByTime(150);

    expect(FakeRecognition.instances).toHaveLength(2);
    expect(onError).not.toHaveBeenCalled();
  });
});
