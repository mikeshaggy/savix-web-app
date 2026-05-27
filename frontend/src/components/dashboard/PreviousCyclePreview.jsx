'use client';
import React from 'react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';

function DeltaCell({ label, deltaAmount, deltaPercent, lowerIsBetter = false }) {
  const t = useTranslations();

  if (deltaAmount == null || deltaPercent == null) {
    return (
      <div className="min-w-0">
        <div className="text-[10px] text-white/35 uppercase tracking-[0.1em] mb-1.5">{label}</div>
        <div className="text-[14px] font-medium text-white/20">—</div>
      </div>
    );
  }

  const amount = Number(deltaAmount);
  const pct = Number(deltaPercent);
  const isPositive = amount > 0;
  const isGood = lowerIsBetter ? !isPositive : isPositive;
  const colorClass = isGood ? 'text-emerald-400' : 'text-rose-400';
  const subColorClass = isGood ? 'text-emerald-400/60' : 'text-rose-400/60';

  return (
    <div className="min-w-0">
      <div className="text-[10px] text-white/35 uppercase tracking-[0.1em] mb-1.5">{label}</div>
      <div
        className={`text-[15px] font-bold font-mono ${colorClass}`}
        style={{ whiteSpace: 'nowrap' }}
      >
        {isPositive ? '+' : ''}{formatCurrency(amount)}
      </div>
      <div className={`text-[11px] font-medium ${subColorClass} mt-0.5`}>
        {isPositive ? '+' : ''}{Math.abs(pct).toFixed(1)}%
      </div>
    </div>
  );
}

function formatShortDate(dateStr) {
  if (!dateStr) return dateStr;
  try {
    return new Date(dateStr).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  } catch {
    return dateStr;
  }
}

export default function PreviousCyclePreview({ preview, period }) {
  const t = useTranslations();
  const comparisonAvailable = period?.comparisonAvailable ?? false;

  const compareDateRange =
    comparisonAvailable && period?.compareStartDate && period?.compareEndDate
      ? `${formatShortDate(period.compareStartDate)} – ${formatShortDate(period.compareEndDate)}`
      : null;

  return (
    <div
      className="bg-[#0e0e1c] border border-white/[0.07] rounded-[18px] overflow-hidden"
      style={{ animation: 'fadeUp 0.5s ease both', animationDelay: '0.26s', minHeight: '220px' }}
    >
      {/* Header */}
      <div className="px-5 py-4 border-b border-white/[0.07]">
        <div className="text-[15px] font-bold tracking-[-0.2px] text-white">{t('dashboard.previousCycle')}</div>
        {compareDateRange && (
          <div className="text-[11px] text-white/35 mt-0.5">{compareDateRange}</div>
        )}
      </div>

      {!comparisonAvailable || !preview ? (
        /* Empty state */
        <div className="px-5 py-8 text-center">
          <div className="text-[13px] text-white/30">{t('dashboard.comparisonUnavailable')}</div>
        </div>
      ) : (
        <>
          {/* Four delta metrics */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-x-4 gap-y-5 px-5 py-5">
            <DeltaCell
              label={t('dashboard.income')}
              deltaAmount={preview.incomeDeltaAmount}
              deltaPercent={preview.incomeDeltaPercent}
            />
            <DeltaCell
              label={t('dashboard.expenses')}
              deltaAmount={preview.expensesDeltaAmount}
              deltaPercent={preview.expensesDeltaPercent}
              lowerIsBetter
            />
            <DeltaCell
              label={t('dashboard.saved')}
              deltaAmount={preview.savedDeltaAmount}
              deltaPercent={preview.savedDeltaPercent}
            />
            {/* Savings rate — percentage points */}
            <div className="min-w-0">
              <div className="text-[10px] text-white/35 uppercase tracking-[0.1em] mb-1.5">
                {t('dashboard.savingsRate')}
              </div>
              {preview.savingsRateDeltaPercentagePoints != null ? (
                <>
                  <div
                    className={`text-[15px] font-bold font-mono ${
                      Number(preview.savingsRateDeltaPercentagePoints) >= 0
                        ? 'text-emerald-400'
                        : 'text-rose-400'
                    }`}
                    style={{ whiteSpace: 'nowrap' }}
                  >
                    {Number(preview.savingsRateDeltaPercentagePoints) > 0 ? '+' : ''}
                    {Number(preview.savingsRateDeltaPercentagePoints).toFixed(1)}{' '}
                    <span className="text-[11px] font-medium opacity-80">pts</span>
                  </div>
                </>
              ) : (
                <div className="text-[14px] font-medium text-white/20">—</div>
              )}
            </div>
          </div>

          {/* Notable category changes */}
          {preview.topChangedCategories && preview.topChangedCategories.length > 0 && (
            <div className="px-5 pb-5 border-t border-white/[0.05] pt-3.5">
              <div className="text-[10px] text-white/35 uppercase tracking-[0.1em] mb-2.5">
                {t('dashboard.topChangedCategories')}
              </div>
              <div className="flex flex-wrap gap-1.5">
                {preview.topChangedCategories.map((cat, idx) => {
                  const delta = cat.deltaPercent != null ? Number(cat.deltaPercent) : null;
                  const isUp = cat.direction === 'UP';
                  return (
                    <div
                      key={cat.categoryId ?? idx}
                      className={`flex items-center gap-1 px-2.5 py-1.5 rounded-[7px] text-[11px] font-medium border ${
                        isUp
                          ? 'bg-rose-500/[0.08] border-rose-500/25 text-rose-300'
                          : 'bg-emerald-500/[0.08] border-emerald-500/25 text-emerald-300'
                      }`}
                    >
                      {cat.categoryEmoji && <span>{cat.categoryEmoji}</span>}
                      <span>{cat.categoryName}</span>
                      {delta != null && (
                        <span className="font-mono opacity-75">
                          {isUp ? '+' : ''}{Math.abs(delta).toFixed(0)}%
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
