'use client';
import React from 'react';
import {
  Flame,
  TrendingUp,
  Wallet,
  Lock,
  ShieldCheck,
  CalendarClock,
  AlertCircle,
  InboxIcon,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';
import AnalyticsMetricCard from './AnalyticsMetricCard';
import { CardLoading } from '@/components/common/Loading';

function ProjectionProgressBar({ projectedPeriodExpenses, incomeForPeriod }) {
  const t = useTranslations('analytics');

  const safeIncome = incomeForPeriod > 0 ? incomeForPeriod : null;
  const rawPct = safeIncome ? (projectedPeriodExpenses / safeIncome) * 100 : null;
  const clampedWidth = rawPct !== null ? Math.min(Math.max(rawPct, 0), 100) : 0;
  const isOver = rawPct !== null && rawPct > 100;
  const barColor = isOver ? '#f87171' : rawPct > 80 ? '#fbbf24' : '#4ade80';

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 col-span-2 md:col-span-3 relative overflow-hidden hover:bg-white/[0.02] transition-colors">
      <div className="flex items-center justify-between mb-3">
        <span className="text-[10px] font-bold tracking-[0.14em] uppercase text-white/25">
          {t('projectedPeriodSpend')} vs. {t('period')}
        </span>
        {rawPct !== null && (
          <span
            className="text-[11px] font-mono font-medium"
            style={{ color: barColor }}
          >
            {rawPct.toFixed(1)}% {t('projectedVsIncome')}
          </span>
        )}
        {rawPct === null && (
          <span className="text-[11px] text-white/25">{t('projectionNotApplicable')}</span>
        )}
      </div>

      <div className="w-full h-2 rounded-full bg-white/[0.06] overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{ width: `${clampedWidth}%`, backgroundColor: barColor }}
        />
      </div>

      <div className="flex justify-between mt-2 text-[10px] text-white/25 font-mono">
        <span>{formatCurrency(projectedPeriodExpenses)}</span>
        <span>{safeIncome ? formatCurrency(safeIncome) : '—'}</span>
      </div>

      <div className="absolute bottom-0 left-0 right-0 h-0.5 opacity-40" style={{ backgroundColor: barColor }} />
    </div>
  );
}

export default function ProjectionCards({ data, loading, error, onRetry }) {
  const t = useTranslations('analytics');

  if (loading) {
    return (
      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        {Array.from({ length: 6 }).map((_, i) => (
          <CardLoading key={i} />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center gap-4 py-12 text-center">
        <AlertCircle className="w-10 h-10 text-red-400" />
        <div>
          <p className="text-white font-medium mb-1">{t('projectionErrorLoading')}</p>
          <p className="text-gray-400 text-sm mb-4">{error}</p>
          {onRetry && (
            <button
              onClick={onRetry}
              className="px-4 py-2 bg-violet-600 text-white text-sm rounded-lg hover:bg-violet-700 transition-colors"
            >
              {t('retry')}
            </button>
          )}
        </div>
      </div>
    );
  }

  if (!data) return null;

  const allZero =
    data.expensesToDate === 0 &&
    data.projectedPeriodExpenses === 0 &&
    data.remainingFixedPayments === 0 &&
    data.safeToSpendToday === 0;

  if (allZero) {
    return (
      <div className="flex flex-col items-center gap-3 py-12 text-center">
        <InboxIcon className="w-10 h-10 text-gray-600" />
        <p className="text-white font-medium">{t('projectionEmpty')}</p>
        {data.startDate && data.endDate && (
          <p className="text-gray-400 text-sm">{data.startDate} – {data.endDate}</p>
        )}
      </div>
    );
  }

  const isActive = data.projectionAvailable;

  const balanceColor = (data.projectedEndBalance ?? 0) >= 0 ? '#4ade80' : '#f87171';
  const safeColor = isActive
    ? ((data.safeToSpendPerDay ?? 0) >= 0 ? '#4ade80' : '#f87171')
    : '#ffffff';

  const periodLabel = data.periodLabel
    ? `${data.periodLabel}: ${data.startDate} – ${data.endDate}`
    : `${data.startDate} – ${data.endDate}`;

  const cards = [
    {
      key: 'dailyBurnRate',
      label: t('dailyBurnRate'),
      value: formatCurrency(data.dailyBurnRate ?? 0),
      icon: Flame,
      color: '#fb923c',
    },
    {
      key: 'projectedPeriodExpenses',
      label: t('projectedPeriodSpend'),
      value: formatCurrency(data.projectedPeriodExpenses ?? 0),
      subtext: periodLabel,
      icon: TrendingUp,
      color: '#f87171',
    },
    {
      key: 'projectedEndBalance',
      label: t('projectedEndBalance'),
      value: formatCurrency(data.projectedEndBalance ?? 0),
      icon: Wallet,
      color: balanceColor,
    },
    {
      key: 'remainingFixedPayments',
      label: t('remainingFixedPayments'),
      value: formatCurrency(data.remainingFixedPayments ?? 0),
      icon: Lock,
      color: '#a78bfa',
    },
    {
      key: 'safeToSpendToday',
      label: t('safeToSpend'),
      value: isActive
        ? `${formatCurrency(data.safeToSpendPerDay ?? 0)} ${t('perDay')}`
        : t('projectionNotApplicable'),
      subtext: isActive
        ? `${formatCurrency(data.safeToSpendToday ?? 0)} ${t('totalRemaining')}`
        : null,
      icon: ShieldCheck,
      color: safeColor,
      dimmed: !isActive,
    },
    {
      key: 'daysRemaining',
      label: t('daysRemaining'),
      value: data.daysRemaining ?? '—',
      subtext: t('daysRemainingSubtext', {
        days: data.daysElapsed ?? 0,
        total: data.daysInPeriod ?? 0,
      }),
      icon: CalendarClock,
      color: '#60a5fa',
    },
  ];

  return (
    <div className="space-y-4">
      {/* Status banner */}
      <div
        className={`flex items-center gap-2 px-4 py-2.5 rounded-lg text-[11px] font-medium border ${
          isActive
            ? 'bg-emerald-500/[0.07] border-emerald-500/20 text-emerald-400'
            : 'bg-white/[0.03] border-white/[0.06] text-white/35'
        }`}
      >
        <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${isActive ? 'bg-emerald-400' : 'bg-white/20'}`} />
        {isActive
          ? t('basedOnPace')
          : data.projectionReason || t('historicalPeriod')}
      </div>

      {/* Metric cards grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        {cards.map((card) => (
          <AnalyticsMetricCard
            key={card.key}
            label={card.label}
            value={card.dimmed ? (
              <span className="opacity-30">{card.value}</span>
            ) : card.value}
            subtext={card.subtext}
            icon={card.icon}
            color={card.dimmed ? '#ffffff' : card.color}
          />
        ))}

        <ProjectionProgressBar
          projectedPeriodExpenses={data.projectedPeriodExpenses ?? 0}
          incomeForPeriod={data.incomeForPeriod ?? 0}
        />
      </div>
    </div>
  );
}
