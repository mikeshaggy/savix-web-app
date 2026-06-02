'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { X, Loader2, Archive } from 'lucide-react';
import { useWallets } from '@/contexts/WalletContext';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { formatCurrency } from '@/utils/helpers';
import { fundApi } from '@/lib/api';
import Button from '@/components/common/Button';
import { SELECT_MD } from '@/components/common/formControls';

const LABEL = 'text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5';

export default function FundArchiveModal({ isOpen, onClose, onSuccess, fund }) {
  const t = useTranslations();
  const { wallets } = useWallets();
  const { lang } = useLanguage();

  const [decision, setDecision] = useState(null); // 'return' | 'keep'
  const [returnToWalletId, setReturnToWalletId] = useState('');
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    setDecision(null);
    setReturnToWalletId('');
    setErrors({});
  }, [isOpen]);

  const handleDecision = useCallback((val) => {
    setDecision(val);
    setErrors(prev => ({ ...prev, decision: '', submit: '' }));
  }, []);

  const fundCurrentAmount = Number(fund?.currentAmount) || 0;
  const hasBalance = fundCurrentAmount > 0;

  const isSubmitDisabled = () => {
    if (submitting) return true;
    if (!hasBalance) return false;
    if (!decision) return true;
    if (decision === 'return' && !returnToWalletId) return true;
    return false;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (hasBalance && !decision) errs.decision = t('funds.errors.balanceDecisionRequired');
    if (hasBalance && decision === 'return' && !returnToWalletId) errs.returnWallet = t('funds.errors.returnWalletRequired');
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }

    try {
      setSubmitting(true);
      let body = {};
      if (hasBalance) {
        if (decision === 'return') {
          body = { returnRemainingBalance: true, returnToWalletId: parseInt(returnToWalletId) };
        } else {
          body = { returnRemainingBalance: false };
        }
      }
      await fundApi.archive(fund.id, body);
      onSuccess?.();
      onClose();
    } catch (err) {
      setErrors(prev => ({ ...prev, submit: err.message || 'Something went wrong' }));
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen || !fund) return null;

  return (
    <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-3 sm:p-6">
      <div
        className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[480px] max-h-[90vh] overflow-hidden relative"
        style={{
          boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1), 0 0 80px rgba(124,58,237,0.06)',
          animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both',
        }}
      >
        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />

        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
          <div>
            <div className="text-lg sm:text-xl font-bold tracking-[-0.3px]">{t('funds.archiveTitle')}</div>
            <div className="text-[13px] text-white/40 mt-0.5">{fund.name}</div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] hover:bg-[#1a1a2e] transition-all"
            aria-label={t('common.close')}
          >
            <X className="w-3 h-3" />
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit}>
          <div className="px-4 sm:px-7 py-5 sm:py-6 space-y-5 max-h-[calc(90vh-150px)] overflow-y-auto">

            {/* Description */}
            <p className="text-[14px] text-white/60 leading-relaxed">{t('funds.archiveDesc')}</p>

            {/* Balance decision — only if fund has balance */}
            {hasBalance && (
              <div className="space-y-3">
                <p className="text-[14px] text-white/80">
                  {t('funds.archiveBalanceInfo', { amount: formatCurrency(fundCurrentAmount, lang) })}
                </p>

                {/* Option A: Return */}
                <div
                  role="button"
                  tabIndex={0}
                  onClick={() => handleDecision('return')}
                  onKeyDown={e => (e.key === 'Enter' || e.key === ' ') && handleDecision('return')}
                  className={`w-full text-left p-4 rounded-xl border transition-all cursor-pointer ${
                    decision === 'return'
                      ? 'border-purple-500/40 bg-purple-500/10'
                      : 'border-white/[0.08] bg-[#131325] hover:border-white/20'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <div className={`w-4 h-4 rounded-full border-2 flex-shrink-0 flex items-center justify-center ${decision === 'return' ? 'border-purple-400' : 'border-white/20'}`}>
                      {decision === 'return' && <div className="w-2 h-2 rounded-full bg-purple-400" />}
                    </div>
                    <span className="text-[14px] font-medium text-white/80">{t('funds.archiveReturnTo')}</span>
                  </div>

                  {decision === 'return' && (
                    <div className="mt-3 ml-6.5">
                      <div className={LABEL}>{t('funds.archiveReturnWallet')}</div>
                      <select
                        value={returnToWalletId}
                        onChange={e => { setReturnToWalletId(e.target.value); setErrors(prev => ({ ...prev, returnWallet: '' })); }}
                        className={`${SELECT_MD} w-full ${errors.returnWallet ? 'border-red-500' : 'border-white/[0.055]'}`}
                      >
                        <option value="">{t('funds.selectWallet')}</option>
                        {wallets.map(w => (
                          <option key={w.id} value={w.id}>{w.name}</option>
                        ))}
                      </select>
                      {errors.returnWallet && <p className="text-red-400 text-xs mt-1.5">{errors.returnWallet}</p>}
                    </div>
                  )}
                </div>

                {/* Option B: Keep */}
                <div
                  role="button"
                  tabIndex={0}
                  onClick={() => handleDecision('keep')}
                  onKeyDown={e => (e.key === 'Enter' || e.key === ' ') && handleDecision('keep')}
                  className={`w-full text-left p-4 rounded-xl border transition-all cursor-pointer ${
                    decision === 'keep'
                      ? 'border-purple-500/40 bg-purple-500/10'
                      : 'border-white/[0.08] bg-[#131325] hover:border-white/20'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <div className={`w-4 h-4 rounded-full border-2 flex-shrink-0 flex items-center justify-center ${decision === 'keep' ? 'border-purple-400' : 'border-white/20'}`}>
                      {decision === 'keep' && <div className="w-2 h-2 rounded-full bg-purple-400" />}
                    </div>
                    <div>
                      <div className="text-[14px] font-medium text-white/80">{t('funds.archiveKeepBalance')}</div>
                      <div className="text-[12px] text-white/40 mt-0.5">{t('funds.archiveKeepNote')}</div>
                    </div>
                  </div>
                </div>

                {errors.decision && <p className="text-red-400 text-xs">{errors.decision}</p>}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-end gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
            {errors.submit && <p className="text-red-400 text-sm mr-auto">{errors.submit}</p>}
            <Button type="button" variant="secondary" size="md" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={isSubmitDisabled()}>
              {submitting ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <>
                  <Archive className="w-3.5 h-3.5" />
                  {t('funds.archiveConfirm')}
                </>
              )}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
