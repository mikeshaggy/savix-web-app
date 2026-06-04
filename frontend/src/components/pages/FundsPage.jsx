'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { PiggyBank, ChevronDown } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { formatCurrency } from '@/utils/helpers';
import { fundApi } from '@/lib/api';
import { Loading } from '@/components/common/Loading';
import ErrorState from '@/components/common/ErrorState';
import EmptyState from '@/components/common/EmptyState';
import FundCard from '@/components/funds/FundCard';
import FundProgressBar from '@/components/funds/FundProgressBar';
import FundModal from '@/components/modals/FundModal';
import FundDepositModal from '@/components/modals/FundDepositModal';
import FundWithdrawModal from '@/components/modals/FundWithdrawModal';
import FundArchiveModal from '@/components/modals/FundArchiveModal';
import { PAGE_CTA } from '@/components/common/formControls';

// Compact stat tile for the funds summary row.
function SummaryStat({ label, children }) {
  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl px-4 py-3">
      <p className="text-[10px] text-white/35 uppercase tracking-[0.1em] mb-1.5">{label}</p>
      {children}
    </div>
  );
}

export default function FundsPage() {
  const t = useTranslations('funds');
  const { lang } = useLanguage();

  const [funds, setFunds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [archivedOpen, setArchivedOpen] = useState(false);

  // Modal state
  const [fundModal, setFundModal] = useState({ open: false, fund: null });
  const [depositModal, setDepositModal] = useState({ open: false, fund: null });
  const [withdrawModal, setWithdrawModal] = useState({ open: false, fund: null });
  const [archiveModal, setArchiveModal] = useState({ open: false, fund: null });

  const fetchFunds = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fundApi.getAll();
      setFunds(data ?? []);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchFunds(); }, [fetchFunds]);

  const activeFunds    = funds.filter(f => f.status === 'ACTIVE');
  const completedFunds = funds.filter(f => f.status === 'COMPLETED');
  const archivedFunds  = funds.filter(f => f.status === 'ARCHIVED');

  // Summary row — derived entirely from already-fetched funds (no extra request).
  const totalSaved  = activeFunds.reduce((s, f) => s + (Number(f.currentAmount) || 0), 0);
  const totalTarget = activeFunds.reduce((s, f) => s + (Number(f.targetAmount) || 0), 0);
  const overallPct  = totalTarget > 0 ? Math.min(Math.round((totalSaved / totalTarget) * 100), 100) : 0;

  const closestDeadline = (() => {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const upcoming = activeFunds
      .filter(f => f.deadlineDate)
      .map(f => ({ name: f.name, days: Math.round((new Date(`${f.deadlineDate}T00:00:00`) - today) / 86400000) }))
      .filter(f => f.days >= 0)
      .sort((a, b) => a.days - b.days);
    return upcoming[0] || null;
  })();

  const handleModalSuccess = useCallback(() => { fetchFunds(); }, [fetchFunds]);

  // Completing a goal is a direct (non-modal) action; it sets COMPLETED, never archives.
  const handleComplete = useCallback(async (fund) => {
    try {
      await fundApi.complete(fund.id);
    } finally {
      fetchFunds();
    }
  }, [fetchFunds]);

  return (
    <div className="flex flex-col min-h-full">
      {/* Page header */}
      <div className="flex items-start justify-between gap-4 mb-8">
        <div>
          <h1 className="text-[22px] font-bold text-white tracking-[-0.4px]">{t('pageTitle')}</h1>
          <p className="text-[14px] text-white/40 mt-1">{t('pageSubtitle')}</p>
        </div>
        <button
          onClick={() => setFundModal({ open: true, fund: null })}
          className={PAGE_CTA}
        >
          <PiggyBank className="w-4 h-4" />
          {t('newFund')}
        </button>
      </div>

      {/* Loading */}
      {loading && (
        <div className="flex-1 flex items-center justify-center">
          <Loading message={t('loadingTitle')} size="md" />
        </div>
      )}

      {/* Error */}
      {!loading && error && (
        <ErrorState
          variant="page"
          title={t('errorTitle')}
          description={t('errorDesc')}
          onRetry={fetchFunds}
          retryLabel={t('retry')}
        />
      )}

      {/* Empty — no funds at all */}
      {!loading && !error && funds.length === 0 && (
        <EmptyState
          variant="page"
          icon={PiggyBank}
          title={t('emptyTitle')}
          description={t('emptyDesc')}
          action={{ label: t('createFirst'), onClick: () => setFundModal({ open: true, fund: null }) }}
        />
      )}

      {/* Content */}
      {!loading && !error && funds.length > 0 && (
        <div className="space-y-8">

          {/* Summary row — only when there are active goals to summarize */}
          {activeFunds.length > 0 && (
            <div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <SummaryStat label={t('totalSaved')}>
                  <p className="text-[17px] font-bold text-white tracking-[-0.3px] font-mono">
                    {formatCurrency(totalSaved, lang)}
                  </p>
                </SummaryStat>
                <SummaryStat label={t('totalTarget')}>
                  <p className="text-[17px] font-bold text-white/70 tracking-[-0.3px] font-mono">
                    {formatCurrency(totalTarget, lang)}
                  </p>
                </SummaryStat>
                <SummaryStat label={t('overallProgress')}>
                  <p className="text-[17px] font-bold text-violet-400 tracking-[-0.3px] mb-1.5 leading-none">
                    {overallPct}%
                  </p>
                  <FundProgressBar progressPercent={overallPct} />
                </SummaryStat>
                <SummaryStat label={t('activeFundsLabel')}>
                  <p className="text-[17px] font-bold text-white tracking-[-0.3px]">{activeFunds.length}</p>
                </SummaryStat>
              </div>
              {closestDeadline && (
                <p className="text-[12px] text-white/35 mt-2.5">
                  {t('closestDeadline')}: <span className="text-white/60">{closestDeadline.name}</span>
                  {' · '}
                  <span className="text-amber-400/80">
                    {closestDeadline.days === 0 ? t('dueToday') : t('daysLeft', { days: closestDeadline.days })}
                  </span>
                </p>
              )}
            </div>
          )}

          {/* Active funds */}
          <section>
            {activeFunds.length === 0 ? (
              <p className="text-[14px] text-white/30 py-6 text-center">{t('noActiveFunds')}</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {activeFunds.map(fund => (
                  <FundCard
                    key={fund.id}
                    fund={fund}
                    onDeposit={f => setDepositModal({ open: true, fund: f })}
                    onWithdraw={f => setWithdrawModal({ open: true, fund: f })}
                    onEdit={f => setFundModal({ open: true, fund: f })}
                    onArchive={f => setArchiveModal({ open: true, fund: f })}
                    onComplete={handleComplete}
                  />
                ))}
              </div>
            )}
          </section>

          {/* Completed funds — finished goals, still visible */}
          {completedFunds.length > 0 && (
            <section>
              <h2 className="text-[13px] font-semibold text-white/50 mb-4 flex items-center gap-2">
                {t('completedSection')}
                <span className="text-white/25 font-normal">({completedFunds.length})</span>
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {completedFunds.map(fund => (
                  <FundCard
                    key={fund.id}
                    fund={fund}
                    onDeposit={null}
                    onWithdraw={null}
                    onEdit={null}
                    onArchive={f => setArchiveModal({ open: true, fund: f })}
                    onComplete={null}
                  />
                ))}
              </div>
            </section>
          )}

          {/* Archived funds — collapsible */}
          {archivedFunds.length > 0 && (
            <section>
              <button
                type="button"
                onClick={() => setArchivedOpen(v => !v)}
                className="flex items-center gap-2 text-[13px] font-medium text-white/40 hover:text-white/70 transition-colors mb-4"
              >
                <ChevronDown
                  className={`w-4 h-4 transition-transform duration-200 ${archivedOpen ? 'rotate-180' : ''}`}
                />
                {t('archivedSection')}
                <span className="text-white/25 font-normal">({archivedFunds.length})</span>
              </button>

              {archivedOpen && (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                  {archivedFunds.map(fund => (
                    <FundCard
                      key={fund.id}
                      fund={fund}
                      onDeposit={null}
                      onWithdraw={f => setWithdrawModal({ open: true, fund: f })}
                      onEdit={null}
                      onArchive={null}
                    />
                  ))}
                </div>
              )}
            </section>
          )}
        </div>
      )}

      {/* Modals */}
      <FundModal
        isOpen={fundModal.open}
        fund={fundModal.fund}
        onClose={() => setFundModal({ open: false, fund: null })}
        onSuccess={handleModalSuccess}
      />
      <FundDepositModal
        isOpen={depositModal.open}
        fund={depositModal.fund}
        onClose={() => setDepositModal({ open: false, fund: null })}
        onSuccess={handleModalSuccess}
      />
      <FundWithdrawModal
        isOpen={withdrawModal.open}
        fund={withdrawModal.fund}
        onClose={() => setWithdrawModal({ open: false, fund: null })}
        onSuccess={handleModalSuccess}
      />
      <FundArchiveModal
        isOpen={archiveModal.open}
        fund={archiveModal.fund}
        onClose={() => setArchiveModal({ open: false, fund: null })}
        onSuccess={handleModalSuccess}
      />
    </div>
  );
}
