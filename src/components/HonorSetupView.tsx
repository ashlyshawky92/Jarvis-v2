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
              Build &amp; Test Phase 1.1 APK on HONOR 400 Pro (Phone-Only)
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              Target Device: HONOR 400 Pro · DNP-NX9 · Snapdragon 8 Gen 3 · 12GB RAM · 512GB
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
            <code className="text-cyan-300">jarvis-android-phase1.1.zip</code> on your smartphone.
          </li>
          <li>
            Upload the extracted project to a private GitHub repository (ensuring{' '}
            <code className="text-cyan-300">.github/workflows/build-apk.yml</code> and{' '}
            <code className="text-cyan-300">.github/workflows/release-apk.yml</code> are present).
          </li>
          <li>
            In GitHub Repo Settings &rarr; Secrets and variables &rarr; Actions, add{' '}
            <code className="text-cyan-300">JARVIS_BACKEND_URL</code> pointing to your HTTPS backend URL (no Gemini
            API key is ever stored in the APK).
          </li>
          <li>
            Open the <strong className="text-slate-100">Actions</strong> tab &rarr; run{' '}
            <strong className="text-slate-100">Build JARVIS Debug APK &amp; Run Unit Tests</strong> &rarr; download{' '}
            <code className="text-cyan-300">JARVIS-Phase1.1-Debug-APK</code> and{' '}
            <code className="text-cyan-300">JARVIS-Unit-Test-Reports</code>.
          </li>
        </ol>
      </section>

      <section className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-6 space-y-3">
          <h3 className="font-display text-base font-semibold text-slate-100">
            Optional Signed Release APK Secrets
          </h3>
          <ul className="space-y-2 text-xs text-slate-300 leading-relaxed">
            <li>
              <code className="text-cyan-300">ANDROID_KEYSTORE_BASE64</code>: Base64-encoded JKS/PKCS12 keystore.
            </li>
            <li>
              <code className="text-cyan-300">ANDROID_KEYSTORE_PASSWORD</code> &amp;{' '}
              <code className="text-cyan-300">ANDROID_KEY_PASSWORD</code>: Keystore credentials.
            </li>
            <li>
              <code className="text-cyan-300">ANDROID_KEY_ALIAS</code>: Signing key alias used by{' '}
              <code className="text-cyan-300">release-apk.yml</code>.
            </li>
          </ul>
        </div>

        <div className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-6 space-y-3">
          <h3 className="font-display text-base font-semibold text-slate-100">
            HONOR MagicOS &amp; Offline Speech Setup
          </h3>
          <ul className="space-y-2 text-xs text-slate-300 leading-relaxed">
            <li>
              <strong className="text-cyan-300">App Launch (Battery):</strong> In MagicOS Settings &rarr; Battery &rarr;
              App Launch, switch JARVIS to Manual before Phase 2 wake-word testing.
            </li>
            <li>
              <strong className="text-cyan-300">Arabic Speech Packs:</strong> Download Arabic (Egypt) in Android Speech
              Recognition &amp; Synthesis settings for on-device STT &amp; offline TTS voice selection.
            </li>
          </ul>
        </div>
      </section>
    </div>
  );
}
