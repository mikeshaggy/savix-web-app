'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { X, Plus, Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { categoryBudgetsApi, categoryApi } from '@/lib/api';
import { INPUT_SM } from '@/components/common/formControls';

/**
 * Create-only modal for adding a new category budget.
 * Props:
 *   isOpen      – boolean
 *   onClose     – () => void
 *   walletId    – number
 *   activeBudgetCategoryIds – Set<number>  already-budgeted category IDs to exclude
 *   onCreated   – (newBudget) => void  called after successful create
 */
export default function BudgetCreateModal({
  isOpen,
  onClose,
  walletId,
  activeBudgetCategoryIds = new Set(),
  onCreated,
}) {
  const t = useTranslations();

  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState({ categoryId: '', amount: '', warningThresholdPercent: '80' });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  // Load EXPENSE categories when modal opens
  useEffect(() => {
    if (!isOpen) return;
    categoryApi.getAllCategories('EXPENSE')
      .then((data) => setCategories(data || []))
      .catch(() => setCategories([]));
  }, [isOpen]);

  // Reset form on close
  useEffect(() => {
    if (!isOpen) {
      setForm({ categoryId: '', amount: '', warningThresholdPercent: '80' });
      setErrors({});
    }
  }, [isOpen]);

  // Keyboard close
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [isOpen, onClose]);

  const available = categories.filter((c) => !activeBudgetCategoryIds.has(c.id));

  const validate = () => {
    const errs = {};
    if (!form.categoryId) errs.categoryId = t('errors.categoryRequired');
    const amt = parseFloat(form.amount);
    if (!form.amount || isNaN(amt) || amt <= 0) errs.amount = t('budget.amountRequired');
    const thr = parseInt(form.warningThresholdPercent, 10);
    if (form.warningThresholdPercent !== '' && (isNaN(thr) || thr < 1 || thr > 100))
      errs.warningThresholdPercent = t('budget.thresholdRange');
    return errs;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }

    setSubmitting(true);
    try {
      const created = await categoryBudgetsApi.create({
        walletId,
        categoryId: parseInt(form.categoryId, 10),
        amount: parseFloat(form.amount),
        warningThresholdPercent: form.warningThresholdPercent
          ? parseInt(form.warningThresholdPercent, 10)
          : 80,
      });
      onCreated?.(created);
      onClose();
    } catch (err) {
      const msg = err.message?.includes('already exists')
        ? t('budget.duplicateBudget')
        : err.message || t('errors.generic');
      setErrors({ form: msg });
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/60 backdrop-blur-[2px] px-0 sm:px-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div
        className="bg-[#0e0e1c] border border-white/[0.09] rounded-t-[20px] sm:rounded-[20px]
                   w-full sm:max-w-sm flex flex-col overflow-hidden
                   shadow-[0_-4px_40px_rgba(0,0,0,0.6)] sm:shadow-[0_8px_40px_rgba(0,0,0,0.6)]"
        style={{ animation: 'fadeUp 0.25s cubic-bezier(0.4,0,0.2,1) both' }}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/[0.07] shrink-0">
          <div>
            <div className="text-[15px] font-bold tracking-[-0.2px] text-white">
              {t('budget.addBudget')}
            </div>
            <div className="text-[12px] text-white/35 mt-0.5">
              {t('budget.addBudgetSubtitle')}
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-7 h-7 flex items-center justify-center rounded-full bg-white/[0.06] hover:bg-white/[0.12] transition-colors text-white/50 hover:text-white"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Body */}
        <div className="px-5 py-5">
          {available.length === 0 ? (
            <p className="text-[13px] text-white/40 py-2">{t('budget.allCategoriesBudgeted')}</p>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-3">
              {/* Category */}
              <div>
                <select
                  value={form.categoryId}
                  onChange={(e) => {
                    setForm((p) => ({ ...p, categoryId: e.target.value }));
                    setErrors((p) => ({ ...p, categoryId: '' }));
                  }}
                  className={`${INPUT_SM} w-full border-white/[0.1] ${errors.categoryId ? 'border-rose-500/60' : ''}`}
                >
                  <option value="">{t('transaction.selectCategory')}</option>
                  {available.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {cat.emoji ? `${cat.emoji} ` : ''}{cat.name}
                    </option>
                  ))}
                </select>
                {errors.categoryId && (
                  <p className="text-rose-400 text-[11px] mt-1">{errors.categoryId}</p>
                )}
              </div>

              {/* Amount + threshold */}
              <div className="grid grid-cols-[1fr_auto] gap-2">
                <div>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    placeholder={t('budget.budgetAmount')}
                    value={form.amount}
                    onChange={(e) => {
                      setForm((p) => ({ ...p, amount: e.target.value }));
                      setErrors((p) => ({ ...p, amount: '' }));
                    }}
                    className={`${INPUT_SM} w-full border-white/[0.1] ${errors.amount ? 'border-rose-500/60' : ''}`}
                  />
                  {errors.amount && (
                    <p className="text-rose-400 text-[11px] mt-1">{errors.amount}</p>
                  )}
                </div>
                <div className="w-20">
                  <input
                    type="number"
                    min="1"
                    max="100"
                    placeholder="80%"
                    value={form.warningThresholdPercent}
                    onChange={(e) =>
                      setForm((p) => ({ ...p, warningThresholdPercent: e.target.value }))
                    }
                    className={`${INPUT_SM} w-full border-white/[0.1] text-center`}
                  />
                  <p className="text-white/25 text-[10px] text-center mt-0.5">{t('budget.warningAt')}</p>
                </div>
              </div>

              {errors.form && (
                <p className="text-rose-400 text-[12px]">{errors.form}</p>
              )}

              <button
                type="submit"
                disabled={submitting}
                className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl text-[13px] font-semibold
                           bg-gradient-to-r from-violet-600 to-purple-600 text-white
                           hover:from-violet-500 hover:to-purple-500 transition-all
                           disabled:opacity-40 disabled:cursor-not-allowed
                           shadow-[0_4px_20px_rgba(124,58,237,0.25)] mt-1"
              >
                {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                {t('budget.addBudget')}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
