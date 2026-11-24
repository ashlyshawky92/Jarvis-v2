import { useMemo, useState } from 'react';
import { Check, Copy, Download, FileCode2, Search } from 'lucide-react';
import type { AndroidProjectPayload } from '../types/jarvis';

interface AndroidSourceViewProps {
  project: AndroidProjectPayload | null;
  loading: boolean;
  onDownloadZip: () => void;
}

export function AndroidSourceView({ project, loading, onDownloadZip }: AndroidSourceViewProps) {
  const [selectedPath, setSelectedPath] = useState<string>('app/src/main/java/com/jarvis/assistant/MainActivity.kt');
  const [search, setSearch] = useState('');
  const [copied, setCopied] = useState(false);

  const files = project?.files ?? [];

  const filteredFiles = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return files;
    return files.filter((f) => f.path.toLowerCase().includes(q));
  }, [files, search]);

  const activeFile = useMemo(() => {
    return files.find((f) => f.path === selectedPath) ?? filteredFiles[0] ?? null;
  }, [files, filteredFiles, selectedPath]);

  const handleCopy = async () => {
    if (!activeFile) return;
    try {
      await navigator.clipboard.writeText(activeFile.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // Clipboard may require HTTPS/user gesture
    }
  };

  if (loading) {
    return (
      <div className="bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-8 text-sm text-slate-400">
        Loading generated Kotlin and Jetpack Compose project files...
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
      {/* File List */}
      <aside className="lg:col-span-4 bg-[#0C1426] border border-[#1A2B4C] rounded-xl p-4 space-y-3">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="font-display text-sm font-semibold text-slate-100">Native Android Files</h2>
            <p className="text-xs text-slate-400 font-mono tabular-nums mt-0.5">
              {project?.fileCount ?? 0} files · Kotlin + Compose
            </p>
          </div>
          <button
            type="button"
            onClick={onDownloadZip}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-cyan-400 text-slate-950 text-xs font-semibold hover:bg-cyan-300 whitespace-nowrap shrink-0 cursor-pointer"
          >
            <Download className="w-3.5 h-3.5" />
            ZIP
          </button>
        </div>

        <div className="relative">
          <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter Kotlin / Gradle files..."
            className="w-full bg-[#080E1C] border border-[#1E3254] rounded-lg pl-8 pr-3 py-2 text-xs text-slate-100 placeholder:text-slate-500 focus:outline-none focus:border-cyan-400"
          />
        </div>

        <div className="space-y-1 max-h-[540px] overflow-y-auto">
          {filteredFiles.map((file) => {
            const active = activeFile?.path === file.path;
            return (
              <button
                key={file.path}
                type="button"
                onClick={() => setSelectedPath(file.path)}
                className={`w-full text-left px-3 py-2 rounded-lg text-xs font-mono transition-colors flex items-center justify-between gap-2 cursor-pointer ${
                  active
                    ? 'bg-[#132240] text-cyan-300 border border-cyan-500/40'
                    : 'text-slate-300 hover:bg-[#101C36] border border-transparent'
                }`}
              >
                <span className="truncate flex items-center gap-2">
                  <FileCode2 className="w-3.5 h-3.5 shrink-0 text-slate-400" />
                  <span className="truncate">{file.path}</span>
                </span>
                <span className="text-[11px] text-slate-500 tabular-nums shrink-0">
                  {(file.size / 1024).toFixed(1)}k
                </span>
              </button>
            );
          })}
        </div>
      </aside>

      {/* Code Viewer */}
      <section className="lg:col-span-8 bg-[#0C1426] border border-[#1A2B4C] rounded-xl overflow-hidden">
        <div className="flex items-center justify-between gap-4 px-5 py-3.5 border-b border-[#1A2B4C] bg-[#091020]">
          <span className="text-xs font-mono text-cyan-300 truncate">
            {activeFile ? activeFile.path : 'No file selected'}
          </span>
          {activeFile && (
            <button
              type="button"
              onClick={() => void handleCopy()}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#243B66] text-xs text-slate-200 hover:bg-slate-800 whitespace-nowrap shrink-0 cursor-pointer"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              {copied ? 'Copied' : 'Copy File'}
            </button>
          )}
        </div>

        <pre className="p-5 text-xs font-mono text-slate-200 overflow-x-auto max-h-[600px] leading-relaxed">
          <code>{activeFile?.content ?? ''}</code>
        </pre>
      </section>
    </div>
  );
}
