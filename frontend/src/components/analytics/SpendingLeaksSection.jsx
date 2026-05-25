'use client';
import React, { useMemo } from 'react';
import { Zap, CheckCircle2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';

// ─── leak detection ───────────────────────────────────────────────────────────
// Keyword list covering common discretionary/draining categories in English + Polish.
// All matched case-insensitively as substrings of the category name.

const LEAK_KEYWORDS = [
  // Restaurants / fast food / delivery
  'restaurant', 'restaur', 'fast food', 'fastfood', 'takeaway', 'takeout',
  'delivery', 'dostawa', 'pizza', 'burger', 'kebab', 'sushi', 'bistro',
  'diner', 'jedzenie na wynos', 'mcdonalds', 'kfc', 'subway',
  // Snacks & drinks (non-alcohol)
  'snack', 'candy', 'sweet', 'sweets', 'chips', 'przekąski',
  'słodycze', 'napoje', 'kawa', 'kawiarnia', 'café', 'cafe', 'coffee shop',
  // Alcohol
  'alcohol', 'alkohol', 'beer', 'piwo', 'wine', 'wino', 'spirits',
  'whiskey', 'vodka', 'wódka', 'liquor',
  // Bars, clubs, events
  'bar', 'pub', 'club', 'klub', 'nightclub', 'impreza', 'party',
  'event', 'concert', 'koncert', 'festival',
  // Smoking / vaping
  'cigarette', 'tobacco', 'papierosy', 'smoke', 'smoking', 'vape', 'vaping',
  'hookah', 'shisha',
  // Impulse / comfort
  'impulse', 'comfort', 'zachcianki',
];

/**
 * Returns true if the category name matches any known "spending leak" keyword.
 * @param {string} name
 * @returns {boolean}
 */
function isLeakCategory(name) {
  const lower = (name ?? '').toLowerCase();
  return LEAK_KEYWORDS.some((kw) => lower.includes(kw));
}

// ─── leak row ─────────────────────────────────────────────────────────────────

const LEAK_COLORS = [
  '#f87171', '#fb923c', '#fbbf24', '#a78bfa', '#60a5fa', '#34d399',
];

function LeakRow({ leak, index, t }) {
  const color = LEAK_COLORS[index % LEAK_COLORS.length];
  return (
    <div className="flex items-center gap-3 py-2.5 border-b border-white/[0.05] last:border-b-0">
      {/* Emoji badge */}
      <div
        className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 text-[15px] leading-none"
        style={{ background: `${color}1a` }}
      >
        {leak.emoji || '💸'}
      </div>

      {/* Name + txn count */}
      <div className="flex-1 min-w-0">
        <p className="text-[13px] font-medium text-white/80 truncate leading-tight">
          {leak.name}
        </p>
        <p className="text-[11px] text-white/25 mt-0.5">
          {t('breakdownLeaksTxns', { count: leak.transactionCount ?? 0 })}
        </p>
      </div>

      {/* Amount + share */}
      <div className="text-right flex-shrink-0">
        <p className="text-[13px] font-semibold font-mono" style={{ color }}>
          {formatCurrency(leak.amount)}
        </p>
        <p className="text-[11px] text-white/30 mt-0.5">
          {t('breakdownLeaksShare', { share: Number(leak.share ?? 0).toFixed(1) })}
        </p>
      </div>
    </div>
  );
}

// ─── main component ───────────────────────────────────────────────────────────

/**
 * @param {{ categoryData: import('@/lib/api').CategoryBreakdownDto | null, loading: boolean }} props
 */
export default function SpendingLeaksSection({ categoryData, loading }) {
  const t = useTranslations('analytics');

  const { leaks, totalLeaks, leaksShare } = useMemo(() => {
    const cats = categoryData?.categories ?? [];
    const total = Number(categoryData?.totalExpenses ?? 0);

    const matched = cats.filter((c) => isLeakCategory(c.name));
    const sum = matched.reduce((acc, c) => acc + Number(c.amount ?? 0), 0);
    const share = total > 0 ? (sum / total) * 100 : 0;

    return { leaks: matched, totalLeaks: sum, leaksShare: share };
  }, [categoryData]);

  if (loading) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 animate-pulse h-32 mt-5" />
    );
  }

  // Don't render anything if there's no category data yet
  if (!categoryData) return null;

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mt-5">
      {/* Header */}
      <div className="flex items-center gap-2 mb-0.5">
        <Zap className="w-4 h-4 text-amber-400 flex-shrink-0" />
        <span className="text-xs font-bold tracking-[0.12em] uppercase text-white/35">
          {t('breakdownLeaksTitle')}
        </span>
      </div>
      <p className="text-[12px] text-white/25 mb-4 ml-6">
        {t('breakdownLeaksSubtitle')}
      </p>

      {leaks.length === 0 ? (
        /* Empty state */
        <div className="flex items-start gap-3 py-1">
          <CheckCircle2 className="w-5 h-5 text-emerald-400/40 flex-shrink-0 mt-0.5" />
          <div>
            <p className="text-[13px] font-medium text-white/50">
              {t('breakdownLeaksEmpty')}
            </p>
            <p className="text-[12px] text-white/25 mt-0.5">
              {t('breakdownLeaksEmptyDesc')}
            </p>
          </div>
        </div>
      ) : (
        <>
          {/* Leak rows */}
          <div>
            {leaks.map((leak, i) => (
              <LeakRow key={leak.categoryId} leak={leak} index={i} t={t} />
            ))}
          </div>

          {/* Footer: totals */}
          <div className="flex items-center justify-between mt-4 pt-3 border-t border-white/[0.06]">
            <div>
              <p className="text-[11px] font-bold tracking-[0.08em] uppercase text-white/25">
                {t('breakdownLeaksTotal')}
              </p>
              <p className="text-[11px] text-white/20 mt-0.5">
                {t('breakdownLeaksTotalHelper', { share: leaksShare.toFixed(1) })}
              </p>
            </div>
            <p className="text-[16px] font-bold font-mono text-amber-400">
              {formatCurrency(totalLeaks)}
            </p>
          </div>
        </>
      )}
    </div>
  );
}
