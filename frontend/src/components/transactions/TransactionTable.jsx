import React, { useMemo } from 'react';
import { Pencil, Trash2, Receipt } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { formatCurrency, formatDate, getImportanceKey } from '@/utils/helpers';
import { useLanguage } from '@/i18n';

export default function TransactionTable({ 
    groups = [],
    categories = [],
    onEdit,
    onDelete
}) {
    const t = useTranslations();
    const { lang } = useLanguage();

    const getCategoryInfo = (txn) => {
        // Server response includes categoryName/categoryEmoji/categoryType directly
        if (txn.categoryName) {
            return {
                name: txn.categoryName,
                emoji: txn.categoryEmoji,
                type: txn.categoryType,
            };
        }
        // Fallback: lookup from categories array
        return categories.find(cat => cat.id === txn.categoryId) || null;
    };

    const getType = (txn) => txn.type || txn.categoryType;

    const formattedGroups = useMemo(() => {
        return groups.map((group) => ({
            label: formatDate(group.date, lang),
            transactions: group.transactions,
        }));
    }, [groups, lang]);

    if (!groups.length) {
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
          <div className="overflow-x-auto">
            {formattedGroups.map(({ label: dateLabel, transactions: txns }) => {
                const dayTotal = txns.reduce((sum, txn) => {
                    const amount = parseFloat(txn.amount || 0);
                    const txnType = getType(txn);
                    return sum + (txnType === 'INCOME' ? amount : -amount);
                }, 0);

                return (
                    <React.Fragment key={dateLabel}>
                        {/* Date group header */}
                        <div className="flex items-center gap-3 px-4 md:px-[22px] py-[11px] bg-[rgba(6,6,15,0.35)] border-b border-white/[0.055]">
                            <span className="text-[13px] md:text-[14px] font-bold tracking-[0.1em] uppercase text-white/22 whitespace-nowrap">{dateLabel}</span>
                            <div className="flex-1 h-px bg-white/[0.055]" />
                            <span className={`font-mono text-[14px] md:text-[15px] font-medium whitespace-nowrap ${
                                dayTotal < 0 ? 'text-red-400/70' : dayTotal > 0 ? 'text-green-400/70' : 'text-white/22'
                            }`}>
                                {dayTotal >= 0 ? '+' : ''}{formatCurrency(dayTotal, lang)}
                            </span>
                        </div>

                        {/* Transaction rows */}
                        {txns.map((txn) => {
                            const category = getCategoryInfo(txn);
                            const txnType = getType(txn);
                            const isIncome = txnType === 'INCOME';
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
                                    onClick={() => onEdit?.(txn)}
                                    className="flex items-center px-4 md:px-[22px] border-b border-white/[0.055] cursor-pointer transition-[background] duration-150 min-h-[56px] md:min-h-[64px] py-[10px] group hover:bg-white/[0.022] last:border-b-0"
                                >
                                    {/* Left: emoji icon + info */}
                                    <div className="flex items-center gap-3 md:gap-[14px] flex-1 min-w-0">
                                        <div className={`w-9 h-9 md:w-10 md:h-10 shrink-0 rounded-[11px] flex items-center justify-center text-base md:text-lg ${
                                            isIncome ? 'bg-green-400/[0.08]' : 'bg-red-400/10'
                                        }`}>
                                            {category?.emoji || '🏷️'}
                                        </div>
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-center gap-2">
                                                <span className="text-sm md:text-base font-medium text-white whitespace-nowrap overflow-hidden text-ellipsis">{txn.title}</span>
                                            </div>
                                            <div className="flex items-center gap-1.5 mt-1 flex-wrap">
                                                {/* Category pill */}
                                                <span className="text-[12px] md:text-[13px] font-medium text-white bg-white/[0.04] border border-white/[0.06] rounded-full px-2 md:px-2.5 py-[2px] whitespace-nowrap">
                                                    {category?.name || t('table.unknownCategory')}
                                                </span>
                                                {/* Importance pill — hide on small screens */}
                                                {txn.importance && (
                                                    <span className={`hidden sm:inline-flex text-[13px] font-medium rounded-full px-2.5 py-[2px] border whitespace-nowrap ${
                                                        importanceColors[txn.importance] || 'bg-white/[0.04] border-white/[0.06] text-white/40'
                                                    }`}>
                                                        {t(`importance.${getImportanceKey(txn.importance)}`)}
                                                    </span>
                                                )}
                                                {/* Notes — hide on small screens */}
                                                {txn.notes && (
                                                    <>
                                                        <div className="hidden md:block w-[3px] h-[3px] rounded-full bg-white/15 mx-0.5" />
                                                        <span className="hidden md:inline text-[13px] text-white truncate max-w-[280px]">{txn.notes}</span>
                                                    </>
                                                )}
                                            </div>
                                        </div>
                                    </div>

                                    {/* Right: amount + actions */}
                                    <div className="flex items-center gap-3 md:gap-5 shrink-0">
                                        <span className={`font-mono text-[15px] md:text-[17px] font-medium min-w-[90px] md:min-w-[120px] text-right ${
                                            isIncome ? 'text-green-400' : 'text-red-400'
                                        }`}>
                                            {isIncome ? '+ ' : '- '}{formatCurrency(amount, lang)}
                                        </span>
                                        <div className="hidden md:flex items-center gap-[5px] opacity-0 group-hover:opacity-100 transition-opacity ml-2">
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
        </div>
    );
}
