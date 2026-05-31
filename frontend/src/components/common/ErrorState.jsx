'use client';
import React from 'react';
import { AlertCircle } from 'lucide-react';
import { PAGE_CTA } from '@/components/common/formControls';

// Two variants:
//   page   — centered, AlertCircle + title + description + PAGE_CTA retry
//   inline — banner strip, small icon + message + text-link retry
//
// Props:
//   title       — primary label / error message (pre-translated by caller)
//   description — secondary copy (pre-translated by caller, optional)
//   onRetry     — retry handler (optional; hides button when absent)
//   retryLabel  — retry button text (pre-translated by caller, defaults to 'Try again')
//   className   — additive wrapper class

export default function ErrorState({
  variant = 'page',
  title,
  description,
  onRetry,
  retryLabel = 'Try again',
  className = '',
}) {
  if (variant === 'inline') {
    return (
      <div
        className={`bg-red-500/[0.08] border border-red-500/[0.15] rounded-[12px] px-4 py-3 flex items-center gap-3 ${className}`}
      >
        <AlertCircle className="w-4 h-4 text-red-400/70 shrink-0" />
        <p className="text-[13px] text-white/50 flex-1">{title}</p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="text-[13px] font-semibold text-red-400 hover:text-red-300 transition-colors shrink-0"
          >
            {retryLabel}
          </button>
        )}
      </div>
    );
  }

  // page variant
  return (
    <div className={`flex items-center justify-center p-8 ${className}`}>
      <div className="flex flex-col items-center text-center">
        <AlertCircle className="w-10 h-10 text-red-400/70" />
        <p className="text-[16px] font-semibold text-white tracking-[-0.2px] mt-4">{title}</p>
        {description && (
          <p className="text-[13px] text-white/40 mt-2 max-w-[280px] leading-relaxed">
            {description}
          </p>
        )}
        {onRetry && (
          <button onClick={onRetry} className={`${PAGE_CTA} mt-5`}>
            {retryLabel}
          </button>
        )}
      </div>
    </div>
  );
}
