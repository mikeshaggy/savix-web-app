'use client';
import React from 'react';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { formatCurrency, resolveAccentColor, hexToRgba } from '@/utils/helpers';
import FundProgressBar from './FundProgressBar';
import FundDeadlineCountdown from './FundDeadlineCountdown';
import { PAGE_CTA } from '@/components/common/formControls';

export default function FundCard({ fund, onDeposit, onWithdraw, onEdit, onArchive }) {
  const t = useTranslations('funds');
  const { lang } = useLanguage();

  const isArchived = fund.status === 'ARCHIVED';
  const progressPercent = Math.round(Number(fund.progressPercent) || 0);
  const currentAmount = Number(fund.currentAmount) || 0;
  const hasBalance = currentAmount > 0;

  // Subtle per-fund accent. Target-reached always reads emerald so it stays
  // distinct; archived cards stay neutral. Invalid/empty colors fall back to violet.
  const accent = fund.isTargetReached ? '#10b981' : resolveAccentColor(fund.color);
  const useAccent = !isArchived;

  const cardBase = isArchived
    ? 'bg-[#0e0e1c] border border-white/[0.04] rounded-2xl p-5 opacity-60'
    : fund.isTargetReached
      ? 'bg-[#0e0e1c] border border-emerald-500/30 rounded-2xl p-5 transition-colors'
      : 'bg-[#0e0e1c] border border-white/[0.06] rounded-2xl p-5 transition-colors hover:border-white/[0.12]';

  return (
    <div className={`relative overflow-hidden ${cardBase}`}>
      {/* Subtle top accent line */}
      {useAccent && (
        <div className="absolute top-0 left-0 right-0 h-px" style={{ backgroundColor: hexToRgba(accent, 0.5) }} />
      )}

      {/* Header: icon + name + status badges */}
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex items-center gap-2.5 min-w-0">
          {fund.icon && (
            <span
              className="w-9 h-9 shrink-0 rounded-[10px] flex items-center justify-center text-[18px] leading-none border border-white/[0.06] bg-white/[0.04]"
              style={useAccent ? { backgroundColor: hexToRgba(accent, 0.12), borderColor: hexToRgba(accent, 0.25) } : undefined}
            >
              {fund.icon}
            </span>
          )}
          <div className="min-w-0">
            <h3 className="text-[15px] font-semibold text-white tracking-[-0.2px] truncate">
              {fund.name}
            </h3>
            {fund.description && (
              <p className="text-[12px] text-white/40 mt-0.5 truncate">{fund.description}</p>
            )}
          </div>
        </div>

        {/* Badges */}
        <div className="flex items-center gap-1.5 flex-shrink-0">
          {fund.isTargetReached && !isArchived && (
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
      </div>

      {/* Progress bar */}
      <FundProgressBar
        progressPercent={fund.progressPercent}
        isTargetReached={fund.isTargetReached}
        color={useAccent ? accent : undefined}
        height="h-[3px]"
      />

      {/* Amounts row — saved is primary, target secondary */}
      <div className="flex items-end justify-between mt-3 mb-3">
        <div>
          <p className="text-[11px] text-white/35 uppercase tracking-[0.06em] mb-0.5">{t('saved')}</p>
          <p className="text-[19px] font-bold text-white tracking-[-0.4px] leading-none">
            {formatCurrency(currentAmount, lang)}
          </p>
        </div>
        <div className="text-right">
          <p className="text-[10px] text-white/30 uppercase tracking-[0.06em] mb-0.5">{t('target')}</p>
          <p className="text-[13px] font-medium text-white/45">
            {formatCurrency(fund.targetAmount, lang)}
          </p>
        </div>
      </div>

      {/* Progress + remaining + deadline row */}
      <div className="flex items-center justify-between mb-4">
        <span className="text-[12px] text-white/40">
          {progressPercent}%
          {!fund.isTargetReached && (
            <>
              {' · '}
              <span className="text-white/60">{formatCurrency(fund.remainingAmount, lang)}</span>
              {' '}{t('remaining').toLowerCase()}
            </>
          )}
        </span>
        <FundDeadlineCountdown deadlineDate={fund.deadlineDate} />
      </div>

      {/* Action buttons — active fund */}
      {!isArchived && (
        <div className="flex gap-2 pt-3 border-t border-white/[0.04]">
          <button
            onClick={() => onDeposit?.(fund)}
            disabled={!onDeposit}
            className={`${PAGE_CTA} text-[12px] px-3 py-1.5 flex-1 justify-center disabled:opacity-40 disabled:cursor-not-allowed disabled:translate-y-0`}
          >
            {t('deposit')}
          </button>
          <button
            onClick={() => onWithdraw?.(fund)}
            disabled={!onWithdraw || !hasBalance}
            title={!hasBalance ? t('noFundsToWithdraw') : undefined}
            className="flex-1 text-[12px] px-3 py-1.5 rounded-[8px] border border-white/[0.08] text-white/50 hover:text-white hover:border-white/20 transition-all disabled:opacity-40 disabled:cursor-not-allowed justify-center flex items-center gap-1"
          >
            {t('withdraw')}
          </button>
          <button
            onClick={() => onEdit?.(fund)}
            disabled={!onEdit}
            className="text-[12px] px-3 py-1.5 rounded-[8px] border border-white/[0.08] text-white/40 hover:text-white hover:border-white/20 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {t('edit')}
          </button>
          <button
            onClick={() => onArchive?.(fund)}
            disabled={!onArchive}
            className={`text-[12px] px-3 py-1.5 rounded-[8px] border transition-all disabled:opacity-40 disabled:cursor-not-allowed ${
              fund.isTargetReached
                ? 'border-emerald-500/40 text-emerald-400 hover:bg-emerald-500/10 hover:border-emerald-400/60'
                : 'border-white/[0.08] text-white/40 hover:text-white hover:border-white/20'
            }`}
          >
            {fund.isTargetReached ? t('completeGoal') : t('archive')}
          </button>
        </div>
      )}

      {/* Action buttons — archived fund (withdraw only, if balance remains) */}
      {isArchived && hasBalance && (
        <div className="flex gap-2 pt-3 border-t border-white/[0.04]">
          <button
            onClick={() => onWithdraw?.(fund)}
            disabled={!onWithdraw}
            className="flex-1 text-[12px] px-3 py-1.5 rounded-[8px] border border-white/[0.08] text-white/50 hover:text-white hover:border-white/20 transition-all disabled:opacity-40 disabled:cursor-not-allowed justify-center flex items-center gap-1"
          >
            {t('withdraw')}
          </button>
        </div>
      )}
    </div>
  );
}
