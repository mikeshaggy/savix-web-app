'use client';
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { BarChart2, AlertCircle, MousePointerClick } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { analyticsApi } from '@/lib/api';
import { formatCurrency } from '@/utils/helpers';

// ─── chart constants ──────────────────────────────────────────────────────────

const CHART_H  = 128; // drawable bar area height (px) — taller for more visual impact
const BAR_MINH = 5;   // minimum bar height for non-zero days
const ZERO_H   = 2;   // stub height for zero-spend days

// ─── chart helpers ────────────────────────────────────────────────────────────

// sqrt scaling keeps low-spend days visible when one day dominates
function sqrtBarH(total, maxDayTotal) {
  if (total <= 0 || maxDayTotal <= 0) return 0;
  return Math.round(BAR_MINH + Math.sqrt(total / maxDayTotal) * (CHART_H - BAR_MINH));
}

function barColor(entry, selectedKey, hoveredKey, maxDayTotal) {
  if (entry.total <= 0) return 'rgba(255,255,255,0.04)';
  if (entry.date === selectedKey) return '#a78bfa';           // selected — violet
  if (entry.date === hoveredKey)  return 'rgba(167,139,250,0.5)'; // hovered — dim violet
  if (Number(entry.total) === Number(maxDayTotal)) return '#5b21b6'; // peak — deep violet
  return '#2e1d5e';                                           // normal — dark violet
}

// ─── stats ────────────────────────────────────────────────────────────────────

function computeStats(days) {
  const arr = days ?? [];
  const active = arr.filter((d) => Number(d.total) > 0);
  const totalAmount = arr.reduce((s, d) => s + Number(d.total ?? 0), 0);
  const highestDay  = active.length
    ? active.reduce((a, b) => Number(b.total) > Number(a.total) ? b : a)
    : null;
  return {
    highestDay,
    avgActive:    active.length ? totalAmount / active.length : 0,
    avgCalendar:  arr.length    ? totalAmount / arr.length    : 0,
    activeDays:   active.length,
    noSpendDays:  arr.length - active.length,
    totalDays:    arr.length,
  };
}

// ─── date formatters ─────────────────────────────────────────────────────────

function parseDate(str) {
  return new Date(str + 'T12:00:00');
}

function fmtShort(str)  {
  try { return parseDate(str).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }); }
  catch { return str; }
}
function fmtMedium(str) {
  try { return parseDate(str).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' }); }
  catch { return str; }
}
function fmtLong(str)   {
  try { return parseDate(str).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' }); }
  catch { return str; }
}
function fmtRange(start, end) {
  if (!start) return '';
  const s = fmtShort(start);
  const e = end ? fmtShort(end) : '';
  return e && e !== s ? `${s} – ${e}` : s;
}

// ─── colour palette for categories ───────────────────────────────────────────

const CAT_PALETTE = [
  '#a78bfa', '#4ade80', '#facc15', '#fb923c',
  '#60a5fa', '#f87171', '#34d399', '#38bdf8',
];
function catColor(i) { return CAT_PALETTE[i % CAT_PALETTE.length]; }

// ─── KPI chip ─────────────────────────────────────────────────────────────────

function KpiChip({ label, value, sub, color }) {
  return (
    <div className="bg-white/[0.03] border border-white/[0.05] rounded-xl px-3 py-2.5 min-w-0 flex flex-col gap-1">
      <p className="text-[9px] font-bold tracking-[0.1em] uppercase text-white/25 truncate">{label}</p>
      <p className="font-mono text-[14px] font-bold leading-tight truncate" style={{ color }}>
        {value}
      </p>
      {sub && <p className="text-[10px] text-white/25 leading-tight truncate">{sub}</p>}
    </div>
  );
}

// ─── Bar chart ────────────────────────────────────────────────────────────────

function DailyChart({ days, maxDayTotal, selectedKey, onSelect }) {
  const [hovered, setHovered] = useState(null);
  const n = days.length;

  // show tick labels at sensible intervals depending on period length
  const ticks = useMemo(() => {
    const step = n > 25 ? 5 : n > 14 ? 3 : 2;
    const s = new Set([1]);
    for (let i = step; i < n; i += step) s.add(i);
    s.add(n);
    return s;
  }, [n]);

  const ttPct     = hovered ? ((hovered.idx + 0.5) / n) * 100 : 50;
  const ttClamped = Math.max(5, Math.min(95, ttPct));

  return (
    <div className="relative select-none">
      {/* bar row */}
      <div
        className="flex items-end gap-[2px]"
        style={{ height: CHART_H }}
        onMouseLeave={() => setHovered(null)}
      >
        {days.map((entry, idx) => {
          const h    = entry.total > 0 ? sqrtBarH(entry.total, maxDayTotal) : ZERO_H;
          const fill = barColor(entry, selectedKey, hovered?.date, maxDayTotal);
          const isSelected = entry.date === selectedKey;
          return (
            <button
              key={entry.date}
              className={`flex-1 rounded-t-[2px] transition-colors duration-75 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-violet-400 ${
                isSelected ? 'ring-1 ring-violet-400/40' : ''
              }`}
              style={{ height: `${h}px`, background: fill, minWidth: 0 }}
              onClick={() => onSelect(entry.date)}
              onMouseEnter={() => setHovered({ ...entry, idx })}
              aria-label={fmtMedium(entry.date)}
              aria-pressed={isSelected}
            />
          );
        })}
      </div>

      {/* hover tooltip */}
      {hovered && Number(hovered.total) > 0 && (
        <div
          className="absolute pointer-events-none z-10"
          style={{
            bottom: '100%',
            left: `${ttClamped}%`,
            transform: 'translateX(-50%)',
            marginBottom: 6,
          }}
        >
          <div className="bg-[#1c1a2e] border border-white/[0.14] rounded-lg px-2.5 py-1.5 shadow-xl whitespace-nowrap">
            <p className="text-[10px] text-white/40 leading-none mb-1">{fmtMedium(hovered.date)}</p>
            <p className="text-[12px] font-mono font-semibold text-violet-300 leading-none">
              {formatCurrency(hovered.total)}
            </p>
            <p className="text-[10px] text-white/30 leading-none mt-1">
              {hovered.transactions}×
            </p>
          </div>
        </div>
      )}

      {/* x-axis tick labels */}
      <div className="flex gap-[2px] mt-1">
        {days.map((entry, idx) => (
          <div key={entry.date} className="flex-1 overflow-hidden text-center">
            {ticks.has(idx + 1) && (
              <span className="text-[9px] font-mono text-white/20 leading-none">{idx + 1}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Day category list (left panel) ──────────────────────────────────────────

function DayCategoryPanel({ dayData, t }) {
  if (!dayData || Number(dayData.total) <= 0) {
    return (
      <div>
        <p className="text-[9px] font-bold tracking-[0.1em] uppercase text-white/25 mb-3">
          {t('dailyDayCategories')}
        </p>
        <p className="text-[12px] text-white/25 py-1">{t('heatmapNoTransactions')}</p>
      </div>
    );
  }

  const { categories, total } = dayData;

  return (
    <div>
      <p className="text-[9px] font-bold tracking-[0.1em] uppercase text-white/25 mb-3">
        {t('dailyDayCategories')}
      </p>
      <div className="space-y-3">
        {categories.map((cat, i) => {
          const pct   = Number(total) > 0 ? (Number(cat.amount) / Number(total)) * 100 : 0;
          const color = catColor(i);
          return (
            <div key={cat.categoryId}>
              <div className="flex items-center justify-between gap-2 mb-1.5">
                <div className="flex items-center gap-2 min-w-0">
                  {cat.emoji && (
                    <span className="text-[14px] leading-none flex-shrink-0">{cat.emoji}</span>
                  )}
                  <span className="text-[12px] text-white/60 truncate">{cat.categoryName}</span>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                  <span className="text-[10px] font-mono text-white/30">{pct.toFixed(0)}%</span>
                  <span className="text-[12px] font-mono font-medium" style={{ color }}>
                    {formatCurrency(cat.amount)}
                  </span>
                </div>
              </div>
              <div className="h-[3px] bg-white/[0.05] rounded-full overflow-hidden">
                <div
                  className="h-full rounded-full transition-[width] duration-300"
                  style={{ width: `${pct}%`, background: color }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Day summary panel (right panel) ─────────────────────────────────────────

function DaySummaryPanel({ dayData, t }) {
  if (!dayData) return null;

  const hasSpend = Number(dayData.total) > 0;
  const topCat   = dayData.categories?.[0] ?? null;
  const topShare = topCat && Number(dayData.total) > 0
    ? (Number(topCat.amount) / Number(dayData.total)) * 100
    : 0;

  return (
    <div className="flex flex-col gap-3">
      <p className="text-[9px] font-bold tracking-[0.1em] uppercase text-white/25">
        {t('dailyDaySummary')}
      </p>

      {!hasSpend ? (
        <p className="text-[12px] text-white/25">{t('heatmapNoTransactions')}</p>
      ) : (
        <>
          {/* Total + txn count */}
          <div className="flex items-center justify-between gap-3">
            <span className="text-[11px] text-white/35">{t('dailyDayTotal')}</span>
            <span className="text-[18px] font-mono font-bold text-violet-300 leading-tight">
              {formatCurrency(dayData.total)}
            </span>
          </div>

          <div className="flex items-center justify-between gap-3">
            <span className="text-[11px] text-white/35">{t('heatmapTransactions')}</span>
            <span className="text-[13px] font-mono text-white/60">
              {t('dailyDayTransactions', { count: dayData.transactions })}
            </span>
          </div>

          {/* Top category */}
          {topCat && (
            <div className="pt-2 border-t border-white/[0.05]">
              <p className="text-[9px] font-bold tracking-[0.1em] uppercase text-white/25 mb-2">
                {t('dailyDayTopCategory')}
              </p>
              <div className="flex items-center gap-2.5">
                {topCat.emoji && (
                  <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 text-[15px] bg-violet-500/15">
                    {topCat.emoji}
                  </div>
                )}
                <div className="min-w-0">
                  <p className="text-[12px] font-medium text-white/70 truncate">{topCat.categoryName}</p>
                  <p className="text-[11px] text-white/30 mt-0.5">
                    {t('dailyDayShareOfDay', { share: topShare.toFixed(0) })}
                    {' · '}
                    <span className="font-mono">{formatCurrency(topCat.amount)}</span>
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Transaction-level placeholder */}
          <div className="pt-2 border-t border-white/[0.05] mt-auto">
            <p className="text-[10px] text-white/18 italic leading-relaxed">
              {t('dailyDayTxPlaceholder')}
            </p>
          </div>
        </>
      )}
    </div>
  );
}

// ─── Selected day header ──────────────────────────────────────────────────────

function SelectedDayHeader({ dayData, t }) {
  if (!dayData) {
    return (
      <div className="flex items-center gap-2 text-white/25 mt-4 pt-3 border-t border-white/[0.05]">
        <MousePointerClick className="w-3.5 h-3.5 flex-shrink-0" />
        <p className="text-[12px]">{t('dailySelectHint')}</p>
      </div>
    );
  }
  return (
    <div className="flex items-center justify-between gap-3 mt-4 pt-3 border-t border-white/[0.05] mb-4">
      <p className="text-[13px] font-semibold text-white/65 leading-tight">
        {fmtLong(dayData.date)}
      </p>
    </div>
  );
}

// ─── main export ─────────────────────────────────────────────────────────────

export default function SpendingHeatmap({ walletId, periodType, startDate, endDate }) {
  const t = useTranslations('analytics');

  const [heatmapData,    setHeatmapData]    = useState(null);
  const [loading,        setLoading]        = useState(false);
  const [error,          setError]          = useState(null);
  const [selectedDayKey, setSelectedDayKey] = useState(null);

  const fetchHeatmap = useCallback(async (wId, pType, sDate, eDate) => {
    setLoading(true);
    setError(null);
    try {
      const result = await analyticsApi.getHeatmap(wId, pType, sDate, eDate);
      setHeatmapData(result);
      // Auto-select the highest spending day
      if (result.maxDayTotal > 0 && result.days?.length) {
        const peak = result.days.reduce((a, b) => (Number(b.total) > Number(a.total) ? b : a));
        setSelectedDayKey(peak.date);
      } else {
        setSelectedDayKey(null);
      }
    } catch (err) {
      console.error('Failed to fetch heatmap:', err);
      setError(err.message || t('heatmapErrorLoading'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!walletId || !periodType) return;
    if (periodType === 'CUSTOM' && (!startDate || !endDate)) return;
    fetchHeatmap(walletId, periodType, startDate, endDate);
  }, [walletId, periodType, startDate, endDate, fetchHeatmap]);

  const days        = heatmapData?.days ?? [];
  const maxDayTotal = heatmapData?.maxDayTotal ?? 0;
  const stats       = useMemo(() => computeStats(days), [days]);
  const hasData     = maxDayTotal > 0;
  const hasLoaded   = heatmapData !== null;

  const selectedDayData = selectedDayKey
    ? (days.find((d) => d.date === selectedDayKey) ?? null)
    : null;

  const handleSelect = useCallback((dateStr) => {
    setSelectedDayKey((prev) => (prev === dateStr ? null : dateStr));
  }, []);

  const periodLabel = heatmapData
    ? fmtRange(heatmapData.startDate, heatmapData.endDate)
    : (startDate ? fmtRange(startDate, endDate) : '');

  // ── loading skeleton ─────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5 space-y-4">
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2">
          {[0,1,2,3,4].map((i) => (
            <div key={i} className="h-16 rounded-xl bg-white/[0.03] animate-pulse" />
          ))}
        </div>
        <div className="h-36 rounded-lg bg-white/[0.02] animate-pulse" />
      </div>
    );
  }

  // ── main render ──────────────────────────────────────────────────────────────
  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5">

      {/* ── Card header ──────────────────────────────────────────────────────── */}
      <div className="flex items-center gap-2 mb-4">
        <BarChart2 className="w-4 h-4 text-violet-400 flex-shrink-0" />
        <span className="text-xs font-bold tracking-[0.12em] uppercase text-white/35">
          {t('timelineTitle')}
        </span>
        {periodLabel && (
          <span className="ml-auto text-xs font-mono text-white/20">{periodLabel}</span>
        )}
      </div>

      {/* ── Error state ───────────────────────────────────────────────────────── */}
      {error && (
        <div className="flex flex-col items-center gap-3 py-10 text-center">
          <AlertCircle className="w-7 h-7 text-red-400/70" />
          <p className="text-sm text-white/40">{error}</p>
          <button
            onClick={() => fetchHeatmap(walletId, periodType, startDate, endDate)}
            className="px-3 py-1.5 bg-violet-600/80 text-white text-xs rounded-lg hover:bg-violet-600 transition-colors"
          >
            {t('retry')}
          </button>
        </div>
      )}

      {!error && (
        <>
          {/* ── KPI chips ─────────────────────────────────────────────────────── */}
          {hasData && (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2 mb-5">
              <KpiChip
                label={t('dailyKpiHighest')}
                value={formatCurrency(stats.highestDay?.total ?? 0)}
                sub={stats.highestDay ? fmtShort(stats.highestDay.date) : undefined}
                color="#a78bfa"
              />
              <KpiChip
                label={t('dailyKpiAvgActive')}
                value={formatCurrency(stats.avgActive)}
                color="#60a5fa"
              />
              <KpiChip
                label={t('dailyKpiAvgCalendar')}
                value={formatCurrency(stats.avgCalendar)}
                color="#94a3b8"
              />
              <KpiChip
                label={t('dailyKpiActiveDays')}
                value={String(stats.activeDays)}
                sub={t('dailyKpiActiveDaysSub', { total: stats.totalDays })}
                color="#4ade80"
              />
              <KpiChip
                label={t('dailyKpiNoSpend')}
                value={String(stats.noSpendDays)}
                sub={t('dailyKpiNoSpendSub', { total: stats.totalDays })}
                color="#fb923c"
              />
            </div>
          )}

          {/* ── Bar chart ────────────────────────────────────────────────────── */}
          {hasLoaded && days.length > 0 && (
            <DailyChart
              days={days}
              maxDayTotal={maxDayTotal}
              selectedKey={selectedDayKey}
              onSelect={handleSelect}
            />
          )}

          {/* ── No data empty state ───────────────────────────────────────────── */}
          {hasLoaded && !hasData && (
            <p className="text-[12px] text-white/25 text-center py-4">{t('noData')}</p>
          )}

          {/* ── Selected day section ─────────────────────────────────────────── */}
          {hasLoaded && (
            <>
              <SelectedDayHeader dayData={selectedDayData} t={t} />

              {selectedDayData && (
                <div className="grid grid-cols-1 md:grid-cols-[3fr_2fr] gap-5 md:gap-8 md:divide-x md:divide-white/[0.05]">
                  {/* Left: category breakdown */}
                  <DayCategoryPanel dayData={selectedDayData} t={t} />

                  {/* Right: at-a-glance summary */}
                  <div className="md:pl-8">
                    <DaySummaryPanel dayData={selectedDayData} t={t} />
                  </div>
                </div>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
}
