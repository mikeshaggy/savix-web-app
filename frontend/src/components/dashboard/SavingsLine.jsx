'use client';
import React from 'react';
import { useTranslations } from 'next-intl';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { savingsModel } from '@/lib/dashboardV2';

// Stage 6.6: savings wallet balance + this cycle's net flow with the salary wallet (> 0 = moved from savings).
export default function SavingsLine({ savings }) {
  const t = useTranslations('dashboard');
  const formatCurrency = useFormatCurrency();
  const model = savingsModel(savings);
  if (!model) return null;

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-[14px] px-5 py-4 text-[13px] text-white/70 tabular-nums">
      <div>
        {t('savingsBalance', { name: model.name, balance: model.balance != null ? formatCurrency(model.balance) : '—' })}
      </div>
      <div className="text-[12px] text-white/40 mt-1">
        {t('savingsNetFlow', { direction: model.direction, amount: formatCurrency(model.amount) })}
      </div>
    </div>
  );
}
