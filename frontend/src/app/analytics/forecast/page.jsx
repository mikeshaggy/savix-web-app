'use client';
import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import ErrorState from '@/components/common/ErrorState';
import { useTranslations } from 'next-intl';
import { useWallets } from '@/contexts/WalletContext';
import { useAnalyticsPeriod } from '@/hooks/useAnalyticsPeriod';
import { useFeatures } from '@/hooks/useFeatures';
import { analyticsApi, transactionApi } from '@/lib/api';
import { forecastPageVariant } from '@/lib/forecastV2';
import { createExcludeAction } from '@/lib/forecastBreakdown';
import CycleForecastHero from '@/components/analytics/CycleForecastHero';
import SpendingTrajectoryChart from '@/components/analytics/SpendingTrajectoryChart';
import SpendingPacePanel from '@/components/analytics/SpendingPacePanel';
import ForecastBreakdown from '@/components/analytics/ForecastBreakdown';
import ProjectionCards from '@/components/analytics/ProjectionCards';

export default function AnalyticsForecastPage() {
  const t = useTranslations('analytics');
  const { currentWallet } = useWallets();
  const { periodType, resolvedStart, resolvedEnd } = useAnalyticsPeriod();
  const { forecastV2 } = useFeatures();

  const [projData,    setProjData]    = useState(null);
  const [projLoading, setProjLoading] = useState(false);
  const [projError,   setProjError]   = useState(null);
  const [period,      setPeriod]      = useState(null);

  // The parameters currently on screen: a response for any other wallet / period (e.g. a silent refetch started
  // before the user switched) is dropped instead of overwriting newer data.
  const currentParams = useRef(null);
  currentParams.current = [currentWallet?.id, periodType, resolvedStart, resolvedEnd].join('|');

  // `silent` refetches (after an Exclude) keep the current panels on screen instead of the loading skeletons and
  // rethrow failures to the caller instead of replacing the page with an error state.
  const fetchProjections = useCallback(async (wId, pType, sDate, eDate, { silent = false } = {}) => {
    const key = [wId, pType, sDate, eDate].join('|');
    if (!silent) setProjLoading(true);
    if (!silent) setProjError(null);
    try {
      const result = await analyticsApi.getProjections(wId, pType, sDate, eDate);
      if (silent && key !== currentParams.current) return;
      setProjData(result);
      // Derive period display dates from projection response
      if (result?.startDate && result?.endDate) {
        setPeriod({ startDate: result.startDate, endDate: result.endDate });
      }
    } catch (err) {
      if (silent) throw err;
      console.error('Failed to fetch projections:', err);
      setProjError(err.message || t('projectionErrorLoading'));
    } finally {
      if (!silent) setProjLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!currentWallet?.id || !periodType) return;
    if (periodType === 'CUSTOM' && (!resolvedStart || !resolvedEnd)) return;
    fetchProjections(currentWallet.id, periodType, resolvedStart, resolvedEnd);
  }, [currentWallet?.id, periodType, resolvedStart, resolvedEnd, fetchProjections]);

  // Stage 5.5 Exclude (plan: `transactionApi.updateTransaction` with `excludedFromPace: true`, then refetch):
  // persist the flag, then silently refetch the projection so hero, pace, trajectory, breakdown and cards all
  // re-read the backend — the excluded state is never held only in the browser. Duplicate clicks share one write.
  // Deliberately not the AppContext mutation: its wallet reload makes AnalyticsShell swap this page for a loader
  // (remount + full refetch), and pace exclusion changes no wallet balance. Other pages refetch on mount.
  const excludeOneOff = useMemo(() => createExcludeAction({
    load: (id) => transactionApi.getTransactionById(id),
    save: (id, body) => transactionApi.updateTransaction(id, body),
    refresh: () => fetchProjections(currentWallet?.id, periodType, resolvedStart, resolvedEnd, { silent: true }),
  }), [fetchProjections, currentWallet?.id, periodType, resolvedStart, resolvedEnd]);

  // Stage 5.6: the v2 projection cards belong to the open salary cycle with Forecast v2 only
  const showCards = forecastPageVariant({ forecastV2, projData }) === 'v2';

  return (
    <div style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}>
      {/* ── Error ───────────────────────────────────────────────────────────── */}
      {projError && !projLoading && (
        <ErrorState
          variant="page"
          title={projError}
          onRetry={() => fetchProjections(currentWallet.id, periodType, resolvedStart, resolvedEnd)}
          retryLabel={t('retry')}
          className="mb-5"
        />
      )}

      {/* ── 1. Hero ─────────────────────────────────────────────────────────── */}
      <div id="forecast-hero-section">
        <CycleForecastHero
          projData={projData}
          loading={projLoading}
          period={period}
        />
      </div>

      {/* ── 1b. Projection cards (Forecast v2 only) ───────────────────────────── */}
      {showCards && !projLoading && (
        <div className="mb-5">
          <ProjectionCards data={projData} />
        </div>
      )}

      {/* ── 2. Trajectory + Pace ────────────────────────────────────────────── */}
      <div id="spending-trajectory-section" className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-5">
        <SpendingTrajectoryChart projData={projData} loading={projLoading} />
        <SpendingPacePanel       projData={projData} loading={projLoading} />
      </div>

      {/* ── 3. Forecast breakdown ledger ────────────────────────────────────── */}
      <div className="mb-5">
        <ForecastBreakdown projData={projData} loading={projLoading} onExcludeOneOff={excludeOneOff} />
      </div>
    </div>
  );
}
