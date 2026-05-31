'use client';
import React, { useState, useMemo } from 'react';
import { Check, X, ArrowRight, Loader2 } from 'lucide-react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { useCategories } from '@/hooks/useApi';
import { useAppContext } from '@/contexts/AppContext';
import TransactionModal from '@/components/modals/TransactionModal';

function computeDaysDelta(dueDateStr) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const due = new Date(dueDateStr);
  due.setHours(0, 0, 0, 0);
  return Math.round((due - today) / (1000 * 60 * 60 * 24));
}

export default function FixedTransactionsTile({ fixedPayments, walletId }) {
  const t = useTranslations();
  const { lang } = useLanguage();
  const { categories } = useCategories();
  const { onCreateTransaction } = useAppContext();

  const [markPaidOccurrence, setMarkPaidOccurrence] = useState(null);
  const [showTransactionModal, setShowTransactionModal] = useState(false);
  const [transactionPrefill, setTransactionPrefill] = useState(null);

  const { nextPending, rows } = useMemo(() => {
    const occs = fixedPayments?.upcomingOccurrences ?? [];
    const np = occs.find((occ) => occ.status === 'PENDING' || occ.status === 'OVERDUE') ?? null;
    const rest = np ? occs.filter((occ) => occ.id !== np.id) : occs;
    return { nextPending: np, rows: rest.slice(0, 8) };
  }, [fixedPayments]);

  const handleMarkPaidClick = (occ) => {
    setMarkPaidOccurrence(occ);
    setTransactionPrefill({
      title: occ.name,
      amount: occ.amount,
      categoryId: occ.categoryId || null,
      walletId: walletId || null,
      notes: null,
      occurrenceId: occ.id,
    });
  };

  const handleOpenTransactionModal = () => {
    setShowTransactionModal(true);
  };

  const handleTransactionSave = async (transactionData) => {
    await onCreateTransaction(transactionData);
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

  if (!fixedPayments) {
    return (
      <div
        className="w-full bg-[#0e0e1c] border border-white/[0.06] rounded-[14px] overflow-hidden flex flex-col items-center justify-center py-16"
        style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both', animationDelay: '0.14s' }}
      >
        <div className="text-[12px] text-white/25">{t('fixedPayments.noData')}</div>
      </div>
    );
  }

  const {
    plannedAmount,
    paidAmount,
    remainingAmount,
    paidCount,
    totalCount,
    balanceAfterRemainingFixedPayments,
    atRisk,
    shortfallAmount,
  } = fixedPayments;

  const paidPct = totalCount > 0 ? (paidCount / totalCount) * 100 : 0;
  const balanceAfterFixed = balanceAfterRemainingFixedPayments;
  const allOccs = fixedPayments.upcomingOccurrences ?? [];
  const overflowCount = allOccs.length - (nextPending ? 1 : 0) - rows.length;
  const npDaysDelta = nextPending?.dueDate ? computeDaysDelta(nextPending.dueDate) : 0;
  const npIsOverdue = nextPending?.status === 'OVERDUE';

  return (
    <>
      <div
        className="w-full bg-[#0e0e1c] border border-white/[0.06] rounded-[14px] overflow-hidden flex flex-col"
        style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both', animationDelay: '0.14s' }}
      >
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
          <a
            href="/transactions/fixed-payments"
            className="text-[12px] text-[#a855f7] opacity-80 hover:opacity-100 transition-opacity"
          >
            {t('dashboard.viewAll')}
          </a>
        </div>

        {/* Risk banner */}
        {atRisk && (
          <div className="flex items-center gap-2.5 px-5 py-2.5 bg-rose-500/[0.09] border-b border-rose-500/[0.2] text-[12px] text-rose-400">
            <div className="w-1.5 h-1.5 rounded-full bg-rose-400 animate-pulse shrink-0" />
            <span>
              {t('fixedPayments.riskWarning', {
                amount: formatCurrency(shortfallAmount ?? 0, lang),
              })}
            </span>
          </div>
        )}

        {/* 4 stat cards */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-px bg-white/[0.07] border-b border-white/[0.07]">
          <div className="bg-[#0e0e1c] p-4 relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-slate-400 opacity-70" />
            <div className="text-[8px] tracking-[0.1em] uppercase text-white/35 mb-2">
              {t('fixedPayments.planned')}
            </div>
            <div className="font-bold text-[15px] tracking-[-0.01em] leading-none text-white whitespace-nowrap">
              {formatCurrency(plannedAmount ?? 0, lang)}
            </div>
            <div className="text-[9px] text-white/30 mt-1.5">
              {t('fixedPayments.countItems', { count: totalCount ?? 0 })}
            </div>
          </div>
          <div className="bg-[#0e0e1c] p-4 relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-emerald-400 opacity-80" />
            <div className="text-[8px] tracking-[0.1em] uppercase text-white/35 mb-2">
              {t('fixedPayments.paid')}
            </div>
            <div className="font-bold text-[15px] tracking-[-0.01em] leading-none text-emerald-400 whitespace-nowrap">
              {formatCurrency(paidAmount ?? 0, lang)}
            </div>
            <div className="text-[9px] text-white/30 mt-1.5">
              {t('fixedPayments.countItems', { count: paidCount ?? 0 })}
            </div>
          </div>
          <div className="bg-[#0e0e1c] p-4 relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-violet-500 opacity-80" />
            <div className="text-[8px] tracking-[0.1em] uppercase text-white/35 mb-2">
              {t('fixedPayments.remaining')}
            </div>
            <div className="font-bold text-[15px] tracking-[-0.01em] leading-none text-violet-400 whitespace-nowrap">
              {formatCurrency(remainingAmount ?? 0, lang)}
            </div>
          </div>
          <div className="bg-[#0e0e1c] p-4 relative">
            <div
              className={`absolute top-0 left-0 right-0 h-[2px] opacity-80 ${
                balanceAfterFixed != null && balanceAfterFixed < 0 ? 'bg-rose-400' : 'bg-amber-400'
              }`}
            />
            <div className="text-[8px] tracking-[0.1em] uppercase text-white/35 mb-2">
              {t('fixedPayments.balanceAfter')}
            </div>
            <div
              className={`font-bold text-[15px] tracking-[-0.01em] leading-none whitespace-nowrap ${
                balanceAfterFixed != null && balanceAfterFixed < 0 ? 'text-rose-400' : 'text-amber-400'
              }`}
            >
              {formatCurrency(balanceAfterFixed ?? 0, lang)}
            </div>
            <div className="text-[9px] text-white/30 mt-1.5">{t('fixedPayments.afterAllFixed')}</div>
          </div>
        </div>

        {/* Progress bar */}
        <div className="flex items-center gap-3 px-5 py-2.5 border-b border-white/[0.07]">
          <span className="text-[9px] text-white/30 whitespace-nowrap shrink-0">
            <span className="text-white/80">{paidCount ?? 0}</span>
            <span className="text-white/25"> / {totalCount ?? 0} {t('fixedPayments.paidLabel')}</span>
          </span>
          <div className="flex-1 h-[3px] bg-white/[0.07] rounded-[3px] overflow-hidden">
            <div
              className="h-full rounded-[3px] relative"
              style={{ width: `${paidPct}%`, background: '#8b5cf6' }}
            >
              <div className="absolute right-0 top-0 bottom-0 w-[8px] bg-white/25 animate-pulse" />
            </div>
          </div>
          <span className="text-[9px] text-purple-400 font-medium whitespace-nowrap shrink-0">
            {Math.round(paidPct)}%
          </span>
        </div>

        {/* Next due: highlighted first pending/overdue occurrence */}
        {nextPending && (
          <div
            className={`border-b border-white/[0.07] ${
              npIsOverdue ? 'bg-rose-500/[0.04]' : 'bg-violet-500/[0.04]'
            }`}
          >
            <div
              className={`px-5 pt-2 text-[8px] tracking-[0.1em] uppercase font-semibold ${
                npIsOverdue ? 'text-rose-400/60' : 'text-violet-400/60'
              }`}
            >
              {t('fixedPayments.nextDue')}
            </div>
            <div className="group flex items-center gap-2.5 px-5 py-2.5 cursor-default">
              {npIsOverdue && (
                <div className="w-[5px] h-[5px] rounded-full bg-red-400 shrink-0 animate-pulse" />
              )}
              <div className="w-[30px] h-[30px] bg-[#1a1a2e] border border-white/[0.06] rounded-[9px] flex items-center justify-center text-[13px] shrink-0">
                🔁
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-[12px] font-semibold text-white truncate">{nextPending.name}</div>
                <div className="text-[9px] text-white/25 flex items-center gap-1.5 mt-0.5">
                  <span>{nextPending.dueDate}</span>
                  {nextPending.categoryName && (
                    <>
                      <span>·</span>
                      <span className="text-[8px] tracking-[0.05em] uppercase bg-white/[0.04] border border-white/[0.06] px-1.5 py-px rounded">
                        {nextPending.categoryName}
                      </span>
                    </>
                  )}
                  <span className={`text-[8px] px-1.5 py-px rounded ${getDaysChipClass(npDaysDelta)}`}>
                    {getDaysLabel(npDaysDelta)}
                  </span>
                </div>
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  handleMarkPaidClick(nextPending);
                }}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-[7px] text-[9px] tracking-[0.06em] cursor-pointer border border-green-400/30 bg-green-400/[0.07] text-green-400 md:opacity-0 md:group-hover:opacity-100 transition-all hover:bg-green-400/[0.15] shrink-0"
              >
                <Check className="w-3 h-3" />
                {t('fixedPayments.markAsPaid')}
              </button>
              <div className="text-right shrink-0">
                <div
                  className={`font-bold text-[14px] tracking-[-0.01em] whitespace-nowrap ${
                    npIsOverdue ? 'text-red-400' : 'text-violet-400'
                  }`}
                >
                  {formatCurrency(nextPending.amount, lang)}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Remaining upcoming occurrences — scrollable */}
        <div className="flex flex-col overflow-y-auto dashboard-scroll" style={{ maxHeight: '152px' }}>
          {!nextPending && rows.length === 0 ? (
            <div className="flex items-center justify-center py-8 text-[12px] text-white/25">
              {t('fixedPayments.noOccurrences')}
            </div>
          ) : (
            rows.map((occ) => {
              const daysDelta = occ.dueDate ? computeDaysDelta(occ.dueDate) : 0;
              const isOverdue = occ.status === 'OVERDUE';
              const isPaid = occ.status === 'PAID';
              const isSkipped = occ.status === 'SKIPPED';
              const canMarkPaid = occ.status === 'PENDING' || isOverdue;

              return (
                <div
                  key={occ.id}
                  className={`group flex items-center gap-2.5 px-5 py-2.5 cursor-pointer transition-colors border-b border-white/[0.03] last:border-b-0 ${
                    isOverdue
                      ? 'bg-red-500/[0.03] hover:bg-red-500/[0.06]'
                      : 'hover:bg-white/[0.025]'
                  } ${isPaid ? 'opacity-60' : ''}`}
                >
                  {isOverdue && (
                    <div className="w-[5px] h-[5px] rounded-full bg-red-400 shrink-0 animate-pulse" />
                  )}

                  <div className="w-[30px] h-[30px] bg-[#1a1a2e] border border-white/[0.06] rounded-[9px] flex items-center justify-center text-[13px] shrink-0">
                    🔁
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="text-[12px] font-medium text-white truncate">{occ.name}</div>
                    <div className="text-[9px] text-white/25 flex items-center gap-1.5 mt-0.5">
                      <span>{occ.dueDate}</span>
                      {occ.categoryName && (
                        <>
                          <span>·</span>
                          <span className="text-[8px] tracking-[0.05em] uppercase bg-white/[0.04] border border-white/[0.06] px-1.5 py-px rounded">
                            {occ.categoryName}
                          </span>
                        </>
                      )}
                      <span className={`text-[8px] px-1.5 py-px rounded ${getDaysChipClass(daysDelta)}`}>
                        {getDaysLabel(daysDelta)}
                      </span>
                    </div>
                  </div>

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

                  <div className="text-right shrink-0 flex flex-col items-end gap-1">
                    <div
                      className={`font-bold text-[13px] tracking-[-0.01em] ${
                        isOverdue ? 'text-red-400' : isPaid ? 'text-green-400' : 'text-purple-400'
                      }`}
                    >
                      {formatCurrency(occ.amount, lang)}
                    </div>
                    <span
                      className={`text-[7px] tracking-[0.08em] uppercase px-1.5 py-px rounded ${
                        isOverdue
                          ? 'bg-red-500/[0.12] text-red-400 border border-red-500/25'
                          : isPaid
                          ? 'bg-green-400/10 text-green-400 border border-green-400/20'
                          : isSkipped
                          ? 'bg-white/[0.04] text-white/25 border border-white/[0.06]'
                          : 'bg-purple-500/[0.12] text-purple-400 border border-purple-500/25'
                      }`}
                    >
                      {occ.status}
                    </span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer: overflow items beyond what's shown */}
        {overflowCount > 0 && (
          <a
            href="/transactions/fixed-payments"
            className="block px-5 py-2 text-[9px] text-white/25 text-center border-t border-white/[0.055] cursor-pointer tracking-[0.06em] hover:text-purple-400 transition-colors"
          >
            + {overflowCount} {t('fixedPayments.moreThisCycle')}
          </a>
        )}
      </div>

      {/* Mark as Paid confirmation */}
      {markPaidOccurrence && !showTransactionModal && (
        <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-3 sm:p-6">
          <div
            className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[420px] overflow-hidden relative"
            style={{
              boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1)',
              animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both',
            }}
          >
            <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />
            <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
              <div className="text-lg font-bold tracking-[-0.3px]">
                {t('fixedPayments.markAsPaid')}
              </div>
              <button
                onClick={() => setMarkPaidOccurrence(null)}
                aria-label={t('common.close')}
                className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] transition-all"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
            <div className="px-4 sm:px-7 py-5">
              <div className="flex items-center gap-3 bg-[#131325] border border-white/[0.06] rounded-xl p-4">
                <div className="w-10 h-10 bg-[#1a1a2e] border border-white/[0.06] rounded-[10px] flex items-center justify-center text-lg">
                  🔁
                </div>
                <div className="flex-1">
                  <div className="text-[14px] font-semibold">{markPaidOccurrence.name}</div>
                  <div className="text-[11px] text-white/25 mt-0.5">
                    {t('fixedPayments.dueOn', { date: markPaidOccurrence.dueDate })}
                  </div>
                </div>
                <div className="text-[16px] font-bold text-purple-400">
                  {formatCurrency(markPaidOccurrence.amount, lang)}
                </div>
              </div>
              <p className="text-[12px] text-white/25 mt-4 leading-relaxed">
                {t('fixedPayments.markPaidInfo')}
              </p>
            </div>
            <div className="flex items-center justify-end gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
              <button
                onClick={() => setMarkPaidOccurrence(null)}
                className="px-[22px] py-3 bg-[#131325] border border-white/[0.055] rounded-xl text-base font-semibold text-white/50 cursor-pointer hover:border-white/[0.12] hover:text-white active:bg-[#0e0e1c] active:scale-[0.98] transition-all"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={handleOpenTransactionModal}
                className="flex items-center gap-2 px-5 py-3 rounded-xl border-none text-base font-bold text-white cursor-pointer transition-all hover:-translate-y-px active:scale-[0.98] bg-gradient-to-br from-[#7c3aed] to-[#a855f7] shadow-[0_4px_20px_rgba(124,58,237,0.3)]"
              >
                {t('fixedPayments.openTransaction')}
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      )}

      {showTransactionModal && (
        <TransactionModal
          isOpen={showTransactionModal}
          onClose={handleCloseAll}
          onSave={handleTransactionSave}
          prefill={transactionPrefill}
          categories={categories}
          loading={false}
        />
      )}
    </>
  );
}
