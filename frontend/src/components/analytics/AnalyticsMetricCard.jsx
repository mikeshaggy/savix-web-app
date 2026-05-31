'use client';
import React from 'react';
import SectionLabel from '@/components/common/SectionLabel';

export default function AnalyticsMetricCard({ label, value, subtext, icon: Icon, color, dotColor }) {
  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 relative overflow-hidden transition-colors hover:bg-white/[0.02] cursor-default">
      <div className="flex items-center justify-between mb-4">
        <SectionLabel variant="metric" className="flex items-center gap-[7px]">
          <span
            className="w-1.5 h-1.5 rounded-full inline-block flex-shrink-0"
            style={{ background: dotColor || color, boxShadow: `0 0 7px ${dotColor || color}` }}
          />
          {label}
        </SectionLabel>
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
    </div>
  );
}
