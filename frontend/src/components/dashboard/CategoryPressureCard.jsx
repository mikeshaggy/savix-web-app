'use client';
import React from 'react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';

const BAR_COLORS = ['#f43f5e', '#8b5cf6', '#f59e0b', '#10b981', '#0ea5e9'];

const DIRECTION_CFG = {
  UP: { textClass: 'text-rose-400', icon: '↑' },
  DOWN: { textClass: 'text-emerald-400', icon: '↓' },
  FLAT: { textClass: 'text-white/30', icon: '→' },
};

export default function CategoryPressureCard({ categoryPressure }) {
  const t = useTranslations();
  const items = categoryPressure ?? [];

  return (
    <div
      className="bg-[#0e0e1c] border border-white/[0.07] rounded-[18px] overflow-hidden flex flex-col w-full"
      style={{ animation: 'fadeUp 0.5s ease both', animationDelay: '0.22s' }}
    >
      {/* Header */}
      <div className="px-5 py-4 border-b border-white/[0.07] shrink-0">
        <div className="text-[15px] font-bold tracking-[-0.2px] text-white">{t('dashboard.categoryPressure')}</div>
        <div className="text-[12px] text-white/35 mt-0.5">{t('dashboard.categoryPressureSubtitle')}</div>
      </div>

      {/* Scrollable list — flex-1 fills the remaining card height set by the grid row */}
      <div className="flex-1 min-h-0 overflow-y-auto dashboard-scroll">
        {items.length === 0 ? (
          <div className="text-center text-white/35 text-sm py-10 px-6">
            {t('dashboard.noCategoryPressure')}
          </div>
        ) : (
          items.map((item, index) => {
            const dcfg = DIRECTION_CFG[item.direction] ?? DIRECTION_CFG.FLAT;
            const deltaPercent = item.deltaPercent != null ? Number(item.deltaPercent) : null;
            const deltaAmount = item.deltaAmount != null ? Number(item.deltaAmount) : null;
            const share = item.shareOfExpensesPercent != null ? Number(item.shareOfExpensesPercent) : 0;

            return (
              <div
                key={item.categoryId ?? index}
                className="px-5 py-3 border-b border-white/[0.04] last:border-b-0 transition-colors hover:bg-white/[0.025]"
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
                        className={`text-[11px] font-semibold ${dcfg.textClass}`}
                        style={{ whiteSpace: 'nowrap' }}
                      >
                        {dcfg.icon}{Math.abs(deltaPercent).toFixed(1)}%
                      </span>
                    )}
                    {/* Share */}
                    <span
                      className="font-mono text-[13px] font-bold text-rose-400 tabular-nums"
                      style={{ whiteSpace: 'nowrap' }}
                    >
                      {share.toFixed(1)}%
                    </span>
                  </div>
                </div>

                {/* Amount row */}
                <div className="flex items-center justify-between gap-3 mb-2">
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

                {/* Progress bar */}
                <div className="h-[3px] bg-white/[0.06] rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full"
                    style={{
                      width: `${Math.min(share, 100)}%`,
                      background: BAR_COLORS[index % BAR_COLORS.length],
                    }}
                  />
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
