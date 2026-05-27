'use client';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertCircle, ArrowDownRight, ArrowUpRight, CalendarDays, GitCompareArrows, Minus, RefreshCcw, Sparkles, TrendingUp } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useWallets } from '@/contexts/WalletContext';
import { useAnalyticsPeriod } from '@/hooks/useAnalyticsPeriod';
import { analyticsApi } from '@/lib/api';
import { formatCurrency, formatDate } from '@/utils/helpers';

const STATUS_META = {
  ABOVE_BASELINE: {
    color: '#f87171',
    badge: 'bg-red-500/15 text-red-300 border-red-400/20',
    icon: ArrowUpRight,
  },
  BELOW_BASELINE: {
    color: '#4ade80',
    badge: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/20',
    icon: ArrowDownRight,
  },
  IN_LINE: {
    color: '#a78bfa',
    badge: 'bg-violet-500/15 text-violet-300 border-violet-400/20',
    icon: Minus,
  },
  NO_BASELINE: {
    color: '#94a3b8',
    badge: 'bg-white/[0.06] text-white/40 border-white/[0.08]',
    icon: Minus,
  },
  NEW_SPEND: {
    color: '#fbbf24',
    badge: 'bg-amber-500/15 text-amber-300 border-amber-400/20',
    icon: Sparkles,
  },
};

const COMPARE_OPTIONS = [
  { cycles: 1, labelKey: 'cycleComparisonLastCycle' },
  { cycles: 3, labelKey: 'cycleComparisonThreeCycleAvg' },
  { cycles: 6, labelKey: 'cycleComparisonSixCycleAvg' },
  { cycles: 12, labelKey: 'cycleComparisonTwelveCycleAvg' },
];

const SORT_OPTIONS = [
  { value: 'increase', labelKey: 'cycleComparisonSortIncrease' },
  { value: 'decrease', labelKey: 'cycleComparisonSortDecrease' },
  { value: 'current', labelKey: 'cycleComparisonSortCurrent' },
  { value: 'comparison', labelKey: 'cycleComparisonSortComparison' },
  { value: 'name', labelKey: 'cycleComparisonSortName' },
  { value: 'status', labelKey: 'cycleComparisonSortStatus' },
];

const STATUS_SORT_ORDER = {
  NEW_SPEND: 1,
  ABOVE_BASELINE: 2,
  IN_LINE: 3,
  BELOW_BASELINE: 4,
  NO_BASELINE: 5,
};

function endOfMonth(month) {
  if (!month) return null;
  const [year, monthIndex] = month.split('-').map(Number);
  if (!year || !monthIndex) return null;
  return new Date(Date.UTC(year, monthIndex, 0)).toISOString().slice(0, 10);
}

function toNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function formatPercent(value) {
  if (value == null) return null;
  const number = Number(value);
  if (!Number.isFinite(number)) return null;
  return `${number > 0 ? '+' : ''}${number.toFixed(1)}%`;
}

function formatDeltaAmount(value, locale) {
  const amount = toNumber(value);
  const sign = amount > 0 ? '+' : '';
  return `${sign}${formatCurrency(amount, locale)}`;
}

function statusLabel(status, t) {
  switch (status) {
    case 'ABOVE_BASELINE':
      return t('cycleComparisonStatusAbove');
    case 'BELOW_BASELINE':
      return t('cycleComparisonStatusBelow');
    case 'IN_LINE':
      return t('cycleComparisonStatusInline');
    case 'NO_BASELINE':
      return t('cycleComparisonStatusNoBaseline');
    case 'NEW_SPEND':
      return t('cycleComparisonStatusNewSpend');
    default:
      return status || '—';
  }
}

function comparisonTitle(baselineCycles, t) {
  return baselineCycles === 1
    ? t('cycleComparisonPreviousCycle')
    : t('cycleComparisonBaselineAverage');
}

function comparisonSubtext(baselineCycles, t) {
  return baselineCycles === 1
    ? t('cycleComparisonPreviousCycle')
    : t('cycleComparisonPreviousCycles', { count: baselineCycles });
}

function sortCategories(categories, sortMode) {
  const sorted = [...(categories ?? [])];
  sorted.sort((a, b) => {
    if (sortMode === 'decrease') {
      return toNumber(a.deltaAmount) - toNumber(b.deltaAmount);
    }
    if (sortMode === 'current') {
      return toNumber(b.currentAmount) - toNumber(a.currentAmount);
    }
    if (sortMode === 'comparison') {
      return toNumber(b.baselineAverageAmount) - toNumber(a.baselineAverageAmount);
    }
    if (sortMode === 'name') {
      return (a.name ?? '').localeCompare(b.name ?? '');
    }
    if (sortMode === 'status') {
      const statusCompare = (STATUS_SORT_ORDER[a.status] ?? 99) - (STATUS_SORT_ORDER[b.status] ?? 99);
      if (statusCompare !== 0) return statusCompare;
      return toNumber(b.deltaAmount) - toNumber(a.deltaAmount);
    }
    return toNumber(b.deltaAmount) - toNumber(a.deltaAmount);
  });
  return sorted;
}

function SummaryCard({ label, value, subtext, icon: Icon, color }) {
  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 relative overflow-hidden">
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="text-[10px] font-bold tracking-[0.14em] uppercase text-white/25">
          {label}
        </div>
        <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: `${color}18` }}>
          <Icon className="w-4 h-4" style={{ color }} />
        </div>
      </div>
      <div className="font-mono text-[clamp(20px,2.2vw,28px)] font-medium leading-none mb-2" style={{ color }}>
        {value}
      </div>
      {subtext && <div className="text-[11px] text-white/30 leading-snug">{subtext}</div>}
      <div className="absolute bottom-0 left-0 right-0 h-0.5 opacity-40" style={{ backgroundColor: color }} />
    </div>
  );
}

function CycleComparisonTooltip({ active, payload, label, locale, t }) {
  if (!active || !payload?.length) return null;
  const point = payload[0]?.payload;
  return (
    <div className="rounded-lg border border-white/[0.08] bg-[#111121] px-3 py-2 shadow-xl">
      <div className="text-[11px] font-semibold text-white/60 mb-1">
        {t('cycleComparisonDayLabel', { day: label })}
        {point?.date ? ` · ${formatDate(point.date, locale)}` : ''}
      </div>
      {payload.map((entry) => (
        <div key={entry.dataKey} className="flex items-center justify-between gap-5 text-xs">
          <span style={{ color: entry.color }}>{entry.name}</span>
          <span className="font-mono text-white/80">{formatCurrency(toNumber(entry.value), locale)}</span>
        </div>
      ))}
    </div>
  );
}

function CumulativeChart({ data, baselineCycles, locale, t }) {
  const current = data?.series?.currentCumulative ?? [];
  const baseline = data?.series?.baselineAverageCumulative ?? [];
  const baselineLabel = baselineCycles === 1
    ? t('cycleComparisonPreviousCycle')
    : t('cycleComparisonBaselineAverage');
  const chartData = current.map((point) => {
    const baselinePoint = baseline.find((item) => item.dayIndex === point.dayIndex);
    return {
      dayIndex: point.dayIndex,
      date: point.date,
      current: toNumber(point.amount),
      baseline: toNumber(baselinePoint?.amount),
    };
  });

  if (chartData.length === 0) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-6">
        <p className="text-sm text-white/35">{t('cycleComparisonNoChartData')}</p>
      </div>
    );
  }

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5">
      <div className="flex flex-col gap-1 mb-4">
        <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35">
          {t('cycleComparisonChartTitle')}
        </div>
        <div className="text-[12px] text-white/25">
          {baselineCycles === 1
            ? t('cycleComparisonChartSubtitlePrevious')
            : t('cycleComparisonChartSubtitleAverage')}
        </div>
      </div>
      <div className="h-[320px]">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 10, right: 14, left: 8, bottom: 6 }}>
            <CartesianGrid stroke="rgba(255,255,255,0.06)" vertical={false} />
            <XAxis
              dataKey="dayIndex"
              tickLine={false}
              axisLine={false}
              tick={{ fill: 'rgba(255,255,255,0.35)', fontSize: 11 }}
              tickFormatter={(value) => t('cycleComparisonDayShort', { day: value })}
              interval="preserveStartEnd"
            />
            <YAxis
              tickLine={false}
              axisLine={false}
              width={96}
              tick={{ fill: 'rgba(255,255,255,0.35)', fontSize: 11 }}
              tickFormatter={(value) => formatCurrency(toNumber(value), locale)}
            />
            <Tooltip content={<CycleComparisonTooltip locale={locale} t={t} />} />
            <Legend wrapperStyle={{ fontSize: 12, color: 'rgba(255,255,255,0.5)' }} />
            <Line
              type="monotone"
              dataKey="current"
              name={t('cycleComparisonCurrentLine')}
              stroke="#f87171"
              strokeWidth={2.5}
              dot={false}
              activeDot={{ r: 4 }}
            />
            <Line
              type="monotone"
              dataKey="baseline"
              name={baselineLabel}
              stroke="#a78bfa"
              strokeWidth={2}
              strokeDasharray="5 5"
              dot={false}
              activeDot={{ r: 4 }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function CategoryStatusBadge({ status, t }) {
  const meta = STATUS_META[status] ?? STATUS_META.IN_LINE;
  const Icon = meta.icon;
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-1 rounded-md border text-[10px] font-semibold ${meta.badge}`}>
      <Icon className="w-3 h-3" />
      {statusLabel(status, t)}
    </span>
  );
}

function CategoryDeltas({ categories, baselineAvailable, baselineCycles, sortMode, setSortMode, locale, t }) {
  const sortedCategories = useMemo(
    () => sortCategories(categories, sortMode),
    [categories, sortMode]
  );

  if (!baselineAvailable) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5">
        <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-2">
          {t('cycleComparisonCategoriesTitle')}
        </div>
        <p className="text-sm text-white/35">{t('cycleComparisonNoComparisonCategoryDesc')}</p>
      </div>
    );
  }

  if (!sortedCategories.length) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5">
        <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-2">
          {t('cycleComparisonCategoriesTitle')}
        </div>
        <p className="text-sm text-white/35">{t('cycleComparisonNoCategories')}</p>
      </div>
    );
  }

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl overflow-hidden">
      <div className="px-5 pt-5 pb-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35">
          {t('cycleComparisonCategoriesTitle')}
        </div>
        <label className="flex items-center gap-2 text-[11px] text-white/30">
          <span>{t('cycleComparisonSortBy')}</span>
          <select
            value={sortMode}
            onChange={(event) => setSortMode(event.target.value)}
            className="bg-white/[0.04] border border-white/[0.08] text-white/70 text-xs rounded-lg px-2.5 py-1.5 outline-none [color-scheme:dark]"
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {t(option.labelKey)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div
        className="max-h-[min(640px,70vh)] overflow-auto
          [&::-webkit-scrollbar]:w-1.5
          [&::-webkit-scrollbar]:h-1.5
          [&::-webkit-scrollbar-track]:bg-transparent
          [&::-webkit-scrollbar-thumb]:rounded-full
          [&::-webkit-scrollbar-thumb]:bg-white/[0.15]
          [&::-webkit-scrollbar-thumb:hover]:bg-violet-500/50"
        style={{ scrollbarWidth: 'thin', scrollbarColor: 'rgba(139,92,246,0.3) transparent' }}
      >
        <table className="w-full min-w-[760px]">
          <thead className="sticky top-0 z-10">
            <tr className="border-b border-white/[0.08] bg-[#0e0e1c]">
              <th className="text-left text-[10px] uppercase tracking-[0.12em] text-white/25 font-bold px-5 py-3">
                {t('cycleComparisonCategory')}
              </th>
              <th className="text-right text-[10px] uppercase tracking-[0.12em] text-white/25 font-bold px-3 py-3">
                {t('cycleComparisonCurrent')}
              </th>
              <th className="text-right text-[10px] uppercase tracking-[0.12em] text-white/25 font-bold px-3 py-3">
                {t('cycleComparisonComparison')}
              </th>
              <th className="text-right text-[10px] uppercase tracking-[0.12em] text-white/25 font-bold px-3 py-3">
                {t('cycleComparisonDelta')}
              </th>
              <th className="text-left text-[10px] uppercase tracking-[0.12em] text-white/25 font-bold px-5 py-3">
                {t('cycleComparisonStatus')}
              </th>
            </tr>
          </thead>
          <tbody>
            {sortedCategories.map((category) => {
              const meta = STATUS_META[category.status] ?? STATUS_META.IN_LINE;
              const percent = formatPercent(category.deltaPercent);
              const deltaCopy = percent
                ? `${formatDeltaAmount(category.deltaAmount, locale)} · ${percent}`
                : category.status === 'NEW_SPEND'
                  ? t('cycleComparisonNewSpending')
                  : formatDeltaAmount(category.deltaAmount, locale);
              return (
                <tr key={category.categoryId} className="border-b border-white/[0.05] last:border-b-0">
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-8 h-8 rounded-lg bg-white/[0.04] flex items-center justify-center text-[15px] flex-shrink-0">
                        {category.emoji || '•'}
                      </div>
                      <div className="min-w-0">
                        <div className="text-sm font-medium text-white/80 truncate">{category.name}</div>
                        <div className="text-[11px] text-white/25">
                          {t(baselineCycles === 1
                            ? 'cycleComparisonTransactionCountsPrevious'
                            : 'cycleComparisonTransactionCountsAverage', {
                            current: category.currentTransactionCount ?? 0,
                            comparison: Number(category.baselineAverageTransactionCount ?? 0).toFixed(1),
                          })}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="px-3 py-3 text-right font-mono text-sm text-white/75">
                    {formatCurrency(toNumber(category.currentAmount), locale)}
                  </td>
                  <td className="px-3 py-3 text-right font-mono text-sm text-white/45">
                    {formatCurrency(toNumber(category.baselineAverageAmount), locale)}
                  </td>
                  <td className="px-3 py-3 text-right font-mono text-sm" style={{ color: meta.color }}>
                    {deltaCopy}
                  </td>
                  <td className="px-5 py-3">
                    <CategoryStatusBadge status={category.status} t={t} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function HighlightCard({ title, items, locale, t }) {
  if (!items?.length) return null;
  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5">
      <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-3">
        {title}
      </div>
      <div className="space-y-3">
        {items.map((category) => {
          const meta = STATUS_META[category.status] ?? STATUS_META.IN_LINE;
          const percent = formatPercent(category.deltaPercent);
          return (
            <div key={category.categoryId} className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2 min-w-0">
                <span className="w-7 h-7 rounded-lg bg-white/[0.04] flex items-center justify-center text-sm flex-shrink-0">
                  {category.emoji || '•'}
                </span>
                <span className="text-sm text-white/75 truncate">{category.name}</span>
              </div>
              <div className="text-right flex-shrink-0">
                <div className="font-mono text-sm" style={{ color: meta.color }}>
                  {formatDeltaAmount(category.deltaAmount, locale)}
                </div>
                <div className="text-[11px] text-white/30">
                  {percent ?? statusLabel(category.status, t)}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default function AnalyticsComparisonPage() {
  const t = useTranslations('analytics');
  const locale = useLocale();
  const { currentWallet } = useWallets();
  const {
    periodType,
    selectedMonth,
    resolvedEnd,
    endDate,
  } = useAnalyticsPeriod();

  const [baselineCycles, setBaselineCycles] = useState(1);
  const [categoryMode, setCategoryMode] = useState('ALL');
  const [categorySort, setCategorySort] = useState('increase');
  const [resolvedAsOfDate, setResolvedAsOfDate] = useState({ periodType: null, date: null });
  const [resolvingAsOfDate, setResolvingAsOfDate] = useState(false);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function resolveAsOfDate() {
      if (!currentWallet?.id) return;
      if (periodType === 'PAY_CYCLE') {
        setResolvingAsOfDate(false);
        setResolvedAsOfDate({ periodType, date: null });
        return;
      }
      if (periodType === 'MONTHLY') {
        setResolvingAsOfDate(false);
        setResolvedAsOfDate({ periodType, date: endOfMonth(selectedMonth) });
        return;
      }
      if (periodType === 'CUSTOM') {
        setResolvingAsOfDate(false);
        setResolvedAsOfDate({ periodType, date: endDate || resolvedEnd || null });
        return;
      }
      if (periodType === 'LAST_PAY_CYCLE') {
        setResolvedAsOfDate({ periodType, date: null });
        setResolvingAsOfDate(true);
        try {
          const period = await analyticsApi.resolvePeriod(currentWallet.id, periodType, null, null);
          if (!cancelled) setResolvedAsOfDate({ periodType, date: period?.endDate ?? null });
        } catch {
          if (!cancelled) setResolvedAsOfDate({ periodType, date: null });
        } finally {
          if (!cancelled) setResolvingAsOfDate(false);
        }
      }
    }

    resolveAsOfDate();
    return () => { cancelled = true; };
  }, [currentWallet?.id, periodType, selectedMonth, endDate, resolvedEnd]);

  const fetchComparison = useCallback(async () => {
    if (!currentWallet?.id || resolvingAsOfDate) return;
    if (periodType === 'LAST_PAY_CYCLE' && resolvedAsOfDate.periodType !== periodType) return;
    if (periodType === 'LAST_PAY_CYCLE' && !resolvedAsOfDate.date) return;
    setLoading(true);
    setError(null);
    try {
      const result = await analyticsApi.getCycleComparison(currentWallet.id, {
        asOfDate: resolvedAsOfDate.periodType === periodType ? resolvedAsOfDate.date : null,
        baselineCycles,
        categoryMode,
      });
      setData(result);
    } catch (err) {
      console.error('Failed to fetch cycle comparison:', err);
      setError(err.message || t('cycleComparisonErrorLoading'));
    } finally {
      setLoading(false);
    }
  }, [currentWallet?.id, resolvingAsOfDate, resolvedAsOfDate, periodType, baselineCycles, categoryMode, t]);

  useEffect(() => {
    fetchComparison();
  }, [fetchComparison]);

  const summaryCards = useMemo(() => {
    if (!data) return [];
    const statusMeta = STATUS_META[data.summary?.status] ?? STATUS_META.IN_LINE;
    const deltaPercent = formatPercent(data.summary?.deltaPercent);
    const cycle = data.currentCycle ?? {};
    return [
      {
        label: t('cycleComparisonSpentSoFar'),
        value: formatCurrency(toNumber(data.summary?.currentExpenses), locale),
        subtext: t('cycleComparisonCalculatedUntil', {
          date: formatDate(cycle.cutoffDate, locale),
        }),
        icon: TrendingUp,
        color: '#f87171',
      },
      {
        label: comparisonTitle(baselineCycles, t),
        value: formatCurrency(toNumber(data.summary?.baselineAverageExpenses), locale),
        subtext: comparisonSubtext(baselineCycles, t),
        icon: GitCompareArrows,
        color: '#a78bfa',
      },
      {
        label: t('cycleComparisonDifference'),
        value: formatDeltaAmount(data.summary?.deltaAmount, locale),
        subtext: deltaPercent ?? statusLabel(data.summary?.status, t),
        icon: statusMeta.icon,
        color: statusMeta.color,
      },
      {
        label: t('cycleComparisonProgress'),
        value: t('cycleComparisonDayOfTotal', {
          day: cycle.elapsedDays ?? 0,
          total: cycle.totalDays ?? 0,
        }),
        subtext: t('cycleComparisonCycleRange', {
          start: formatDate(cycle.startDate, locale),
          end: formatDate(cycle.endDate, locale),
        }),
        icon: CalendarDays,
        color: '#60a5fa',
      },
    ];
  }, [baselineCycles, data, locale, t]);

  const noBaseline = data && (!data.baseline?.available || (data.baseline?.cyclesUsed ?? 0) === 0);
  const noTransactions = data
    && toNumber(data.summary?.currentExpenses) === 0
    && (data.categories?.length ?? 0) === 0;
  const largestIncrease = data?.highlights?.largestIncrease ?? [];
  const largestDecrease = data?.highlights?.largestDecrease ?? [];
  const hasHighlights = largestIncrease.length > 0 || largestDecrease.length > 0;

  return (
    <div style={{ animation: 'fadeUp 0.35s ease both' }}>
      <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3 mb-5">
        <div className="flex flex-col gap-1.5 min-w-0">
          <div className="flex flex-col sm:flex-row sm:items-center gap-2 min-w-0">
            <span className="text-[11px] font-semibold text-white/35 whitespace-nowrap">
              {t('cycleComparisonCompareAgainst')}
            </span>
            <div className="flex items-center bg-[#0e0e1c] border border-white/[0.055] rounded-xl p-1 gap-0.5 overflow-x-auto max-w-full">
              {COMPARE_OPTIONS.map((option) => (
                <button
                  key={option.cycles}
                  onClick={() => setBaselineCycles(option.cycles)}
                  className={`px-3 py-1.5 rounded-[9px] text-xs font-semibold transition-all whitespace-nowrap ${
                    baselineCycles === option.cycles
                      ? 'bg-purple-600 text-white shadow-[0_2px_12px_rgba(124,58,237,0.3)]'
                      : 'text-white/30 hover:text-white/60'
                  }`}
                >
                  {t(option.labelKey)}
                </button>
              ))}
            </div>
          </div>
          {data?.asOfDate && data?.currentCycle?.cutoffDate && (
            <div className="text-[12px] text-white/35">
              {t('cycleComparisonAsOfCopy', {
                requested: formatDate(data.asOfDate, locale),
                cutoff: formatDate(data.currentCycle.cutoffDate, locale),
              })}
            </div>
          )}
        </div>
        <div className="flex flex-col sm:flex-row sm:items-center gap-2 lg:justify-end">
          <span className="text-[11px] font-semibold text-white/35 whitespace-nowrap">
            {t('cycleComparisonCategoriesControl')}
          </span>
          <button
            onClick={() => setCategoryMode(mode => mode === 'ALL' ? 'INCLUDED_IN_TOP_CATEGORIES' : 'ALL')}
            className={`px-3 py-2 rounded-xl border text-xs font-semibold transition-colors whitespace-nowrap ${
              categoryMode === 'INCLUDED_IN_TOP_CATEGORIES'
                ? 'bg-violet-500/15 border-violet-400/25 text-violet-200'
                : 'bg-[#0e0e1c] border-white/[0.055] text-white/35 hover:text-white/60'
            }`}
          >
            {categoryMode === 'INCLUDED_IN_TOP_CATEGORIES'
              ? t('cycleComparisonIncludedOnly')
              : t('cycleComparisonAllCategories')}
          </button>
        </div>
      </div>

      {(loading || resolvingAsOfDate) && (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-5">
          {[0, 1, 2, 3].map((item) => (
            <div key={item} className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl h-32 animate-pulse" />
          ))}
        </div>
      )}

      {error && !loading && (
        <div className="flex flex-col items-center gap-4 py-10 text-center mb-5">
          <AlertCircle className="w-9 h-9 text-red-400" />
          <p className="text-white/60 text-sm max-w-xl">{error}</p>
          <button
            onClick={fetchComparison}
            className="inline-flex items-center gap-2 px-4 py-2 bg-violet-600 text-white text-xs rounded-lg hover:bg-violet-700 transition-colors"
          >
            <RefreshCcw className="w-3.5 h-3.5" />
            {t('retry')}
          </button>
        </div>
      )}

      {!loading && !error && data && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-5">
            {summaryCards.map((card) => (
              <SummaryCard key={card.label} {...card} />
            ))}
          </div>

          {noBaseline && (
            <div className="bg-amber-500/[0.07] border border-amber-400/15 rounded-xl p-5 mb-5">
              <div className="text-sm font-semibold text-amber-200 mb-1">
                {t('cycleComparisonNoBaselineTitle')}
              </div>
              <p className="text-sm text-amber-100/45">
                {t('cycleComparisonNoBaselineDesc')}
              </p>
            </div>
          )}

          {noTransactions && (
            <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5">
              <div className="text-sm font-semibold text-white/70 mb-1">
                {t('cycleComparisonNoSpendTitle')}
              </div>
              <p className="text-sm text-white/35">
                {t('cycleComparisonNoSpendDesc')}
              </p>
            </div>
          )}

          <div className="mb-5">
            <CumulativeChart data={data} baselineCycles={baselineCycles} locale={locale} t={t} />
          </div>

          {hasHighlights && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-5">
              <HighlightCard
                title={t('cycleComparisonLargestIncrease')}
                items={largestIncrease}
                locale={locale}
                t={t}
              />
              <HighlightCard
                title={t('cycleComparisonLargestDecrease')}
                items={largestDecrease}
                locale={locale}
                t={t}
              />
            </div>
          )}

          <CategoryDeltas
            categories={data.categories ?? []}
            baselineAvailable={!noBaseline}
            baselineCycles={baselineCycles}
            sortMode={categorySort}
            setSortMode={setCategorySort}
            locale={locale}
            t={t}
          />

          {Array.isArray(data.insights) && data.insights.length > 0 && (
            <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mt-5">
              <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-3">
                {t('insightsTitle')}
              </div>
              <div className="space-y-2">
                {data.insights.map((insight, index) => (
                  <p key={insight.id ?? index} className="text-sm text-white/55">
                    {insight.message ?? insight.title}
                  </p>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
