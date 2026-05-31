'use client';
import React from 'react';
import { useWallets } from '@/contexts/WalletContext';
import { useAnalyticsPeriod } from '@/hooks/useAnalyticsPeriod';
import SpendingHeatmap from '@/components/analytics/SpendingHeatmap';

export default function AnalyticsDailyPage() {
  const { currentWallet } = useWallets();
  const { periodType, resolvedStart, resolvedEnd } = useAnalyticsPeriod();

  return (
    <div style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}>
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
