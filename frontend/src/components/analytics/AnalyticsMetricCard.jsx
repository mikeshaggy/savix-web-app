'use client';
import React from 'react';

export default function AnalyticsMetricCard({ label, value, subtext, icon: Icon, color, dotColor }) {
  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 relative overflow-hidden transition-colors hover:bg-white/[0.02] cursor-default">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-[7px] text-[10px] font-bold tracking-[0.14em] uppercase text-white/25">
          <span
            className="w-1.5 h-1.5 rounded-full inline-block flex-shrink-0"
            style={{ background: dotColor || color, boxShadow: `0 0 7px ${dotColor || color}` }}
          />
          {label}
        </div>
        {Icon && (
          <div
            className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
            style={{ background: `${color}18` }}
          >
            <Icon className="w-4 h-4" style={{ color }} />
          </div>
        )}
      </div>

      <div
        className="font-mono text-[clamp(20px,2.2vw,28px)] font-medium tracking-[-0.5px] leading-none mb-2"
        style={{ color }}
      >
        {value}
      </div>

      {subtext && (
        <div className="text-[11px] text-white/30">{subtext}</div>
      )}

      <div
        className="absolute bottom-0 left-0 right-0 h-0.5 opacity-40"
        style={{ backgroundColor: color }}
      />
    </div>
  );
}
