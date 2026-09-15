'use client';
import React from 'react';
import { TrendingUp, TrendingDown, Calendar, AlertTriangle } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { CardLoading } from '@/components/common/Loading';

// Format "YYYY-MM-DD" → locale short date, e.g. "May 1".
// T12:00:00 prevents timezone drift when parsing date-only strings.
function fmtShort(str) {
  try {
    return new Date(str + 'T12:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  } catch {
    return str ?? '';
  }
}

/**
 * Reporting header: period label plus income / spent / net actuals — no verdict, no projected number,
 * no overspend copy. Mirrors the dashboard's reporting header for non-planning periods.
 */
function ReportingHeader({ projData, period, t, formatCurrency }) {
  const start = period?.startDate ?? projData.startDate;
  const end = period?.endDate ?? projData.endDate;
  const income = projData.incomeForPeriod ?? null;
  const spent = projData.expensesToDate ?? null;
  const net = income != null && spent != null ? Number(income) - Number(spent) : null;
  const reason = projData.projectionReason === 'AWAITING_SALARY'
    ? t('reportingAwaitingSalary')
    : t('reportingOnly');

  return (
    <div
      className="rounded-2xl border border-white/[0.06] bg-[#0e0e1c] mb-6 overflow-hidden"
      style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}
      data-testid="forecast-reporting-header"
    >
      <div className="flex items-center justify-between gap-3 px-6 py-3 border-b border-white/[0.04]">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-[11px] font-bold tracking-[0.12em] uppercase text-white/45">
            {t('reportingPeriod')}
          </span>
          <span className="text-[11px] text-white/30 truncate">{reason}</span>
        </div>
        {start && end && (
          <div className="flex items-center gap-1.5 text-[11px] text-white/35 flex-shrink-0">
            <Calendar className="w-3.5 h-3.5" />
            <span className="font-mono">{fmtShort(start)} – {fmtShort(end)}</span>
          </div>
        )}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-px bg-white/[0.035]">
        {[
          { key: 'income', label: t('income'), value: income, cls: 'text-white' },
          { key: 'spent', label: t('spentSoFar'), value: spent, cls: 'text-white' },
          {
            key: 'net',
            label: t('net'),
            value: net,
            cls: net != null && net < 0 ? 'text-rose-400' : 'text-emerald-400',
          },
        ].map(({ key, label, value, cls }) => (
          <div key={key} className="bg-[#0e0e1c] px-4 md:px-5 py-5 md:py-6 min-w-0">
            <div className="text-[9px] tracking-[0.12em] uppercase text-white/35 mb-2.5">{label}</div>
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

export default function CycleForecastHero({ projData, loading, period }) {
  const t = useTranslations('analytics');
  const formatCurrency = useFormatCurrency();

  if (loading) {
    return (
      <div className="mb-6" style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}>
        <div className="rounded-2xl bg-[#0e0e1c] border border-white/[0.06] p-6 md:p-8 h-40 animate-pulse" />
      </div>
    );
  }

  if (!projData) return null;

  // Reporting mode (Stage 2.8): MONTHLY / CUSTOM / LAST_PAY_CYCLE, a closed cycle, an AWAITING_SALARY
  // cycle or a non-salary wallet carry no projection — show the period and its actuals, nothing else.
  if (!projData.projectionAvailable) {
    return (
      <ReportingHeader
        projData={projData}
        period={period}
        t={t}
        formatCurrency={formatCurrency}
      />
    );
  }

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
        animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both',
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
                {fmtShort(period.startDate)} – {fmtShort(period.endDate)}
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
