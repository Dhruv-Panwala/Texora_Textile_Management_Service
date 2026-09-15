export interface RecognitionAlternative {
  transcript: string;
  confidence?: number;
}

export interface RecognitionUpdate {
  transcript: string;
  alternatives: RecognitionAlternative[];
  confidence?: number;
}

export interface RecognitionProvider {
  isSupported(): boolean;
  start(callbacks: {
    onUpdate: (update: RecognitionUpdate) => void;
    onError: (message: string) => void;
    onEnd?: () => void;
  }): RecognitionSession;
}

export interface RecognitionSession {
  stop(): void;
}

interface SpeechRecognitionAlternativeLike {
  transcript: string;
  confidence?: number;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  length: number;
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

function recognitionConstructor(): SpeechRecognitionConstructor | undefined {
  if (typeof window === 'undefined') {
    return undefined;
  }
  return window.SpeechRecognition || window.webkitSpeechRecognition;
}

export function createBrowserRecognitionProvider(): RecognitionProvider {
  return {
    isSupported: () => Boolean(recognitionConstructor()),
    start: ({ onUpdate, onError, onEnd }) => {
      let stopped = false;
      let recognition: SpeechRecognitionLike | null = null;
      let finalTranscript = '';
      let segmentFinalTranscript = '';
      let segmentInterimTranscript = '';
      let restartTimer: number | undefined;

      const currentTranscript = () => `${finalTranscript} ${segmentFinalTranscript} ${segmentInterimTranscript}`.trim();

      const commitSegment = () => {
        // Some browsers end a segment during a pause before its last interim
        // result is marked final. Keep that text so the next segment can
        // continue the same spoken entry.
        const segmentTranscript = `${segmentFinalTranscript} ${segmentInterimTranscript}`.trim();
        if (segmentTranscript) {
          finalTranscript = `${finalTranscript} ${segmentTranscript}`.trim();
        }
        segmentFinalTranscript = '';
        segmentInterimTranscript = '';
      };

      const begin = () => {
        const Constructor = recognitionConstructor();
        if (!Constructor || stopped) {
          return;
        }
        recognition = new Constructor();
        recognition.lang = 'en-IN';
        recognition.continuous = true;
        recognition.interimResults = true;
        recognition.onresult = (event) => {
          let interimTranscript = '';
          const alternatives: RecognitionAlternative[] = [];
          for (let index = event.resultIndex; index < event.results.length; index += 1) {
            const result = event.results[index];
            const primary = result[0];
            if (result.isFinal) {
              segmentFinalTranscript = `${segmentFinalTranscript} ${primary.transcript}`.trim();
            } else {
              interimTranscript = `${interimTranscript} ${primary.transcript}`.trim();
            }
            for (let alternativeIndex = 0; alternativeIndex < result.length; alternativeIndex += 1) {
              const alternative = result[alternativeIndex];
              if (alternative?.transcript) {
                alternatives.push({ transcript: alternative.transcript, confidence: alternative.confidence });
              }
            }
          }
          segmentInterimTranscript = interimTranscript;
          const confidenceValues = alternatives.map((alternative) => alternative.confidence)
            .filter((confidence): confidence is number => confidence != null);
          onUpdate({
            transcript: currentTranscript(),
            alternatives,
            confidence: confidenceValues.length > 0
              ? confidenceValues.reduce((sum, confidence) => sum + confidence, 0) / confidenceValues.length
              : undefined,
          });
        };
        recognition.onerror = (event) => {
          // Browsers can emit these while ending one recognition segment. The
          // `onend` handler starts the next segment while the user is still
          // listening, so a pause must not discard the transcript.
          if (stopped || event.error === 'aborted' || event.error === 'no-speech' || event.error === 'network') {
            return;
          }
          stopped = true;
          if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
            onError('Microphone permission was denied.');
          } else if (event.error === 'audio-capture') {
            onError('No microphone was found. Check the microphone and try again.');
          } else {
            onError('Voice capture failed. Please try again.');
          }
        };
        recognition.onend = () => {
          recognition = null;
          commitSegment();
          if (stopped) {
            onEnd?.();
            return;
          }
          // Browsers often end recognition after a pause even with continuous=true.
          restartTimer = window.setTimeout(() => {
            restartTimer = undefined;
            begin();
          }, 150);
        };
        try {
          recognition.start();
        } catch {
          stopped = true;
          onError('Voice capture could not start. Please try again.');
        }
      };

      begin();
      return {
        stop: () => {
          stopped = true;
          if (restartTimer != null) {
            window.clearTimeout(restartTimer);
            restartTimer = undefined;
          }
          if (recognition) {
            recognition.stop();
          } else {
            commitSegment();
            onEnd?.();
          }
        },
      };
    },
  };
}
