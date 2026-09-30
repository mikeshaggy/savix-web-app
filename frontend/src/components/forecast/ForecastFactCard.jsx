'use client';
import React from 'react';
import SectionLabel from '@/components/common/SectionLabel';

// Stage 5.6 card: either a `figure` (one large value + note) or `context` (small labelled rows, no large value —
// used where the hero already carries the headline figure).
export default function ForecastFactCard({ label, icon: Icon, color, figure, figureColor, rows = [], note }) {
  return (
    <div
      className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 relative overflow-hidden min-w-0"
      data-testid="forecast-fact-card"
      data-kind={figure !== undefined ? 'figure' : 'context'}
    >
      <div className="flex items-center justify-between mb-3">
        <SectionLabel variant="metric" className="flex items-center gap-[7px]">
          <span className="w-1.5 h-1.5 rounded-full inline-block flex-shrink-0" style={{ background: color }} />
          {label}
        </SectionLabel>
        {Icon && (
          <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: `${color}18` }}>
            <Icon className="w-4 h-4" style={{ color }} />
          </div>
        )}
      </div>
      {figure !== undefined && (
        <div
          className="font-mono text-[clamp(20px,2.2vw,28px)] font-medium tracking-[-0.5px] leading-none mb-2"
          style={{ color: figureColor ?? color }}
        >
          {figure}
        </div>
      )}
      {rows.length > 0 && (
        <dl className="flex flex-col gap-1.5">
          {rows.map((row) => (
            <div key={row.label} className="flex items-baseline justify-between gap-3 text-xs">
              <dt className="text-white/40">{row.label}</dt>
              <dd className={`font-mono tabular-nums ${row.negative ? 'text-rose-400' : 'text-white/70'}`}>{row.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {note && <div className="text-[11px] text-white/30 mt-2">{note}</div>}
    </div>
  );
}
