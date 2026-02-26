import React, { useMemo } from 'react';
import { Pencil, Trash2, Receipt } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { formatCurrency, formatDate, getImportanceKey, getCycleKey } from '@/utils/helpers';
import { useLanguage } from '@/i18n';

export default function TransactionTable({ 
    filteredTransactions = [], 
    categories = [],
    onEdit,
    onDelete
}) {
    const t = useTranslations();
    const { lang } = useLanguage();

    const getCategoryInfo = (categoryId) => {
        return categories.find(cat => cat.id === categoryId);
    };

    const dateGroups = useMemo(() => {
        const groups = [];
        const seen = {};
        filteredTransactions.forEach(txn => {
            const dateStr = txn.transactionDate || txn.date;
            if (!dateStr) return;
            const key = typeof dateStr === 'string' ? dateStr.split('T')[0] : new Date(dateStr).toISOString().split('T')[0];
            if (!seen[key]) {
                seen[key] = { label: formatDate(dateStr, lang), transactions: [] };
                groups.push(seen[key]);
            }
            seen[key].transactions.push(txn);
        });
        return groups;
    }, [filteredTransactions, lang]);

    const hasActiveFilters = filteredTransactions.length === 0;

    if (filteredTransactions.length === 0) {
        return (
            <div className="bg-[#0e0e1c] border border-white/[0.055] rounded-[18px] overflow-hidden">
                <div className="text-center py-16">
                    <Receipt className="w-12 h-12 text-white/[0.12] mx-auto mb-4" />
                    <p className="text-white/50 text-base font-medium">{t('table.noTransactionsFound')}</p>
                    <p className="text-white/22 text-sm mt-2">{t('table.tryAdjustingFilters')}</p>
                </div>
            </div>
        );
    }

    return (
        <div className="bg-[#0e0e1c] border border-white/[0.055] rounded-[18px] overflow-hidden">
            {dateGroups.map(({ label: dateLabel, transactions: txns }) => {
                const dayTotal = txns.reduce((sum, txn) => {
                    const amount = parseFloat(txn.amount || 0);
                    return sum + (txn.type === 'INCOME' ? amount : -amount);
                }, 0);

                return (
                    <React.Fragment key={dateLabel}>
                        {/* Date group header */}
                        <div className="flex items-center gap-3 px-[22px] py-[11px] bg-[rgba(6,6,15,0.35)] border-b border-white/[0.055]">
                            <span className="text-[14px] font-bold tracking-[0.1em] uppercase text-white/22 whitespace-nowrap">{dateLabel}</span>
                            <div className="flex-1 h-px bg-white/[0.055]" />
                            <span className={`font-mono text-[15px] font-medium whitespace-nowrap ${
                                dayTotal < 0 ? 'text-red-400/70' : dayTotal > 0 ? 'text-green-400/70' : 'text-white/22'
                            }`}>
                                {dayTotal >= 0 ? '+' : ''}{formatCurrency(dayTotal, lang)}
                            </span>
                        </div>

                        {/* Transaction rows */}
                        {txns.map((txn) => {
                            const category = getCategoryInfo(txn.categoryId);
                            const isIncome = txn.type === 'INCOME';
                            const amount = parseFloat(txn.amount || 0);

                            const importanceColors = {
                                'ESSENTIAL': 'bg-green-500/12 border-green-500/35 text-green-400',
                                'HAVE_TO_HAVE': 'bg-violet-500/15 border-violet-500/40 text-violet-300',
                                'NICE_TO_HAVE': 'bg-yellow-500/12 border-yellow-500/35 text-yellow-400',
                                'SHOULDNT_HAVE': 'bg-red-500/12 border-red-500/35 text-red-400',
                                'INVESTMENT': 'bg-blue-400/15 border-blue-400/45 text-blue-300',
                            };

                            return (
                                <div
                                    key={txn.id}
                                    className="flex items-center px-[22px] border-b border-white/[0.055] cursor-pointer transition-[background] duration-150 min-h-[64px] py-[10px] group hover:bg-white/[0.022] last:border-b-0"
                                >
                                    {/* Left: emoji icon + info */}
                                    <div className="flex items-center gap-[14px] flex-1 min-w-0">
                                        <div className={`w-10 h-10 shrink-0 rounded-[11px] flex items-center justify-center text-lg ${
                                            isIncome ? 'bg-green-400/[0.08]' : 'bg-red-400/10'
                                        }`}>
                                            {category?.emoji || '🏷️'}
                                        </div>
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-center gap-2">
                                                <span className="text-base font-medium text-white whitespace-nowrap overflow-hidden text-ellipsis">{txn.title}</span>
                                            </div>
                                            <div className="flex items-center gap-1.5 mt-1 flex-wrap">
                                                {/* Category pill */}
                                                <span className="text-[13px] font-medium text-white bg-white/[0.04] border border-white/[0.06] rounded-full px-2.5 py-[2px] whitespace-nowrap">
                                                    {category?.name || t('table.unknownCategory')}
                                                </span>
                                                {/* Type pill */}
                                                <span className={`text-[13px] font-medium rounded-full px-2.5 py-[2px] border whitespace-nowrap ${
                                                    isIncome
                                                        ? 'bg-green-400/10 border-green-400/25 text-green-400/70'
                                                        : 'bg-red-400/10 border-red-400/25 text-red-400/70'
                                                }`}>
                                                    {t(`categoryType.${txn.type?.toLowerCase()}`)}
                                                </span>
                                                {/* Importance pill */}
                                                {txn.importance && (
                                                    <span className={`text-[13px] font-medium rounded-full px-2.5 py-[2px] border whitespace-nowrap ${
                                                        importanceColors[txn.importance] || 'bg-white/[0.04] border-white/[0.06] text-white/40'
                                                    }`}>
                                                        {t(`importance.${getImportanceKey(txn.importance)}`)}
                                                    </span>
                                                )}
                                                {/* Notes / description - inline with pills */}
                                                {txn.notes && (
                                                    <>
                                                        <div className="w-[3px] h-[3px] rounded-full bg-white/15 mx-0.5" />
                                                        <span className="text-[13px] text-white truncate max-w-[280px]">{txn.notes}</span>
                                                    </>
                                                )}
                                            </div>
                                        </div>
                                    </div>

                                    {/* Right: cycle + amount + actions */}
                                    <div className="flex items-center gap-5 shrink-0">
                                        {txn.cycle && (
                                            <span className="text-[13px] text-white font-medium bg-[#131325] border border-white/[0.055] rounded-[6px] px-[9px] py-[3px] whitespace-nowrap">
                                                {t(`cycle.${getCycleKey(txn.cycle)}`)}
                                            </span>
                                        )}
                                        <span className={`font-mono text-[17px] font-medium min-w-[120px] text-right ${
                                            isIncome ? 'text-green-400' : 'text-red-400'
                                        }`}>
                                            {isIncome ? '+ ' : '− '}{formatCurrency(amount, lang)}
                                        </span>
                                        <div className="flex items-center gap-[5px] opacity-0 group-hover:opacity-100 transition-opacity ml-2">
                                            <button
                                                onClick={(e) => { e.stopPropagation(); onEdit?.(txn); }}
                                                className="w-9 h-9 rounded-lg flex items-center justify-center bg-[#131325] border border-white/[0.055] cursor-pointer text-white/22 transition-all hover:text-white hover:border-white/[0.12]"
                                                title={t('common.edit')}
                                            >
                                                <Pencil className="w-3 h-3" />
                                            </button>
                                            <button
                                                onClick={(e) => { e.stopPropagation(); onDelete?.(txn); }}
                                                className="w-9 h-9 rounded-lg flex items-center justify-center bg-[#131325] border border-white/[0.055] cursor-pointer text-white/22 transition-all hover:text-red-400 hover:border-red-400/30 hover:bg-red-400/[0.08]"
                                                title={t('common.delete')}
                                            >
                                                <Trash2 className="w-3 h-3" />
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </React.Fragment>
                );
            })}
        </div>
    );
}
