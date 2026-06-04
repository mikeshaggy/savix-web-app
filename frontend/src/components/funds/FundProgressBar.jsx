'use client';
import React from 'react';

/**
 * Thin progress bar for fund saving progress.
 *
 * Props:
 *   progressPercent  – 0–100+ (capped at 100 visually)
 *   isTargetReached  – when true renders an emerald bar (overrides color)
 *   color            – optional hex accent for the fill; falls back to violet
 *   height           – Tailwind h-* class, defaults to 'h-[3px]'
 */
export default function FundProgressBar({ progressPercent = 0, isTargetReached = false, color, height = 'h-[3px]' }) {
  const width = Math.min(Math.max(Number(progressPercent) || 0, 0), 100);

  // Target-reached always shows emerald so it stays visually distinct.
  // Otherwise use the fund's accent color (inline) or the violet default (class).
  const fillClass = isTargetReached ? 'bg-emerald-400' : color ? '' : 'bg-violet-500';
  const fillStyle = !isTargetReached && color ? { width: `${width}%`, backgroundColor: color } : { width: `${width}%` };

  return (
    <div className={`${height} bg-white/[0.06] rounded-full overflow-hidden`}>
      <div
        className={`h-full rounded-full transition-all duration-300 ${fillClass}`}
        style={fillStyle}
      />
    </div>
  );
}
