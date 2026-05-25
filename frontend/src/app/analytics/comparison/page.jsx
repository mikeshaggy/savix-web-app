'use client';
import React from 'react';
import { useWallets } from '@/contexts/WalletContext';
import { useAnalyticsPeriod } from '@/hooks/useAnalyticsPeriod';
import BaselineComparisonChart from '@/components/analytics/BaselineComparisonChart';

export default function AnalyticsComparisonPage() {
  const { currentWallet } = useWallets();
  const { periodType, resolvedStart, resolvedEnd } = useAnalyticsPeriod();

  return (
    <div style={{ animation: 'fadeUp 0.35s ease both' }}>
      {/* ── Period comparison ───────────────────────────────────────────────── */}
      <BaselineComparisonChart
        walletId={currentWallet.id}
        periodType={periodType}
        startDate={resolvedStart}
        endDate={resolvedEnd}
      />
    </div>
  );
}
