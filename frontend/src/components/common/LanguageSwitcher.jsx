'use client';

import React from 'react';
import { useLanguage } from '@/i18n';
import { useTranslations } from 'next-intl';

export default function LanguageSwitcher({ className = '' }) {
  const { lang, setLang, mounted } = useLanguage();
  const t = useTranslations('language');

  if (!mounted) {
    return (
      <div className={`flex bg-[#131325] border border-white/[0.055] rounded-[10px] overflow-hidden ${className}`}>
        <div className="py-[7px] px-[11px] w-9 h-8" />
        <div className="py-[7px] px-[11px] w-9 h-8" />
      </div>
    );
  }

  return (
    <div className={`flex bg-[#131325] border border-white/[0.055] rounded-[10px] overflow-hidden text-[11px] font-bold tracking-[0.05em] ${className}`}>
      <button
        onClick={() => setLang('en')}
        className={`py-[7px] px-[11px] cursor-pointer transition-all border-none ${
          lang === 'en'
            ? 'bg-[#7c3aed] text-white'
            : 'text-white/25 hover:text-white/50 bg-transparent'
        }`}
        aria-label="Switch to English"
        title={t('en')}
      >
        EN
      </button>
      <button
        onClick={() => setLang('pl')}
        className={`py-[7px] px-[11px] cursor-pointer transition-all border-none ${
          lang === 'pl'
            ? 'bg-[#7c3aed] text-white'
            : 'text-white/25 hover:text-white/50 bg-transparent'
        }`}
        aria-label="Switch to Polish"
        title={t('pl')}
      >
        PL
      </button>
    </div>
  );
}
