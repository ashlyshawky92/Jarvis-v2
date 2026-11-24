interface HonorSetupViewProps {
  onDownloadZip: () => void;
}

export function HonorSetupView({ onDownloadZip }: HonorSetupViewProps) {
  return (
    <div className="space-y-6">
      <section className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-6 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h2 className="font-display text-lg font-semibold text-slate-100">
              Build &amp; Install on HONOR 400 Pro (Phone-Only Workflow)
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              Target Hardware: HONOR 400 Pro · DNP-NX9 · Snapdragon 8 Gen 3 · 12GB RAM · 512GB
            </p>
          </div>
          <button
            type="button"
            onClick={onDownloadZip}
            className="px-4 py-2 rounded-lg bg-cyan-400 text-slate-950 text-xs font-semibold hover:bg-cyan-300 whitespace-nowrap shrink-0 cursor-pointer"
          >
            Download Android ZIP
          </button>
        </div>

        <ol className="space-y-3 text-sm text-slate-300 list-decimal list-inside">
          <li>
            Tap <strong className="text-slate-100">Download Android ZIP</strong> to save{' '}
            <code className="text-cyan-300">jarvis-android-phase1.zip</code> on your phone.
          </li>
          <li>
            Create a private GitHub repository using GitHub Mobile or your browser, and upload the extracted files
            (making sure <code className="text-cyan-300">.github/workflows/build-apk.yml</code> is included).
          </li>
          <li>
            In GitHub Repo Settings &rarr; Secrets and variables &rarr; Actions, add{' '}
            <code className="text-cyan-300">JARVIS_BACKEND_URL</code> pointing to this app&apos;s deployed URL (no Gemini
            API key is ever compiled into the APK).
          </li>
          <li>
            Open the <strong className="text-slate-100">Actions</strong> tab on GitHub &rarr; run{' '}
            <strong className="text-slate-100">Build JARVIS Android APK</strong> &rarr; download{' '}
            <code className="text-cyan-300">JARVIS-Phase1-Debug-APK</code> and install it on your HONOR 400 Pro.
          </li>
        </ol>
      </section>

      <section className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-6 space-y-3">
          <h3 className="font-display text-base font-semibold text-slate-100">
            Manual Permissions (Phase 1)
          </h3>
          <ul className="space-y-2 text-xs text-slate-300 leading-relaxed">
            <li>
              <strong className="text-cyan-300">RECORD_AUDIO:</strong> Required when you tap the microphone button to
              speak Egyptian Arabic commands.
            </li>
            <li>
              <strong className="text-cyan-300">POST_NOTIFICATIONS:</strong> Required on Android 13+ for assistant
              status and upcoming Phase 5 reminders.
            </li>
            <li>
              <strong className="text-slate-200">Deferred to Phases 3–4:</strong> Contacts, Call Phone, SMS,
              Notification Listener, and Accessibility Service are intentionally NOT requested yet until their modules
              are built.
            </li>
          </ul>
        </div>

        <div className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-6 space-y-3">
          <h3 className="font-display text-base font-semibold text-slate-100">
            HONOR MagicOS Background &amp; Voice Tips
          </h3>
          <ul className="space-y-2 text-xs text-slate-300 leading-relaxed">
            <li>
              <strong className="text-cyan-300">App Launch (Battery):</strong> In MagicOS Settings &rarr; Battery &rarr;
              App Launch, switch JARVIS from Automatic to Manual (Allow Auto-launch, Secondary launch, Run in
              background) before Phase 2 wake-word testing.
            </li>
            <li>
              <strong className="text-cyan-300">Google Speech Services:</strong> Ensure Arabic (Egypt) offline speech
              pack is downloaded in Android Settings &rarr; System &rarr; Languages &rarr; Speech recognition &amp;
              synthesis for offline STT fallback.
            </li>
          </ul>
        </div>
      </section>
    </div>
  );
}
