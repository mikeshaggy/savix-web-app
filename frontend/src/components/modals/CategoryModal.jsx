import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { X, Save, Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';

export default function CategoryModal({ isOpen, onClose, onSave, category = null, loading = false }) {
  const t = useTranslations();
  const isEditing = !!category;
  
  const [formData, setFormData] = useState({
    name: '',
    type: 'EXPENSE',
    emoji: '',
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (category) {
      setFormData({
        name: category.name || '',
        type: category.type || 'EXPENSE',
        emoji: category.emoji || '',
      });
    } else {
      setFormData({
        name: '',
        type: 'EXPENSE',
        emoji: '',
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
    setFormData(prev => ({ ...prev, [field]: value }));
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
      });
      
      if (!category) {
        setFormData({
          name: '',
          type: 'EXPENSE',
          emoji: '',
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
      });
    }
    onClose();
  }, [category, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-[60] p-6">
      <div 
        className="bg-[#0e0e1c] border border-white/[0.12] rounded-3xl w-full max-w-[480px] overflow-hidden relative"
        style={{ 
          boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1), 0 0 80px rgba(124,58,237,0.06)',
          animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both'
        }}
      >
        {/* Top glow line */}
        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-gradient-to-r from-transparent via-purple-400 to-transparent opacity-60" />

        {/* Header */}
        <div className="flex items-center justify-between px-7 pt-6 pb-5 border-b border-white/[0.055]">
          <div className="flex items-center gap-2.5 text-xl font-bold tracking-[-0.3px]">
            {isEditing ? t('category.editCategory') : t('category.addCategory')}
          </div>
          <button
            onClick={handleClose}
            className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] hover:bg-[#1a1a2e] transition-all"
          >
            <X className="w-3 h-3" />
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit}>
          <div className="px-7 py-6 flex flex-col gap-[22px]">
              
            {/* Category Type */}
            <div>
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-[9px] flex items-center gap-1.5">
                {t('category.categoryType')} <span className="text-purple-300">*</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => handleChange('type', 'INCOME')}
                  className={`py-[13px] rounded-xl border text-base font-semibold text-center transition-all cursor-pointer ${
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
                  className={`py-[13px] rounded-xl border text-base font-semibold text-center transition-all cursor-pointer ${
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
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-[9px] flex items-center gap-1.5">
                {t('category.categoryName')} <span className="text-purple-300">*</span>
              </div>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                maxLength={50}
                className={`w-full bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white placeholder-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] ${
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
            <div>
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-[9px] flex items-center gap-1.5">
                {t('category.emoji')}
                <span className="text-white/25 font-normal tracking-normal normal-case text-[13px]">({t('common.optional')})</span>
              </div>
              <div className="flex items-center gap-2.5">
                <div className="w-11 h-11 shrink-0 bg-[#131325] border border-white/[0.055] rounded-xl flex items-center justify-center text-[22px] cursor-pointer hover:border-white/[0.12] transition-all select-none">
                  {formData.emoji?.trim() || '🏷️'}
                </div>
                <input
                  type="text"
                  value={formData.emoji}
                  onChange={(e) => handleChange('emoji', e.target.value)}
                  maxLength={16}
                  className={`flex-1 bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white placeholder-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] ${
                    errors.emoji ? 'border-red-500' : 'border-white/[0.055]'
                  }`}
                  placeholder={t('category.emojiPlaceholder')}
                />
              </div>
              {errors.emoji ? (
                <p className="text-red-400 text-xs mt-1.5">
                  {errors.emoji.startsWith('category.') ? t(errors.emoji) : errors.emoji}
                </p>
              ) : (
                <p className="text-[13px] text-white/25 mt-[7px] leading-relaxed">
                  {t('category.emojiHint')}
                </p>
              )}
            </div>

          </div>

          {/* Footer */}
          <div className="flex items-center justify-end gap-2.5 px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
            {errors.submit && (
              <p className="text-red-400 text-sm mr-auto">{errors.submit}</p>
            )}
            <button
              type="button"
              onClick={handleClose}
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
                boxShadow: '0 4px 20px rgba(124,58,237,0.3)'
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
        </form>
      </div>
    </div>
  );
}
