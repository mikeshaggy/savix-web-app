'use client';
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { X, Save, Loader2 } from 'lucide-react';
import { useWallets } from '@/contexts/WalletContext';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { formatCurrency } from '@/utils/helpers';
import { fundApi } from '@/lib/api';
import Button from '@/components/common/Button';
import { INPUT_MD, SELECT_MD, TEXTAREA_MD } from '@/components/common/formControls';

const LABEL = 'text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5';

export default function FundWithdrawModal({ isOpen, onClose, onSuccess, fund }) {
  const t = useTranslations();
  const { wallets } = useWallets();
  const { lang } = useLanguage();

  const [formData, setFormData] = useState({ destinationWalletId: '', amount: '', date: '', notes: '' });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    const today = new Date().toISOString().split('T')[0];
    setFormData({ destinationWalletId: '', amount: '', date: today, notes: '' });
    setErrors({});
  }, [isOpen]);

  const handleChange = useCallback((field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    setErrors(prev => ({ ...prev, [field]: '', submit: '' }));
  }, []);

  const destWallet = useMemo(
    () => wallets.find(w => w.id === parseInt(formData.destinationWalletId)),
    [wallets, formData.destinationWalletId]
  );

  const parsedAmount = parseFloat(formData.amount) || 0;
  const fundCurrentAmount = Number(fund?.currentAmount) || 0;
  const destBalanceAfter = destWallet ? (destWallet.balance ?? 0) + parsedAmount : null;
  const fundAmountAfter = parsedAmount > 0 ? fundCurrentAmount - parsedAmount : null;
  const progressAfter = fund?.targetAmount > 0 && fundAmountAfter !== null
    ? Math.min(Math.max((fundAmountAfter / Number(fund.targetAmount)) * 100, 0), 100)
    : null;
  const insufficientFund = parsedAmount > 0 && parsedAmount > fundCurrentAmount;

  const validate = () => {
    const errs = {};
    if (!formData.destinationWalletId) errs.destinationWalletId = t('funds.errors.destinationWalletRequired');
    const amt = parseFloat(formData.amount);
    if (!formData.amount) errs.amount = t('funds.errors.amountRequired');
    else if (isNaN(amt) || amt <= 0) errs.amount = t('funds.errors.amountInvalid');
    if (!formData.date) errs.date = t('funds.errors.dateRequired');
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    try {
      setSubmitting(true);
      await fundApi.withdraw(fund.id, {
        destinationWalletId: parseInt(formData.destinationWalletId),
        amount: parseFloat(formData.amount),
        date: formData.date,
        notes: formData.notes.trim() || undefined,
      });
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
        className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[540px] max-h-[90vh] overflow-hidden relative"
        style={{
          boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1), 0 0 80px rgba(124,58,237,0.06)',
          animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both',
        }}
      >
        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />

        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
          <div>
            <div className="text-lg sm:text-xl font-bold tracking-[-0.3px]">
              {fund.emoji && <span className="mr-2">{fund.emoji}</span>}
              {t('funds.withdrawTitle')}
            </div>
            <div className="text-[13px] text-white/40 mt-0.5">
              {fund.name} · {formatCurrency(fundCurrentAmount, lang)} {t('funds.saved')}
            </div>
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

            {/* Destination Wallet */}
            <div>
              <div className={LABEL}>{t('funds.withdrawDest')} <span className="text-purple-300">*</span></div>
              <select
                value={formData.destinationWalletId}
                onChange={e => handleChange('destinationWalletId', e.target.value)}
                className={`${SELECT_MD} w-full ${errors.destinationWalletId ? 'border-red-500' : 'border-white/[0.055]'}`}
              >
                <option value="">{t('funds.selectWallet')}</option>
                {wallets.map(w => (
                  <option key={w.id} value={w.id}>{w.name}</option>
                ))}
              </select>
              {errors.destinationWalletId && <p className="text-red-400 text-xs mt-1.5">{errors.destinationWalletId}</p>}
            </div>

            {/* Amount */}
            <div>
              <div className={LABEL}>{t('funds.amount')} <span className="text-purple-300">*</span></div>
              <div className="relative">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 font-mono text-base font-medium text-white/25">PLN</span>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={formData.amount}
                  onChange={e => handleChange('amount', e.target.value)}
                  className={`w-full bg-[#131325] border rounded-[11px] pl-14 pr-3.5 py-3 font-mono text-xl font-medium tracking-[-0.5px] text-white placeholder:text-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] ${errors.amount ? 'border-red-500' : 'border-white/[0.055]'}`}
                  placeholder="0.00"
                />
              </div>
              {errors.amount && <p className="text-red-400 text-xs mt-1.5">{errors.amount}</p>}
            </div>

            {/* Live preview */}
            {parsedAmount > 0 && (
              <div className="p-4 bg-[#131325] border border-white/[0.055] rounded-2xl space-y-2">
                <div className="text-[11px] font-bold tracking-[0.12em] uppercase text-white/25 mb-3">
                  {t('transfer.balancePreview')}
                </div>
                <div className="flex justify-between text-[13px]">
                  <span className="text-white/40">{t('funds.fundAfter')}</span>
                  <span className={`font-mono font-semibold ${insufficientFund ? 'text-red-400' : 'text-white/80'}`}>
                    {formatCurrency(Math.max(fundCurrentAmount - parsedAmount, 0), lang)}
                    {progressAfter !== null && !insufficientFund && (
                      <span className="text-white/30 font-normal ml-1">· {Math.round(progressAfter)}%</span>
                    )}
                  </span>
                </div>
                {destWallet && (
                  <div className="flex justify-between text-[13px]">
                    <span className="text-white/40">{t('funds.walletAfter')}</span>
                    <span className="font-mono font-semibold text-emerald-400">
                      {formatCurrency(destBalanceAfter, lang)}
                    </span>
                  </div>
                )}
                {insufficientFund && (
                  <div className="mt-2 text-[11px] text-red-400/80 bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2">
                    {t('funds.insufficientFundBalance')}
                  </div>
                )}
              </div>
            )}

            {/* Date */}
            <div>
              <div className={LABEL}>{t('funds.withdrawDate')} <span className="text-purple-300">*</span></div>
              <input
                type="date"
                value={formData.date}
                onChange={e => handleChange('date', e.target.value)}
                className={`${INPUT_MD} w-full [color-scheme:dark] ${errors.date ? 'border-red-500' : 'border-white/[0.055]'}`}
              />
              {errors.date && <p className="text-red-400 text-xs mt-1.5">{errors.date}</p>}
            </div>

            {/* Notes */}
            <div>
              <div className={LABEL}>{t('funds.withdrawNotes')}</div>
              <textarea
                value={formData.notes}
                onChange={e => handleChange('notes', e.target.value)}
                rows={2}
                placeholder={t('funds.withdrawNotesPh')}
                className={`${TEXTAREA_MD} w-full border-white/[0.055] min-h-[60px] max-h-[100px]`}
              />
            </div>
          </div>

          {/* Footer */}
          <div className="flex items-center justify-end gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
            {errors.submit && <p className="text-red-400 text-sm mr-auto">{errors.submit}</p>}
            <Button type="button" variant="secondary" size="md" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={submitting || insufficientFund}>
              {submitting ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <>
                  <div className="w-[18px] h-[18px] bg-white/20 rounded-[6px] flex items-center justify-center">
                    <Save className="w-2.5 h-2.5" />
                  </div>
                  {t('funds.withdrawSave')}
                </>
              )}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
