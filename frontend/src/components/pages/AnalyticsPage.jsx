'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { Wallet, BarChart2, AlertCircle, InboxIcon } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useWallets } from '@/contexts/WalletContext';
import { analyticsApi } from '@/lib/api';
import AnalyticsHeader from '@/components/analytics/AnalyticsHeader';
import MonthlyOverviewSection from '@/components/analytics/MonthlyOverviewSection';

function getCurrentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

export default function AnalyticsPage() {
  const t = useTranslations('analytics');
  const { currentWallet, loading: walletsLoading } = useWallets();

  const [periodType, setPeriodType] = useState('PAY_CYCLE');
  const [selectedMonth, setSelectedMonth] = useState(getCurrentMonth);
  const [customStartDate, setCustomStartDate] = useState(null);
  const [customEndDate, setCustomEndDate] = useState(null);

  const [data, setData] = useState(null);
  const [period, setPeriod] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchOverview = useCallback(async (walletId, pType, startDate, endDate) => {
    setLoading(true);
    setError(null);
    try {
      const result = await analyticsApi.getPeriodOverview(walletId, pType, startDate, endDate);
      setData(result);
      setPeriod({ startDate: result.startDate, endDate: result.endDate });
    } catch (err) {
      console.error('Failed to fetch period overview:', err);
      setError(err.message || t('errorLoading'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!currentWallet?.id) return;
    if (periodType === 'CUSTOM' && (!customStartDate || !customEndDate)) return;

    let startDate = null;
    let endDate = null;

    if (periodType === 'MONTHLY') {
      startDate = `${selectedMonth}-01`;
    } else if (periodType === 'CUSTOM') {
      startDate = customStartDate;
      endDate = customEndDate;
    }

    fetchOverview(currentWallet.id, periodType, startDate, endDate);
  }, [currentWallet?.id, periodType, selectedMonth, customStartDate, customEndDate, fetchOverview]);

  const handlePeriodTypeChange = (newType) => {
    if (newType === 'CUSTOM') return;
    setPeriodType(newType);
  };

  const handleCustomDateChange = (startDate, endDate) => {
    setCustomStartDate(startDate);
    setCustomEndDate(endDate);
    setPeriodType('CUSTOM');
  };

  const handleMonthChange = (month) => {
    setSelectedMonth(month);
  };

  const isEmpty =
    data &&
    data.transactionCount === 0 &&
    data.income === 0 &&
    data.expenses === 0;

  if (walletsLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px] p-8">
        <div className="flex flex-col items-center gap-3">
          <BarChart2 className="w-8 h-8 animate-pulse text-violet-500" />
          <p className="text-gray-400 text-sm">{t('loadingAnalytics')}</p>
        </div>
      </div>
    );
  }

  if (!currentWallet) {
    return (
      <div className="flex items-center justify-center min-h-[400px] p-8">
        <div className="text-center max-w-sm">
          <Wallet className="w-14 h-14 text-violet-400 mx-auto mb-4" />
          <h3 className="text-lg font-semibold text-white mb-2">{t('noWallet')}</h3>
          <p className="text-gray-400 text-sm">{t('noWalletDesc')}</p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <AnalyticsHeader
        period={period}
        periodType={periodType}
        selectedMonth={selectedMonth}
        onPeriodTypeChange={handlePeriodTypeChange}
        onMonthChange={handleMonthChange}
        onCustomDateChange={handleCustomDateChange}
      />

      {/* Section heading */}
      <div className="flex items-center gap-2 mb-4">
        <BarChart2 className="w-4 h-4 text-violet-400" />
        <h2 className="text-sm font-bold uppercase tracking-[0.12em] text-white/40">
          {t('monthlyOverview')}
        </h2>
      </div>

      {/* Error state */}
      {error && !loading && (
        <div className="flex flex-col items-center gap-4 py-12 text-center">
          <AlertCircle className="w-10 h-10 text-red-400" />
          <div>
            <p className="text-white font-medium mb-1">{t('errorLoading')}</p>
            <p className="text-gray-400 text-sm mb-4">{error}</p>
            <button
              onClick={() => {
                const startDate = periodType === 'MONTHLY' ? `${selectedMonth}-01`
                  : periodType === 'CUSTOM' ? customStartDate : null;
                const endDate = periodType === 'CUSTOM' ? customEndDate : null;
                fetchOverview(currentWallet.id, periodType, startDate, endDate);
              }}
              className="px-4 py-2 bg-violet-600 text-white text-sm rounded-lg hover:bg-violet-700 transition-colors"
            >
              {t('retry')}
            </button>
          </div>
        </div>
      )}

      {/* Empty state */}
      {isEmpty && !loading && !error && (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <InboxIcon className="w-10 h-10 text-gray-600" />
          <p className="text-white font-medium">{t('noData')}</p>
          <p className="text-gray-400 text-sm">
            {period ? `${period.startDate} – ${period.endDate}` : ''}
          </p>
        </div>
      )}

      {/* Data / loading grid */}
      {!error && !isEmpty && (
        <MonthlyOverviewSection data={data} loading={loading} />
      )}
    </div>
  );
}
