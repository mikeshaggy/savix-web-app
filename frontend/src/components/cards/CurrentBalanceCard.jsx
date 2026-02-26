import React from 'react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';
import { useLanguage } from '@/i18n';

export default function CurrentBalanceCard({ balance, savingsRate }) {
    const t = useTranslations('cards');
    const { lang } = useLanguage();
    
    return (
        <div className="col-span-2 bg-gradient-to-br from-violet-500/10 to-purple-500/10 border border-violet-500/20 rounded-xl p-6">
            <div className="flex items-center justify-between">
                <div>
                    <h3 className="text-gray-400 text-sm font-medium mb-3">{t('currentBalance')}</h3>
                    <p className={`text-4xl font-bold ${ balance >= 0 ? 'text-violet-400' : 'text-red-400'}`}>
                        {formatCurrency(Math.abs(balance), lang)}
                    </p>
                    <p className="text-sm text-gray-500 mt-2">{t('savingsRate')}: {savingsRate}%</p>
                </div>
                <div className="w-24 h-24 relative">
                    <svg className="w-full h-full transform -rotate-90">
                        <circle
                            cx="48"
                            cy="48"
                            r="36"
                            stroke="currentColor"
                            strokeWidth="8"
                            fill="none"
                            className="text-gray-700"
                        />
                        <circle
                            cx="48"
                            cy="48"
                            r="36"
                            stroke="currentColor"
                            strokeWidth="8"
                            fill="none"
                            strokeDasharray={`${2 * Math.PI * 36}`}
                            strokeDashoffset={`${2 * Math.PI * 36 * (1 - savingsRate / 100)}`}
                            className="text-violet-500 transition-all duration-500"
                        />
                    </svg>
                    <div className="absolute inset-0 flex items-center justify-center">
                        <span className="text-lg font-bold">{savingsRate}%</span>
                    </div>
                </div>
            </div>
        </div>
    );
}
