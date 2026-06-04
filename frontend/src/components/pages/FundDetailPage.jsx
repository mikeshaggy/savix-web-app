'use client';
import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { ArrowLeft, Pencil, Archive, CheckCircle2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { formatCurrency, resolveAccentColor, hexToRgba } from '@/utils/helpers';
import { fundApi } from '@/lib/api';
import { Loading } from '@/components/common/Loading';
import ErrorState from '@/components/common/ErrorState';
import FundProgressBar from '@/components/funds/FundProgressBar';
import FundDeadlineCountdown from '@/components/funds/FundDeadlineCountdown';
import FundMovementsList from '@/components/funds/FundMovementsList';
import FundModal from '@/components/modals/FundModal';
import FundDepositModal from '@/components/modals/FundDepositModal';
import FundWithdrawModal from '@/components/modals/FundWithdrawModal';
import FundArchiveModal from '@/components/modals/FundArchiveModal';
import { PAGE_CTA } from '@/components/common/formControls';

const PAGE_SIZE = 10;

// Compact stat tile.
function Stat({ label, children }) {
  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl px-4 py-3">
      <p className="text-[10px] text-white/35 uppercase tracking-[0.1em] mb-1.5">{label}</p>
      {children}
    </div>
  );
}

const OUTLINE_BTN =
  'text-[12px] px-3 py-1.5 rounded-[8px] border border-white/[0.08] text-white/50 hover:text-white hover:border-white/20 transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5';

export default function FundDetailPage({ fundId }) {
  const t = useTranslations('funds');
  const { lang } = useLanguage();

  const [fund, setFund] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [movements, setMovements] = useState(null);
  const [movPage, setMovPage] = useState(0);
  const [movLoading, setMovLoading] = useState(true);
  const [movError, setMovError] = useState(null);

  const [depositOpen, setDepositOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [completing, setCompleting] = useState(false);

  const fetchFund = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setFund(await fundApi.getById(fundId));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [fundId]);

  const fetchMovements = useCallback(async (page) => {
    setMovLoading(true);
    setMovError(null);
    try {
      setMovements(await fundApi.getMovements(fundId, { page, size: PAGE_SIZE }));
    } catch (err) {
      setMovError(err);
    } finally {
      setMovLoading(false);
    }
  }, [fundId]);

  useEffect(() => { fetchFund(); }, [fetchFund]);
  useEffect(() => { fetchMovements(movPage); }, [fetchMovements, movPage]);

  // After any action, refresh the fund and show the newest movements (page 0).
  const handleActionSuccess = useCallback(() => {
    fetchFund();
    if (movPage === 0) fetchMovements(0);
    else setMovPage(0);
  }, [fetchFund, fetchMovements, movPage]);

  // Complete the goal (sets COMPLETED, never archives). Direct, non-modal action.
  const handleComplete = useCallback(async () => {
    setCompleting(true);
    try {
      await fundApi.complete(fundId);
      handleActionSuccess();
    } finally {
      setCompleting(false);
    }
  }, [fundId, handleActionSuccess]);

  const backLink = (
    <Link
      href="/funds"
      className="inline-flex items-center gap-1.5 text-[13px] text-white/40 hover:text-white transition-colors mb-6"
    >
      <ArrowLeft className="w-4 h-4" />
      {t('backToFunds')}
    </Link>
  );

  if (loading) {
    return (
      <div className="flex flex-col min-h-full">
        {backLink}
        <div className="flex-1 flex items-center justify-center">
          <Loading message={t('loadingFund')} size="md" />
        </div>
      </div>
    );
  }

  if (error || !fund) {
    return (
      <div className="flex flex-col min-h-full">
        {backLink}
        <ErrorState
          variant="page"
          title={t('errorLoadingFund')}
          description={t('errorDesc')}
          onRetry={fetchFund}
          retryLabel={t('retry')}
        />
      </div>
    );
  }

  const isActive = fund.status === 'ACTIVE';
  const isCompleted = fund.status === 'COMPLETED';
  const isArchived = fund.status === 'ARCHIVED';
  const currentAmount = Number(fund.currentAmount) || 0;
  const hasBalance = currentAmount > 0;
  const progressPercent = Math.round(Number(fund.progressPercent) || 0);
  const accent = fund.isTargetReached ? '#10b981' : resolveAccentColor(fund.color);
  const useAccent = !isArchived;

  return (
    <div className="flex flex-col min-h-full">
      {backLink}

      {/* Hero */}
      <div className="relative overflow-hidden bg-[#0e0e1c] border border-white/[0.06] rounded-2xl p-6 mb-5">
        {useAccent && (
          <div className="absolute top-0 left-0 right-0 h-px" style={{ backgroundColor: hexToRgba(accent, 0.5) }} />
        )}

        <div className="flex items-start justify-between gap-4 flex-wrap">
          {/* Identity */}
          <div className="flex items-center gap-3 min-w-0">
            {fund.emoji && (
              <span
                className="w-12 h-12 shrink-0 rounded-[12px] flex items-center justify-center text-[24px] leading-none border border-white/[0.06] bg-white/[0.04]"
                style={useAccent ? { backgroundColor: hexToRgba(accent, 0.12), borderColor: hexToRgba(accent, 0.25) } : undefined}
              >
                {fund.emoji}
              </span>
            )}
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-[20px] font-bold text-white tracking-[-0.3px] truncate">{fund.name}</h1>
                {isCompleted && (
                  <span className="text-[11px] font-semibold text-emerald-400 bg-emerald-400/10 border border-emerald-400/20 px-2 py-0.5 rounded-full">
                    {t('status_COMPLETED')}
                  </span>
                )}
                {fund.isTargetReached && isActive && (
                  <span className="text-[11px] font-semibold text-emerald-400 bg-emerald-400/10 border border-emerald-400/20 px-2 py-0.5 rounded-full">
                    {t('targetReachedBadge')}
                  </span>
                )}
                {isArchived && (
                  <span className="text-[11px] font-medium text-white/30 bg-white/[0.04] border border-white/[0.06] px-2 py-0.5 rounded-full">
                    {t('status_ARCHIVED')}
                  </span>
                )}
              </div>
              {fund.description && (
                <p className="text-[13px] text-white/40 mt-1">{fund.description}</p>
              )}
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-2 flex-wrap">
            {isActive && (
              <>
                <button onClick={() => setDepositOpen(true)} className={`${PAGE_CTA} text-[12px] px-3 py-1.5`}>
                  {t('deposit')}
                </button>
                <button
                  onClick={() => setWithdrawOpen(true)}
                  disabled={!hasBalance}
                  title={!hasBalance ? t('noFundsToWithdraw') : undefined}
                  className={OUTLINE_BTN}
                >
                  {t('withdraw')}
                </button>
                <button onClick={() => setEditOpen(true)} className={OUTLINE_BTN}>
                  <Pencil className="w-3.5 h-3.5" />
                  {t('edit')}
                </button>
                {/* Target reached → Complete goal (COMPLETED). Otherwise → Archive. */}
                {fund.isTargetReached ? (
                  <button
                    onClick={handleComplete}
                    disabled={completing}
                    className="text-[12px] px-3 py-1.5 rounded-[8px] border border-emerald-500/40 text-emerald-400 hover:bg-emerald-500/10 hover:border-emerald-400/60 transition-all flex items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    {t('completeGoal')}
                  </button>
                ) : (
                  <button onClick={() => setArchiveOpen(true)} className={OUTLINE_BTN}>
                    <Archive className="w-3.5 h-3.5" />
                    {t('archive')}
                  </button>
                )}
              </>
            )}
            {isCompleted && (
              <button onClick={() => setArchiveOpen(true)} className={OUTLINE_BTN}>
                <Archive className="w-3.5 h-3.5" />
                {t('archive')}
              </button>
            )}
            {isArchived && hasBalance && (
              <button onClick={() => setWithdrawOpen(true)} className={OUTLINE_BTN}>
                {t('withdraw')}
              </button>
            )}
          </div>
        </div>

        {/* Progress */}
        <div className="mt-5 mb-4">
          <div className="flex items-end justify-between mb-2">
            <p className="text-[24px] font-bold text-white tracking-[-0.5px] leading-none">
              {formatCurrency(currentAmount, lang)}
              <span className="text-[14px] font-medium text-white/40 ml-1.5">
                / {formatCurrency(fund.targetAmount, lang)}
              </span>
            </p>
            <span className={`text-[14px] font-bold ${fund.isTargetReached ? 'text-emerald-400' : 'text-white/60'}`}>
              {progressPercent}%
            </span>
          </div>
          <FundProgressBar
            progressPercent={fund.progressPercent}
            isTargetReached={fund.isTargetReached}
            color={useAccent ? accent : undefined}
            height="h-[5px]"
          />
        </div>

        {/* Stats */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mt-5">
          <Stat label={t('saved')}>
            <p className="text-[15px] font-bold text-white font-mono tracking-[-0.3px]">{formatCurrency(currentAmount, lang)}</p>
          </Stat>
          <Stat label={t('target')}>
            <p className="text-[15px] font-bold text-white/70 font-mono tracking-[-0.3px]">{formatCurrency(fund.targetAmount, lang)}</p>
          </Stat>
          <Stat label={t('remaining')}>
            <p className="text-[15px] font-bold text-white/70 font-mono tracking-[-0.3px]">{formatCurrency(fund.remainingAmount, lang)}</p>
          </Stat>
          <Stat label={t('progress')}>
            <p className="text-[15px] font-bold text-violet-400 tracking-[-0.3px]">{progressPercent}%</p>
          </Stat>
          <Stat label={t('deadline')}>
            <div className="text-[14px] font-medium">
              <FundDeadlineCountdown deadlineDate={fund.deadlineDate} />
            </div>
          </Stat>
        </div>
      </div>

      {/* Movement history */}
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-2xl overflow-hidden">
        <div className="px-5 py-4 border-b border-white/[0.055]">
          <h2 className="text-[14px] font-bold text-white tracking-[-0.2px]">{t('movementHistory')}</h2>
        </div>
        <FundMovementsList
          data={movements}
          loading={movLoading}
          error={movError}
          onRetry={() => fetchMovements(movPage)}
          onPrev={() => setMovPage((p) => Math.max(p - 1, 0))}
          onNext={() => setMovPage((p) => p + 1)}
        />
      </div>

      {/* Modals */}
      <FundModal
        isOpen={editOpen}
        fund={fund}
        onClose={() => setEditOpen(false)}
        onSuccess={handleActionSuccess}
      />
      <FundDepositModal
        isOpen={depositOpen}
        fund={fund}
        onClose={() => setDepositOpen(false)}
        onSuccess={handleActionSuccess}
      />
      <FundWithdrawModal
        isOpen={withdrawOpen}
        fund={fund}
        onClose={() => setWithdrawOpen(false)}
        onSuccess={handleActionSuccess}
      />
      <FundArchiveModal
        isOpen={archiveOpen}
        fund={fund}
        onClose={() => setArchiveOpen(false)}
        onSuccess={handleActionSuccess}
      />
    </div>
  );
}
