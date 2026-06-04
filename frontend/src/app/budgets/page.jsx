'use client';
import React from 'react';
import { useTranslations } from 'next-intl';
import BudgetsPage from '@/components/pages/BudgetsPage';

// Budgets now lives under Planning (top-level), no longer inside the Analytics
// shell. The page owns its own header (the shell previously supplied it).
export default function BudgetsRoute() {
  const t = useTranslations('analytics');
  return (
    <div className="flex flex-col min-h-full">
      <div className="mb-8">
        <h1 className="text-[22px] font-bold text-white tracking-[-0.4px]">{t('budgetsTitle')}</h1>
        <p className="text-[14px] text-white/40 mt-1">{t('budgetsSubtitle')}</p>
      </div>
      <BudgetsPage />
    </div>
  );
}
