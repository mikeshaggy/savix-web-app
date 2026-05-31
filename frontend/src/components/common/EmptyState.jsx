'use client';
import React from 'react';
import { PAGE_CTA } from '@/components/common/formControls';

// Two variants:
//   page — centered, min-h-[400px], for full-page "no data / no selection" screens
//   card — py-12, for inline empty grids and lists
//
// Props:
//   icon        — Lucide icon component (optional)
//   title       — primary label (pre-translated by caller)
//   description — body copy (pre-translated by caller, optional)
//   action      — { label, onClick } optional CTA (uses PAGE_CTA)
//   className   — additive wrapper class

const outerStyles = {
  page: 'flex items-center justify-center min-h-[400px] p-8',
  card: 'flex flex-col items-center justify-center py-12 px-6',
};

const iconContainerStyles = {
  page: 'w-12 h-12 rounded-2xl bg-white/[0.04] border border-white/[0.06] flex items-center justify-center',
  card: 'w-10 h-10 rounded-xl bg-white/[0.04] border border-white/[0.06] flex items-center justify-center',
};

const iconStyles = {
  page: 'w-6 h-6 text-white/25',
  card: 'w-5 h-5 text-white/20',
};

const titleStyles = {
  page: 'text-[18px] font-semibold text-white tracking-[-0.2px] mt-5',
  card: 'text-[15px] font-semibold text-white/40 mt-4',
};

const descStyles = {
  page: 'text-[14px] text-white/40 leading-relaxed max-w-[280px] mt-2',
  card: 'text-[13px] text-white/25 mt-1',
};

export default function EmptyState({
  variant = 'page',
  icon: Icon,
  title,
  description,
  action,
  className = '',
}) {
  return (
    <div className={`${outerStyles[variant]} ${className}`}>
      <div className="flex flex-col items-center text-center">
        {Icon && (
          <div className={iconContainerStyles[variant]}>
            <Icon className={iconStyles[variant]} />
          </div>
        )}
        {title && <p className={titleStyles[variant]}>{title}</p>}
        {description && <p className={descStyles[variant]}>{description}</p>}
        {action && (
          <button onClick={action.onClick} className={`${PAGE_CTA} mt-6`}>
            {action.label}
          </button>
        )}
      </div>
    </div>
  );
}
