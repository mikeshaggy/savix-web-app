'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Wallet, Plus } from 'lucide-react';
import { useWallets } from '@/contexts/WalletContext';
import { useAppContext } from '@/contexts/AppContext';
import { Loading } from '../common/Loading';
import { dashboardApi } from '@/lib/api';
import DashboardHeader from '../dashboard/DashboardHeader';
import CycleHealthHero from '../dashboard/CycleHealthHero';
import SummaryCards from '../dashboard/SummaryCards';
import FixedTransactionsTile from '../dashboard/FixedTransactionsTile';
import InsightsCard from '../dashboard/InsightsCard';
import CategoryPressureCard from '../dashboard/CategoryPressureCard';
import PreviousCyclePreview from '../dashboard/PreviousCyclePreview';
import { useTranslations } from 'next-intl';

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
        return <Loading message={t('dashboard.loadingWallets')} />;
    }

    if (!walletsLoading && wallets.length === 0) {
        return (
            <div className="flex items-center justify-center min-h-[400px] p-8">
                <div className="text-center max-w-md">
                    <div className="mb-6">
                        <Wallet className="w-16 h-16 text-violet-400 mx-auto mb-4" />
                        <h2 className="text-2xl font-semibold text-white mb-2">{t('dashboard.welcomeToSavix')}</h2>
                        <p className="text-gray-400 mb-6">{t('dashboard.noWalletsYet')}</p>
                    </div>
                    <button
                        onClick={() => router.push('/wallets')}
                        className="inline-flex items-center px-6 py-3 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors font-medium"
                    >
                        <Plus className="w-5 h-5 mr-2" />
                        {t('dashboard.createFirstWallet')}
                    </button>
                    <p className="text-sm text-gray-500 mt-4">{t('dashboard.walletHelp')}</p>
                </div>
            </div>
        );
    }

    if (!currentWallet && wallets.length > 0) {
        return (
            <div className="flex items-center justify-center min-h-[400px] p-8">
                <div className="text-center max-w-md">
                    <div className="mb-6">
                        <Wallet className="w-12 h-12 text-violet-400 mx-auto mb-4" />
                        <h2 className="text-xl font-semibold text-white mb-2">{t('dashboard.noWalletSelected')}</h2>
                        <p className="text-gray-400 mb-6">{t('dashboard.selectWalletDashboard')}</p>
                    </div>
                    <button
                        onClick={() => router.push('/wallets')}
                        className="inline-flex items-center px-6 py-3 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors font-medium"
                    >
                        <Wallet className="w-5 h-5 mr-2" />
                        {t('topbar.manageWallets')}
                    </button>
                </div>
            </div>
        );
    }

    if (loading) {
        return <Loading message={t('dashboard.loadingDashboard')} />;
    }

    if (error) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold text-white mb-2">{t('errors.errorLoadingDashboard')}</h2>
                    <p className="text-gray-400 mb-4">{error}</p>
                    <button
                        onClick={() => window.location.reload()}
                        className="px-4 py-2 bg-violet-600 text-white rounded-lg hover:bg-violet-700"
                    >
                        {t('common.retry')}
                    </button>
                </div>
            </div>
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
                <CategoryPressureCard categoryPressure={summary.categoryPressure} />

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
