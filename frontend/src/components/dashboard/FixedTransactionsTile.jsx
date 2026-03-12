'use client';
import React, { useState, useMemo } from 'react';
import { Check, X, ArrowRight, Loader2 } from 'lucide-react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { useCategories } from '@/hooks/useApi';
import { transactionApi } from '@/lib/api';
import TransactionModal from '@/components/modals/TransactionModal';

export default function FixedTransactionsTile({ tileData, loading, error, onRefresh }) {
  const t = useTranslations();
  const { lang } = useLanguage();
  const { categories } = useCategories();

  const [markPaidOccurrence, setMarkPaidOccurrence] = useState(null);
  const [showTransactionModal, setShowTransactionModal] = useState(false);
  const [transactionPrefill, setTransactionPrefill] = useState(null);

  const rows = useMemo(() => {
    if (!tileData) return [];
    const overdue = tileData.overdue || [];
    const upcoming = tileData.upcoming || [];
    return [...overdue, ...upcoming].slice(0, 5);
  }, [tileData]);

  const handleMarkPaidClick = (occurrence) => {
    setMarkPaidOccurrence(occurrence);
    setTransactionPrefill({
      title: occurrence.title,
      amount: occurrence.expectedAmount,
      categoryId: occurrence.categoryId || null,
      walletId: occurrence.walletId || null,
      notes: null,
      occurrenceId: occurrence.occurrenceId,
    });
  };

  const handleOpenTransactionModal = () => {
    setShowTransactionModal(true);
  };

  const handleTransactionSave = async (transactionData, _id) => {
    await transactionApi.createTransaction(transactionData);
    if (onRefresh) onRefresh();
  };

  const handleCloseAll = () => {
    setShowTransactionModal(false);
    setMarkPaidOccurrence(null);
    setTransactionPrefill(null);
  };

  const getDaysChipClass = (daysDelta) => {
    if (daysDelta < 0) return 'text-red-400 bg-red-500/10 border border-red-500/20';
    if (daysDelta <= 7) return 'text-amber-400 bg-amber-500/10 border border-amber-500/20';
    return 'text-white/25 bg-white/[0.04] border border-white/[0.06]';
  };

  const getDaysLabel = (daysDelta) => {
    if (daysDelta < 0) return t('fixedPayments.daysLate', { days: Math.abs(daysDelta) });
    if (daysDelta === 0) return t('fixedPayments.dueToday');
    return t('fixedPayments.daysLeft', { days: daysDelta });
  };

  if (loading) {
    return (
      <div className="w-full bg-[#13131f] border border-white/[0.06] rounded-[14px] overflow-hidden flex flex-col items-center justify-center py-16"
           style={{ animation: 'fadeUp 0.5s ease both', animationDelay: '0.14s' }}>
        <Loader2 className="w-6 h-6 text-purple-400 animate-spin mb-3" />
        <div className="text-[12px] text-white/25">{t('common.loading')}</div>
      </div>
    );
  }

  if (error || !tileData) {
    return (
      <div className="w-full bg-[#13131f] border border-white/[0.06] rounded-[14px] overflow-hidden flex flex-col items-center justify-center py-16"
           style={{ animation: 'fadeUp 0.5s ease both', animationDelay: '0.14s' }}>
        <div className="text-[12px] text-white/25">
          {error || t('fixedPayments.noData')}
        </div>
      </div>
    );
  }

  const { summary, progress, riskIndicator, balanceAfterFixed } = tileData;

  return (
    <>
      <div className="w-full bg-[#13131f] border border-white/[0.06] rounded-[14px] overflow-hidden flex flex-col"
           style={{ animation: 'fadeUp 0.5s ease both', animationDelay: '0.14s' }}>

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/[0.055]">
          <div>
            <div className="text-[15px] font-bold tracking-[-0.2px]">
              {t('fixedPayments.tileTitle')}
            </div>
            <div className="text-[12px] text-white/25 mt-0.5">
              {t('fixedPayments.tileSubtitle')}
            </div>
          </div>
          <a href="/wallets/fixed-payments" className="text-[12px] text-[#a855f7] opacity-80 hover:opacity-100 transition-opacity">
            {t('dashboard.viewAll')}
          </a>
        </div>

        {/* Risk banner */}
        {riskIndicator?.atRisk && (
          <div className="flex items-center gap-2.5 px-5 py-2.5 bg-red-500/[0.07] border-b border-red-500/[0.15] text-[12px] text-red-400">
            <div className="w-1.5 h-1.5 rounded-full bg-red-400 animate-pulse shrink-0" />
            <span>
              {t('fixedPayments.riskWarning', { amount: formatCurrency(riskIndicator.shortfallAmount, lang) })}
            </span>
          </div>
        )}

        {/* 4 stat cards */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-px bg-white/[0.06] border-b border-white/[0.055]">
          {/* Planned */}
          <div className="bg-[#13131f] p-3.5 relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-[#94a3b8] opacity-80" />
            <div className="text-[8px] tracking-[0.1em] uppercase text-white/25 mb-1.5">
              {t('fixedPayments.planned')}
            </div>
            <div className="font-bold text-[16px] tracking-[-0.01em] leading-none text-white">
              {formatCurrency(summary?.plannedAmount ?? 0, lang)}
            </div>
            <div className="text-[9px] text-white/25 mt-1.5">
              {t('fixedPayments.countItems', { count: summary?.plannedCount ?? 0 })}
            </div>
          </div>
          {/* Paid */}
          <div className="bg-[#13131f] p-3.5 relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-green-400 opacity-80" />
            <div className="text-[8px] tracking-[0.1em] uppercase text-white/25 mb-1.5">
              {t('fixedPayments.paid')}
            </div>
            <div className="font-bold text-[16px] tracking-[-0.01em] leading-none text-green-400">
              {formatCurrency(summary?.paidAmount ?? 0, lang)}
            </div>
            <div className="text-[9px] text-white/25 mt-1.5">
              {t('fixedPayments.countItems', { count: summary?.paidCount ?? 0 })}
            </div>
          </div>
          {/* Remaining */}
          <div className="bg-[#13131f] p-3.5 relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-[#7c6af7] opacity-80" />
            <div className="text-[8px] tracking-[0.1em] uppercase text-white/25 mb-1.5">
              {t('fixedPayments.remaining')}
            </div>
            <div className="font-bold text-[16px] tracking-[-0.01em] leading-none text-purple-400">
              {formatCurrency(summary?.remainingAmount ?? 0, lang)}
            </div>
            <div className="text-[9px] text-white/25 mt-1.5">
              {t('fixedPayments.countItems', { count: summary?.remainingCount ?? 0 })}
            </div>
          </div>
          {/* Balance After */}
          <div className="bg-[#13131f] p-3.5 relative">
            <div className={`absolute top-0 left-0 right-0 h-[2px] opacity-80 ${balanceAfterFixed < 0 ? 'bg-red-400' : 'bg-amber-400'}`} />
            <div className="text-[8px] tracking-[0.1em] uppercase text-white/25 mb-1.5">
              {t('fixedPayments.balanceAfter')}
            </div>
            <div className={`font-bold text-[16px] tracking-[-0.01em] leading-none ${balanceAfterFixed < 0 ? 'text-red-400' : 'text-amber-400'}`}>
              {formatCurrency(balanceAfterFixed ?? 0, lang)}
            </div>
            <div className="text-[9px] text-white/25 mt-1.5">
              {t('fixedPayments.afterAllFixed')}
            </div>
          </div>
        </div>

        {/* Progress bar strip */}
        <div className="flex items-center gap-3 px-5 py-2.5 border-b border-white/[0.055]">
          <span className="text-[9px] text-white/25 whitespace-nowrap shrink-0">
            <span className="text-white">{progress?.paidCount ?? 0}</span> / {progress?.totalCount ?? 0} {t('fixedPayments.paidLabel')}
          </span>
          <div className="flex-1 h-[3px] bg-white/[0.06] rounded-[3px] overflow-hidden">
            <div
              className="h-full rounded-[3px] relative"
              style={{
                width: `${progress?.paidPct ?? 0}%`,
                background: 'linear-gradient(90deg, #7c3aed, #a855f7)',
              }}
            >
              <div className="absolute right-0 top-0 bottom-0 w-[14px] bg-gradient-to-r from-transparent to-white/30 animate-pulse" />
            </div>
          </div>
          <span className="text-[9px] text-purple-400 font-medium whitespace-nowrap shrink-0">
            {Math.round(progress?.paidPct ?? 0)}%
          </span>
        </div>

        {/* Transaction rows */}
        <div className="flex-1 flex flex-col">
          {rows.length === 0 ? (
            <div className="flex items-center justify-center py-8 text-[12px] text-white/25">
              {t('fixedPayments.noOccurrences')}
            </div>
          ) : (
            rows.map((occ, idx) => {
              const isOverdue = occ.status === 'OVERDUE';
              const isPaid = occ.status === 'PAID';
              const isSkipped = occ.status === 'SKIPPED';
              const canMarkPaid = occ.status === 'PENDING' || isOverdue;

              return (
                <div
                  key={occ.occurrenceId}
                  className={`group flex items-center gap-2.5 px-5 py-2.5 cursor-pointer transition-colors border-b border-white/[0.03] last:border-b-0 ${
                    isOverdue ? 'bg-red-500/[0.03] hover:bg-red-500/[0.06]' : 'hover:bg-white/[0.025]'
                  } ${isPaid ? 'opacity-60' : ''}`}
                  style={{ animation: `fadeUp 0.3s ease both`, animationDelay: `${0.04 * (idx + 1)}s` }}
                >
                  {/* Overdue pulse */}
                  {isOverdue && (
                    <div className="w-[5px] h-[5px] rounded-full bg-red-400 shrink-0 animate-pulse" />
                  )}

                  {/* Icon */}
                  <div className="w-[30px] h-[30px] bg-[#1a1a2a] border border-white/[0.06] rounded-[9px] flex items-center justify-center text-[13px] shrink-0">
                    {occ.categoryEmoji || '🔁'}
                  </div>

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <div className="text-[12px] font-medium text-white truncate">{occ.title}</div>
                    <div className="text-[9px] text-white/25 flex items-center gap-1.5 mt-0.5">
                      <span>{occ.dueDate}</span>
                      <span>·</span>
                      <span className="text-[8px] tracking-[0.05em] uppercase bg-white/[0.04] border border-white/[0.06] px-1.5 py-px rounded">
                        {occ.categoryName}
                      </span>
                      <span className={`text-[8px] px-1.5 py-px rounded ${getDaysChipClass(occ.daysDelta)}`}>
                        {getDaysLabel(occ.daysDelta)}
                      </span>
                    </div>
                  </div>

                  {/* Mark paid button (hover) */}
                  {canMarkPaid && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleMarkPaidClick(occ);
                      }}
                      className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-[7px] text-[9px] tracking-[0.06em] cursor-pointer border border-green-400/30 bg-green-400/[0.07] text-green-400 md:opacity-0 md:group-hover:opacity-100 transition-all hover:bg-green-400/[0.15] shrink-0"
                    >
                      <Check className="w-3 h-3" />
                      {t('fixedPayments.markAsPaid')}
                    </button>
                  )}

                  {/* Amount + badge */}
                  <div className="text-right shrink-0 flex flex-col items-end gap-1">
                    <div className={`font-bold text-[13px] tracking-[-0.01em] ${
                      isOverdue ? 'text-red-400' : isPaid ? 'text-green-400' : 'text-purple-400'
                    }`}>
                      {formatCurrency(occ.expectedAmount, lang)}
                    </div>
                    <span className={`text-[7px] tracking-[0.08em] uppercase px-1.5 py-px rounded ${
                      isOverdue
                        ? 'bg-red-500/[0.12] text-red-400 border border-red-500/25'
                        : isPaid
                          ? 'bg-green-400/10 text-green-400 border border-green-400/20'
                          : isSkipped
                            ? 'bg-white/[0.04] text-white/25 border border-white/[0.06]'
                            : 'bg-purple-500/[0.12] text-purple-400 border border-purple-500/25'
                    }`}>
                      {occ.status}
                    </span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer */}
        {(progress?.totalCount ?? 0) > 5 && (
          <a
            href="/wallets/fixed-payments"
            className="block px-5 py-2 text-[9px] text-white/25 text-center border-t border-white/[0.055] cursor-pointer tracking-[0.06em] hover:text-purple-400 transition-colors"
          >
            + {(progress.totalCount - 5)} {t('fixedPayments.moreThisCycle')}
          </a>
        )}
      </div>

      {/* Mark as Paid confirmation modal */}
      {markPaidOccurrence && !showTransactionModal && (
        <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-3 sm:p-6">
          <div
            className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[420px] overflow-hidden relative"
            style={{
              boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1)',
              animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both',
            }}
          >
            {/* Top glow */}
            <div className="absolute top-0 left-[10%] right-[10%] h-px bg-gradient-to-r from-transparent via-purple-400 to-transparent opacity-60" />

            {/* Header */}
            <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
              <div className="text-lg font-bold tracking-[-0.3px]">
                {t('fixedPayments.markAsPaid')}
              </div>
              <button
                onClick={() => setMarkPaidOccurrence(null)}
                className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] transition-all"
              >
                <X className="w-3 h-3" />
              </button>
            </div>

            {/* Preview card */}
            <div className="px-4 sm:px-7 py-5">
              <div className="flex items-center gap-3 bg-[#131325] border border-white/[0.06] rounded-xl p-4">
                <div className="w-10 h-10 bg-[#1a1a2a] border border-white/[0.06] rounded-[10px] flex items-center justify-center text-lg">
                  {markPaidOccurrence.categoryEmoji || '🔁'}
                </div>
                <div className="flex-1">
                  <div className="text-[14px] font-semibold">{markPaidOccurrence.title}</div>
                  <div className="text-[11px] text-white/25 mt-0.5">
                    {t('fixedPayments.dueOn', { date: markPaidOccurrence.dueDate })}
                  </div>
                </div>
                <div className="text-[16px] font-bold text-purple-400">
                  {formatCurrency(markPaidOccurrence.expectedAmount, lang)}
                </div>
              </div>

              {/* Info note */}
              <p className="text-[12px] text-white/25 mt-4 leading-relaxed">
                {t('fixedPayments.markPaidInfo')}
              </p>
            </div>

            {/* Footer */}
            <div className="flex items-center justify-end gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
              <button
                onClick={() => setMarkPaidOccurrence(null)}
                className="px-[22px] py-3 bg-[#131325] border border-white/[0.055] rounded-xl text-base font-semibold text-white/50 cursor-pointer hover:border-white/[0.12] hover:text-white transition-all"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={handleOpenTransactionModal}
                className="flex items-center gap-2 px-5 py-3 rounded-xl border-none text-base font-bold text-white cursor-pointer transition-all hover:-translate-y-px"
                style={{
                  background: 'linear-gradient(135deg, #7c3aed, #a855f7)',
                  boxShadow: '0 4px 20px rgba(124,58,237,0.3)',
                }}
              >
                {t('fixedPayments.openTransaction')}
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Transaction Modal for mark-as-paid */}
      {showTransactionModal && (
        <TransactionModal
          isOpen={showTransactionModal}
          onClose={handleCloseAll}
          onSave={handleTransactionSave}
          prefill={transactionPrefill}
          onPrefillSaved={() => { if (onRefresh) onRefresh(); }}
          categories={categories}
          loading={false}
        />
      )}
    </>
  );
}
