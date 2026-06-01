'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Wallet } from 'lucide-react';
import { useWallets } from '@/contexts/WalletContext';
import { useAppContext } from '@/contexts/AppContext';
import EmptyState from '../common/EmptyState';
import ErrorState from '../common/ErrorState';
import { dashboardApi } from '@/lib/api';
import DashboardHeader from '../dashboard/DashboardHeader';
import CycleHealthHero from '../dashboard/CycleHealthHero';
import SummaryCards from '../dashboard/SummaryCards';
import FixedTransactionsTile from '../dashboard/FixedTransactionsTile';
import InsightsCard from '../dashboard/InsightsCard';
import CategoryPressureCard from '../dashboard/CategoryPressureCard';
import PreviousCyclePreview from '../dashboard/PreviousCyclePreview';
import { useTranslations } from 'next-intl';

// ─── Dashboard skeleton ───────────────────────────────────────────────────────
// Shown during walletsLoading and dashboard data loading.
// Mirrors the real dashboard layout shape without real data.
function DashboardSkeleton() {
  return (
    <div className="animate-pulse">

      {/* Header: title + wallet name left, period selector right */}
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between mb-6">
        <div className="flex flex-col gap-2">
          <div className="h-8 w-32 bg-white/[0.06] rounded-lg" />
          <div className="h-4 w-24 bg-white/[0.04] rounded" />
        </div>
        <div className="flex flex-col items-start md:items-end gap-2">
          <div className="h-9 w-56 bg-white/[0.04] rounded-xl" />
          <div className="h-9 w-72 bg-white/[0.04] rounded-xl" />
        </div>
      </div>

      {/* CycleHealthHero shape */}
      <div className="w-full bg-[#0e0e1c] border border-white/[0.06] rounded-[18px] overflow-hidden mb-5">
        {/* Status bar */}
        <div className="flex items-center justify-between px-6 py-3 border-b border-white/[0.06]">
          <div className="h-3 w-20 bg-white/[0.06] rounded" />
          <div className="h-3 w-24 bg-white/[0.04] rounded" />
        </div>
        {/* Metrics grid: primary balance + 3 secondary */}
        <div className="grid grid-cols-3 gap-px bg-white/[0.035] md:[grid-template-columns:minmax(0,1.35fr)_repeat(3,minmax(0,0.9fr))]">
          <div className="col-span-3 md:col-span-1 bg-[#0e0e1c] px-6 md:px-8 py-6 md:py-8">
            <div className="h-2.5 w-16 bg-white/[0.05] rounded mb-3" />
            <div className="h-10 w-40 bg-white/[0.07] rounded-lg" />
          </div>
          {[0, 1, 2].map((i) => (
            <div key={i} className="col-span-1 bg-[#0e0e1c] px-4 md:px-5 py-5 md:py-6">
              <div className="h-2 w-14 bg-white/[0.05] rounded mb-2.5" />
              <div className="h-7 w-24 bg-white/[0.06] rounded-lg" />
            </div>
          ))}
        </div>
        {/* Progress strip */}
        <div className="flex items-center gap-3 px-6 py-3 border-t border-white/[0.04]">
          <div className="h-2 w-16 bg-white/[0.04] rounded" />
          <div className="flex-1 h-[3px] bg-white/[0.06] rounded-full" />
          <div className="h-2 w-12 bg-white/[0.04] rounded" />
        </div>
      </div>

      {/* SummaryCards row: 4 equal cells */}
      <div className="mb-5 bg-[#0e0e1c] border border-white/[0.07] rounded-[20px] overflow-hidden">
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="relative py-5 sm:py-7 px-4 sm:px-6">
              {/* Top accent bar placeholder */}
              <div className="absolute top-0 left-0 right-0 h-[2px] bg-white/[0.06]" />
              {/* Vertical divider (mirrors real SummaryCards) */}
              {i > 0 && (
                <div className="absolute left-0 top-[15%] bottom-[15%] w-px bg-white/[0.07]" />
              )}
              <div className="h-2.5 w-16 bg-white/[0.05] rounded mb-4" />
              <div className="h-8 w-28 bg-white/[0.07] rounded-lg mb-3" />
              <div className="h-2.5 w-14 bg-white/[0.04] rounded" />
            </div>
          ))}
        </div>
      </div>

      {/* Operational grid — exact template from real dashboard */}
      <div className="grid grid-cols-1 gap-5 lg:[grid-template-columns:minmax(0,1.35fr)_minmax(360px,0.95fr)]">
        {/* Row 1 left — FixedTransactionsTile */}
        <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-[14px] h-[320px]" />
        {/* Row 1 right — CategoryPressureCard */}
        <div className="bg-[#0e0e1c] border border-white/[0.07] rounded-[18px] h-[320px]" />
        {/* Row 2 left — PreviousCyclePreview */}
        <div className="bg-[#0e0e1c] border border-white/[0.07] rounded-[18px] h-[220px]" />
        {/* Row 2 right — InsightsCard */}
        <div className="bg-[#0e0e1c] border border-white/[0.07] rounded-[18px] h-[220px]" />
      </div>

    </div>
  );
}

export default function DashboardPage() {
    const t = useTranslations();
    const { currentWallet, wallets, loading: walletsLoading } = useWallets();
    const { walletMutationVersion } = useAppContext();
    const router = useRouter();

    const [summary, setSummary] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [periodType, setPeriodType] = useState('PAY_CYCLE');
    const [selectedMonth, setSelectedMonth] = useState(() => {
        const now = new Date();
        return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    });
    const [customStartDate, setCustomStartDate] = useState(null);
    const [customEndDate, setCustomEndDate] = useState(null);


    const fetchSummary = useCallback(async (walletId, pType, startDate, endDate) => {
        setLoading(true);
        setError(null);
        try {
            const data = await dashboardApi.getDashboardSummary(walletId, {
                periodType: pType,
                startDate,
                endDate,
            });
            setSummary(data);
        } catch (err) {
            console.error('Failed to fetch dashboard summary:', err);
            setError(err.message || 'Failed to load dashboard data');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (!currentWallet?.id) return;
        if (periodType === 'CUSTOM' && (!customStartDate || !customEndDate)) return;

        if (periodType === 'CUSTOM') {
            fetchSummary(currentWallet.id, 'CUSTOM', customStartDate, customEndDate);
        } else if (periodType === 'MONTHLY') {
            fetchSummary(currentWallet.id, 'MONTHLY', `${selectedMonth}-01`, null);
        } else {
            fetchSummary(currentWallet.id, periodType, null, null);
        }
    }, [currentWallet?.id, periodType, selectedMonth, customStartDate, customEndDate, walletMutationVersion, fetchSummary]);

    const handlePeriodTypeChange = (newPeriodType) => {
        if (newPeriodType === 'CUSTOM') return;
        setPeriodType(newPeriodType);
        setCustomStartDate(null);
        setCustomEndDate(null);
    };

    const handleCustomDateChange = (startDate, endDate) => {
        setCustomStartDate(startDate);
        setCustomEndDate(endDate);
        setPeriodType('CUSTOM');
    };

    const handleMonthChange = (month) => {
        setSelectedMonth(month);
    };

    if (walletsLoading) {
        return <DashboardSkeleton />;
    }

    if (!walletsLoading && wallets.length === 0) {
        return (
            <EmptyState
                icon={Wallet}
                title={t('dashboard.welcomeToSavix')}
                description={t('dashboard.noWalletsYet')}
                action={{ label: t('dashboard.createFirstWallet'), onClick: () => router.push('/wallets') }}
            />
        );
    }

    if (!currentWallet && wallets.length > 0) {
        return (
            <EmptyState
                icon={Wallet}
                title={t('dashboard.noWalletSelected')}
                description={t('dashboard.selectWalletDashboard')}
                action={{ label: t('topbar.manageWallets'), onClick: () => router.push('/wallets') }}
            />
        );
    }

    if (loading) {
        return <DashboardSkeleton />;
    }

    if (error) {
        return (
            <ErrorState
                title={t('errors.errorLoadingDashboard')}
                description={error}
                onRetry={() => window.location.reload()}
                retryLabel={t('common.retry')}
            />
        );
    }

    if (!summary) {
        return null;
    }

    return (
        <div>
            {/* Header: wallet name + period selector */}
            <DashboardHeader
                walletName={summary.walletName}
                period={summary.period}
                periodType={periodType}
                selectedMonth={selectedMonth}
                onPeriodTypeChange={handlePeriodTypeChange}
                onMonthChange={handleMonthChange}
                onCustomDateChange={handleCustomDateChange}
                customStartDate={customStartDate}
                customEndDate={customEndDate}
            />

            {/* Hero: Cycle Health */}
            <CycleHealthHero cycleHealth={summary.cycleHealth} period={summary.period} />

            {/* KPI row */}
            <div className="mb-5">
                <SummaryCards kpis={summary.kpis} />
            </div>

            {/* Main operational grid — 2×2 on desktop, stacked on mobile.
                 Grid stretch (default) equalizes card heights per row automatically. */}
            <div
                className="grid grid-cols-1 gap-5 lg:[grid-template-columns:minmax(0,1.35fr)_minmax(360px,0.95fr)]"
            >
                {/* Row 1 – left: Fixed Payments (primary operational card) */}
                <FixedTransactionsTile
                    fixedPayments={summary.fixedPayments}
                    walletId={currentWallet?.id}
                />
                {/* Row 1 – right: Category Pressure (fills to match Fixed Payments height) */}
                <CategoryPressureCard
                  categoryPressure={summary.categoryPressure}
                  onManageBudgets={() => router.push('/analytics/budgets')}
                />

                {/* Row 2 – left: VS Previous Cycle */}
                <PreviousCyclePreview
                    preview={summary.previousCyclePreview}
                    period={summary.period}
                />
                {/* Row 2 – right: Insights (fills to match VS Previous Cycle height) */}
                <InsightsCard insights={summary.insights} />
            </div>


        </div>
    );
}
