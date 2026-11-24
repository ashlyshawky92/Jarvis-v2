import { useEffect, useRef, useState } from 'react';
import {
  Battery,
  Mic,
  MicOff,
  Plus,
  Send,
  Square,
  Trash2,
  Volume2,
  Wifi,
  WifiOff,
} from 'lucide-react';
import type { AssistantState, ChatTurn, HealthStatus } from '../types/jarvis';
import { JarvisOrbCanvas } from './JarvisOrbCanvas';

interface BrainTesterViewProps {
  health: HealthStatus | null;
}

interface SpeechRecognitionInstance {
  lang: string;
  interimResults: boolean;
  maxAlternatives: number;
  start: () => void;
  stop: () => void;
  onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
}

const SAMPLE_PROMPTS = [
  'يا جارفيس، صباح الخير',
  'شوفلي حالة البطارية والنت',
  'احفظ في الذاكرة إن اسمي أشلي',
  'اتصل بأحمد',
  'افتح أنغامي',
];

const STATE_LABELS: Record<AssistantState, string> = {
  STANDBY: 'في الانتظار (Standby)',
  LISTENING: 'بسمعك يا فندم... (Listening)',
  THINKING: 'بفكر... (Thinking)',
  EXECUTING: 'تحليل الأمر... (Planning)',
  SPEAKING: 'بتكلم... (Speaking)',
  OFFLINE: 'غير متصل بالإنترنت (Offline)',
  ERROR: 'حصلت مشكلة (Error)',
};

export function BrainTesterView({ health }: BrainTesterViewProps) {
  const [assistantState, setAssistantState] = useState<AssistantState>(
    typeof navigator !== 'undefined' && !navigator.onLine ? 'OFFLINE' : 'STANDBY'
  );
  const [isOnline, setIsOnline] = useState<boolean>(
    typeof navigator !== 'undefined' ? navigator.onLine : true
  );
  const [batteryPct, setBatteryPct] = useState<number | null>(null);
  const [selectedModel, setSelectedModel] = useState<'gemini-4-hydrogen' | 'gemini-3.1-flash-lite'>(
    'gemini-4-hydrogen'
  );
  const [speechRate, setSpeechRate] = useState<number>(1.0);
  const [speechPitch, setSpeechPitch] = useState<number>(0.92);
  const [autoSpeak, setAutoSpeak] = useState<boolean>(true);
  const [input, setInput] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [memoryEnabled, setMemoryEnabled] = useState<boolean>(true);
  const [memories, setMemories] = useState<Record<string, string>>({});
  const [newMemKey, setNewMemKey] = useState('');
  const [newMemVal, setNewMemVal] = useState('');

  const recognitionRef = useRef<SpeechRecognitionInstance | null>(null);

  const webSpeechSupported =
    typeof window !== 'undefined' &&
    Boolean(
      (window as unknown as Record<string, unknown>).SpeechRecognition ||
        (window as unknown as Record<string, unknown>).webkitSpeechRecognition
    );

  const ttsSupported = typeof window !== 'undefined' && 'speechSynthesis' in window;

  useEffect(() => {
    const onOnline = () => {
      setIsOnline(true);
      setAssistantState((prev) => (prev === 'OFFLINE' ? 'STANDBY' : prev));
    };
    const onOffline = () => {
      setIsOnline(false);
      setAssistantState('OFFLINE');
    };
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);

    const nav = navigator as unknown as {
      getBattery?: () => Promise<{ level: number; addEventListener: (e: string, cb: () => void) => void }>;
    };
    if (nav.getBattery) {
      nav
        .getBattery()
        .then((bat) => {
          setBatteryPct(Math.round(bat.level * 100));
          bat.addEventListener('levelchange', () => setBatteryPct(Math.round(bat.level * 100)));
        })
        .catch(() => {});
    }

    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  const stopAudio = () => {
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch {
        // ignore
      }
      recognitionRef.current = null;
    }
    if (ttsSupported) {
      window.speechSynthesis.cancel();
    }
    setAssistantState(isOnline ? 'STANDBY' : 'OFFLINE');
  };

  const speakArabic = (text: string) => {
    if (!ttsSupported || !text.trim()) {
      setAssistantState(isOnline ? 'STANDBY' : 'OFFLINE');
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    const voices = window.speechSynthesis.getVoices();
    const arEg =
      voices.find((v) => v.lang.toLowerCase().startsWith('ar-eg')) ||
      voices.find((v) => v.lang.toLowerCase().startsWith('ar'));
    if (arEg) utterance.voice = arEg;
    utterance.lang = 'ar-EG';
    utterance.rate = speechRate;
    utterance.pitch = speechPitch;
    utterance.onstart = () => setAssistantState('SPEAKING');
    utterance.onend = () => setAssistantState(isOnline ? 'STANDBY' : 'OFFLINE');
    utterance.onerror = () => setAssistantState(isOnline ? 'STANDBY' : 'OFFLINE');
    window.speechSynthesis.speak(utterance);
  };

  const sendCommand = async (rawText: string) => {
    const clean = rawText.trim();
    if (!clean) return;

    if (ttsSupported) window.speechSynthesis.cancel();
    setErrorMsg(null);

    const userTurn: ChatTurn = {
      id: `${Date.now()}-u`,
      role: 'user',
      text: clean,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };
    setTurns((prev) => [...prev, userTurn]);
    setInput('');

    if (!navigator.onLine) {
      setAssistantState('OFFLINE');
      const offlineReply = 'الاتصال بالإنترنت مش متاح حالياً يا فندم.';
      setTurns((prev) => [
        ...prev,
        {
          id: `${Date.now()}-a`,
          role: 'assistant',
          text: offlineReply,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
      if (autoSpeak) speakArabic(offlineReply);
      return;
    }

    setAssistantState('THINKING');
    const started = performance.now();

    try {
      const resp = await fetch('/api/jarvis/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: clean,
          model: selectedModel,
          history: turns.slice(-8).map((t) => ({ role: t.role, text: t.text })),
          memories: memoryEnabled ? memories : {},
        }),
      });
      const data = await resp.json();
      if (!resp.ok || data.error) {
        throw new Error(data.error || `HTTP ${resp.status}`);
      }

      const latencyMs = Math.round(performance.now() - started);
      if (
        memoryEnabled &&
        data.plannedAction?.actionId === 'save_memory_note' &&
        data.plannedAction?.parameters
      ) {
        const entries = Object.entries(data.plannedAction.parameters as Record<string, string>);
        if (entries.length > 0) {
          const [k, v] = entries[0];
          setMemories((prev) => ({ ...prev, [k]: v }));
        }
      }

      const botTurn: ChatTurn = {
        id: `${Date.now()}-a`,
        role: 'assistant',
        text: data.reply,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        modelUsed: data.modelUsed,
        latencyMs,
        plannedAction: data.plannedAction,
      };
      setTurns((prev) => [...prev, botTurn]);

      if (autoSpeak) {
        speakArabic(data.reply);
      } else {
        setAssistantState('STANDBY');
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Request failed';
      setErrorMsg(msg);
      setAssistantState('ERROR');
    }
  };

  const toggleMic = () => {
    if (!webSpeechSupported) {
      setErrorMsg('Web Speech API is not supported in this browser. Use Chrome on Android or type below.');
      setAssistantState('ERROR');
      return;
    }

    if (assistantState === 'LISTENING') {
      stopAudio();
      return;
    }

    if (ttsSupported) window.speechSynthesis.cancel();
    setErrorMsg(null);

    const SpeechCtor = (
      (window as unknown as Record<string, unknown>).SpeechRecognition ||
      (window as unknown as Record<string, unknown>).webkitSpeechRecognition
    ) as new () => SpeechRecognitionInstance;

    const rec = new SpeechCtor();
    rec.lang = 'ar-EG';
    rec.interimResults = false;
    rec.maxAlternatives = 1;

    rec.onresult = (event) => {
      const transcript = event.results?.[0]?.[0]?.transcript?.trim() ?? '';
      recognitionRef.current = null;
      if (transcript) {
        void sendCommand(transcript);
      } else {
        setAssistantState(isOnline ? 'STANDBY' : 'OFFLINE');
      }
    };

    rec.onerror = (event) => {
      recognitionRef.current = null;
      setErrorMsg(`Microphone / Speech recognition error: ${event.error}`);
      setAssistantState('ERROR');
    };

    rec.onend = () => {
      recognitionRef.current = null;
      setAssistantState((prev) => (prev === 'LISTENING' ? 'STANDBY' : prev));
    };

    recognitionRef.current = rec;
    setAssistantState('LISTENING');
    rec.start();
  };

  const addManualMemory = () => {
    const k = newMemKey.trim();
    const v = newMemVal.trim();
    if (!k || !v) return;
    setMemories((prev) => ({ ...prev, [k]: v }));
    setNewMemKey('');
    setNewMemVal('');
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
      {/* Left/Center: Live AI Brain & Voice HUD */}
      <section className="lg:col-span-8 bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-6 space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#1A2B4C] pb-4">
          <div>
            <h2 className="font-display text-lg font-semibold text-slate-100">
              Egyptian Arabic AI Brain Console
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              Live server-side Gemini endpoint (/api/jarvis/chat) · Browser microphone &amp; speech test
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-4 text-xs text-slate-300 font-mono tabular-nums">
            <span className="inline-flex items-center gap-1.5">
              {isOnline ? (
                <Wifi className="w-3.5 h-3.5 text-emerald-400" />
              ) : (
                <WifiOff className="w-3.5 h-3.5 text-amber-400" />
              )}
              {isOnline ? 'Online' : 'Offline'}
            </span>
            <span aria-hidden="true">·</span>
            <span className="inline-flex items-center gap-1.5">
              {webSpeechSupported ? (
                <Mic className="w-3.5 h-3.5 text-cyan-400" />
              ) : (
                <MicOff className="w-3.5 h-3.5 text-slate-500" />
              )}
              {webSpeechSupported ? 'Web STT Ready' : 'Web STT Unavailable'}
            </span>
            <span aria-hidden="true">·</span>
            <span className="inline-flex items-center gap-1.5">
              <Battery className="w-3.5 h-3.5 text-cyan-400" />
              {batteryPct !== null ? `${batteryPct}%` : 'N/A'}
            </span>
          </div>
        </div>

        {/* Orb & Primary Controls */}
        <div className="flex flex-col items-center py-2 space-y-4">
          <JarvisOrbCanvas state={assistantState} />
          <div className="text-center space-y-1">
            <p className="text-sm font-medium text-cyan-300">{STATE_LABELS[assistantState]}</p>
            {health && !health.geminiConfigured && (
              <p className="text-xs text-amber-400">
                GEMINI_API_KEY is not configured in Secrets yet. Add it to test live responses.
              </p>
            )}
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={toggleMic}
              className={`inline-flex items-center gap-2 px-5 py-2.5 rounded-lg text-sm font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                assistantState === 'LISTENING'
                  ? 'bg-red-500 text-white hover:bg-red-600'
                  : 'bg-cyan-400 text-slate-950 hover:bg-cyan-300'
              }`}
            >
              <Mic className="w-4 h-4" />
              {assistantState === 'LISTENING' ? 'Stop Listening' : 'Speak (ar-EG)'}
            </button>

            {(assistantState === 'SPEAKING' || assistantState === 'LISTENING') && (
              <button
                type="button"
                onClick={stopAudio}
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium border border-[#284270] text-slate-200 hover:bg-slate-800 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
              >
                <Square className="w-4 h-4" />
                Stop
              </button>
            )}

            <button
              type="button"
              onClick={() => speakArabic('تحت أمرك يا فندم. صوت جارفيس جاهز.')}
              disabled={!ttsSupported}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium border border-[#284270] text-slate-200 hover:bg-slate-800 disabled:opacity-40 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
            >
              <Volume2 className="w-4 h-4 text-cyan-400" />
              Test Voice
            </button>
          </div>
        </div>

        {/* Sample Egyptian Arabic Prompts */}
        <div className="flex flex-wrap items-center gap-2">
          {SAMPLE_PROMPTS.map((sample) => (
            <button
              key={sample}
              type="button"
              onClick={() => void sendCommand(sample)}
              dir="rtl"
              className="px-3 py-1.5 text-xs rounded-lg bg-[#111C33] border border-[#1E3254] text-slate-200 hover:border-cyan-400/60 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
            >
              {sample}
            </button>
          ))}
        </div>

        {/* Error Display */}
        {errorMsg && (
          <div className="p-3 rounded-lg bg-red-950/50 border border-red-700/60 text-xs text-red-200">
            {errorMsg}
          </div>
        )}

        {/* Conversation Log */}
        <div className="space-y-3 max-h-[340px] overflow-y-auto pr-1">
          {turns.length === 0 ? (
            <div className="p-6 text-center border border-dashed border-[#1E3254] rounded-lg text-sm text-slate-400">
              Send an Egyptian Arabic command below or tap Speak to test Gemini intent planning and structured action allowlisting.
            </div>
          ) : (
            turns.map((turn) => (
              <div
                key={turn.id}
                className={`p-4 rounded-lg border ${
                  turn.role === 'user'
                    ? 'bg-[#101B33] border-[#21365C]'
                    : 'bg-[#091020] border-[#182B4B]'
                }`}
              >
                <div className="flex items-center justify-between text-xs text-slate-400 mb-1.5 font-mono tabular-nums">
                  <span>{turn.role === 'user' ? 'User' : 'J.A.R.V.I.S.'}</span>
                  <span>
                    {turn.timestamp}
                    {turn.modelUsed ? ` · ${turn.modelUsed}` : ''}
                    {turn.latencyMs ? ` · ${turn.latencyMs}ms` : ''}
                  </span>
                </div>
                <p dir="rtl" className="text-sm text-slate-100 leading-relaxed">
                  {turn.text}
                </p>

                {turn.plannedAction && (
                  <div className="mt-3 pt-3 border-t border-[#1A2B4C] text-xs font-mono text-cyan-300 space-y-1">
                    <div>
                      Planned Action: {turn.plannedAction.actionId} · Phase {turn.plannedAction.scheduledPhase} ·{' '}
                      {turn.plannedAction.implementedInPhase1
                        ? 'Implemented in Phase 1'
                        : 'Scheduled for Later Phase (Not Executed in Web)'}
                    </div>
                    <div className="text-slate-400">
                      Confirmation Required: {turn.plannedAction.requiresConfirmation ? 'Yes' : 'No'} · Params:{' '}
                      {JSON.stringify(turn.plannedAction.parameters)}
                    </div>
                  </div>
                )}
              </div>
            ))
          )}
        </div>

        {/* Text Input */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void sendCommand(input);
          }}
          className="flex items-center gap-2"
        >
          <input
            type="text"
            dir="rtl"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="اكتب أمرك بالمصري هنا (مثال: يا جارفيس شوفلي البطارية)..."
            className="flex-1 bg-[#080E1C] border border-[#1E3254] rounded-lg px-4 py-2.5 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:border-cyan-400"
          />
          <button
            type="submit"
            disabled={!input.trim() || assistantState === 'THINKING'}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-cyan-400 text-slate-950 font-semibold text-sm hover:bg-cyan-300 disabled:opacity-40 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
          >
            <Send className="w-4 h-4" />
            Send
          </button>
        </form>
      </section>

      {/* Right Column: AI Config, Voice Profile & Local Memory */}
      <aside className="lg:col-span-4 space-y-6">
        <div className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-5 space-y-4">
          <h3 className="font-display text-sm font-semibold text-slate-100">AI Brain &amp; Voice Profile</h3>

          <div className="space-y-1.5">
            <label className="text-xs text-slate-400 block">Gemini Model</label>
            <select
              value={selectedModel}
              onChange={(e) => setSelectedModel(e.target.value as 'gemini-4-hydrogen' | 'gemini-3.1-flash-lite')}
              className="w-full bg-[#080E1C] border border-[#1E3254] rounded-lg px-3 py-2 text-xs text-slate-100"
            >
              <option value="gemini-4-hydrogen">gemini-4-hydrogen (Default)</option>
              <option value="gemini-3.1-flash-lite">gemini-3.1-flash-lite (Low Latency)</option>
            </select>
          </div>

          <div className="space-y-2 pt-2 border-t border-[#1A2B4C]">
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-400">Active Profile</span>
              <span className="text-cyan-300 font-mono">JARVIS (ar-EG)</span>
            </div>

            <div>
              <div className="flex justify-between text-xs text-slate-400 mb-1 font-mono tabular-nums">
                <span>Speed</span>
                <span>{speechRate.toFixed(2)}x</span>
              </div>
              <input
                type="range"
                min="0.7"
                max="1.4"
                step="0.05"
                value={speechRate}
                onChange={(e) => setSpeechRate(Number(e.target.value))}
                className="w-full accent-cyan-400"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs text-slate-400 mb-1 font-mono tabular-nums">
                <span>Pitch</span>
                <span>{speechPitch.toFixed(2)}</span>
              </div>
              <input
                type="range"
                min="0.75"
                max="1.25"
                step="0.02"
                value={speechPitch}
                onChange={(e) => setSpeechPitch(Number(e.target.value))}
                className="w-full accent-cyan-400"
              />
            </div>

            <label className="flex items-center justify-between text-xs text-slate-300 pt-1 cursor-pointer">
              <span>Speak Replies Automatically</span>
              <input
                type="checkbox"
                checked={autoSpeak}
                onChange={(e) => setAutoSpeak(e.target.checked)}
                className="accent-cyan-400"
              />
            </label>
          </div>
        </div>

        {/* User-Approved Memory Panel */}
        <div className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-display text-sm font-semibold text-slate-100">User-Approved Memory</h3>
            <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
              <span>Enabled</span>
              <input
                type="checkbox"
                checked={memoryEnabled}
                onChange={(e) => setMemoryEnabled(e.target.checked)}
                className="accent-cyan-400"
              />
            </label>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <input
              type="text"
              value={newMemKey}
              onChange={(e) => setNewMemKey(e.target.value)}
              placeholder="Key (e.g. name)"
              className="bg-[#080E1C] border border-[#1E3254] rounded-lg px-2.5 py-1.5 text-xs text-slate-100"
            />
            <div className="flex gap-1.5">
              <input
                type="text"
                value={newMemVal}
                onChange={(e) => setNewMemVal(e.target.value)}
                placeholder="Value"
                className="w-full bg-[#080E1C] border border-[#1E3254] rounded-lg px-2.5 py-1.5 text-xs text-slate-100"
              />
              <button
                type="button"
                onClick={addManualMemory}
                aria-label="Add Memory"
                className="px-2.5 rounded-lg bg-cyan-400 text-slate-950 hover:bg-cyan-300 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          <div className="space-y-2 max-h-48 overflow-y-auto">
            {Object.keys(memories).length === 0 ? (
              <p className="text-xs text-slate-500">No saved preferences yet.</p>
            ) : (
              Object.entries(memories).map(([k, v]) => (
                <div
                  key={k}
                  className="flex items-center justify-between text-xs bg-[#080E1C] border border-[#1A2B4C] rounded-lg px-3 py-2"
                >
                  <span className="text-slate-200 truncate">
                    <strong className="text-cyan-300">{k}:</strong> {v}
                  </span>
                  <button
                    type="button"
                    onClick={() =>
                      setMemories((prev) => {
                        const copy = { ...prev };
                        delete copy[k];
                        return copy;
                      })
                    }
                    aria-label={`Delete ${k}`}
                    className="text-slate-400 hover:text-red-400 ml-2 cursor-pointer"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))
            )}
          </div>

          {turns.length > 0 && (
            <button
              type="button"
              onClick={() => setTurns([])}
              className="w-full py-2 text-xs font-medium text-slate-300 border border-[#1E3254] rounded-lg hover:bg-slate-800 transition-colors cursor-pointer"
            >
              Clear Conversation History
            </button>
          )}
        </div>
      </aside>
    </div>
  );
}
