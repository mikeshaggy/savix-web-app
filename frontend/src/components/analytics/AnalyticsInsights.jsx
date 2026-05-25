'use client';
import React from 'react';
import { Lightbulb, Flame, TrendingUp } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';

function InsightChip({ icon: Icon, text, color }) {
  return (
    <div
      className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm border"
      style={{
        background: `${color}0d`,
        borderColor: `${color}28`,
        color: `${color}dd`,
      }}
    >
      <Icon className="w-4 h-4 flex-shrink-0" style={{ color }} />
      <span>{text}</span>
    </div>
  );
}

export default function AnalyticsInsights({ projData }) {
  const t = useTranslations('analytics');

  const insights = [];

  if (projData) {
    const burnRate = projData.dailyBurnRate ?? 0;
    const daysRemaining = projData.daysRemaining ?? 0;
    const income = projData.incomeForPeriod ?? 0;
    const projectedTotal = projData.projectedPeriodExpenses ?? 0;

    if (projData.projectionAvailable && burnRate > 0 && daysRemaining > 0) {
      insights.push({
        icon: Flame,
        text: t('insightBurnRate', { amount: formatCurrency(burnRate) }),
        color: '#fb923c',
      });
    }

    if (income > 0 && projectedTotal > income) {
      const overspend = projectedTotal - income;
      insights.push({
        icon: TrendingUp,
        text: t('insightOverspend', { amount: formatCurrency(overspend) }),
        color: '#f87171',
      });
    }
  }

  if (insights.length === 0) return null;

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5">
      <div className="flex items-center gap-2 mb-3">
        <div
          className="w-6 h-6 rounded-lg flex items-center justify-center flex-shrink-0"
          style={{ background: '#fbbf2418' }}
        >
          <Lightbulb className="w-3.5 h-3.5 text-amber-400" />
        </div>
        <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35">
          {t('categoryInsights')}
        </div>
      </div>

      <div className="flex flex-col gap-2">
        {insights.map((item, i) => (
          <InsightChip key={i} {...item} />
        ))}
      </div>
    </div>
  );
}
