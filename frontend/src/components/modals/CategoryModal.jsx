import React, { useState, useEffect, useCallback } from 'react';
import { X, Save, Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import Button from '@/components/common/Button';
import EmojiPickerField from '@/components/common/EmojiPickerField';
import { INPUT_SM } from '@/components/common/formControls';

export default function CategoryModal({ isOpen, onClose, onSave, category = null, loading = false }) {
  const t = useTranslations();
  const isEditing = !!category;
  
  const [formData, setFormData] = useState({
    name: '',
    type: 'EXPENSE',
    emoji: '',
    isCycleAnchor: false,
    excludedFromTopCategories: false,
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (category) {
      setFormData({
        name: category.name || '',
        type: category.type || 'EXPENSE',
        emoji: category.emoji || '',
        isCycleAnchor: category.isCycleAnchor || false,
        excludedFromTopCategories: category.excludedFromTopCategories || false,
      });
    } else {
      setFormData({
        name: '',
        type: 'EXPENSE',
        emoji: '',
        isCycleAnchor: false,
        excludedFromTopCategories: false,
      });
    }
    setErrors({});
  }, [category, isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        handleClose();
      }
    };
    
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen]);

  const handleChange = useCallback((field, value) => {
    setFormData(prev => {
      const next = { ...prev, [field]: value };
      if (field === 'type' && value === 'EXPENSE') {
        next.isCycleAnchor = false;
      }
      return next;
    });
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: '' }));
    }
  }, [errors]);

  const validateForm = useCallback(() => {
    const newErrors = {};

    if (!formData.name.trim()) {
      newErrors.name = 'category.nameRequired';
    } else if (formData.name.trim().length < 2) {
      newErrors.name = 'category.nameTooShort';
    } else if (formData.name.trim().length > 50) {
      newErrors.name = 'category.nameTooLong';
    }

    if (!formData.type) {
      newErrors.type = 'category.typeRequired';
    }

    if (formData.emoji && formData.emoji.trim().length > 16) {
      newErrors.emoji = 'category.emojiTooLong';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validateForm()) {
      return;
    }

    try {
      setSubmitting(true);
      
      const trimmedEmoji = formData.emoji?.trim();
      
      await onSave({
        name: formData.name.trim(),
        type: formData.type,
        emoji: trimmedEmoji || null,
        isCycleAnchor: formData.type === 'INCOME' ? formData.isCycleAnchor : false,
        excludedFromTopCategories: formData.excludedFromTopCategories,
      });
      
      if (!category) {
        setFormData({
          name: '',
          type: 'EXPENSE',
          emoji: '',
          isCycleAnchor: false,
          excludedFromTopCategories: false,
        });
      }
      
      setErrors({});
      onClose();
    } catch (error) {
      console.error('Failed to save category:', error);
      if (error.details) {
        const backendErrors = {};
        Object.entries(error.details).forEach(([field, message]) => {
          backendErrors[field] = message;
        });
        setErrors(prev => ({ ...prev, ...backendErrors }));
      } else if (error.status === 409 || (error.message?.toLowerCase().includes('emoji') && error.message?.toLowerCase().includes('already'))) {
        setErrors({ emoji: t('category.emojiAlreadyUsed') });
      } else {
        setErrors({ submit: error.message || t('category.saveFailed') });
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleClose = useCallback(() => {
    setErrors({});
    if (!category) {
      setFormData({
        name: '',
        type: 'EXPENSE',
        emoji: '',
        isCycleAnchor: false,
        excludedFromTopCategories: false,
      });
    }
    onClose();
  }, [category, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-[60] p-3 sm:p-6">
      <div 
        className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl w-full max-w-[500px] max-h-[calc(100vh-24px)] overflow-hidden relative flex flex-col"
        style={{ 
          boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1), 0 0 80px rgba(124,58,237,0.06)',
          animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both'
        }}
      >
        {/* Top accent line */}
        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />

        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-6 pt-5 pb-4 border-b border-white/[0.055]">
          <div className="flex items-center gap-2.5 text-lg font-bold tracking-[-0.3px]">
            {isEditing ? t('category.editCategory') : t('category.addCategory')}
          </div>
          <button
            onClick={handleClose}
            className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] hover:bg-[#1a1a2e] transition-all"
            aria-label={t('common.close')}
          >
            <X className="w-3 h-3" />
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit} className="min-h-0 flex flex-col">
          <div className="px-4 sm:px-6 py-5 flex flex-col gap-5 overflow-y-auto">
              
            {/* Category Type */}
            <div>
              <div className="text-[12px] font-bold tracking-[0.12em] uppercase text-white/30 mb-2 flex items-center gap-1.5">
                {t('category.categoryType')} <span className="text-purple-300">*</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => handleChange('type', 'INCOME')}
                  className={`py-2.5 rounded-[10px] border text-[14px] font-semibold text-center transition-all cursor-pointer ${
                    formData.type === 'INCOME'
                      ? 'bg-green-400/10 border-green-400/35 text-green-400 shadow-[0_0_20px_rgba(74,222,128,0.08)]'
                      : 'bg-[#131325] border-white/[0.055] text-white/25 hover:border-white/[0.12] hover:text-white'
                  }`}
                >
                  {t('categoryType.income')}
                </button>
                <button
                  type="button"
                  onClick={() => handleChange('type', 'EXPENSE')}
                  className={`py-2.5 rounded-[10px] border text-[14px] font-semibold text-center transition-all cursor-pointer ${
                    formData.type === 'EXPENSE'
                      ? 'bg-red-400/10 border-red-400/35 text-red-400 shadow-[0_0_20px_rgba(248,113,113,0.08)]'
                      : 'bg-[#131325] border-white/[0.055] text-white/25 hover:border-white/[0.12] hover:text-white'
                  }`}
                >
                  {t('categoryType.expense')}
                </button>
              </div>
              {errors.type && (
                <p className="text-red-400 text-xs mt-1.5">{t(errors.type)}</p>
              )}
            </div>

            {/* Category Name */}
            <div>
              <div className="text-[12px] font-bold tracking-[0.12em] uppercase text-white/30 mb-2 flex items-center gap-1.5">
                {t('category.categoryName')} <span className="text-purple-300">*</span>
              </div>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                maxLength={50}
                className={`${INPUT_SM} w-full ${
                  errors.name ? 'border-red-500' : 'border-white/[0.055]'
                }`}
                placeholder={t('category.categoryNamePlaceholder')}
                autoFocus
              />
              {errors.name && (
                <p className="text-red-400 text-xs mt-1.5">{t(errors.name)}</p>
              )}
            </div>

            {/* Category Emoji */}
            <EmojiPickerField
              label={t('category.emoji')}
              optionalLabel={t('common.optional')}
              value={formData.emoji}
              onChange={(v) => handleChange('emoji', v)}
              placeholder={t('category.emojiPlaceholder')}
              hint={t('category.emojiHint')}
              error={errors.emoji ? (errors.emoji.startsWith('category.') ? t(errors.emoji) : errors.emoji) : ''}
              maxLength={16}
              inputClassName={INPUT_SM}
            />

            <div className="flex flex-col gap-2.5">
              <SettingToggle
                label={t('category.cycleAnchor')}
                description={formData.type === 'INCOME' ? t('category.cycleAnchorHint') : t('category.cycleAnchorIncomeOnly')}
                checked={formData.isCycleAnchor}
                disabled={formData.type !== 'INCOME'}
                onChange={(checked) => handleChange('isCycleAnchor', checked)}
              />

              <SettingToggle
                label={t('category.hideFromTopCategories')}
                description={t('category.hideFromTopCategoriesHint')}
                checked={formData.excludedFromTopCategories}
                onChange={(checked) => handleChange('excludedFromTopCategories', checked)}
              />
            </div>

          </div>

          {/* Footer */}
          <div className="flex items-center justify-end gap-2.5 px-4 sm:px-6 py-4 border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
            {errors.submit && (
              <p className="text-red-400 text-sm mr-auto">{errors.submit}</p>
            )}
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={handleClose}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              disabled={submitting}
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
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

function SettingToggle({ label, description, checked, disabled = false, onChange }) {
  return (
    <div className={`rounded-[12px] border px-3.5 py-3 flex items-start gap-3 transition-all ${
      disabled
        ? 'bg-[#101020] border-white/[0.045] opacity-70'
        : checked
          ? 'bg-purple-400/10 border-purple-400/30'
          : 'bg-[#131325] border-white/[0.055] hover:border-white/[0.12]'
    }`}>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={`mt-0.5 relative h-6 w-11 shrink-0 rounded-full border transition-all disabled:cursor-not-allowed ${
          checked
            ? 'bg-purple-500 border-purple-400'
            : 'bg-white/[0.06] border-white/[0.12]'
        }`}
      >
        <span className={`absolute top-1/2 h-[18px] w-[18px] -translate-y-1/2 rounded-full bg-white shadow-sm transition-transform ${
          checked ? 'translate-x-[19px]' : 'translate-x-[3px]'
        }`} />
      </button>
      <div className="min-w-0">
        <div className={`text-[14px] font-semibold ${disabled ? 'text-white/35' : 'text-white/80'}`}>
          {label}
        </div>
        <p className={`mt-1 text-[12.5px] leading-relaxed ${disabled ? 'text-white/25' : 'text-white/35'}`}>
          {description}
        </p>
      </div>
    </div>
  );
}
