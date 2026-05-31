import React from 'react';
import { PieChart, Settings } from 'lucide-react';
import { useTranslations } from 'next-intl';

export function AnalyticsView() {
    const t = useTranslations();
    return (
        <div className="text-center py-12">
            <PieChart className="w-16 h-16 text-white/[0.15] mx-auto mb-4" />
            <h3 className="text-xl font-semibold mb-2">{t('analytics.comingSoon')}</h3>
            <p className="text-white/40">{t('analytics.comingSoonDesc')}</p>
        </div>
    );
}

export function SettingsView() {
    const t = useTranslations();
    return (
        <div className="text-center py-12">
            <Settings className="w-16 h-16 text-white/[0.15] mx-auto mb-4" />
            <h3 className="text-xl font-semibold mb-2">{t('settings.comingSoon')}</h3>
            <p className="text-white/40">{t('settings.comingSoonDesc')}</p>
        </div>
    );
}
