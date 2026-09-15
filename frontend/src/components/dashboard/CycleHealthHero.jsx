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

function formatMonthYear(dateStr) {
  if (!dateStr) return null;
  try {
    // Build from the Y/M parts: `new Date('YYYY-MM-DD')` is UTC midnight and would shift the month in UTC-negative zones.
    const [y, m] = String(dateStr).split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  } catch {
    return dateStr;
  }
}

/**
 * Reporting header: MONTHLY / CUSTOM / LAST_PAY_CYCLE (and any period without a health verdict) show the
 * period label plus income / spent / net actuals only — no status pill, no safe-to-spend, no projection,
 * no "Cycle ends". Stage 2.2: these periods are reporting-only by contract.
 */
function ReportingHeader({ period, kpis, t, formatCurrency }) {
  const label = period?.type === 'MONTHLY'
    ? formatMonthYear(period?.startDate)
    : `${formatShortDate(period?.startDate) ?? '—'} – ${formatShortDate(period?.endDate) ?? '—'}`;
  const income = kpis?.income?.amount ?? null;
  const spent = kpis?.expenses?.amount ?? null;
  const net = kpis?.saved?.amount ?? null;

  return (
    <div
      className="w-full bg-[#0e0e1c] rounded-[18px] overflow-hidden mb-5 border border-white/[0.06]"
      style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both', animationDelay: '0.04s' }}
      data-testid="reporting-header"
    >
      <div className="flex items-center justify-between px-6 py-3 border-b border-white/[0.04]">
        <span className="text-[11px] font-bold tracking-[0.12em] uppercase text-white/45">
          {t('dashboard.reportingPeriod')}
        </span>
        <span className="text-[11px] text-white/35">{label}</span>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-px bg-white/[0.035]">
        {[
          { key: 'income', label: t('dashboard.income'), value: income, cls: 'text-white' },
          { key: 'spent', label: t('dashboard.expenses'), value: spent, cls: 'text-white' },
          {
            key: 'net',
            label: t('dashboard.saved'),
            value: net,
            cls: net != null && Number(net) < 0 ? 'text-rose-400' : 'text-emerald-400',
          },
        ].map(({ key, label: l, value, cls }) => (
          <div key={key} className="bg-[#0e0e1c] px-4 md:px-5 py-5 md:py-6 min-w-0">
            <div className="text-[9px] tracking-[0.12em] uppercase text-white/35 mb-2.5">{l}</div>
            <div
              className={`font-mono text-[clamp(18px,3.4vw,26px)] font-bold tracking-[-0.5px] leading-none tabular-nums ${cls}`}
              style={{ overflowWrap: 'break-word' }}
            >
              {value != null ? formatCurrency(value) : '—'}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Late salary: the expected payday has passed without a salary — balance only, no verdict, no projection. */
function AwaitingSalaryHero({ cycleHealth, period, t, formatCurrency }) {
  const expected = formatShortDate(period?.expectedPaydayDate);
  return (
    <div
      className="w-full bg-[#0e0e1c] rounded-[18px] overflow-hidden mb-5 border border-amber-400/35"
      style={{ boxShadow: '0 0 48px rgba(245,158,11,0.10)', animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}
      data-testid="awaiting-salary-hero"
    >
      <div className="flex items-center gap-2 px-6 py-3 border-b border-amber-400/20 bg-amber-400/[0.04]">
        <span className="w-2 h-2 rounded-full bg-amber-400 shrink-0" />
        <span className="text-[11px] font-bold tracking-[0.12em] uppercase text-amber-400">
          {t('dashboard.awaitingSalary', { date: expected ?? '—' })}
        </span>
      </div>
      <div className="px-6 md:px-8 py-6 md:py-8">
        <div className="text-[9px] tracking-[0.12em] uppercase text-white/35 mb-3">{t('dashboard.currentBalance')}</div>
        <div
          className="font-mono text-[clamp(24px,5vw,40px)] font-bold tracking-[-1px] leading-none tabular-nums text-white"
          style={{ overflowWrap: 'break-word' }}
        >
          {formatCurrency(cycleHealth.currentBalance)}
        </div>
        {period?.daysElapsed != null && (
          <div className="text-[9px] text-white/30 mt-3">{t('dashboard.daysElapsed', { days: period.daysElapsed })}</div>
        )}
      </div>
    </div>
  );
}

export default function CycleHealthHero({ cycleHealth, period, kpis }) {
  const t = useTranslations();
  const formatCurrency = useFormatCurrency();

  if (!cycleHealth || period?.reporting) {
    return <ReportingHeader period={period} kpis={kpis} t={t} formatCurrency={formatCurrency} />;
  }
  if (cycleHealth.projectionReason === 'AWAITING_SALARY') {
    return <AwaitingSalaryHero cycleHealth={cycleHealth} period={period} t={t} formatCurrency={formatCurrency} />;
  }

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

  // "Cycle ends Oct 8" — only for the open PAY_CYCLE (reporting periods never reach this branch)
  const cycleEndDate = period?.endDate ?? null;
  const cutoffDate = period?.cutoffDate ?? period?.asOfDate ?? null;
  const showCycleEnd = period?.type === 'PAY_CYCLE' && cycleEndDate && cycleEndDate !== cutoffDate;
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
