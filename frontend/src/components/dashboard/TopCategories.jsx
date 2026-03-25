'use client';
import React from 'react';
import { formatCurrency } from '../../utils/helpers';
import { useTranslations } from 'next-intl';

const barColors = [
    '#f43f5e',
    '#8b5cf6',
    '#f59e0b',
    '#10b981',
    '#0ea5e9',
];

const formatChange = (change) => {
    if (!change) return null;
    const prefix = change.isPositive ? '+' : '-';
    return `${prefix}${Math.abs(change.percentage)}%`;
};

export default function TopCategories({ categories }) {
    const t = useTranslations();

    return (
        <div className="bg-[#0e0e1c] border border-white/[0.055] rounded-[18px] overflow-hidden relative transition-colors hover:border-white/[0.12] w-full flex flex-col"
             style={{ animation: 'fadeUp 0.5s ease both', animationDelay: '0.2s' }}>
            {/* Header */}
            <div className="px-6 pt-5 pb-0 flex items-start gap-3 mb-4">
                <div>
                    <div className="text-[15px] font-bold tracking-[-0.2px]">{t('dashboard.topCategories')}</div>
                    <div className="text-xs text-white/25 mt-0.5">{t('dashboard.thisPayCycle')}</div>
                </div>
            </div>

            {/* List */}
            <div className="pb-3 flex-1 flex flex-col justify-center">
                {!categories || categories.length === 0 ? (
                    <div className="text-center text-white/25 text-sm py-8 px-6">
                        {t('dashboard.noSpendingData')}
                    </div>
                ) : (
                    categories.map((cat, index) => (
                        <div key={index}
                             className="px-6 py-3 cursor-pointer transition-colors hover:bg-white/[0.025]">
                            <div className="flex justify-between items-baseline mb-2">
                                <div>
                                    <div className="text-[15px] font-medium">{cat.categoryName}</div>
                                    <div className="text-xs text-white/30 mt-0.5">
                                        {formatCurrency(cat.amount)}
                                        {formatChange(cat.change) && ` · ${formatChange(cat.change)}`}
                                    </div>
                                </div>
                                <div className="text-right">
                                    <div className="font-mono text-base font-medium tracking-[-0.3px] text-red-400">
                                        {cat.percentageOfTotal}%
                                    </div>
                                    <div className="text-xs text-white/25 font-normal">{t('dashboard.ofExpenses')}</div>
                                </div>
                            </div>
                            <div className="h-[5px] bg-white/[0.05] rounded-full overflow-hidden">
                                <div className="h-full rounded-full"
                                     style={{
                                         width: `${Math.min(cat.percentageOfTotal, 100)}%`,
                                         background: barColors[index % barColors.length]
                                     }} />
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
}
