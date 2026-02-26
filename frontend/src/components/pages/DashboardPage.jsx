'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Wallet, Plus } from 'lucide-react';
import { useWallets } from '@/contexts/WalletContext';
import { Loading } from '../common/Loading';
import { dashboardApi } from '@/lib/api';
import DashboardHeader from '../dashboard/DashboardHeader';
import SummaryCards from '../dashboard/SummaryCards';
import FixedTransactionsTile from '../dashboard/FixedTransactionsTile';
import { useTranslations } from 'next-intl';
import TopCategories from "@/components/dashboard/TopCategories";

export default function DashboardPage() {
    const t = useTranslations();
    const { currentWallet, wallets, loading: walletsLoading } = useWallets();
    const router = useRouter();
    
    const [dashboardData, setDashboardData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [periodType, setPeriodType] = useState('PAY_CYCLE');
    const [customStartDate, setCustomStartDate] = useState(null);
    const [customEndDate, setCustomEndDate] = useState(null);

    const fetchDashboard = useCallback(async (walletId, pType, startDate, endDate) => {
        setLoading(true);
        setError(null);
        try {
            const data = await dashboardApi.getDashboard(walletId, startDate, endDate, pType);
            setDashboardData(data);
        } catch (err) {
            console.error('Failed to fetch dashboard:', err);
            setError(err.message || 'Failed to load dashboard data');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (!currentWallet?.id) return;

        if (periodType === 'CUSTOM' && customStartDate && customEndDate) {
            fetchDashboard(currentWallet.id, 'CUSTOM', customStartDate, customEndDate);
        } else if (periodType !== 'CUSTOM') {
            fetchDashboard(currentWallet.id, periodType, null, null);
        }
    }, [currentWallet?.id, periodType, customStartDate, customEndDate, fetchDashboard]);

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
                        <p className="text-gray-400 mb-6">
                            {t('dashboard.noWalletsYet')}
                        </p>
                    </div>
                    <button 
                        onClick={() => router.push('/wallets')}
                        className="inline-flex items-center px-6 py-3 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors font-medium"
                    >
                        <Plus className="w-5 h-5 mr-2" />
                        {t('dashboard.createFirstWallet')}
                    </button>
                    <p className="text-sm text-gray-500 mt-4">
                        {t('dashboard.walletHelp')}
                    </p>
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
                        <p className="text-gray-400 mb-6">
                            {t('dashboard.selectWalletDashboard')}
                        </p>
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

    if (!dashboardData) {
        return null;
    }

    return (
        <div>
            <DashboardHeader 
                period={dashboardData.period}
                periodType={periodType}
                currentBalance={currentWallet?.balance}
                onPeriodTypeChange={handlePeriodTypeChange}
                onCustomDateChange={handleCustomDateChange}
            />

            {/* Bento grid */}
            <div className="grid grid-cols-12 gap-3.5 items-stretch">
                {/* Stat banner — full width */}
                <div className="col-span-12">
                    <SummaryCards summary={dashboardData.summary} />
                </div>

                {/* Fixed transactions — 7 cols */}
                <div className="col-span-7 flex">
                    <FixedTransactionsTile />
                </div>

                {/* Categories — 5 cols */}
                <div className="col-span-5 flex">
                    <TopCategories categories={dashboardData.topCategories} />
                </div>
            </div>
        </div>
    );
}

