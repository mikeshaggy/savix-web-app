'use client';
import React from 'react';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useTranslations } from 'next-intl';

const STATUS_CFG = {
  ON_TRACK: {
    accent: '#34d399',
    glow: 'rgba(52,211,153,0.14)',
    textClass: 'text-emerald-400',
    dotClass: 'bg-emerald-400',
    borderOpacity: '35',
  },
  WARNING: {
    accent: '#f59e0b',
    glow: 'rgba(245,158,11,0.14)',
    textClass: 'text-amber-400',
    dotClass: 'bg-amber-400',
    borderOpacity: '35',
  },
  DANGER: {
    accent: '#f43f5e',
    glow: 'rgba(244,63,94,0.16)',
    textClass: 'text-rose-400',
    dotClass: 'bg-rose-400',
    borderOpacity: '40',
  },
};

function formatShortDate(dateStr) {
  if (!dateStr) return null;
  try {
    return new Date(dateStr).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  } catch {
    return dateStr;
  }
}

export default function CycleHealthHero({ cycleHealth, period }) {
  const t = useTranslations();
  const formatCurrency = useFormatCurrency();

  if (!cycleHealth) return null;

  const {
    status,
    currentBalance,
    safeToSpend,
    safeToSpendPerDay,
    projectedEndBalance,
    spendingPaceDeltaPercent,
    projectionAvailable,
  } = cycleHealth;

  const cfg = STATUS_CFG[status] ?? STATUS_CFG.ON_TRACK;

  const daysElapsed = period?.daysElapsed ?? 0;
  const daysRemaining = period?.daysRemaining ?? 0;
  const daysInPeriod = period?.daysInPeriod ?? (daysElapsed + daysRemaining);
  const progressPct = daysInPeriod > 0 ? Math.min((daysElapsed / daysInPeriod) * 100, 100) : 0;
  const comparisonAvailable = period?.comparisonAvailable ?? false;

  const pacePercent = spendingPaceDeltaPercent != null ? Number(spendingPaceDeltaPercent) : null;
  // Negative = spending less than before = good
  const paceIsGood = pacePercent != null ? pacePercent < 0 : null;

  // Show "Cycle ends Jun 7" when the billing end is beyond the data cutoff
  const cycleEndDate = period?.endDate ?? null;
  const cutoffDate = period?.cutoffDate ?? period?.asOfDate ?? null;
  const showCycleEnd = cycleEndDate && cycleEndDate !== cutoffDate;
  const cycleEndShort = showCycleEnd ? formatShortDate(cycleEndDate) : null;

  return (
    <div
      className="w-full bg-[#0e0e1c] rounded-[18px] overflow-hidden mb-5"
      style={{
        border: `1px solid ${cfg.accent}${cfg.borderOpacity}`,
        boxShadow: `0 0 48px ${cfg.glow}`,
        animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both',
        animationDelay: '0.04s',
      }}
    >
      {/* Status bar */}
      <div
        className="flex items-center justify-between px-6 py-3 border-b"
        style={{ borderColor: `${cfg.accent}20`, backgroundColor: `${cfg.accent}0a` }}
      >
        <div className="flex items-center gap-2">
          <span className={`w-2 h-2 rounded-full ${cfg.dotClass} animate-pulse shrink-0`} />
          <span className={`text-[11px] font-bold tracking-[0.12em] uppercase ${cfg.textClass}`}>
            {t(`dashboard.health_${status}`)}
          </span>
        </div>
        <div className="flex items-center gap-3 text-[11px] text-white/35">
          {cycleEndShort && (
            <span>
              {t('dashboard.cycleEnds', { date: cycleEndShort })}
            </span>
          )}
          {daysRemaining > 0 && (
            <span className={cycleEndShort ? 'opacity-60' : ''}>
              {t('dashboard.daysRemaining', { days: daysRemaining })}
            </span>
          )}
        </div>
      </div>

      {/* Current Balance — primary block, always full width */}
      <div className="bg-[#0e0e1c] px-6 md:px-8 py-6 md:py-8 border-b border-white/[0.035]">
        <div className="text-[9px] tracking-[0.12em] uppercase text-white/35 mb-3">
          {t('dashboard.currentBalance')}
        </div>
        <div
          className={`font-mono text-[clamp(24px,5vw,40px)] font-bold tracking-[-1px] leading-none tabular-nums ${cfg.textClass}`}
          style={{ overflowWrap: 'break-word' }}
        >
          {formatCurrency(currentBalance)}
        </div>
      </div>

      {/* Secondary metrics — stack on mobile, 3-across from sm up so values always have room */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-px bg-white/[0.035]">
        {/* Safe to Spend */}
        <div className="bg-[#0e0e1c] px-4 md:px-5 py-5 md:py-6 min-w-0">
          <div className="text-[9px] tracking-[0.12em] uppercase text-white/35 mb-2.5">
            {t('dashboard.safeToSpend')}
          </div>
          {safeToSpendPerDay != null ? (
            <div className="flex items-baseline gap-1.5 flex-wrap">
              <div
                className={`font-mono text-[clamp(18px,3.4vw,26px)] font-bold tracking-[-0.5px] leading-none tabular-nums ${
                  Number(safeToSpendPerDay) >= 0 ? 'text-white' : 'text-rose-400'
                }`}
                style={{ overflowWrap: 'break-word' }}
              >
                {formatCurrency(safeToSpendPerDay)}
              </div>
              <div className="text-[9px] text-white/30 leading-none">{t('dashboard.perDay')}</div>
            </div>
          ) : (
            <div className="font-mono text-[clamp(18px,3.4vw,26px)] font-bold leading-none text-white/20">—</div>
          )}
          {safeToSpend != null && (
            <div className="text-[9px] text-white/30 mt-1.5">
              {t('dashboard.safeToSpendTotal', { amount: formatCurrency(safeToSpend) })}
            </div>
          )}
        </div>

        {/* Projected Cycle Surplus/Deficit */}
        <div className="bg-[#0e0e1c] px-4 md:px-5 py-5 md:py-6 min-w-0">
          <div className="text-[9px] tracking-[0.12em] uppercase text-white/35 mb-2.5">
            {projectionAvailable && projectedEndBalance != null
              ? Number(projectedEndBalance) >= 0
                ? t('dashboard.projectedCycleSurplus')
                : t('dashboard.projectedCycleDeficit')
              : t('dashboard.projectedCycleSurplus')}
          </div>
          {projectionAvailable && projectedEndBalance != null ? (
            <div
              className={`font-mono text-[clamp(18px,3.4vw,26px)] font-bold tracking-[-0.5px] leading-none tabular-nums ${
                Number(projectedEndBalance) >= 0 ? 'text-white' : 'text-rose-400'
              }`}
              style={{ overflowWrap: 'break-word' }}
            >
              {formatCurrency(projectedEndBalance)}
            </div>
          ) : (
            <>
              <div className="font-mono text-[clamp(18px,3.4vw,26px)] font-bold leading-none text-white/20">—</div>
              {!projectionAvailable && (
                <div className="text-[9px] text-white/30 mt-1.5">
                  {t('dashboard.projectionUnavailable')}
                </div>
              )}
            </>
          )}
        </div>

        {/* Spending Pace */}
        <div className="bg-[#0e0e1c] px-4 md:px-5 py-5 md:py-6 min-w-0">
          <div className="text-[9px] tracking-[0.12em] uppercase text-white/35 mb-2.5">
            {t('dashboard.spendingPace')}
          </div>
          {comparisonAvailable && pacePercent != null ? (
            <>
              <div
                className={`font-mono text-[clamp(18px,3.4vw,26px)] font-bold tracking-[-0.5px] leading-none tabular-nums ${
                  paceIsGood ? 'text-emerald-400' : 'text-rose-400'
                }`}
              >
                {pacePercent > 0 ? '+' : ''}{pacePercent.toFixed(1)}%
              </div>
              <div className={`text-[9px] mt-1.5 ${paceIsGood ? 'text-emerald-400/70' : 'text-rose-400/70'}`}>
                {paceIsGood ? t('dashboard.spendingSlower') : t('dashboard.spendingFaster')}
              </div>
            </>
          ) : (
            <>
              <div className="font-mono text-[clamp(18px,3.4vw,26px)] font-bold leading-none text-white/20">—</div>
              <div className="text-[9px] text-white/30 mt-1.5">{t('dashboard.noComparison')}</div>
            </>
          )}
        </div>
      </div>

      {/* Cycle progress strip */}
      {daysInPeriod > 0 && (
        <div className="flex items-center gap-3 px-6 py-3 border-t border-white/[0.04]">
          <span className="text-[9px] text-white/30 whitespace-nowrap shrink-0">
            {t('dashboard.daysElapsed', { days: daysElapsed })}
          </span>
          <div className="flex-1 h-[3px] bg-white/[0.07] rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all"
              style={{ width: `${progressPct}%`, background: cfg.accent }}
            />
          </div>
          <span className="text-[9px] text-white/30 whitespace-nowrap shrink-0">
            {t('dashboard.daysTotal', { days: daysInPeriod })}
          </span>
        </div>
      )}
    </div>
  );
}
