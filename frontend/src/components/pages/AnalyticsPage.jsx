'use client';
import React from 'react';
import { AnalyticsView } from '@/components/views/PlaceholderViews';
import { useAppContext } from '@/contexts/AppContext';
import { Loading } from '../common/Loading';
import { useTranslations } from 'next-intl';

export default function AnalyticsPage() {
    const t = useTranslations();
    const { 
        dashboardData, 
        allTransactions, 
        categories,
        isLoading,
        hasError
    } = useAppContext();

    if (isLoading) {
        return <Loading message={t('analytics.loadingAnalytics')} />;
    }

    if (hasError) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold mb-2">{t('errors.errorLoadingAnalytics')}</h2>
                    <p className="text-gray-400">{t('errors.tryRefreshing')}</p>
                </div>
            </div>
        );
    }
    
    return (
        <AnalyticsView 
            dashboardData={dashboardData}
            allTransactions={allTransactions}
            categories={categories}
        />
    );
}
