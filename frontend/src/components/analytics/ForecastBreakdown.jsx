'use client';
import React from 'react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';

function InputRow({ label, value, color }) {
  return (
    <div className="flex items-center justify-between py-1.5">
      <div className="flex items-center gap-2">
        <span
          className="w-1.5 h-1.5 rounded-full flex-shrink-0"
          style={{ background: color, opacity: 0.7 }}
        />
        <span className="text-sm text-white/50">{label}</span>
      </div>
      <span className="font-mono text-sm font-medium ml-4" style={{ color }}>
        {value}
      </span>
    </div>
  );
}

export default function ForecastBreakdown({ projData, loading }) {
  const t = useTranslations('analytics');

  if (loading) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 animate-pulse h-36" />
    );
  }

  if (!projData) return null;

  const spentSoFar = projData.expensesToDate ?? 0;
  const remainingFixed = projData.remainingFixedPayments ?? 0;
  const projectedTotal = projData.projectedPeriodExpenses ?? 0;
  const projectedVariable = Math.max(0, projectedTotal - spentSoFar - remainingFixed);
  const endBalance = projData.projectedEndBalance ?? 0;
  const income = projData.incomeForPeriod ?? 0;

  const balanceColor = endBalance >= 0 ? '#4ade80' : '#f87171';

  const inputRows = [
    { label: t('income'), value: formatCurrency(income), color: '#4ade80' },
    { label: `− ${t('spentSoFar')}`, value: formatCurrency(spentSoFar), color: '#f87171' },
    { label: `− ${t('remainingFixedPayments')}`, value: formatCurrency(remainingFixed), color: '#a78bfa' },
    { label: `− ${t('projectedVariable')}`, value: formatCurrency(projectedVariable), color: '#fb923c' },
  ];

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 relative overflow-hidden">
      <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-3">
        {t('forecastBreakdown')}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-[3fr_2fr] gap-4 md:gap-6">
        {/* Left: input rows */}
        <div className="flex flex-col">
          {inputRows.map((row, i) => (
            <InputRow key={i} {...row} />
          ))}
        </div>

        {/* Right: projected totals */}
        <div className="flex flex-col justify-center gap-3 md:border-l md:border-white/[0.06] md:pl-6 pt-2 md:pt-0">
          <div>
            <div className="text-xs text-white/35 uppercase tracking-[0.12em] mb-1">
              {t('projectedTotalSpend')}
            </div>
            <div className="font-mono text-lg font-semibold text-red-400">
              {formatCurrency(projectedTotal)}
            </div>
          </div>
          <div className="h-px bg-white/[0.07]" />
          <div>
            <div className="text-xs text-white/35 uppercase tracking-[0.12em] mb-1">
              {t('projectedEndBalance')}
            </div>
            <div className="font-mono text-2xl font-bold" style={{ color: balanceColor }}>
              {formatCurrency(endBalance)}
            </div>
          </div>
        </div>
      </div>

      <div
        className="absolute bottom-0 left-0 right-0 h-0.5 opacity-25"
        style={{ backgroundColor: balanceColor }}
      />
    </div>
  );
}
