'use client';
import React from 'react';
import { useTranslations } from 'next-intl';

/**
 * Small deadline badge for a fund card.
 *
 * Props:
 *   deadlineDate – ISO date string or null
 *
 * Visual states:
 *   no deadline  → subtle "No deadline" text
 *   > 30 days    → muted white text
 *   1–30 days    → amber warning text
 *   overdue      → red text
 */
export default function FundDeadlineCountdown({ deadlineDate }) {
  const t = useTranslations('funds');

  if (!deadlineDate) {
    return (
      <span className="text-[12px] text-white/25">{t('noDeadline')}</span>
    );
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  // Parse as local midnight to avoid UTC-offset date-shift on date-only strings.
  const deadline = new Date(`${deadlineDate}T00:00:00`);

  const diffMs = deadline - today;
  const days = Math.round(diffMs / (1000 * 60 * 60 * 24));

  if (days < 0) {
    return (
      <span className="text-[12px] font-medium text-red-400">
        {t('daysOverdue', { days: Math.abs(days) })}
      </span>
    );
  }

  if (days <= 30) {
    return (
      <span className="text-[12px] font-medium text-amber-400">
        {t('daysLeft', { days })}
      </span>
    );
  }

  return (
    <span className="text-[12px] text-white/35">
      {t('daysLeft', { days })}
    </span>
  );
}
