'use client';
import React from 'react';
import { TrendingUp, TrendingDown, Calendar, AlertTriangle } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';
import { CardLoading } from '@/components/common/Loading';

export default function CycleForecastHero({ projData, loading, period }) {
  const t = useTranslations('analytics');

  if (loading) {
    return (
      <div className="mb-6" style={{ animation: 'fadeUp 0.4s ease both' }}>
        <div className="rounded-2xl bg-[#0e0e1c] border border-white/[0.06] p-6 md:p-8 h-40 animate-pulse" />
      </div>
    );
  }

  if (!projData) return null;

  const endBalance = projData.projectedEndBalance ?? 0;
  const isPositive = endBalance >= 0;
  const isActive = projData.projectionAvailable;

  const pct =
    (projData.incomeForPeriod ?? 0) > 0
      ? ((projData.projectedPeriodExpenses / projData.incomeForPeriod) * 100).toFixed(1)
      : null;

  const color = isPositive ? '#4ade80' : '#f87171';
  const borderClass = isPositive
    ? 'border-emerald-500/20'
    : 'border-red-500/20';
  const bgGlow = isPositive
    ? 'rgba(74,222,128,0.06)'
    : 'rgba(248,113,113,0.06)';

  const Icon = isPositive ? TrendingUp : TrendingDown;

  return (
    <div
      className={`rounded-2xl border ${borderClass} p-6 md:p-8 mb-6 relative overflow-hidden`}
      style={{
        background: bgGlow,
        animation: 'fadeUp 0.4s ease both',
      }}
    >
      {/* Radial glow behind the number */}
      <div
        className="absolute top-0 left-0 right-0 bottom-0 pointer-events-none"
        style={{
          background: `radial-gradient(ellipse at 20% 50%, ${color}0a, transparent 60%)`,
        }}
      />

      <div className="relative flex flex-col md:flex-row md:items-center gap-6 md:gap-12">
        {/* Main projection number */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-3">
            <span className="text-xs font-bold tracking-[0.14em] uppercase text-white/35">
              {t('cycleForecast')}
            </span>
            {isActive && (
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse flex-shrink-0" />
            )}
            {!isActive && (
              <span className="text-xs text-white/30 font-medium">
                {t('historicalPeriod')}
              </span>
            )}
          </div>

          <div
            className="font-mono text-[clamp(36px,6vw,64px)] font-bold tracking-[-2px] leading-none mb-3"
            style={{ color }}
          >
            {formatCurrency(Math.abs(endBalance))}
          </div>

          <div className="flex items-center gap-2">
            <Icon className="w-4 h-4 flex-shrink-0" style={{ color }} />
            <span className="text-sm text-white/60">
              {isPositive ? t('projectedSurplus') : t('projectedDeficit')}
            </span>
            {pct !== null && (
              <span className="text-xs font-mono text-white/35 ml-1">
                ({pct}% {t('ofIncome')})
              </span>
            )}
          </div>

          {!isPositive && pct !== null && parseFloat(pct) > 100 && (
            <div className="flex items-center gap-1.5 mt-3 text-sm text-red-400/80">
              <AlertTriangle className="w-4 h-4 flex-shrink-0" />
              <span>
                {t('overspendWarning', { pct: (parseFloat(pct) - 100).toFixed(1) })}
              </span>
            </div>
          )}
        </div>

        {/* Side stats */}
        <div className="flex flex-row flex-wrap md:flex-col gap-4 md:gap-5 md:text-right md:min-w-[190px]">
          {period && (
            <div className="flex items-center md:justify-end gap-1.5 text-sm text-white/40">
              <Calendar className="w-3.5 h-3.5 flex-shrink-0" />
              <span className="font-mono">
                {period.startDate} – {period.endDate}
              </span>
            </div>
          )}

          {(projData.daysInPeriod ?? 0) > 0 && (
            <div className="text-sm">
              <span className="text-white/35">{t('daysRemaining')}: </span>
              <span className="font-mono font-semibold text-white/70">
                {projData.daysRemaining}
              </span>
              <span className="text-white/30"> / {projData.daysInPeriod}</span>
            </div>
          )}

          {(projData.incomeForPeriod ?? 0) > 0 && (
            <div className="text-sm">
              <span className="text-white/35">{t('income')}: </span>
              <span className="font-mono font-semibold text-emerald-400">
                {formatCurrency(projData.incomeForPeriod)}
              </span>
            </div>
          )}

          {(projData.projectedPeriodExpenses ?? 0) > 0 && (
            <div className="text-sm">
              <span className="text-white/35">{t('projectedPeriodSpend')}: </span>
              <span className="font-mono font-semibold" style={{ color }}>
                {formatCurrency(projData.projectedPeriodExpenses)}
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
