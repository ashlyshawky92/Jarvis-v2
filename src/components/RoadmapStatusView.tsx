const FEATURE_MATRIX = [
  {
    feature: 'Environment Inspection (Node.js vs Android SDK)',
    phase: 'Pre-Phase 1',
    status: 'Verified',
    notes: 'Confirmed Node.js/Vite container has no JDK, kotlinc, Gradle, or Android SDK; cloud APK CI workflow added.',
  },
  {
    feature: 'Native Kotlin + Jetpack Compose Project & Theme',
    phase: 'Phase 1',
    status: 'Generated (Uncompiled)',
    notes: 'Complete Gradle Kotlin DSL, Material 3 dark cyan/navy HUD, animated JarvisOrb Canvas.',
  },
  {
    feature: 'Gemini AI Brain (Egyptian Arabic + Allowlist Planner)',
    phase: 'Phase 1',
    status: 'Implemented & Tested (Server)',
    notes: 'Server-side @google/genai endpoint (/api/jarvis/chat) + Android GeminiBackendAiProvider.kt.',
  },
  {
    feature: 'Android STT (ar-EG) & JARVIS Voice Profile TTS',
    phase: 'Phase 1',
    status: 'Generated (Untested on Device)',
    notes: 'SpeechToTextEngine.kt + TextToSpeechEngine.kt with interruption & rate/pitch tuning.',
  },
  {
    feature: 'Local User-Approved Memory (DataStore)',
    phase: 'Phase 1',
    status: 'Generated (Untested on Device)',
    notes: 'MemoryRepository.kt with view, edit, delete, clear, and opt-out controls.',
  },
  {
    feature: 'Runtime Device & MagicOS Detection',
    phase: 'Phase 1',
    status: 'Generated (Untested on Device)',
    notes: 'DeviceRuntimeInfo.kt reads Build.MANUFACTURER, MODEL, SDK_INT, and ro.build.version.magic.',
  },
  {
    feature: 'Local Always-On Wake Word ("JARVIS")',
    phase: 'Phase 2',
    status: 'Not Implemented Yet',
    notes: 'Requires SDK/license verification (e.g. Porcupine) & HONOR MagicOS foreground service testing.',
  },
  {
    feature: 'App Launching, Contacts, Phone Calls, SMS',
    phase: 'Phase 3',
    status: 'Planned in Allowlist',
    notes: 'Blocked cleanly in ActionRegistry until Phase 3 permissions and handlers are built.',
  },
  {
    feature: 'WhatsApp, Messenger, Telegram Notifications',
    phase: 'Phase 4',
    status: 'Planned in Allowlist',
    notes: 'Will use NotificationListenerService & supported reply intents with confirmation.',
  },
  {
    feature: 'Reminders, Timers & Multi-step Automation',
    phase: 'Phase 5',
    status: 'Planned in Allowlist',
    notes: 'Will use AlarmManager / WorkManager with local task history.',
  },
];

const TESTING_CHECKLIST = [
  '1. Application startup on HONOR 400 Pro (DNP-NX9)',
  '2. Runtime RECORD_AUDIO & POST_NOTIFICATIONS permission flow',
  '3. Egyptian Arabic speech recognition (SpeechRecognizer ar-EG)',
  '4. JARVIS voice profile TTS clarity & interruption stop()',
  '5. Gemini backend connection & API error recovery',
  '6. Multi-turn Egyptian Arabic conversation context',
  '7. ActionRegistry allowlist rejection for unimplemented/unknown actions',
  '8. Local DataStore memory persistence & deletion',
  '9. Offline detection & battery status local execution',
  '10. HONOR MagicOS battery optimization exclusion behavior',
];

export function RoadmapStatusView() {
  return (
    <div className="space-y-6">
      <section className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-6 space-y-3">
        <h2 className="font-display text-lg font-semibold text-slate-100">
          Environment Inspection &amp; Architecture Report
        </h2>
        <p className="text-sm text-slate-300 leading-relaxed">
          Runtime inspection confirmed this AI Studio container runs Node.js v22, Vite, and Express without Java JDK,
          Kotlin compiler, Gradle, or Android SDK installed. Rather than disguising a web app as native Android or
          claiming unverified APK builds, this workspace delivers both: genuine native Kotlin/Jetpack Compose Phase 1
          source files (with a GitHub Actions workflow to compile the APK from your phone) and a live server-side
          Gemini Egyptian Arabic AI Brain backend.
        </p>
      </section>

      <section className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl overflow-hidden">
        <div className="px-6 py-4 border-b border-[#1A2B4C]">
          <h3 className="font-display text-base font-semibold text-slate-100">
            Feature Implementation &amp; Verification Status
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
          Phase 1 On-Device Testing Checklist (HONOR 400 Pro)
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
