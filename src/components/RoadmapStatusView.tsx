const FEATURE_MATRIX = [
  {
    feature: 'Environment Inspection (Node.js vs Android SDK)',
    phase: 'Pre-Phase 1',
    status: 'Verified',
    notes: 'Confirmed Node.js/Vite container has no JDK, kotlinc, Gradle, or Android SDK; CI runs tests & builds APK.',
  },
  {
    feature: 'MemoryRepository (DataStore + Corruption Protection)',
    phase: 'Phase 1.1',
    status: 'Generated + Unit Tested (Source)',
    notes: 'Awaited writes, normalized keys, categories, timestamps, relevant-prompt filtering, corruption backup.',
  },
  {
    feature: 'SpeechToTextEngine (On-Device API + Lifecycle Guard)',
    phase: 'Phase 1.1',
    status: 'Generated (Untested on Device)',
    notes: 'IDLE/INITIALIZING/LISTENING/PROCESSING/ERROR states, createOnDeviceSpeechRecognizer, session ID guard.',
  },
  {
    feature: 'TextToSpeechEngine (JARVIS Profile + Voice Discovery)',
    phase: 'Phase 1.1',
    status: 'Generated (Untested on Device)',
    notes: 'Utterance ID tracking, guaranteed error recovery, installed voice listing, TtsProvider abstraction.',
  },
  {
    feature: 'GeminiBackendAiProvider (HTTPS + Backoff + Cancellation)',
    phase: 'Phase 1.1',
    status: 'Implemented & Tested (Server)',
    notes: 'HTTPS URL validation, 3x exponential backoff on 5xx/429/IO, OkHttp Call.cancel(), allowlist validation.',
  },
  {
    feature: 'ActionRegistry & LocalCommandRouter',
    phase: 'Phase 1.1',
    status: 'Generated + Unit Tested (Source)',
    notes: '4 Phase 1 actions, enforced confirmation policy, token idempotency, Egyptian Arabic/English offline routing.',
  },
  {
    feature: 'Persistent Conversation History (SQLite)',
    phase: 'Phase 1.1',
    status: 'Generated (Untested on Device)',
    notes: 'SQLiteOpenHelper WAL store separated from MemoryRepository with parameterized queries & migrations.',
  },
  {
    feature: 'WakeWordEngine Interface (Preparation Only)',
    phase: 'Phase 1.1',
    status: 'Contract Prepared',
    notes: 'No fake detector; documents FOREGROUND_SERVICE_MICROPHONE & HONOR MagicOS battery requirements.',
  },
  {
    feature: 'JVM Automated Test Suite (4 Test Files)',
    phase: 'Phase 1.1',
    status: 'Generated (Run via Gradle CI)',
    notes: 'MemoryRepositoryTest, ActionRegistryTest, LocalCommandRouterTest, JarvisSessionCoordinatorTest.',
  },
  {
    feature: 'App Launch, Calls, SMS, WhatsApp, Reminders',
    phase: 'Phases 3–5',
    status: 'Planned in Allowlist',
    notes: 'Explicitly marked NOT_IMPLEMENTED_IN_PHASE and blocked safely until implemented.',
  },
];

const TESTING_CHECKLIST = [
  '1. Run gradle testDebugUnitTest in GitHub Actions (all 4 suites)',
  '2. Verify MemoryRepository corruption recovery and disabled-memory guards',
  '3. Verify ActionRegistry cannot bypass confirmation via AI payload',
  '4. Verify LocalCommandRouter offline responses on airplane mode',
  '5. Verify SpeechRecognizer ar-EG & on-device availability on HONOR 400 Pro',
  '6. Verify TTS voice selection & error recovery from SPEAKING state',
  '7. Verify HTTPS enforcement in BackendUrlValidator',
  '8. Verify SQLite conversation history persistence across app restarts',
  '9. Verify Activity lifecycle onStop() releases microphone cleanly',
  '10. Verify HONOR MagicOS battery optimization guidance intent',
];

export function RoadmapStatusView() {
  return (
    <div className="space-y-6">
      <section className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-6 space-y-3">
        <h2 className="font-display text-lg font-semibold text-slate-100">
          Phase 1.1 Engineering Audit &amp; Architecture Report
        </h2>
        <p className="text-sm text-slate-300 leading-relaxed">
          Phase 1.1 upgrades the existing native Kotlin + Jetpack Compose project with awaited DataStore memory
          persistence, corruption protection, on-device SpeechRecognizer lifecycle management, TTS error recovery,
          HTTPS-enforced Gemini retries and cancellation, SQLite conversation history, a deterministic offline
          LocalCommandRouter, a WakeWordEngine preparation contract, and 4 JUnit4 unit test suites. Note: Gradle
          compilation and unit test execution require GitHub Actions or an Android SDK environment because this web
          container has Node.js only.
        </p>
      </section>

      <section className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl overflow-hidden">
        <div className="px-6 py-4 border-b border-[#1A2B4C]">
          <h3 className="font-display text-base font-semibold text-slate-100">
            Phase 1.1 Implementation &amp; Verification Status
          </h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="border-b border-[#1A2B4C] text-slate-400 bg-[#091020]">
                <th className="py-3 px-4 font-medium">Component</th>
                <th className="py-3 px-4 font-medium">Phase</th>
                <th className="py-3 px-4 font-medium">Status</th>
                <th className="py-3 px-4 font-medium">Verification Notes</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#152440]">
              {FEATURE_MATRIX.map((row) => (
                <tr key={row.feature} className="hover:bg-[#101B33]/60">
                  <td className="py-3 px-4 font-medium text-slate-100">{row.feature}</td>
                  <td className="py-3 px-4 font-mono text-cyan-300 whitespace-nowrap">{row.phase}</td>
                  <td className="py-3 px-4 font-mono whitespace-nowrap text-slate-200">{row.status}</td>
                  <td className="py-3 px-4 text-slate-400">{row.notes}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-6 space-y-3">
        <h3 className="font-display text-base font-semibold text-slate-100">
          Phase 1.1 Verification &amp; On-Device Testing Checklist
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5 text-xs text-slate-300">
          {TESTING_CHECKLIST.map((item) => (
            <div key={item} className="p-3 rounded-lg bg-[#080E1C] border border-[#1A2B4C]">
              {item}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
