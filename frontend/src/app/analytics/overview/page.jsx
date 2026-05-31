'use client';
import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  TrendingUp, PieChart, GitCompareArrows, CalendarDays,
  Wallet, ShieldCheck, Flame, BarChart3,
  CheckCircle2, AlertTriangle, AlertCircle, Clock,
  ChevronRight,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useWallets } from '@/contexts/WalletContext';
import { useAnalyticsPeriod } from '@/hooks/useAnalyticsPeriod';
import { analyticsApi } from '@/lib/api';
import { formatCurrency } from '@/utils/helpers';
import InsightCards from '@/components/analytics/InsightCards';
import ErrorState from '@/components/common/ErrorState';

// ─── status config ────────────────────────────────────────────────────────────

const STATUS_CONFIG = {
  GOOD: {
    Icon: CheckCircle2,
    iconColor: '#4ade80',
    cardClass: 'border-emerald-500/20 bg-emerald-500/[0.04]',
    titleKey: 'overviewStatusGood',
    msgKey:   'overviewStatusGoodMsg',
  },
  WARNING: {
    Icon: AlertTriangle,
    iconColor: '#fbbf24',
    cardClass: 'border-amber-500/20 bg-amber-500/[0.05]',
    titleKey: 'overviewStatusWarning',
    msgKey:   'overviewStatusWarningMsg',
  },
  CRITICAL: {
    Icon: AlertCircle,
    iconColor: '#f87171',
    cardClass: 'border-red-500/20 bg-red-500/[0.05]',
    titleKey: 'overviewStatusCritical',
    msgKey:   'overviewStatusCriticalMsg',
  },
  NEUTRAL: {
    Icon: Clock,
    iconColor: '#9ca3af',
    cardClass: 'border-white/[0.08] bg-white/[0.02]',
    titleKey: 'overviewStatusNeutral',
    msgKey:   'overviewStatusNeutralMsg',
  },
};

// ─── status hero ─────────────────────────────────────────────────────────────

function StatusHero({ summary, t }) {
  const cfg = STATUS_CONFIG[summary.status] ?? STATUS_CONFIG.NEUTRAL;
  const { Icon, iconColor, cardClass, titleKey, msgKey } = cfg;

  const elapsed = summary.daysElapsed ?? 0;
  const total   = summary.daysInPeriod ?? 1;
  const pct     = Math.min(100, Math.round((elapsed / total) * 100));

  return (
    <div className={`rounded-xl border p-5 mb-5 ${cardClass}`}>
      <div className="flex items-start gap-3">
        <div
          className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0 mt-0.5"
          style={{ background: `${iconColor}18` }}
        >
          <Icon className="w-5 h-5" style={{ color: iconColor }} />
        </div>

        <div className="flex-1 min-w-0">
          <p className="text-[14px] font-bold text-white/90 leading-tight mb-0.5">
            {t(titleKey)}
          </p>
          <p className="text-[12px] text-white/45 leading-relaxed">
            {t(msgKey)}
          </p>
        </div>

        {/* day progress badge */}
        {summary.projectionAvailable && (
          <div className="flex-shrink-0 text-right">
            <p className="text-[10px] font-bold tracking-[0.08em] text-white/25 uppercase mb-1">
              {t('overviewDaysProgress', { elapsed, total: summary.daysInPeriod })}
            </p>
            <div className="w-28 h-1.5 bg-white/[0.08] rounded-full overflow-hidden">
              <div
                className="h-full rounded-full transition-all"
                style={{ width: `${pct}%`, background: iconColor }}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── KPI chip ─────────────────────────────────────────────────────────────────

function KpiChip({ icon: Icon, label, value, helper, color }) {
  return (
    <div className="flex flex-col gap-1 p-4 bg-[#0e0e1c] border border-white/[0.06] rounded-xl">
      <div className="flex items-center gap-2 mb-0.5">
        <div
          className="w-6 h-6 rounded-md flex items-center justify-center flex-shrink-0"
          style={{ background: `${color}18` }}
        >
          <Icon className="w-3.5 h-3.5" style={{ color }} />
        </div>
        <span className="text-[10px] font-bold tracking-[0.1em] uppercase text-white/25 truncate">
          {label}
        </span>
      </div>
      <p className="font-mono text-[18px] font-bold leading-tight" style={{ color }}>
        {value}
      </p>
      {helper && (
        <p className="text-[10px] text-white/20 leading-tight mt-0.5">{helper}</p>
      )}
    </div>
  );
}

// ─── explore card ─────────────────────────────────────────────────────────────

function ExploreCard({ href, icon: Icon, color, title, desc, valueLine, qs }) {
  const fullHref = qs ? `${href}?${qs}` : href;
  return (
    <Link
      href={fullHref}
      className="group flex items-start gap-3 p-4 bg-[#0e0e1c] border border-white/[0.06] rounded-xl hover:border-white/[0.12] hover:bg-white/[0.02] transition-all"
    >
      <div
        className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0 mt-0.5 transition-transform group-hover:scale-105"
        style={{ background: `${color}18` }}
      >
        <Icon style={{ color, width: 18, height: 18 }} />
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-[13px] font-semibold text-white/80 leading-tight mb-0.5">{title}</p>
        <p className="text-[11px] text-white/30 leading-snug mb-2">{desc}</p>
        {valueLine && (
          <p
            className="text-[11px] font-semibold leading-tight"
            style={{ color: `${color}cc` }}
          >
            {valueLine}
          </p>
        )}
      </div>

      <ChevronRight className="w-4 h-4 text-white/[0.15] group-hover:text-white/30 transition-colors flex-shrink-0 mt-1" />
    </Link>
  );
}

// ─── loading skeleton ─────────────────────────────────────────────────────────

function OverviewSkeleton() {
  return (
    <>
      <div className="h-20 rounded-xl bg-[#0e0e1c] animate-pulse mb-5" />
      <div className="h-24 rounded-xl bg-[#0e0e1c] animate-pulse mb-5" />
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-24 rounded-xl bg-[#0e0e1c] animate-pulse" />
        ))}
      </div>
    </>
  );
}

// ─── main page ────────────────────────────────────────────────────────────────

export default function AnalyticsOverviewPage() {
  const t = useTranslations('analytics');
  const { currentWallet } = useWallets();
  const { periodType, resolvedStart, resolvedEnd } = useAnalyticsPeriod();
  const searchParams = useSearchParams();
  const qs = searchParams.toString();

  const [summary,  setSummary]  = useState(null);
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState(null);

  const fetchSummary = useCallback(async (wId, pType, sDate, eDate) => {
    setLoading(true);
    setError(null);
    try {
      const result = await analyticsApi.getSummary(wId, pType, sDate, eDate);
      setSummary(result);
    } catch (err) {
      console.error('Failed to fetch analytics summary:', err);
      setError(err.message || t('overviewErrorLoading'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!currentWallet?.id || !periodType) return;
    if (periodType === 'CUSTOM' && (!resolvedStart || !resolvedEnd)) return;
    fetchSummary(currentWallet.id, periodType, resolvedStart, resolvedEnd);
  }, [currentWallet?.id, periodType, resolvedStart, resolvedEnd, fetchSummary]);

  // ── derived values ───────────────────────────────────────────────────────────
  const isActive    = summary?.projectionAvailable ?? false;
  const endBalance  = summary?.projectedEndBalance ?? 0;
  const safeToday   = summary?.safeToSpend ?? 0;
  const safePerDay  = summary?.safeToSpendPerDay ?? 0;
  const burnRate    = summary?.dailyBurnRate ?? 0;
  const savingsRate = summary?.savingsRate ?? null;

  const balanceColor = endBalance >= 0 ? '#4ade80' : '#f87171';
  const safeColor    = !isActive ? '#9ca3af'
                      : safePerDay >= 0 ? '#4ade80' : '#f87171';
  const savingsColor = (savingsRate === null || savingsRate >= 0) ? '#4ade80' : '#f87171';

  // ── explore card values ──────────────────────────────────────────────────────
  const forecastValue = summary?.projectedTotalSpend != null
    ? t('overviewCardForecastValue', { amount: formatCurrency(summary.projectedTotalSpend) })
    : null;

  const breakdownValue = summary?.topCategoryName
    ? t('overviewCardBreakdownValue', {
        emoji: summary.topCategoryEmoji ?? '📦',
        name:  summary.topCategoryName,
      })
    : null;

  const comparisonValue = summary?.comparisonAvailable && summary?.expensesDeltaPercent != null
    ? t('overviewCardComparisonValue', {
        delta: Number(summary.expensesDeltaPercent) >= 0
          ? `+${Number(summary.expensesDeltaPercent).toFixed(1)}`
          : Number(summary.expensesDeltaPercent).toFixed(1),
      })
    : null;

  const dailyValue = summary?.highestSpendingDay
    ? (() => {
        const d = new Date(summary.highestSpendingDay + 'T12:00:00');
        return t('overviewCardDailyValue', {
          date: d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
        });
      })()
    : null;

  // ── render ───────────────────────────────────────────────────────────────────
  return (
    <div style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}>

      {/* Loading state */}
      {loading && <OverviewSkeleton />}

      {/* Error state */}
      {error && !loading && (
        <ErrorState
          variant="inline"
          title={error}
          onRetry={() => fetchSummary(currentWallet.id, periodType, resolvedStart, resolvedEnd)}
          retryLabel={t('common.retry')}
          className="mb-5"
        />
      )}

      {/* Main content */}
      {!loading && !error && summary && (
        <>
          {/* Status hero */}
          <StatusHero summary={summary} t={t} />

          {/* Insights (self-fetching) */}
          <InsightCards
            walletId={currentWallet.id}
            periodType={periodType}
            startDate={resolvedStart}
            endDate={resolvedEnd}
          />

          {/* KPI chips */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
            <KpiChip
              icon={Wallet}
              label={t('overviewKpiBalance')}
              value={formatCurrency(endBalance)}
              helper={t('overviewKpiBalanceHelper')}
              color={balanceColor}
            />
            <KpiChip
              icon={ShieldCheck}
              label={t('overviewKpiSafe')}
              value={isActive ? `${formatCurrency(safePerDay)} ${t('perDay')}` : t('projectionNotApplicable')}
              helper={isActive ? t('overviewKpiSafeTotalHelper', { amount: formatCurrency(safeToday) }) : t('overviewKpiSafeHelper')}
              color={safeColor}
            />
            <KpiChip
              icon={Flame}
              label={t('overviewKpiBurn')}
              value={formatCurrency(burnRate)}
              helper={t('overviewKpiBurnHelper')}
              color="#fb923c"
            />
            <KpiChip
              icon={BarChart3}
              label={t('overviewKpiSavings')}
              value={savingsRate !== null ? `${Number(savingsRate).toFixed(1)}%` : '—'}
              helper={t('overviewKpiSavingsHelper')}
              color={savingsColor}
            />
          </div>

          {/* Explore section */}
          <div>
            <p className="text-[10px] font-bold tracking-[0.1em] uppercase text-white/30 mb-3">
              {t('exploreMore')}
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <ExploreCard
                href="/analytics/forecast"
                icon={TrendingUp}
                color="#a78bfa"
                title={t('navForecast')}
                desc={t('cardForecastDesc')}
                valueLine={forecastValue}
                qs={qs}
              />
              <ExploreCard
                href="/analytics/breakdown"
                icon={PieChart}
                color="#60a5fa"
                title={t('navBreakdown')}
                desc={t('cardBreakdownDesc')}
                valueLine={breakdownValue}
                qs={qs}
              />
              <ExploreCard
                href="/analytics/comparison"
                icon={GitCompareArrows}
                color="#34d399"
                title={t('navComparison')}
                desc={t('cardComparisonDesc')}
                valueLine={comparisonValue}
                qs={qs}
              />
              <ExploreCard
                href="/analytics/daily"
                icon={CalendarDays}
                color="#fb923c"
                title={t('navDaily')}
                desc={t('cardDailyDesc')}
                valueLine={dailyValue}
                qs={qs}
              />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
