'use client';
import React from 'react';
import { SettingsView } from '../views/PlaceholderViews';
import { useAppContext } from '@/contexts/AppContext';
import { Loading } from '../common/Loading';
import { useTranslations } from 'next-intl';

export default function SettingsPage() {
    const t = useTranslations();
    const { 
        dashboardData, 
        categories, 
        onRefresh,
        isLoading,
        hasError
    } = useAppContext();

    if (isLoading) {
        return <Loading message={t('common.loading')} />;
    }

    if (hasError) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold mb-2">{t('errors.errorLoadingSettings')}</h2>
                    <p className="text-gray-400">{t('errors.tryRefreshing')}</p>
                </div>
            </div>
        );
    }
    
    return (
        <SettingsView 
            dashboardData={dashboardData}
            categories={categories}
            onRefresh={onRefresh}
        />
    );
}
