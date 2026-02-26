'use client';
import React from 'react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';

// PLACEHOLDER DATA
const mockFixedTransactions = {
  totalFixed: 2430,
  percentageOfExpenses: 26,
  remaining: 1998,
  upcomingCount: 3,
  upcoming: [
    { name: 'Rent', dueDate: '2025-11-20', category: 'housing', amount: 1750, emoji: '🏠' },
    { name: 'Internet', dueDate: '2025-11-24', category: 'utilities', amount: 79, emoji: '📡' },
    { name: 'Subscriptions', dueDate: '2025-11-27', category: 'subscriptions', amount: 69, emoji: '📱' }
  ]
};

export default function FixedTransactionsTile() {
  const t = useTranslations();
  
  return (
    <div className="bg-[#0e0e1c] border border-white/[0.055] rounded-[18px] overflow-hidden relative transition-colors hover:border-white/[0.12] w-full flex flex-col"
         style={{ animation: 'fadeUp 0.5s ease both', animationDelay: '0.14s' }}>
      {/* Header */}
      <div className="px-[22px] pt-5 pb-0 flex items-start gap-3 mb-4">
        <div>
          <div className="text-[13px] font-bold tracking-[-0.2px]">{t('dashboard.fixedTransactions')} {t('dashboard.placeholder')}</div>
          <div className="text-[11px] text-white/25 mt-0.5">{t('dashboard.fixedTransactionsDesc')}</div>
        </div>
        <span className="ml-auto text-[11px] font-semibold text-purple-300 cursor-pointer mt-0.5 hover:text-fuchsia-400 transition-colors whitespace-nowrap">
          {t('dashboard.viewAll')}
        </span>
      </div>

      {/* Meta segments */}
      <div className="flex border-t border-b border-white/[0.055]">
        <div className="flex-1 py-3.5 px-[22px]">
          <div className="text-[10px] text-white/25 uppercase tracking-[0.1em] font-semibold mb-1">{t('dashboard.totalFixed')}</div>
          <div className="font-mono text-lg font-medium tracking-[-0.5px] text-red-400">
            {formatCurrency(mockFixedTransactions.totalFixed)}
          </div>
          <div className="text-[10px] text-white/25 mt-[3px]">{t('dashboard.ofExpensesPeriod', { percentage: mockFixedTransactions.percentageOfExpenses })}</div>
          <div className="h-[3px] bg-white/[0.06] rounded-full mt-2 overflow-hidden">
            <div className="h-full rounded-full"
                 style={{ 
                   width: `${mockFixedTransactions.percentageOfExpenses}%`,
                   background: 'linear-gradient(90deg, #7c3aed, #a855f7)'
                 }} />
          </div>
        </div>
        <div className="flex-1 py-3.5 px-[22px] border-l border-white/[0.055]">
          <div className="text-[10px] text-white/25 uppercase tracking-[0.1em] font-semibold mb-1">{t('dashboard.remainingFixed')}</div>
          <div className="font-mono text-lg font-medium tracking-[-0.5px] text-purple-300">
            {formatCurrency(mockFixedTransactions.remaining)}
          </div>
          <div className="text-[10px] text-white/25 mt-[3px]">{t('dashboard.upcomingTransactions', { count: mockFixedTransactions.upcomingCount })}</div>
          <div className="h-[3px] bg-white/[0.06] rounded-full mt-2 overflow-hidden">
            <div className="h-full rounded-full"
                 style={{ 
                   width: '60%',
                   background: 'linear-gradient(90deg, #c084fc, #e879f9)'
                 }} />
          </div>
        </div>
      </div>

      {/* Transaction list */}
      <div className="py-1 pb-2 flex-1">
        {mockFixedTransactions.upcoming.map((tx, index) => (
          <div key={index}
               className="flex items-center gap-3 px-[22px] py-2.5 cursor-pointer transition-colors hover:bg-white/[0.025]">
            <div className="w-[34px] h-[34px] rounded-[10px] flex items-center justify-center text-[15px] shrink-0"
                 style={{ background: 'rgba(124,58,237,0.12)' }}>
              {tx.emoji}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-[13px] font-medium">{tx.name}</div>
              <div className="text-[11px] text-white/25 mt-0.5">{t('dashboard.due', { date: tx.dueDate, category: tx.category })}</div>
            </div>
            <span className="text-[10px] font-semibold tracking-[0.06em] px-2 py-[3px] rounded-full bg-purple-500/[0.12] border border-purple-500/20 text-purple-300">
              {t('dashboard.fixed')}
            </span>
            <span className="font-mono text-[13px] font-medium tracking-[-0.3px] text-red-400 shrink-0">
              −{formatCurrency(tx.amount)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
