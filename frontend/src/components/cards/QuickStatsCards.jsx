import React from 'react';
import { Tag, Clock, TrendingUp, Calendar } from 'lucide-react';
import { useTranslations } from 'next-intl';

export default function QuickStatsCards({ categories = [], filteredTransactions = [], allTransactions = [] }) {
    const t = useTranslations();
    
    return (
        <div className="col-span-12 grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="bg-[#13131f] border border-white/[0.06] rounded-xl p-4 flex items-center gap-4">
                <div className="w-12 h-12 bg-violet-500/20 rounded-lg flex items-center justify-center">
                    <Tag className="w-6 h-6 text-violet-400" />
                </div>
                <div>
                    <p className="text-2xl font-bold">{categories.length}</p>
                    <p className="text-xs text-gray-500">{t('stats.categories')}</p>
                </div>
            </div>

            <div className="bg-gray-900/50 border border-gray-800 rounded-xl p-4 flex items-center gap-4">
                <div className="w-12 h-12 bg-blue-500/20 rounded-lg flex items-center justify-center">
                    <Clock className="w-6 h-6 text-blue-400" />
                </div>
                <div>
                    <p className="text-2xl font-bold">{filteredTransactions.length}</p>
                    <p className="text-xs text-gray-500">{t('stats.transactions')}</p>
                </div>
            </div>

            <div className="bg-gray-900/50 border border-gray-800 rounded-xl p-4 flex items-center gap-4">
                <div className="w-12 h-12 bg-green-500/20 rounded-lg flex items-center justify-center">
                    <TrendingUp className="w-6 h-6 text-green-400" />
                </div>
                <div>
                    <p className="text-2xl font-bold">23%</p>
                    <p className="text-xs text-gray-500">{t('stats.growth')}</p>
                </div>
            </div>

            <div className="bg-gray-900/50 border border-gray-800 rounded-xl p-4 flex items-center gap-4">
                <div className="w-12 h-12 bg-orange-500/20 rounded-lg flex items-center justify-center">
                    <Calendar className="w-6 h-6 text-orange-400" />
                </div>
                <div>
                    <p className="text-2xl font-bold">{allTransactions.length}</p>
                    <p className="text-xs text-gray-500">{t('stats.totalRecords')}</p>
                </div>
            </div>
        </div>
    );
}
