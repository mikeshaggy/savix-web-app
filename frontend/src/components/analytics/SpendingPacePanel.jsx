'use client';
import React from 'react';
import { Flame, ShieldCheck, CalendarClock, Lock, TrendingDown, Zap } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';

function PaceRow({ icon: Icon, label, subtext, value, color }) {
  return (
    <div className="flex items-center justify-between py-3.5 border-b border-white/[0.05] last:border-0">
      <div className="flex items-center gap-3">
        <div
          className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{ background: `${color}1a` }}
        >
          <Icon className="w-5 h-5" style={{ color }} />
        </div>
        <div>
          <div className="text-sm font-medium text-white/65">{label}</div>
          {subtext && (
            <div className="text-xs text-white/35 mt-0.5">{subtext}</div>
          )}
        </div>
      </div>
      <div
        className="font-mono text-[15px] font-bold ml-4 text-right flex-shrink-0"
        style={{ color }}
      >
        {value}
      </div>
    </div>
  );
}

export default function SpendingPacePanel({ projData, loading }) {
  const t = useTranslations('analytics');

  if (loading) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 animate-pulse h-56" />
    );
  }

  if (!projData) return null;

  const isActive = projData.projectionAvailable;
  const daysRemaining = projData.daysRemaining ?? 0;
  const remainingFixed = projData.remainingFixedPayments ?? 0;
  const burnRate = projData.dailyBurnRate ?? 0;

  const safeDailyBudget = isActive ? (projData.safeToSpendPerDay ?? null) : null;

  // How much the user needs to cut per day vs current burn rate
  const reductionNeeded =
    safeDailyBudget !== null && burnRate > Math.max(0, safeDailyBudget)
      ? burnRate - Math.max(0, safeDailyBudget)
      : 0;

  const safeColor =
    safeDailyBudget !== null
      ? safeDailyBudget >= 0
        ? '#4ade80'
        : '#f87171'
      : '#ffffff';

  const rows = [
    {
      icon: Flame,
      label: t('dailyBurnRate'),
      subtext: t('currentPace'),
      value: formatCurrency(burnRate),
      color: '#fb923c',
    },
    ...(safeDailyBudget !== null
      ? [
          {
            icon: ShieldCheck,
            label: t('safeToSpendPerDay'),
            subtext: t('fromTodayUntilEnd'),
            value: formatCurrency(safeDailyBudget),
            color: safeColor,
          },
        ]
      : []),
    ...(reductionNeeded > 0
      ? [
          {
            icon: TrendingDown,
            label: t('requiredReduction'),
            subtext: t('perDayToBudget'),
            value: formatCurrency(reductionNeeded),
            color: '#fbbf24',
          },
        ]
      : []),
    {
      icon: Lock,
      label: t('remainingFixedPayments'),
      subtext: t('fixedThisCycle'),
      value: formatCurrency(remainingFixed),
      color: '#a78bfa',
    },
    {
      icon: CalendarClock,
      label: t('daysRemaining'),
      subtext: t('daysRemainingSubtext', {
        days: projData.daysElapsed ?? 0,
        total: projData.daysInPeriod ?? 0,
      }),
      value: daysRemaining,
      color: '#60a5fa',
    },
  ];

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-6 relative overflow-hidden">
      <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-1">
        {t('spendingPace')}
      </div>

      <div className="mt-1">
        {rows.map((row, i) => (
          <PaceRow key={i} {...row} />
        ))}
      </div>

      {/* Safe to spend — highlighted footer */}
      {isActive && (
        <div className="mt-4 pt-4 border-t border-white/[0.08]">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <Zap className="w-5 h-5 text-violet-400" />
              <span className="text-sm font-semibold text-white/60">
                {t('safeToSpend')}
              </span>
            </div>
            <div
              className="font-mono text-xl font-bold"
              style={{
                color: (projData.safeToSpendPerDay ?? 0) >= 0 ? '#4ade80' : '#f87171',
              }}
            >
              {`${formatCurrency(projData.safeToSpendPerDay ?? 0)} ${t('perDay')}`}
            </div>
          </div>
          <div className="text-xs text-white/30 mt-1 ml-[30px]">
            {`${formatCurrency(projData.safeToSpendToday ?? 0)} ${t('totalRemaining')}`}
          </div>
        </div>
      )}

      <div className="absolute bottom-0 left-0 right-0 h-0.5 opacity-20 bg-violet-500" />
    </div>
  );
}
