'use client';
import React, { useState, useMemo } from 'react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';
import BudgetProgressBar from '@/components/common/BudgetProgressBar';

// Deterministic palette — same category always gets the same color
const COLOR_PALETTE = [
  '#a78bfa', // violet-400
  '#4ade80', // green-400
  '#facc15', // yellow-400
  '#fb923c', // orange-400
  '#60a5fa', // blue-400
  '#f87171', // red-400
  '#94a3b8', // slate-400
  '#c084fc', // purple-400
  '#34d399', // emerald-400
  '#38bdf8', // sky-400
];

function getCategoryColor(categoryId) {
  return COLOR_PALETTE[(categoryId ?? 0) % COLOR_PALETTE.length];
}

const BUDGET_STATUS_LABEL_CLASS = {
  EXCEEDED: 'text-rose-400',
  WARNING:  'text-amber-400',
  OK:       'text-emerald-400/70',
};

function CategoryRow({ cat, rank, budgetMap }) {
  const t = useTranslations('analytics');
  const color = getCategoryColor(cat.categoryId);
  const barFill = Math.min(100, cat.share ?? 0);

  const budget = budgetMap?.[cat.categoryId] ?? null;
  const budgetPercent = budget ? Number(budget.usagePercent ?? 0) : 0;
  const budgetStatus  = budget?.status ?? null;

  return (
    <div className={`flex items-center gap-3 py-2.5 border-b border-white/[0.05] last:border-b-0 ${
      budgetStatus === 'EXCEEDED' ? 'bg-rose-500/[0.03]' : ''
    }`}>
      {/* Rank */}
      <span className="text-[12px] font-mono text-white/20 w-4 flex-shrink-0 text-right leading-none">
        {rank}
      </span>

      {/* Icon */}
      <div
        className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 text-[15px] leading-none"
        style={{ background: `${color}1f` }}
      >
        {cat.emoji}
      </div>

      {/* Body */}
      <div className="flex-1 min-w-0">
        {/* Top row: name + amount */}
        <div className="flex items-baseline justify-between gap-2 mb-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <span className="text-[14px] font-medium text-white/80 truncate">
              {cat.name}
            </span>
            {budgetStatus === 'EXCEEDED' && (
              <span className="text-[9px] font-bold text-rose-400 bg-rose-500/10 px-1.5 py-0.5 rounded-full shrink-0">
                {t('budgetExceeded')}
              </span>
            )}
            {budgetStatus === 'WARNING' && (
              <span className="text-[9px] font-bold text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded-full shrink-0">
                {t('budgetWarning')}
              </span>
            )}
          </div>
          <span className="text-[14px] font-semibold font-mono text-white/90 flex-shrink-0">
            {formatCurrency(cat.amount)}
          </span>
        </div>

        {/* Spending share bar */}
        <div className="flex items-center gap-2 mb-1">
          <span className="text-[11px] text-white/25 flex-shrink-0 whitespace-nowrap">
            {t('txnCount', { count: cat.transactionCount })}
          </span>
          <div className="flex-1 h-1 bg-white/[0.06] rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-500"
              style={{ width: `${barFill}%`, background: color, opacity: 0.65 }}
            />
          </div>
          <span className="text-[12px] font-mono text-white/40 flex-shrink-0">
            {cat.share.toFixed(1)}%
          </span>
        </div>

        {/* Budget overlay — only when budget exists */}
        {budget && (
          <div className="mt-0.5">
            <BudgetProgressBar percent={budgetPercent} status={budgetStatus} height="h-[2px]" />
            <div className="flex items-center justify-between mt-0.5">
              <span className="text-[9px] text-white/25 font-mono">
                {formatCurrency(Number(budget.spentAmount ?? 0))} / {formatCurrency(Number(budget.budgetAmount ?? 0))}
              </span>
              <span className={`text-[9px] font-semibold ${BUDGET_STATUS_LABEL_CLASS[budgetStatus] ?? 'text-white/30'}`}>
                {budgetPercent.toFixed(0)}%
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default function CategoryBreakdownChart({ data, loading, budgetMap }) {
  const t = useTranslations('analytics');
  const [sortMode, setSortMode] = useState('amount'); // 'amount' | 'frequency'

  const categories = useMemo(() => {
    const raw = data?.categories ?? [];
    if (sortMode === 'frequency') {
      return [...raw].sort((a, b) => (b.transactionCount ?? 0) - (a.transactionCount ?? 0));
    }
    // default: already sorted by amount DESC from the API
    return raw;
  }, [data, sortMode]);

  if (loading) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 animate-pulse h-[400px]" />
    );
  }

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl flex flex-col h-full md:h-[400px]">
      {/* Pinned header */}
      <div className="px-5 pt-5 pb-2 flex-shrink-0 flex items-center justify-between gap-3">
        <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35">
          {t('categoryBreakdown')}
        </div>

        {/* Sort toggle — only show when there's data */}
        {categories.length > 0 && (
          <div className="flex items-center gap-0.5 bg-white/[0.04] rounded-lg p-0.5 flex-shrink-0">
            <button
              onClick={() => setSortMode('amount')}
              className={`text-[10px] font-semibold px-2 py-1 rounded-md transition-all ${
                sortMode === 'amount'
                  ? 'bg-violet-500/25 text-violet-300'
                  : 'text-white/30 hover:text-white/60'
              }`}
            >
              {t('breakdownSortAmount')}
            </button>
            <button
              onClick={() => setSortMode('frequency')}
              className={`text-[10px] font-semibold px-2 py-1 rounded-md transition-all ${
                sortMode === 'frequency'
                  ? 'bg-violet-500/25 text-violet-300'
                  : 'text-white/30 hover:text-white/60'
              }`}
            >
              {t('breakdownSortFrequency')}
            </button>
          </div>
        )}
      </div>

      {categories.length === 0 ? (
        <div className="px-5 pb-5">
          <p className="text-sm text-white/40">{t('noCategoryData')}</p>
        </div>
      ) : (
        <div
          className="flex-1 overflow-y-auto px-5 pb-3
            [&::-webkit-scrollbar]:w-1
            [&::-webkit-scrollbar-track]:bg-transparent
            [&::-webkit-scrollbar-thumb]:rounded-full
            [&::-webkit-scrollbar-thumb]:bg-white/[0.15]
            [&::-webkit-scrollbar-thumb:hover]:bg-violet-500/50"
          style={{ scrollbarWidth: 'thin', scrollbarColor: 'rgba(139,92,246,0.3) transparent' }}
        >
          {categories.map((cat, index) => (
            <CategoryRow key={cat.categoryId} cat={cat} rank={index + 1} budgetMap={budgetMap} />
          ))}
        </div>
      )}
    </div>
  );
}
