'use client';
import React from 'react';
import { useTranslations } from 'next-intl';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { cycleSoFarModel } from '@/lib/dashboardV2';

// Stage 6.5: "Spent so far: fixed X · variable Y · typical by day d: Z". Neutral: no delta, no verdict colour.
export default function CycleSoFarLine({ cycleSoFar, period }) {
  const t = useTranslations('dashboard');
  const formatCurrency = useFormatCurrency();
  const model = cycleSoFarModel(cycleSoFar, period);
  if (!model) return null;

  const typicalText = model.hasTypical
    ? t('typicalByToday', { day: model.day ?? '—', typical: formatCurrency(model.typical) })
    : t('typicalNone', { day: model.day ?? '—' });

  return (
    <div
      className="bg-[#0e0e1c] border border-white/[0.06] rounded-[14px] px-5 py-4 text-[13px] text-white/70 tabular-nums"
      title={t('cycleSoFarHint')}
    >
      <span>
        {t('cycleSoFar', {
          fixed: formatCurrency(model.fixedSpent),
          variable: formatCurrency(model.variableSpent),
        })}
      </span>
      <span className="text-white/35"> · {typicalText}</span>
      <div className="text-[11px] text-white/25 mt-1">{t('cycleSoFarHint')}</div>
    </div>
  );
}
