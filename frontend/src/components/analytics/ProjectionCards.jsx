'use client';
import React from 'react';
import {
  Flame,
  TrendingUp,
  Wallet,
  Lock,
  ShieldCheck,
  CalendarClock,
  InboxIcon,
} from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useFeatures } from '@/hooks/useFeatures';
import { useForecastCurrency } from '@/hooks/useForecastCurrency';
import { forecastPageVariant, formatWholeCurrency } from '@/lib/forecastV2';
import { projectionCardsModel } from '@/lib/forecastCards';
import ForecastFactCard from '@/components/forecast/ForecastFactCard';
import AnalyticsMetricCard from './AnalyticsMetricCard';
import { CardLoading } from '@/components/common/Loading';
import ErrorState from '@/components/common/ErrorState';
import EmptyState from '@/components/common/EmptyState';
import SectionLabel from '@/components/common/SectionLabel';

function ProjectionProgressBar({ projectedPeriodExpenses, incomeForPeriod }) {
  const t = useTranslations('analytics');
  const formatCurrency = useFormatCurrency();

  const safeIncome = incomeForPeriod > 0 ? incomeForPeriod : null;
  const rawPct = safeIncome ? (projectedPeriodExpenses / safeIncome) * 100 : null;
  const clampedWidth = rawPct !== null ? Math.min(Math.max(rawPct, 0), 100) : 0;
  const isOver = rawPct !== null && rawPct > 100;
  const barColor = isOver ? '#f87171' : rawPct > 80 ? '#fbbf24' : '#4ade80';

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 col-span-2 md:col-span-3 relative overflow-hidden hover:bg-white/[0.02] transition-colors">
      <div className="flex items-center justify-between mb-3">
        <SectionLabel variant="metric">
          {t('projectedPeriodSpend')} vs. {t('period')}
        </SectionLabel>
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
    </div>
  );
}

const V2_CARD_CFG = {
  leftUntilPayday: { icon: Wallet, color: '#4ade80' },
  expectedAtPayday: { icon: TrendingUp, color: '#e2e8f0' },
  committed: { icon: Lock, color: '#a78bfa' },
  expectedVariable: { icon: Flame, color: '#fb923c' },
};

/**
 * Stage 5.6 cards (`forecast-v2` on): Left until payday / Expected at payday (range) / Committed / Expected
 * variable, all ForecastV2Dto fields. The hero is the single large presentation of the two headline figures, so
 * their cards are context cards (balance − committed; pessimistic … optimistic) without a large value; only
 * Committed and Expected variable are shown as figures. No burn rate, safe-to-spend, projected surplus or
 * "% of income".
 */
function ProjectionCardsV2({ forecast }) {
  const t = useTranslations('forecast');
  const locale = useLocale();
  const lang = locale === 'pl' ? 'pl' : 'en';
  const formatCurrency = useForecastCurrency();
  const money = (v) => (v === null ? '—' : formatCurrency(v));
  const approx = (v) => (v === null ? '—' : t('approxAmount', { amount: formatWholeCurrency(v, lang) }));
  const neg = (v) => v !== null && v < 0;

  const cards = projectionCardsModel(forecast).map((card) => {
    const cfg = V2_CARD_CFG[card.key];
    switch (card.key) {
      case 'leftUntilPayday':
        return {
          ...cfg,
          key: card.key,
          label: t('leftUntilPayday'),
          rows: [
            { label: t('balance'), value: money(card.balance), negative: neg(card.balance) },
            { label: `− ${t('committed')}`, value: money(card.committed) },
          ],
          note: t('cardLeftNote'),
        };
      case 'expectedAtPayday':
        return {
          ...cfg,
          key: card.key,
          label: t('expectedLeftAtPayday'),
          rows: [
            { label: t('pessimistic'), value: approx(card.pessimistic), negative: neg(card.pessimistic) },
            { label: t('optimistic'), value: approx(card.optimistic), negative: neg(card.optimistic) },
          ],
          note: card.basisKey ? t(card.basisKey) : null,
        };
      case 'committed':
        return {
          ...cfg,
          key: card.key,
          label: t('committed'),
          figure: money(card.value),
          note: t('cardCommittedSubtext', { count: card.count }),
        };
      default:
        return {
          ...cfg,
          key: card.key,
          label: t('expectedVariable'),
          figure: money(card.value),
          note: card.lessSpend !== null && card.moreSpend !== null
            ? t('cardVariableSubtext', { less: money(card.lessSpend), more: money(card.moreSpend) })
            : null,
        };
    }
  });

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4" data-testid="projection-cards-v2">
      {cards.map(({ key, ...card }) => (
        <ForecastFactCard key={key} {...card} />
      ))}
    </div>
  );
}

export default function ProjectionCards({ data, loading, error, onRetry }) {
  const t = useTranslations('analytics');
  const formatCurrency = useFormatCurrency();
  const { forecastV2 } = useFeatures();

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
      <ErrorState
        variant="inline"
        title={t('projectionErrorLoading')}
        onRetry={onRetry}
        retryLabel={t('retry')}
      />
    );
  }

  if (!data) return null;

  if (forecastPageVariant({ forecastV2, projData: data }) === 'v2') {
    return <ProjectionCardsV2 forecast={data.forecast} />;
  }

  const allZero =
    data.expensesToDate === 0 &&
    data.projectedPeriodExpenses === 0 &&
    data.remainingFixedPayments === 0 &&
    data.safeToSpendToday === 0;

  if (allZero) {
    return (
      <EmptyState
        variant="card"
        icon={InboxIcon}
        title={t('projectionEmpty')}
        description={data.startDate && data.endDate ? `${data.startDate} – ${data.endDate}` : undefined}
      />
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
      label: (data.projectedEndBalance ?? 0) >= 0 ? t('projectedCycleSurplus') : t('projectedCycleDeficit'),
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
