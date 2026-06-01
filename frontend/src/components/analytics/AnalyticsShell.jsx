'use client';
import React, { useState, useEffect } from 'react';
import { Wallet } from 'lucide-react';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useWallets } from '@/contexts/WalletContext';
import { useAnalyticsPeriod } from '@/hooks/useAnalyticsPeriod';
import { analyticsApi } from '@/lib/api';
import PeriodSelector from '@/components/common/PeriodSelector';
import { Loading } from '@/components/common/Loading';
import EmptyState from '@/components/common/EmptyState';
import AnalyticsSubNav from './AnalyticsSubNav';

// Map each analytics subpage path to its title/subtitle translation keys.
// These keys exist in the 'analytics' namespace and are used by each page's
// own header; the shell now owns the rendering so pages no longer need them.
const PAGE_META = {
  '/analytics/overview':   { titleKey: 'overviewTitle',    subtitleKey: 'overviewSubtitle' },
  '/analytics/budgets':    { titleKey: 'budgetsTitle',     subtitleKey: 'budgetsSubtitle' },
  '/analytics/forecast':   { titleKey: 'forecastTitle',    subtitleKey: 'forecastSubtitle' },
  '/analytics/breakdown':  { titleKey: 'breakdownTitle',   subtitleKey: 'breakdownSubtitle' },
  '/analytics/comparison': { titleKey: 'comparisonTitle',  subtitleKey: 'comparisonSubtitle' },
  '/analytics/daily':      { titleKey: 'dailyTitle',       subtitleKey: 'dailySubtitle' },
};

/**
 * Client-side shell shared by all /analytics/* pages.
 *
 * Renders:
 *   1. Two-column header — page title + subtitle on the left,
 *      period selector (pill above tabs) on the right.
 *   2. Sub-navigation tabs (Overview / Forecast / Breakdown / Comparison / Daily).
 *   3. The current page's content ({children}).
 */
export default function AnalyticsShell({ children }) {
  const t        = useTranslations('analytics');
  const pathname = usePathname();

  const { currentWallet, loading: walletsLoading } = useWallets();
  const {
    periodType,
    selectedMonth,
    startDate,
    endDate,
    resolvedStart,
    resolvedEnd,
    setPeriodType,
    setMonth,
    setCustomDates,
  } = useAnalyticsPeriod();

  // Resolved dates for the period pill — fetched from the backend so that
  // PAY_CYCLE and LAST_PAY_CYCLE show actual date ranges, not just "—".
  const [displayStart, setDisplayStart] = useState(null);
  const [displayEnd,   setDisplayEnd]   = useState(null);

  useEffect(() => {
    if (!currentWallet?.id || !periodType) return;
    // For CUSTOM the pill already shows the user-entered dates directly;
    // no need for an extra round-trip.
    if (periodType === 'CUSTOM') {
      setDisplayStart(startDate);
      setDisplayEnd(endDate);
      return;
    }
    let cancelled = false;
    analyticsApi
      .resolvePeriod(currentWallet.id, periodType, resolvedStart, resolvedEnd)
      .then((p) => {
        if (!cancelled) {
          setDisplayStart(p.startDate ?? null);
          setDisplayEnd(p.endDate ?? null);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setDisplayStart(null);
          setDisplayEnd(null);
        }
      });
    return () => { cancelled = true; };
  }, [currentWallet?.id, periodType, resolvedStart, resolvedEnd, startDate, endDate]);

  // ── Loading ────────────────────────────────────────────────────────────────
  if (walletsLoading) {
    return <Loading message={t('loadingAnalytics')} />;
  }

  // ── No wallet ──────────────────────────────────────────────────────────────
  if (!currentWallet) {
    return (
      <EmptyState
        variant="page"
        icon={Wallet}
        title={t('noWallet')}
        description={t('noWalletDesc')}
      />
    );
  }

  const meta = PAGE_META[pathname] ?? { titleKey: 'title', subtitleKey: 'subtitle' };

  return (
    <>
      {/* ── Page header: title left · period selector right ───────────────── */}
      <div
        className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between mb-5"
        style={{ animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both' }}
      >
        {/* Left — page title + subtitle (driven by current pathname) */}
        <div>
          <h1 className="text-xl font-bold text-white leading-tight">
            {t(meta.titleKey)}
          </h1>
          <p className="text-sm text-white/40 mt-0.5">
            {t(meta.subtitleKey)}
          </p>
        </div>

        {/* Right — period selector: pill above, segmented tabs below */}
        <PeriodSelector
          periodType={periodType}
          selectedMonth={selectedMonth}
          startDate={startDate}
          endDate={endDate}
          displayStart={displayStart}
          displayEnd={displayEnd}
          onPeriodTypeChange={setPeriodType}
          onMonthChange={setMonth}
          onCustomDateChange={setCustomDates}
          align="end"
        />
      </div>

      {/* ── Sub-navigation tabs ────────────────────────────────────────────── */}
      <AnalyticsSubNav />

      {/* ── Page content ──────────────────────────────────────────────────── */}
      {children}
    </>
  );
}
