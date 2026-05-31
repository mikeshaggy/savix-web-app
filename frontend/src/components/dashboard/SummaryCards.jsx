'use client';
import React from 'react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';

export default function SummaryCards({ kpis }) {
  const t = useTranslations();

  if (!kpis) return null;

  const segments = [
    {
      key: 'income',
      label: t('dashboard.income'),
      value: kpis.income?.amount,
      deltaPercent: kpis.income?.deltaPercent != null ? Number(kpis.income.deltaPercent) : null,
      dotColor: '#34d399',
      valueClass: 'text-emerald-400',
      floorColor: 'rgba(52,211,153,0.45)',
      lowerIsBetter: false,
    },
    {
      key: 'expenses',
      label: t('dashboard.expenses'),
      value: kpis.expenses?.amount,
      deltaPercent: kpis.expenses?.deltaPercent != null ? Number(kpis.expenses.deltaPercent) : null,
      dotColor: '#f43f5e',
      valueClass: 'text-rose-400',
      floorColor: 'rgba(244,63,94,0.45)',
      lowerIsBetter: true,
    },
    {
      key: 'saved',
      label: t('dashboard.saved'),
      value: kpis.saved?.amount,
      deltaPercent: kpis.saved?.deltaPercent != null ? Number(kpis.saved.deltaPercent) : null,
      dotColor: '#c084fc',
      valueClass: '',
      floorColor: 'rgba(192,132,252,0.45)',
      gradient: true,
      lowerIsBetter: false,
    },
    {
      key: 'savingsRate',
      label: t('dashboard.savingsRate'),
      value: kpis.savingsRate?.percent,
      deltaPercent:
        kpis.savingsRate?.deltaPercentagePoints != null
          ? Number(kpis.savingsRate.deltaPercentagePoints)
          : null,
      dotColor: '#f59e0b',
      valueClass: 'text-amber-400',
      floorColor: 'rgba(245,158,11,0.4)',
      isPercentage: true,
      isPercentagePoints: true,
      lowerIsBetter: false,
    },
  ];

  return (
    <div
      className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 rounded-[20px] overflow-hidden border border-white/[0.07] bg-[#0e0e1c]"
      style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both', animationDelay: '0.08s' }}
    >
      {segments.map((seg, index) => {
        const delta = seg.deltaPercent;
        const hasComparison = delta != null;
        const isPositive = hasComparison ? delta > 0 : null;
        const isGood = isPositive != null ? (seg.lowerIsBetter ? !isPositive : isPositive) : null;

        return (
          <div
            key={seg.key}
            className="relative py-5 sm:py-7 px-4 sm:px-6 overflow-hidden transition-colors hover:bg-white/[0.02] cursor-default"
          >
            {/* Top accent bar */}
            <div
              className="absolute top-0 left-0 right-0 h-[2px]"
              style={{ background: seg.dotColor, opacity: 0.65 }}
            />

            {/* Divider */}
            {index > 0 && (
              <div className="absolute left-0 top-[15%] bottom-[15%] w-px bg-white/[0.07]" />
            )}

            {/* Label */}
            <div className="flex items-center gap-[7px] text-[10px] font-bold tracking-[0.14em] uppercase text-white/35 mb-4">
              <span
                className="w-1.5 h-1.5 rounded-full inline-block shrink-0"
                style={{ background: seg.dotColor, boxShadow: `0 0 8px ${seg.dotColor}` }}
              />
              {seg.label}
            </div>

            {/* Value */}
            <div
              className={`font-mono text-[clamp(22px,2.7vw,32px)] font-bold tracking-[-0.5px] leading-none mb-3 ${seg.gradient ? '' : seg.valueClass}`}
              style={{
                whiteSpace: 'nowrap',
                ...(seg.gradient
                  ? {
                      background: 'linear-gradient(90deg, #c084fc, #e879f9)',
                      WebkitBackgroundClip: 'text',
                      WebkitTextFillColor: 'transparent',
                      backgroundClip: 'text',
                    }
                  : {}),
              }}
            >
              {seg.isPercentage ? (
                <>
                  {seg.value != null ? Number(seg.value).toFixed(1) : '0'}
                  <span className="text-[15px] font-light opacity-55 tracking-normal">%</span>
                </>
              ) : (
                formatCurrency(seg.value)
              )}
            </div>

            {/* Change indicator */}
            {hasComparison ? (
              <div
                className={`text-[11px] font-semibold flex items-center gap-[5px] ${
                  isGood ? 'text-emerald-400' : 'text-rose-400'
                }`}
              >
                <svg
                  width="10"
                  height="10"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="3"
                >
                  {isPositive ? (
                    <polyline points="23 6 13.5 15.5 8.5 10.5 1 18" />
                  ) : (
                    <polyline points="23 18 13.5 8.5 8.5 13.5 1 6" />
                  )}
                </svg>
                {seg.isPercentagePoints
                  ? `${isPositive ? '+' : ''}${Math.abs(delta).toFixed(1)} pts`
                  : `${Math.abs(delta).toFixed(1)}%`}
              </div>
            ) : (
              <div className="text-[11px] text-white/25">{t('dashboard.noComparison')}</div>
            )}

          </div>
        );
      })}
    </div>
  );
}
