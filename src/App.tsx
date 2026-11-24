import { useEffect, useState } from 'react';
import JSZip from 'jszip';
import { Download } from 'lucide-react';
import { AndroidSourceView } from './components/AndroidSourceView';
import { BrainTesterView } from './components/BrainTesterView';
import { HonorSetupView } from './components/HonorSetupView';
import { RoadmapStatusView } from './components/RoadmapStatusView';
import type { AndroidProjectPayload, HealthStatus } from './types/jarvis';

type ActiveTab = 'brain' | 'source' | 'roadmap' | 'honor';

export default function App() {
  const [activeTab, setActiveTab] = useState<ActiveTab>('brain');
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [androidProject, setAndroidProject] = useState<AndroidProjectPayload | null>(null);
  const [loadingProject, setLoadingProject] = useState<boolean>(true);
  const [exporting, setExporting] = useState<boolean>(false);

  useEffect(() => {
    fetch('/api/jarvis/health')
      .then((r) => r.json())
      .then((data: HealthStatus) => setHealth(data))
      .catch(() => {});

    fetch('/api/android-project')
      .then((r) => r.json())
      .then((data: AndroidProjectPayload) => setAndroidProject(data))
      .catch(() => {})
      .finally(() => setLoadingProject(false));
  }, []);

  const handleDownloadZip = async () => {
    if (!androidProject || androidProject.files.length === 0 || exporting) return;
    setExporting(true);
    try {
      const zip = new JSZip();
      for (const file of androidProject.files) {
        zip.file(file.path, file.content);
      }
      const blob = await zip.generateAsync({ type: 'blob' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = 'jarvis-android-phase1.1.zip';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#060B16] text-slate-100 flex flex-col">
      {/* 3-Zone Top Bar */}
      <header className="border-b border-[#182846] bg-[#080F1E]">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-8">
          <a
            href="#top"
            onClick={(e) => {
              e.preventDefault();
              setActiveTab('brain');
            }}
            className="font-display text-lg font-bold tracking-[0.16em] text-cyan-400 whitespace-nowrap shrink-0"
          >
            J.A.R.V.I.S.
          </a>

          <nav className="hidden md:flex items-center gap-6 text-sm font-medium">
            {(
              [
                ['brain', 'Brain Tester'],
                ['source', 'Android Source'],
                ['roadmap', 'Architecture & Phases'],
                ['honor', 'HONOR 400 Pro Setup'],
              ] as const
            ).map(([id, label]) => (
              <button
                key={id}
                type="button"
                onClick={() => setActiveTab(id)}
                className={`py-1 border-b-2 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                  activeTab === id
                    ? 'border-cyan-400 text-slate-100'
                    : 'border-transparent text-slate-400 hover:text-slate-200'
                }`}
              >
                {label}
              </button>
            ))}
          </nav>

          <button
            type="button"
            onClick={() => void handleDownloadZip()}
            disabled={!androidProject || exporting}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-cyan-400 text-slate-950 text-xs font-semibold hover:bg-cyan-300 disabled:opacity-50 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
          >
            <Download className="w-3.5 h-3.5" />
            {exporting ? 'Preparing...' : 'Download Android ZIP'}
          </button>
        </div>

        {/* Mobile Nav Strip */}
        <div className="md:hidden border-t border-[#14223D] px-4 py-2 flex items-center gap-2 overflow-x-auto">
          {(
            [
              ['brain', 'Brain Tester'],
              ['source', 'Android Source'],
              ['roadmap', 'Phases'],
              ['honor', 'HONOR Setup'],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              onClick={() => setActiveTab(id)}
              className={`px-3 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer ${
                activeTab === id ? 'bg-[#132240] text-cyan-300' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6">
        {activeTab === 'brain' && <BrainTesterView health={health} />}
        {activeTab === 'source' && (
          <AndroidSourceView
            project={androidProject}
            loading={loadingProject}
            onDownloadZip={() => void handleDownloadZip()}
          />
        )}
        {activeTab === 'roadmap' && <RoadmapStatusView />}
        {activeTab === 'honor' && <HonorSetupView onDownloadZip={() => void handleDownloadZip()} />}
      </main>
    </div>
  );
}
