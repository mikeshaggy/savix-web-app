'use client';
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Receipt, TrendingUp, Repeat2, AlertTriangle } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useWallets } from '@/contexts/WalletContext';
import { useAnalyticsPeriod } from '@/hooks/useAnalyticsPeriod';
import { analyticsApi } from '@/lib/api';
import { formatCurrency } from '@/utils/helpers';
import SpendingBreakdownSection from '@/components/analytics/SpendingBreakdownSection';
import SpendingLeaksSection from '@/components/analytics/SpendingLeaksSection';
import ErrorState from '@/components/common/ErrorState';

// ─── KPI chip ─────────────────────────────────────────────────────────────────

/**
 * @param {{
 *   icon: React.ComponentType,
 *   label: string,
 *   value: string,
 *   sub: string | null,
 *   color: string,
 *   loading: boolean,
 * }} props
 */
function KpiChip({ icon: Icon, label, value, sub, color, loading }) {
  return (
    <div className="flex flex-col gap-1 p-4 bg-[#0e0e1c] border border-white/[0.06] rounded-xl">
      <div className="flex items-center gap-2 mb-0.5">
        <div
          className="w-6 h-6 rounded-md flex items-center justify-center flex-shrink-0"
          style={{ background: `${color}18` }}
        >
          <Icon className="w-3 h-3" style={{ color }} />
        </div>
        <span className="text-[10px] font-bold tracking-[0.1em] uppercase text-white/25 truncate">
          {label}
        </span>
      </div>

      {loading ? (
        <div className="h-5 w-20 bg-white/[0.06] rounded animate-pulse mt-1" />
      ) : (
        <>
          <p
            className="font-mono text-[17px] font-bold leading-tight truncate"
            style={{ color }}
          >
            {value}
          </p>
          {sub && (
            <p className="text-[10px] text-white/20 leading-tight mt-0.5 truncate">{sub}</p>
          )}
        </>
      )}
    </div>
  );
}

// ─── main page ────────────────────────────────────────────────────────────────

export default function AnalyticsBreakdownPage() {
  const t = useTranslations('analytics');
  const { currentWallet } = useWallets();
  const { periodType, resolvedStart, resolvedEnd } = useAnalyticsPeriod();

  const [categoryData,      setCategoryData]      = useState(null);
  const [categoryLoading,   setCategoryLoading]   = useState(false);
  const [importanceData,    setImportanceData]    = useState(null);
  const [importanceLoading, setImportanceLoading] = useState(false);
  const [fetchError,        setFetchError]        = useState(null);

  const fetchBreakdowns = useCallback(async (wId, pType, sDate, eDate) => {
    setCategoryLoading(true);
    setImportanceLoading(true);
    setFetchError(null);
    try {
      const [catResult, impResult] = await Promise.allSettled([
        analyticsApi.getCategoryBreakdown(wId, pType, sDate, eDate),
        analyticsApi.getImportanceBreakdown(wId, pType, sDate, eDate),
      ]);
      setCategoryData(catResult.status   === 'fulfilled' ? catResult.value   : null);
      setImportanceData(impResult.status === 'fulfilled' ? impResult.value   : null);
      if (catResult.status !== 'fulfilled' && impResult.status !== 'fulfilled') {
        setFetchError(t('errorLoading'));
      }
    } finally {
      setCategoryLoading(false);
      setImportanceLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!currentWallet?.id || !periodType) return;
    if (periodType === 'CUSTOM' && (!resolvedStart || !resolvedEnd)) return;
    fetchBreakdowns(currentWallet.id, periodType, resolvedStart, resolvedEnd);
  }, [currentWallet?.id, periodType, resolvedStart, resolvedEnd, fetchBreakdowns]);

  // ── KPI derivations ──────────────────────────────────────────────────────────
  const kpiData = useMemo(() => {
    const cats = categoryData?.categories ?? [];
    const total = Number(categoryData?.totalExpenses ?? 0);

    // Top category by amount (API already sorts desc)
    const topCat = cats[0] ?? null;

    // Most frequent by transaction count
    const mostFrequent = cats.length > 0
      ? [...cats].sort((a, b) => (b.transactionCount ?? 0) - (a.transactionCount ?? 0))[0]
      : null;

    // Problem spend (SHOULDNT_HAVE from importance breakdown)
    const problemItem = importanceData?.breakdown?.find(
      (b) => b.importance === 'SHOULDNT_HAVE'
    ) ?? null;

    return { cats, total, topCat, mostFrequent, problemItem };
  }, [categoryData, importanceData]);

  const kpiLoading = categoryLoading || importanceLoading;

  // ── render ───────────────────────────────────────────────────────────────────
  return (
    <div style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}>
      {/* Error banner (only when both calls fail) */}
      {fetchError && !kpiLoading && (
        <ErrorState
          variant="inline"
          title={fetchError}
          onRetry={() => fetchBreakdowns(currentWallet.id, periodType, resolvedStart, resolvedEnd)}
          retryLabel={t('retry')}
          className="mb-5"
        />
      )}

      {/* ── KPI chips ──────────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
        {/* Total Spent */}
        <KpiChip
          icon={Receipt}
          label={t('breakdownKpiTotal')}
          value={kpiData.total > 0 ? formatCurrency(kpiData.total) : t('breakdownKpiNoData')}
          sub={
            kpiData.cats.length > 0
              ? t('breakdownKpiTotalHelper', { count: kpiData.cats.length })
              : null
          }
          color="#a78bfa"
          loading={categoryLoading}
        />

        {/* Top Category */}
        <KpiChip
          icon={TrendingUp}
          label={t('breakdownKpiTop')}
          value={
            kpiData.topCat
              ? `${kpiData.topCat.emoji ?? ''} ${kpiData.topCat.name}`.trim()
              : t('breakdownKpiNoData')
          }
          sub={
            kpiData.topCat
              ? t('breakdownKpiTopHelper', {
                  share: Number(kpiData.topCat.share ?? 0).toFixed(1),
                })
              : null
          }
          color="#60a5fa"
          loading={categoryLoading}
        />

        {/* Most Frequent */}
        <KpiChip
          icon={Repeat2}
          label={t('breakdownKpiFrequent')}
          value={
            kpiData.mostFrequent
              ? `${kpiData.mostFrequent.emoji ?? ''} ${kpiData.mostFrequent.name}`.trim()
              : t('breakdownKpiNoData')
          }
          sub={
            kpiData.mostFrequent
              ? t('breakdownKpiFrequentHelper', {
                  count: kpiData.mostFrequent.transactionCount ?? 0,
                })
              : null
          }
          color="#fb923c"
          loading={categoryLoading}
        />

        {/* Problem Spend (SHOULDNT_HAVE) */}
        <KpiChip
          icon={AlertTriangle}
          label={t('breakdownKpiProblem')}
          value={
            kpiData.problemItem
              ? formatCurrency(kpiData.problemItem.amount)
              : t('breakdownKpiNoData')
          }
          sub={
            kpiData.problemItem
              ? t('breakdownKpiProblemHelper', {
                  share: Number(kpiData.problemItem.share ?? 0).toFixed(1),
                })
              : null
          }
          color="#f87171"
          loading={importanceLoading}
        />
      </div>

      {/* ── Category + Importance charts ───────────────────────────────────────── */}
      <div id="breakdown-section">
        <SpendingBreakdownSection
          categoryData={categoryData}
          importanceData={importanceData}
          categoryLoading={categoryLoading}
          importanceLoading={importanceLoading}
        />
      </div>

      {/* ── Spending Leaks ─────────────────────────────────────────────────────── */}
      <SpendingLeaksSection
        categoryData={categoryData}
        loading={categoryLoading}
      />
    </div>
  );
}
