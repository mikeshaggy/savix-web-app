'use client';
import React from 'react';
import { TrendingUp, TrendingDown, Minus, Settings2 } from 'lucide-react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';
import BudgetProgressBar from '@/components/common/BudgetProgressBar';

const BAR_COLORS = ['#f43f5e', '#8b5cf6', '#f59e0b', '#10b981', '#0ea5e9'];

const DIRECTION_CFG = {
  UP:   { textClass: 'text-rose-400',    Icon: TrendingUp },
  DOWN: { textClass: 'text-emerald-400', Icon: TrendingDown },
  FLAT: { textClass: 'text-white/30',    Icon: Minus },
};

const BUDGET_STATUS_ROW_CLASS = {
  EXCEEDED: 'bg-rose-500/[0.06]',
  WARNING:  '',
  OK:       '',
};

export default function CategoryPressureCard({ categoryPressure, onManageBudgets }) {
  const t = useTranslations();
  const items = categoryPressure ?? [];

  return (
    <div
      className="bg-[#0e0e1c] border border-white/[0.07] rounded-[18px] overflow-hidden flex flex-col w-full"
      style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both', animationDelay: '0.22s' }}
    >
      {/* Header */}
      <div className="px-5 py-4 border-b border-white/[0.07] shrink-0 flex items-start justify-between gap-3">
        <div>
          <div className="text-[15px] font-bold tracking-[-0.2px] text-white">
            {t('dashboard.categoryPressure')}
          </div>
          <div className="text-[12px] text-white/35 mt-0.5">
            {t('dashboard.categoryPressureSubtitle')}
          </div>
        </div>
        {onManageBudgets && (
          <button
            onClick={onManageBudgets}
            title={t('budget.manageBudgets')}
            className="mt-0.5 flex items-center gap-1 text-[11px] text-white/30 hover:text-white/60 transition-colors shrink-0"
          >
            <Settings2 className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">{t('budget.manageBudgets')}</span>
          </button>
        )}
      </div>

      {/* Scrollable list */}
      <div className="flex-1 min-h-0 overflow-y-auto dashboard-scroll">
        {items.length === 0 ? (
          <div className="text-center text-white/35 text-sm py-10 px-6">
            {t('dashboard.noCategoryPressure')}
          </div>
        ) : (
          items.map((item, index) => {
            const dcfg = DIRECTION_CFG[item.direction] ?? DIRECTION_CFG.FLAT;
            const deltaPercent = item.deltaPercent != null ? Number(item.deltaPercent) : null;
            const deltaAmount  = item.deltaAmount  != null ? Number(item.deltaAmount)  : null;
            const share = item.shareOfExpensesPercent != null ? Number(item.shareOfExpensesPercent) : 0;

            const hasBudget = item.budgetAmount != null;
            const budgetUsage = hasBudget ? Number(item.budgetUsagePercent ?? 0) : 0;
            const budgetStatus = hasBudget ? item.budgetStatus : null;
            const rowBgClass = BUDGET_STATUS_ROW_CLASS[budgetStatus] ?? '';

            return (
              <div
                key={item.categoryId ?? index}
                className={`px-5 py-3 border-b border-white/[0.04] last:border-b-0 transition-colors hover:bg-white/[0.025] ${rowBgClass}`}
              >
                {/* Top row: name + share */}
                <div className="flex items-center justify-between gap-3 mb-1.5">
                  <div className="flex items-center gap-1.5 min-w-0">
                    {item.categoryEmoji ? (
                      <span className="text-[14px] shrink-0">{item.categoryEmoji}</span>
                    ) : null}
                    <span className="text-[13px] font-medium text-white truncate">
                      {item.categoryName}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    {/* Delta */}
                    {deltaPercent != null && (
                      <span
                        className={`inline-flex items-center gap-[3px] text-[11px] font-semibold ${dcfg.textClass}`}
                        style={{ whiteSpace: 'nowrap' }}
                      >
                        <dcfg.Icon className="w-3 h-3 shrink-0" />
                        {Math.abs(deltaPercent).toFixed(1)}%
                      </span>
                    )}
                    {/* Share */}
                    <span
                      className={`font-mono text-[13px] font-bold tabular-nums ${
                        share >= 30 ? 'text-rose-400' : share >= 15 ? 'text-amber-400' : 'text-white/40'
                      }`}
                      style={{ whiteSpace: 'nowrap' }}
                    >
                      {share.toFixed(1)}%
                    </span>
                  </div>
                </div>

                {/* Amount row */}
                <div className="flex items-center justify-between gap-3 mb-1.5">
                  <span
                    className="text-[11px] text-white/45 font-mono"
                    style={{ whiteSpace: 'nowrap' }}
                  >
                    {formatCurrency(item.amount)}
                    {deltaAmount != null && (
                      <span className={`ml-1.5 ${dcfg.textClass}`}>
                        ({deltaAmount > 0 ? '+' : ''}{formatCurrency(deltaAmount)})
                      </span>
                    )}
                  </span>
                  <span className="text-[9px] text-white/25">{t('dashboard.ofExpenses')}</span>
                </div>

                {/* Spending share progress bar */}
                <div className="h-[3px] bg-white/[0.06] rounded-full overflow-hidden mb-1">
                  <div
                    className="h-full rounded-full"
                    style={{
                      width: `${Math.min(share, 100)}%`,
                      background: BAR_COLORS[index % BAR_COLORS.length],
                    }}
                  />
                </div>

                {/* Budget progress bar — only rendered when budget exists */}
                {hasBudget && (
                  <div className="mt-1.5">
                    <BudgetProgressBar percent={budgetUsage} status={budgetStatus} height="h-[2px]" />
                    <div className="flex items-center justify-between mt-0.5">
                      <span className="text-[9px] text-white/25">
                        {formatCurrency(item.amount)} / {formatCurrency(item.budgetAmount)}
                      </span>
                      <span
                        className={`text-[9px] font-semibold ${
                          budgetStatus === 'EXCEEDED'
                            ? 'text-rose-400'
                            : budgetStatus === 'WARNING'
                            ? 'text-amber-400'
                            : 'text-emerald-400/60'
                        }`}
                      >
                        {budgetUsage.toFixed(0)}%
                      </span>
                    </div>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
