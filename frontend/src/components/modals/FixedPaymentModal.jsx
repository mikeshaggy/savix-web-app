import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X, Save, Loader2, Trash2 } from 'lucide-react';
import { useCategories } from '@/hooks/useApi';
import { useWallets } from '@/contexts/WalletContext';
import { useTranslations } from 'next-intl';

const CYCLE_OPTIONS = [
  { value: 'WEEKLY', labelKey: 'fixedPayments.weekly' },
  { value: 'MONTHLY', labelKey: 'fixedPayments.monthly' },
  { value: 'QUARTERLY', labelKey: 'fixedPayments.quarterly' },
  { value: 'YEARLY', labelKey: 'fixedPayments.yearly' },
];

export default function FixedPaymentModal({
  isOpen,
  onClose,
  onSave,
  fixedPayment = null,
  onDeactivateRequest = null,
}) {
  const { currentWallet } = useWallets();
  const { categories } = useCategories();
  const t = useTranslations();

  const isEditing = !!fixedPayment;

  const [formData, setFormData] = useState({
    title: '',
    amount: '',
    categoryId: '',
    cycle: 'MONTHLY',
    anchorDate: new Date().toISOString().split('T')[0],
    activeFrom: new Date().toISOString().split('T')[0],
    activeTo: '',
    notes: '',
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const [categorySearch, setCategorySearch] = useState('');
  const [showCategoryDropdown, setShowCategoryDropdown] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const categoryInputRef = useRef(null);
  const dropdownRef = useRef(null);
  const dropdownPortalRef = useRef(null);
  const categoryOptionRefs = useRef([]);
  const [dropdownPosition, setDropdownPosition] = useState({
    top: 0, left: 0, width: 0, maxHeight: 280, openUp: false,
  });

  const expenseCategories = useMemo(() => {
    return (categories || []).filter(c => c.type === 'EXPENSE');
  }, [categories]);

  useEffect(() => {
    if (fixedPayment) {
      setFormData({
        title: fixedPayment.title || '',
        amount: fixedPayment.amount?.toString() || '',
        categoryId: fixedPayment.categoryId?.toString() || '',
        cycle: fixedPayment.cycle || 'MONTHLY',
        anchorDate: fixedPayment.anchorDate || '',
        activeFrom: fixedPayment.activeFrom || '',
        activeTo: fixedPayment.activeTo || '',
        notes: fixedPayment.notes || '',
      });
      const cat = expenseCategories.find(c => c.id === fixedPayment.categoryId);
      setCategorySearch(cat?.name || fixedPayment.categoryName || '');
    } else {
      setFormData({
        title: '',
        amount: '',
        categoryId: '',
        cycle: 'MONTHLY',
        anchorDate: new Date().toISOString().split('T')[0],
        activeFrom: new Date().toISOString().split('T')[0],
        activeTo: '',
        notes: '',
      });
      setCategorySearch('');
    }
    setErrors({});
  }, [fixedPayment, isOpen, expenseCategories]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (showCategoryDropdown) {
          setShowCategoryDropdown(false);
          setActiveIndex(-1);
          return;
        }
        onClose();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose, showCategoryDropdown]);

  useEffect(() => {
    const handleClickOutside = (e) => {
      const inDropdownArea = dropdownRef.current && dropdownRef.current.contains(e.target);
      const inPortal = dropdownPortalRef.current && dropdownPortalRef.current.contains(e.target);
      if (!inDropdownArea && !inPortal) {
        setShowCategoryDropdown(false);
        setActiveIndex(-1);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (showCategoryDropdown && categoryInputRef.current) {
      const rect = categoryInputRef.current.getBoundingClientRect();
      const viewportHeight = window.innerHeight;
      const spaceBelow = viewportHeight - rect.bottom - 16;
      const spaceAbove = rect.top - 16;
      const preferredHeight = 280;
      const headerBuffer = 44;
      const openUp = spaceBelow < 160 && spaceAbove > spaceBelow;
      const rawMaxHeight = openUp
        ? Math.min(spaceAbove, preferredHeight)
        : Math.min(spaceBelow, preferredHeight);
      const maxHeight = Math.max(rawMaxHeight - headerBuffer, 100);

      setDropdownPosition({
        left: rect.left,
        width: rect.width,
        maxHeight,
        openUp,
        top: openUp ? rect.top : rect.bottom + 8,
        bottom: openUp ? viewportHeight - rect.top + 8 : undefined,
      });
    }
  }, [showCategoryDropdown]);

  const selectedCategory = useMemo(() => {
    return expenseCategories.find(c => c.id === parseInt(formData.categoryId));
  }, [expenseCategories, formData.categoryId]);

  const filteredCategories = useMemo(() => {
    if (!categorySearch.trim()) return expenseCategories.slice(0, 30);
    const query = categorySearch.toLowerCase().trim();
    return expenseCategories
      .filter(cat => cat.name.toLowerCase().includes(query) || cat.emoji?.includes(query))
      .slice(0, 30);
  }, [expenseCategories, categorySearch]);

  useEffect(() => {
    if (!showCategoryDropdown) {
      setActiveIndex(-1);
      categoryOptionRefs.current = [];
      return;
    }

    if (!filteredCategories.length) {
      setActiveIndex(-1);
      return;
    }

    if (activeIndex >= filteredCategories.length) {
      setActiveIndex(filteredCategories.length - 1);
    }
  }, [showCategoryDropdown, filteredCategories.length, activeIndex]);

  useEffect(() => {
    if (!showCategoryDropdown || activeIndex < 0) return;
    categoryOptionRefs.current[activeIndex]?.scrollIntoView({ block: 'nearest' });
  }, [showCategoryDropdown, activeIndex]);

  const handleChange = useCallback((field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: '' }));
    }
  }, [errors]);

  const handleCategorySelect = useCallback((category) => {
    setFormData(prev => ({ ...prev, categoryId: category.id.toString() }));
    setCategorySearch(category.name);
    setShowCategoryDropdown(false);
    setActiveIndex(-1);
    if (errors.categoryId) {
      setErrors(prev => ({ ...prev, categoryId: '' }));
    }
  }, [errors]);

  const handleCategoryInputKeyDown = useCallback((e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!showCategoryDropdown) {
        setShowCategoryDropdown(true);
        setActiveIndex(filteredCategories.length ? 0 : -1);
        return;
      }
      if (!filteredCategories.length) return;
      setActiveIndex((prev) => {
        if (prev < 0) return 0;
        return (prev + 1) % filteredCategories.length;
      });
      return;
    }

    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (!showCategoryDropdown) {
        setShowCategoryDropdown(true);
        setActiveIndex(filteredCategories.length ? 0 : -1);
        return;
      }
      if (!filteredCategories.length) return;
      setActiveIndex((prev) => {
        if (prev <= 0) return filteredCategories.length - 1;
        return prev - 1;
      });
      return;
    }

    if (e.key === 'Enter' && showCategoryDropdown) {
      if (!filteredCategories.length) return;
      e.preventDefault();
      const idx = activeIndex >= 0 ? activeIndex : 0;
      handleCategorySelect(filteredCategories[idx]);
      return;
    }

    if (e.key === 'Escape' && showCategoryDropdown) {
      e.preventDefault();
      e.stopPropagation();
      setShowCategoryDropdown(false);
      setActiveIndex(-1);
    }
  }, [activeIndex, filteredCategories, handleCategorySelect, showCategoryDropdown]);

  const validateForm = useCallback(() => {
    const newErrors = {};

    if (!formData.title.trim()) {
      newErrors.title = 'errors.titleRequired';
    } else if (formData.title.trim().length > 50) {
      newErrors.title = 'errors.titleTooLong';
    }

    const amount = parseFloat(formData.amount);
    if (!formData.amount || isNaN(amount) || amount < 0.01) {
      newErrors.amount = 'errors.amountRequired';
    }

    if (!isEditing && !currentWallet?.id) {
      newErrors.walletId = 'errors.walletRequired';
    }

    if (!isEditing && !formData.categoryId) {
      newErrors.categoryId = 'errors.categoryRequired';
    }

    if (!formData.cycle) {
      newErrors.cycle = 'fixedPayments.cycleRequired';
    }

    if (!formData.anchorDate) {
      newErrors.anchorDate = 'errors.dateRequired';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData, isEditing, currentWallet]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateForm()) return;

    try {
      setSubmitting(true);

      if (isEditing) {
        await onSave({
          title: formData.title.trim(),
          amount: parseFloat(formData.amount),
          anchorDate: formData.anchorDate,
          cycle: formData.cycle,
          activeTo: formData.activeTo || null,
          notes: formData.notes?.trim() || null,
        });
      } else {
        await onSave({
          walletId: currentWallet.id,
          categoryId: parseInt(formData.categoryId),
          title: formData.title.trim(),
          amount: parseFloat(formData.amount),
          anchorDate: formData.anchorDate,
          cycle: formData.cycle,
          activeFrom: formData.activeFrom || null,
          activeTo: formData.activeTo || null,
          notes: formData.notes?.trim() || null,
        });
      }

      setErrors({});
      onClose();
    } catch (error) {
      console.error('Failed to save fixed payment:', error);
      if (error.details) {
        const backendErrors = {};
        Object.entries(error.details).forEach(([field, message]) => {
          backendErrors[field] = message;
        });
        setErrors(prev => ({ ...prev, ...backendErrors }));
      } else {
        setErrors({ submit: error.message || t('errors.generic') });
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-3 sm:p-6">
      <div
        className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[980px] max-h-[90vh] overflow-hidden relative"
        style={{
          boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1), 0 0 80px rgba(124,58,237,0.06)',
          animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both',
        }}
      >
        {/* Top accent line */}
        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />

        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
          <div className="flex items-center gap-2.5 text-lg sm:text-xl font-bold tracking-[-0.3px]">
            {isEditing ? t('fixedPayments.editPayment') : t('fixedPayments.addPayment')}
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] hover:bg-[#1a1a2e] transition-all"
          >
            <X className="w-3 h-3" />
          </button>
        </div>

        {/* Body - Two Column Layout */}
        <form onSubmit={handleSubmit}>
          <div className="grid grid-cols-1 lg:grid-cols-2 max-h-[calc(90vh-150px)] overflow-y-auto">
            {/* LEFT COLUMN */}
            <div className="px-4 sm:px-7 py-5 sm:py-6 flex flex-col gap-5">

            {/* Title */}
            <div>
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                {t('transaction.title')} <span className="text-purple-300">*</span>
              </div>
              <input
                type="text"
                value={formData.title}
                onChange={(e) => handleChange('title', e.target.value)}
                maxLength={50}
                className={`w-full bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white placeholder-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] ${
                  errors.title ? 'border-red-500' : 'border-white/[0.055]'
                }`}
                placeholder={t('fixedPayments.titlePlaceholder')}
                autoFocus
              />
              {errors.title && <p className="text-red-400 text-xs mt-1.5">{t(errors.title)}</p>}
            </div>

            {/* Amount */}
            <div>
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                {t('transaction.amount')} <span className="text-purple-300">*</span>
              </div>
              <div className="relative">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 font-mono text-base font-medium text-white/25">
                  PLN
                </span>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={formData.amount}
                  onChange={(e) => handleChange('amount', e.target.value)}
                  className={`w-full bg-[#131325] border rounded-[11px] pl-14 pr-3.5 py-3 font-mono text-xl font-medium tracking-[-0.5px] text-white placeholder-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] ${
                    errors.amount ? 'border-red-500' : 'border-white/[0.055]'
                  }`}
                  placeholder="0.00"
                />
              </div>
              {errors.amount && <p className="text-red-400 text-xs mt-1.5">{t(errors.amount)}</p>}
            </div>

            {/* Category */}
            {isEditing ? (
              <div>
                <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2">
                  {t('transaction.category')}
                </div>
                <div className="flex items-center gap-2 bg-[#131325] border border-white/[0.055] rounded-[11px] px-3.5 py-3 text-base text-white/50">
                  <span>{fixedPayment.categoryEmoji || '🏷️'}</span>
                  <span>{fixedPayment.categoryName}</span>
                </div>
              </div>
            ) : (
              <div ref={dropdownRef}>
                <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                  {t('transaction.category')} <span className="text-purple-300">*</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-10 h-10 shrink-0 rounded-[11px] flex items-center justify-center text-lg border border-white/[0.055] bg-white/[0.02] text-white/80">
                    {selectedCategory?.emoji || '🏷️'}
                  </div>
                  <input
                    ref={categoryInputRef}
                    type="text"
                    value={categorySearch}
                    onChange={(e) => {
                      setCategorySearch(e.target.value);
                      setShowCategoryDropdown(true);
                      setActiveIndex(0);
                      if (formData.categoryId && e.target.value !== selectedCategory?.name) {
                        handleChange('categoryId', '');
                      }
                    }}
                    onFocus={() => {
                      setShowCategoryDropdown(true);
                      setActiveIndex(filteredCategories.length ? 0 : -1);
                    }}
                    onKeyDown={handleCategoryInputKeyDown}
                    className={`flex-1 bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white placeholder-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] ${
                      errors.categoryId ? 'border-red-500' : 'border-white/[0.055]'
                    }`}
                    placeholder={t('transaction.searchCategory')}
                    autoComplete="off"
                  />
                </div>

                {/* Category dropdown portal */}
                {showCategoryDropdown && typeof document !== 'undefined' && createPortal(
                  <div
                    ref={dropdownPortalRef}
                    className="bg-[rgba(18,22,38,0.98)] border border-white/[0.06] rounded-2xl shadow-2xl overflow-hidden backdrop-blur-xl"
                    style={{
                      position: 'fixed',
                      left: dropdownPosition.left,
                      width: dropdownPosition.width,
                      zIndex: 9999,
                      ...(dropdownPosition.openUp
                        ? { bottom: dropdownPosition.bottom }
                        : { top: dropdownPosition.top }),
                    }}
                  >
                    <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/[0.04] text-gray-500 text-xs">
                      <span className="text-gray-400">{filteredCategories.length} {t('transaction.items')}</span>
                    </div>
                    <div className="overflow-y-auto pb-2" style={{ maxHeight: dropdownPosition.maxHeight }}>
                      {filteredCategories.length === 0 ? (
                        <div className="px-4 py-4 text-gray-400 text-sm">{t('transaction.noMatches')}</div>
                      ) : (
                        filteredCategories.map((cat, idx) => (
                          <div
                            key={cat.id}
                            ref={(node) => {
                              categoryOptionRefs.current[idx] = node;
                            }}
                            onMouseDown={(e) => {
                              e.preventDefault();
                              handleCategorySelect(cat);
                            }}
                            onMouseEnter={() => setActiveIndex(idx)}
                            className={`flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors border-l-2 ${
                              idx === activeIndex
                                ? 'bg-violet-500/10 border-l-violet-400'
                                : 'hover:bg-white/[0.03] border-l-transparent'
                            }`}
                          >
                            <div className="w-8 h-8 rounded-lg bg-white/[0.02] border border-white/[0.04] flex items-center justify-center text-base shrink-0">
                              {cat.emoji || '🏷️'}
                            </div>
                            <div className="font-semibold text-sm text-white/90">{cat.name}</div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>,
                  document.body,
                )}

                {errors.categoryId && <p className="text-red-400 text-xs mt-1.5">{t(errors.categoryId)}</p>}
              </div>
            )}

            {/* Anchor Date */}
            <div>
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                {t('fixedPayments.anchorDate')} <span className="text-purple-300">*</span>
              </div>
              <input
                type="date"
                value={formData.anchorDate}
                onChange={(e) => handleChange('anchorDate', e.target.value)}
                className={`w-full bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] [color-scheme:dark] ${
                  errors.anchorDate ? 'border-red-500' : 'border-white/[0.055]'
                }`}
              />
              <p className="text-[12px] text-white/25 mt-1.5">{t('fixedPayments.anchorDateHint')}</p>
              {errors.anchorDate && <p className="text-red-400 text-xs mt-1.5">{t(errors.anchorDate)}</p>}
            </div>

            </div>

            {/* RIGHT COLUMN */}
            <div className="px-4 sm:px-7 py-5 sm:py-6 border-t lg:border-t-0 lg:border-l border-white/[0.055] flex flex-col gap-5">

            {/* Cycle */}
            <div>
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                {t('fixedPayments.cycle')} <span className="text-purple-300">*</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                {CYCLE_OPTIONS.map(option => (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => handleChange('cycle', option.value)}
                    className={`py-3 rounded-[11px] border text-[13px] font-medium transition-all cursor-pointer ${
                      formData.cycle === option.value
                        ? 'border-purple-500/40 bg-purple-500/15 text-purple-300'
                        : 'border-white/[0.055] bg-[#131325] text-white/25 hover:border-white/[0.12] hover:text-white'
                    }`}
                  >
                    {t(option.labelKey)}
                  </button>
                ))}
              </div>
              {errors.cycle && <p className="text-red-400 text-xs mt-1.5">{t(errors.cycle)}</p>}
            </div>

            {/* Active From */}
            {!isEditing && (
              <div>
                <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2">
                  {t('fixedPayments.activeFrom')} <span className="text-purple-300">*</span>
                </div>
                <input
                  type="date"
                  value={formData.activeFrom}
                  onChange={(e) => handleChange('activeFrom', e.target.value)}
                  className="w-full bg-[#131325] border border-white/[0.055] rounded-[11px] px-3.5 py-3 text-base text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] [color-scheme:dark]"
                />
              </div>
            )}

            {/* Active To */}
            <div>
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2">
                {t('fixedPayments.activeTo')}
                <span className="text-white/25 font-normal tracking-normal normal-case text-[13px] ml-1.5">({t('common.optional')})</span>
              </div>
              <input
                type="date"
                value={formData.activeTo}
                onChange={(e) => handleChange('activeTo', e.target.value)}
                className="w-full bg-[#131325] border border-white/[0.055] rounded-[11px] px-3.5 py-3 text-base text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] [color-scheme:dark]"
              />
              <p className="text-[12px] text-white/25 mt-1.5">{t('fixedPayments.activeToHint')}</p>
            </div>

            {/* Notes */}
            <div>
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2">
                {t('transaction.notes')}
              </div>
              <textarea
                value={formData.notes}
                onChange={(e) => handleChange('notes', e.target.value)}
                rows={3}
                className="w-full bg-[#131325] border border-white/[0.055] rounded-[11px] px-3.5 py-3 text-[15px] text-white placeholder-white/25 outline-none transition-all resize-y min-h-[70px] max-h-[120px] leading-relaxed focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]"
                placeholder={t('fixedPayments.notesPlaceholder')}
              />
            </div>

            </div>
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
            <div>
              {isEditing && onDeactivateRequest && (
                <button
                  type="button"
                  onClick={() => onDeactivateRequest(fixedPayment)}
                  disabled={submitting}
                  className="inline-flex items-center gap-2 px-4 py-3 bg-[rgba(244,63,94,0.08)] border border-[rgba(244,63,94,0.35)] rounded-xl text-base font-semibold text-[#fda4af] cursor-pointer transition-all hover:bg-[rgba(244,63,94,0.14)] hover:border-[rgba(244,63,94,0.45)] disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <Trash2 className="w-4 h-4" />
                  {t('fixedPayments.deactivate')}
                </button>
              )}
            </div>
            {errors.submit && (
              <p className="text-red-400 text-sm mr-auto">{errors.submit}</p>
            )}
            <div className="flex items-center gap-2.5 ml-auto">
              <button
                type="button"
                onClick={onClose}
                className="px-[22px] py-3 bg-[#131325] border border-white/[0.055] rounded-xl text-base font-semibold text-white/50 cursor-pointer hover:border-white/[0.12] hover:text-white transition-all"
              >
                {t('common.cancel')}
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="flex items-center gap-2 px-6 py-3 rounded-xl border-none text-base font-bold text-white cursor-pointer transition-all disabled:opacity-50 disabled:cursor-not-allowed hover:-translate-y-px"
                style={{
                  background: 'linear-gradient(135deg, #7c3aed, #a855f7)',
                  boxShadow: '0 4px 20px rgba(124,58,237,0.3)',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.boxShadow = '0 8px 32px rgba(124,58,237,0.3)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.boxShadow = '0 4px 20px rgba(124,58,237,0.3)'; }}
              >
                {submitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    {t('common.loading')}
                  </>
                ) : (
                  <>
                    <div className="w-[18px] h-[18px] bg-white/20 rounded-[6px] flex items-center justify-center">
                      <Save className="w-2.5 h-2.5" />
                    </div>
                    {t('common.save')}
                  </>
                )}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
