import { useEffect, useRef } from 'react';
import type { AssistantState } from '../types/jarvis';

interface JarvisOrbCanvasProps {
  state: AssistantState;
}

export function JarvisOrbCanvas({ state }: JarvisOrbCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let rafId = 0;
    let angle = 0;

    const render = () => {
      const dpr = window.devicePixelRatio || 1;
      const size = 216;
      if (canvas.width !== size * dpr) {
        canvas.width = size * dpr;
        canvas.height = size * dpr;
      }
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, size, size);

      const cx = size / 2;
      const cy = size / 2;
      const baseR = 64;

      const colorMap: Record<AssistantState, string> = {
        STANDBY: '#00E5FF',
        LISTENING: '#00E5FF',
        THINKING: '#3B82F6',
        EXECUTING: '#3B82F6',
        SPEAKING: '#38BDF8',
        OFFLINE: '#F59E0B',
        ERROR: '#EF4444',
      };
      const strokeColor = colorMap[state];

      const speed =
        state === 'THINKING' || state === 'EXECUTING'
          ? 0.035
          : state === 'LISTENING' || state === 'SPEAKING'
            ? 0.022
            : 0.008;

      if (!prefersReducedMotion) {
        angle += speed;
      }

      // Soft core halo
      const grad = ctx.createRadialGradient(cx, cy, 12, cx, cy, 104);
      grad.addColorStop(0, `${strokeColor}33`);
      grad.addColorStop(1, 'rgba(6, 11, 22, 0)');
      ctx.fillStyle = grad;
      ctx.beginPath();
      ctx.arc(cx, cy, 104, 0, Math.PI * 2);
      ctx.fill();

      // Outer arcs
      ctx.strokeStyle = `${strokeColor}B8`;
      ctx.lineWidth = 2.5;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.arc(cx, cy, baseR + 24, angle, angle + Math.PI * 1.15);
      ctx.stroke();

      ctx.strokeStyle = 'rgba(41, 121, 255, 0.55)';
      ctx.lineWidth = 1.75;
      ctx.beginPath();
      ctx.arc(cx, cy, baseR + 30, -angle * 1.4, -angle * 1.4 + Math.PI * 0.8);
      ctx.stroke();

      // State-driven waveform bars
      const bars = 44;
      const amplitude =
        state === 'LISTENING'
          ? 16
          : state === 'SPEAKING'
            ? 14
            : state === 'THINKING' || state === 'EXECUTING'
              ? 8
              : 2;

      ctx.strokeStyle = `${strokeColor}CC`;
      ctx.lineWidth = 2;
      for (let i = 0; i < bars; i++) {
        const theta = (i / bars) * Math.PI * 2 + angle * 0.3;
        const wave = Math.sin(i * 0.8 + angle * 4) * 0.5 + 0.5;
        const len = 3 + amplitude * wave;
        const r1 = baseR + 4;
        const r2 = r1 + len;
        ctx.beginPath();
        ctx.moveTo(cx + Math.cos(theta) * r1, cy + Math.sin(theta) * r1);
        ctx.lineTo(cx + Math.cos(theta) * r2, cy + Math.sin(theta) * r2);
        ctx.stroke();
      }

      // Inner core
      ctx.fillStyle = '#0A1324';
      ctx.beginPath();
      ctx.arc(cx, cy, baseR - 6, 0, Math.PI * 2);
      ctx.fill();

      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(cx, cy, baseR - 6, 0, Math.PI * 2);
      ctx.stroke();

      if (!prefersReducedMotion) {
        rafId = requestAnimationFrame(render);
      }
    };

    render();
    return () => cancelAnimationFrame(rafId);
  }, [state]);

  return (
    <div className="relative flex items-center justify-center w-[216px] h-[216px] mx-auto">
      <canvas ref={canvasRef} className="w-[216px] h-[216px]" aria-hidden="true" />
      <span className="absolute font-display text-sm font-semibold tracking-[0.2em] text-slate-100 select-none">
        J.A.R.V.I.S
      </span>
    </div>
  );
}
