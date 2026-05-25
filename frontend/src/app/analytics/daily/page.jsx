'use client';
import React from 'react';
import { useWallets } from '@/contexts/WalletContext';
import { useAnalyticsPeriod } from '@/hooks/useAnalyticsPeriod';
import SpendingHeatmap from '@/components/analytics/SpendingHeatmap';

export default function AnalyticsDailyPage() {
  const { currentWallet } = useWallets();
  const { periodType, resolvedStart, resolvedEnd } = useAnalyticsPeriod();

  return (
    <div style={{ animation: 'fadeUp 0.35s ease both' }}>
      {/* ── Daily heatmap ───────────────────────────────────────────────────── */}
      <SpendingHeatmap
        walletId={currentWallet.id}
        periodType={periodType}
        startDate={resolvedStart}
        endDate={resolvedEnd}
      />
    </div>
  );
}
