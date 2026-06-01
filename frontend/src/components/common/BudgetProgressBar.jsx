'use client';
import React from 'react';

/**
 * Thin progress bar that visualises budget usage.
 *
 * Props:
 *   percent  – 0–100+ (numbers above 100 are clamped to 100 visually)
 *   status   – 'OK' | 'WARNING' | 'EXCEEDED' (or null — renders neutral bar)
 *   height   – Tailwind h-* class, defaults to 'h-[3px]'
 */

const STATUS_COLORS = {
  OK:       'bg-emerald-400',
  WARNING:  'bg-amber-400',
  EXCEEDED: 'bg-rose-400',
};

export default function BudgetProgressBar({ percent = 0, status = null, height = 'h-[3px]' }) {
  const colorClass = STATUS_COLORS[status] ?? 'bg-white/30';
  const width = Math.min(Math.max(Number(percent) || 0, 0), 100);

  return (
    <div className={`${height} bg-white/[0.06] rounded-full overflow-hidden`}>
      <div
        className={`h-full rounded-full transition-all duration-300 ${colorClass}`}
        style={{ width: `${width}%` }}
      />
    </div>
  );
}
