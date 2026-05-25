'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { AlertCircle } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useWallets } from '@/contexts/WalletContext';
import { useAnalyticsPeriod } from '@/hooks/useAnalyticsPeriod';
import { analyticsApi } from '@/lib/api';
import CycleForecastHero from '@/components/analytics/CycleForecastHero';
import SpendingTrajectoryChart from '@/components/analytics/SpendingTrajectoryChart';
import SpendingPacePanel from '@/components/analytics/SpendingPacePanel';
import ForecastBreakdown from '@/components/analytics/ForecastBreakdown';

export default function AnalyticsForecastPage() {
  const t = useTranslations('analytics');
  const { currentWallet } = useWallets();
  const { periodType, resolvedStart, resolvedEnd } = useAnalyticsPeriod();

  const [projData,    setProjData]    = useState(null);
  const [projLoading, setProjLoading] = useState(false);
  const [projError,   setProjError]   = useState(null);
  const [period,      setPeriod]      = useState(null);

  const fetchProjections = useCallback(async (wId, pType, sDate, eDate) => {
    setProjLoading(true);
    setProjError(null);
    try {
      const result = await analyticsApi.getProjections(wId, pType, sDate, eDate);
      setProjData(result);
      // Derive period display dates from projection response
      if (result?.startDate && result?.endDate) {
        setPeriod({ startDate: result.startDate, endDate: result.endDate });
      }
    } catch (err) {
      console.error('Failed to fetch projections:', err);
      setProjError(err.message || t('projectionErrorLoading'));
    } finally {
      setProjLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!currentWallet?.id || !periodType) return;
    if (periodType === 'CUSTOM' && (!resolvedStart || !resolvedEnd)) return;
    fetchProjections(currentWallet.id, periodType, resolvedStart, resolvedEnd);
  }, [currentWallet?.id, periodType, resolvedStart, resolvedEnd, fetchProjections]);

  return (
    <div style={{ animation: 'fadeUp 0.35s ease both' }}>
      {/* ── Error ───────────────────────────────────────────────────────────── */}
      {projError && !projLoading && (
        <div className="flex flex-col items-center gap-4 py-10 text-center mb-5">
          <AlertCircle className="w-9 h-9 text-red-400" />
          <p className="text-white/60 text-sm">{projError}</p>
          <button
            onClick={() => fetchProjections(currentWallet.id, periodType, resolvedStart, resolvedEnd)}
            className="px-4 py-2 bg-violet-600 text-white text-xs rounded-lg hover:bg-violet-700 transition-colors"
          >
            {t('retry')}
          </button>
        </div>
      )}

      {/* ── 1. Hero ─────────────────────────────────────────────────────────── */}
      <div id="forecast-hero-section">
        <CycleForecastHero
          projData={projData}
          loading={projLoading}
          period={period}
        />
      </div>

      {/* ── 2. Trajectory + Pace ────────────────────────────────────────────── */}
      <div id="spending-trajectory-section" className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-5">
        <SpendingTrajectoryChart projData={projData} loading={projLoading} />
        <SpendingPacePanel       projData={projData} loading={projLoading} />
      </div>

      {/* ── 3. Forecast breakdown ledger ────────────────────────────────────── */}
      <div className="mb-5">
        <ForecastBreakdown projData={projData} loading={projLoading} />
      </div>
    </div>
  );
}
