import React from 'react';
import { ArrowDownRight } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';
import { useLanguage } from '@/i18n';

export default function ExpenseCard({ expenses }) {
    const t = useTranslations('dashboard');
    const { lang } = useLanguage();
    
    return (
        <div className="bg-gradient-to-br from-red-500/10 to-rose-500/10 border border-red-500/20 rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
                <h3 className="text-gray-400 text-sm font-medium">{t('expenses')}</h3>
                <div className="w-10 h-10 bg-red-500/20 rounded-lg flex items-center justify-center">
                    <ArrowDownRight className="w-5 h-5 text-red-400" />
                </div>
            </div>
            <p className="text-3xl font-bold text-red-400">{formatCurrency(expenses, lang)}</p>
        </div>
    );
}
