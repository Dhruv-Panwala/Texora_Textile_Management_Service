import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { TakaVoiceInput } from './TakaVoiceInput';
import type { RecognitionProvider, RecognitionSession } from '../services/recognitionProvider';

class FakeRecognitionProvider implements RecognitionProvider {
  callbacks?: Parameters<RecognitionProvider['start']>[0];
  stopped = false;

  isSupported() {
    return true;
  }

  start(callbacks: Parameters<RecognitionProvider['start']>[0]): RecognitionSession {
    this.callbacks = callbacks;
    return {
      stop: () => {
        this.stopped = true;
        callbacks.onEnd?.();
      },
    };
  }

  emit(transcript: string) {
    this.callbacks?.onUpdate({
      transcript,
      alternatives: [{ transcript, confidence: 0.91 }, { transcript: 'alternative phrase', confidence: 0.42 }],
      confidence: 0.91,
    });
  }
}

describe('TakaVoiceInput', () => {
  it('keeps recognition results in preview until the user confirms them', async () => {
    const user = userEvent.setup();
    const provider = new FakeRecognitionProvider();
    const onConfirm = vi.fn();
    render(<TakaVoiceInput provider={provider} onEntriesConfirmed={onConfirm} />);

    await user.click(screen.getByRole('button', { name: /speak taka/i }));
    provider.emit('taka number 123 taka metres 450.5');
    expect(onConfirm).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: /stop and review/i }));

    expect(screen.getByText(/review parsed entries/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue('123')).toBeInTheDocument();
    expect(screen.getByText(/confidence: 91%/i)).toBeInTheDocument();
    expect(screen.getByText(/alternative phrase/i)).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /add confirmed entries to sale/i }));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith([{ takaNo: 123, meters: 450.5 }]));
  });

  it('supports edit/delete and optional bulk library saving', async () => {
    const user = userEvent.setup();
    const provider = new FakeRecognitionProvider();
    const onConfirm = vi.fn();
    const onSaveToLibrary = vi.fn().mockResolvedValue(undefined);
    render(<TakaVoiceInput provider={provider} onEntriesConfirmed={onConfirm} onSaveToLibrary={onSaveToLibrary} />);

    await user.click(screen.getByRole('button', { name: /speak taka/i }));
    provider.emit('taka number 123 taka metres 450, taka number 124 taka metres 451');
    await user.click(screen.getByRole('button', { name: /stop and review/i }));
    await user.clear(screen.getByLabelText(/taka number 1/i));
    await user.type(screen.getByLabelText(/taka number 1/i), '125');
    await user.click(screen.getAllByTitle(/delete parsed taka/i)[1]);
    await user.click(screen.getByLabelText(/also save to reusable library/i));
    await user.click(screen.getByRole('button', { name: /add confirmed entries to sale/i }));

    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith([{ takaNo: 125, meters: 450 }]));
    expect(onSaveToLibrary).toHaveBeenCalledWith([{ takaNo: 125, meters: 450 }]);
  });

  it('parses a paused, punctuation-added phrase without requiring a comma', async () => {
    const user = userEvent.setup();
    const provider = new FakeRecognitionProvider();
    render(<TakaVoiceInput provider={provider} onEntriesConfirmed={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /speak taka/i }));
    provider.emit('Taka number 123. Taka metres 574.');
    await user.click(screen.getByRole('button', { name: /stop and review/i }));

    expect(screen.getByDisplayValue('123')).toBeInTheDocument();
    expect(screen.getByDisplayValue('574')).toBeInTheDocument();
    expect(screen.queryByText(/comma is required/i)).not.toBeInTheDocument();
  });

  it('keeps manual entry available when speech is unsupported', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const provider: RecognitionProvider = {
      isSupported: () => false,
      start: () => ({ stop: () => undefined }),
    };
    render(<TakaVoiceInput provider={provider} onEntriesConfirmed={onConfirm} />);

    await user.type(screen.getByLabelText('Taka number'), '321');
    await user.type(screen.getByLabelText('Meters'), '99.25');
    await user.click(screen.getByRole('button', { name: /add manually/i }));
    await user.click(screen.getByRole('button', { name: /add confirmed entries to sale/i }));

    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith([{ takaNo: 321, meters: 99.25 }]));
  });
});
