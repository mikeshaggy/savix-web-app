'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { X, Save, Loader2 } from 'lucide-react';
import { useWallets } from '@/contexts/WalletContext';
import { useTranslations } from 'next-intl';
import { fundApi } from '@/lib/api';
import Button from '@/components/common/Button';
import EmojiPickerField from '@/components/common/EmojiPickerField';
import ColorPickerField from '@/components/common/ColorPickerField';
import { COLOR_PRESETS } from '@/components/common/pickerPresets';
import { INPUT_MD, SELECT_MD, TEXTAREA_MD } from '@/components/common/formControls';

const LABEL = 'text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5';
const OPT = 'text-white/20 font-normal normal-case tracking-normal';

export default function FundModal({ isOpen, onClose, onSuccess, fund = null }) {
  const t = useTranslations();
  const { wallets } = useWallets();

  const isEditing = !!fund;

  const [formData, setFormData] = useState({ name: '', targetAmount: '', icon: '', color: '', deadlineDate: '', sourceWalletId: '', description: '' });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    if (isEditing) {
      setFormData({
        name: fund.name || '',
        targetAmount: fund.targetAmount?.toString() || '',
        icon: fund.icon || '',
        color: fund.color || '',
        deadlineDate: fund.deadlineDate || '',
        sourceWalletId: fund.sourceWalletId?.toString() || '',
        description: fund.description || '',
      });
    } else {
      setFormData({ name: '', targetAmount: '', icon: '', color: '', deadlineDate: '', sourceWalletId: '', description: '' });
    }
    setErrors({});
  }, [isOpen, fund, isEditing]);

  const handleChange = useCallback((field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    setErrors(prev => ({ ...prev, [field]: '', submit: '' }));
  }, []);

  const validate = () => {
    const errs = {};
    if (!formData.name.trim()) errs.name = t('funds.errors.nameRequired');
    else if (formData.name.trim().length > 100) errs.name = t('funds.errors.nameTooLong');
    const target = parseFloat(formData.targetAmount);
    if (!formData.targetAmount) errs.targetAmount = t('funds.errors.targetRequired');
    else if (isNaN(target) || target <= 0) errs.targetAmount = t('funds.errors.targetInvalid');
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    try {
      setSubmitting(true);
      const body = {
        name: formData.name.trim(),
        targetAmount: parseFloat(formData.targetAmount),
        icon: formData.icon.trim() || undefined,
        color: formData.color.trim() || undefined,
        deadlineDate: formData.deadlineDate || undefined,
        sourceWalletId: formData.sourceWalletId ? parseInt(formData.sourceWalletId) : undefined,
        description: formData.description.trim() || undefined,
      };
      if (isEditing) {
        await fundApi.update(fund.id, body);
      } else {
        await fundApi.create(body);
      }
      onSuccess?.();
      onClose();
    } catch (err) {
      setErrors(prev => ({ ...prev, submit: err.message || 'Something went wrong' }));
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-3 sm:p-6">
      <div
        className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[560px] max-h-[90vh] overflow-hidden relative"
        style={{
          boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1), 0 0 80px rgba(124,58,237,0.06)',
          animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both',
        }}
      >
        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />

        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
          <div className="text-lg sm:text-xl font-bold tracking-[-0.3px]">
            {isEditing ? t('funds.editFund') : t('funds.newFund')}
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

            {/* Name */}
            <div>
              <div className={LABEL}>{t('funds.name')} <span className="text-purple-300">*</span></div>
              <input
                type="text"
                value={formData.name}
                onChange={e => handleChange('name', e.target.value)}
                maxLength={100}
                placeholder={t('funds.namePlaceholder')}
                className={`${INPUT_MD} w-full ${errors.name ? 'border-red-500' : 'border-white/[0.055]'}`}
              />
              {errors.name && <p className="text-red-400 text-xs mt-1.5">{errors.name}</p>}
            </div>

            {/* Target Amount */}
            <div>
              <div className={LABEL}>{t('funds.targetAmount')} <span className="text-purple-300">*</span></div>
              <div className="relative">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 font-mono text-base font-medium text-white/25">PLN</span>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={formData.targetAmount}
                  onChange={e => handleChange('targetAmount', e.target.value)}
                  className={`w-full bg-[#131325] border rounded-[11px] pl-14 pr-3.5 py-3 font-mono text-xl font-medium tracking-[-0.5px] text-white placeholder:text-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] ${errors.targetAmount ? 'border-red-500' : 'border-white/[0.055]'}`}
                  placeholder="0.00"
                />
              </div>
              {errors.targetAmount && <p className="text-red-400 text-xs mt-1.5">{errors.targetAmount}</p>}
            </div>

            {/* Icon */}
            <EmojiPickerField
              label={t('funds.icon')}
              optionalLabel={t('funds.optional')}
              value={formData.icon}
              onChange={v => handleChange('icon', v)}
              placeholder={t('funds.iconPlaceholder')}
              fallback="🏦"
              maxLength={10}
              inputClassName={INPUT_MD}
            />

            {/* Color */}
            <ColorPickerField
              label={t('funds.color')}
              optionalLabel={t('funds.optional')}
              value={formData.color}
              onChange={v => handleChange('color', v)}
              presets={COLOR_PRESETS}
              hint={t('funds.colorHint')}
            />

            {/* Deadline */}
            <div>
              <div className={LABEL}>{t('funds.deadline')} <span className={OPT}>— {t('funds.optional')}</span></div>
              <input
                type="date"
                value={formData.deadlineDate}
                onChange={e => handleChange('deadlineDate', e.target.value)}
                className={`${INPUT_MD} w-full border-white/[0.055] [color-scheme:dark]`}
              />
            </div>

            {/* Source Wallet */}
            <div>
              <div className={LABEL}>{t('funds.sourceWallet')} <span className={OPT}>— {t('funds.optional')}</span></div>
              <select
                value={formData.sourceWalletId}
                onChange={e => handleChange('sourceWalletId', e.target.value)}
                className={`${SELECT_MD} w-full border-white/[0.055]`}
              >
                <option value="">{t('funds.selectWallet')}</option>
                {wallets.map(w => (
                  <option key={w.id} value={w.id}>{w.name}</option>
                ))}
              </select>
            </div>

            {/* Description */}
            <div>
              <div className={LABEL}>{t('funds.description')} <span className={OPT}>— {t('funds.optional')}</span></div>
              <textarea
                value={formData.description}
                onChange={e => handleChange('description', e.target.value)}
                rows={3}
                placeholder={t('funds.descriptionPlaceholder')}
                className={`${TEXTAREA_MD} w-full border-white/[0.055] min-h-[70px] max-h-[120px]`}
              />
            </div>
          </div>

          {/* Footer */}
          <div className="flex items-center justify-end gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
            {errors.submit && <p className="text-red-400 text-sm mr-auto">{errors.submit}</p>}
            <Button type="button" variant="secondary" size="md" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={submitting}>
              {submitting ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <>
                  <div className="w-[18px] h-[18px] bg-white/20 rounded-[6px] flex items-center justify-center">
                    <Save className="w-2.5 h-2.5" />
                  </div>
                  {isEditing ? t('funds.saveChanges') : t('funds.createFund')}
                </>
              )}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
