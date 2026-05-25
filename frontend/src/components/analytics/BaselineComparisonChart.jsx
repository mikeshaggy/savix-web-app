'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { TrendingUp, AlertCircle } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { analyticsApi } from '@/lib/api';
import { formatCurrency } from '@/utils/helpers';

// ─── helpers ──────────────────────────────────────────────────────────────────

function fmtPct(v) {
  if (v == null) return '—';
  return `${Number(v).toFixed(2)}%`;
}

function deltaVariant(deltaPercent, deltaAvailable, isExpenses) {
  if (!deltaAvailable || deltaPercent == null) return 'neutral';
  if (deltaPercent === 0) return 'neutral';
  const isGood = isExpenses ? deltaPercent < 0 : deltaPercent > 0;
  return isGood ? 'positive' : 'negative';
}

const BADGE_CLASS = {
  positive: 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20',
  negative: 'bg-red-500/15 text-red-400 border border-red-400/20',
  neutral:  'bg-white/[0.06] text-white/35 border border-white/[0.07]',
};

const INTERP_CLASS = {
  positive: 'text-emerald-400/70',
  negative: 'text-red-400/60',
  neutral:  'text-white/25',
};

// ─── comparison bar ───────────────────────────────────────────────────────────

function ComparisonBar({ current, baseline, color, nowLabel, avgLabel }) {
  const safeMax = Math.max(current, baseline, 0.01);
  const currentPct = Math.min(100, (current / safeMax) * 100);
  const baselinePct = Math.min(100, (baseline / safeMax) * 100);

  return (
    <div className="flex flex-col gap-1.5 mt-3 pt-3 border-t border-white/[0.05]">
      <div className="flex items-center gap-2">
        <span className="text-[9px] font-mono text-white/25 w-8 flex-shrink-0 uppercase tracking-wide">
          {nowLabel}
        </span>
        <div className="flex-1 h-1.5 bg-white/[0.05] rounded-full overflow-hidden">
          <div
            className="h-full rounded-full"
            style={{ width: `${currentPct}%`, backgroundColor: color }}
          />
        </div>
      </div>
      <div className="flex items-center gap-2">
        <span className="text-[9px] font-mono text-white/25 w-8 flex-shrink-0 uppercase tracking-wide">
          {avgLabel}
        </span>
        <div className="flex-1 h-1.5 bg-white/[0.05] rounded-full overflow-hidden">
          <div
            className="h-full rounded-full"
            style={{ width: `${baselinePct}%`, backgroundColor: color, opacity: 0.28 }}
          />
        </div>
      </div>
    </div>
  );
}

// ─── metric card ──────────────────────────────────────────────────────────────

function MetricCard({
  label, current, baseline, deltaDisplay, deltaPercent, deltaAvailable,
  isExpenses, formatValue, color, interpretation, showBaseline, nowLabel, avgLabel,
}) {
  const variant = deltaVariant(deltaPercent, deltaAvailable, isExpenses);

  return (
    <div className="flex flex-col p-4 bg-white/[0.02] rounded-xl border border-white/[0.05]">
      <div className="flex items-start justify-between gap-2 mb-2">
        <p className="text-[9px] font-bold tracking-[0.1em] uppercase text-white/25 mt-0.5 leading-tight">
          {label}
        </p>
        <span className={`inline-block text-[11px] font-mono font-semibold px-2 py-0.5 rounded-md flex-shrink-0 ${BADGE_CLASS[variant]}`}>
          {deltaDisplay}
        </span>
      </div>

      <p className="text-[15px] font-mono font-semibold leading-tight truncate" style={{ color }}>
        {formatValue(current)}
      </p>

      {showBaseline && (
        <p className="text-[10px] text-white/25 mt-1 truncate">
          {avgLabel.toLowerCase()}: {formatValue(baseline)}
        </p>
      )}

      {interpretation && (
        <p className={`text-[10px] font-medium mt-1 leading-snug ${INTERP_CLASS[variant]}`}>
          {interpretation}
        </p>
      )}

      {showBaseline && (
        <ComparisonBar
          current={typeof current === 'number' ? current : 0}
          baseline={typeof baseline === 'number' ? baseline : 0}
          color={color}
          nowLabel={nowLabel}
          avgLabel={avgLabel}
        />
      )}
    </div>
  );
}

// ─── auto summary ─────────────────────────────────────────────────────────────

function buildSummary(data, t) {
  if (!data) return null;

  const expDelta     = data.expensesDeltaPercent      ?? null;
  const incomeDelta  = data.incomeDeltaPercent        ?? null;
  const savingsDelta = data.savingsRateDeltaPercent   ?? null;
  const expAvail     = data.expensesDeltaAvailable    ?? false;
  const incAvail     = data.incomeDeltaAvailable      ?? false;
  const savAvail     = data.savingsRateDeltaAvailable ?? false;

  if (!expAvail && !incAvail) return t('baselineSummaryNoData');

  if (expAvail && expDelta !== null && expDelta < -2 &&
      savAvail && savingsDelta !== null && savingsDelta > 2) {
    return t('baselineSummaryGood');
  }
  if (expAvail && expDelta !== null && expDelta > 5) {
    return t('baselineSummaryExpensesHigh');
  }
  if (incAvail && incomeDelta !== null && incomeDelta < -5) {
    return t('baselineSummaryIncomeLow');
  }
  return null;
}

// ─── main component ───────────────────────────────────────────────────────────

export default function BaselineComparisonChart({ walletId, periodType, startDate, endDate }) {
  const t = useTranslations('analytics');

  const [data,    setData]    = useState(null);
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState(null);

  const fetchBaseline = useCallback(async (wId, pType, sDate, eDate) => {
    setLoading(true);
    setError(null);
    try {
      const result = await analyticsApi.getBaseline(wId, pType, sDate, eDate);
      setData(result);
    } catch (err) {
      console.error('Failed to fetch baseline:', err);
      setError(err.message || t('baselineErrorLoading'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!walletId || !periodType) return;
    if (periodType === 'CUSTOM' && (!startDate || !endDate)) return;
    fetchBaseline(walletId, periodType, startDate, endDate);
  }, [walletId, periodType, startDate, endDate, fetchBaseline]);

  if (loading) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5 animate-pulse h-44" />
    );
  }

  if (error) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5">
        <div className="flex flex-col items-center gap-3 py-6 text-center">
          <AlertCircle className="w-7 h-7 text-red-400" />
          <p className="text-sm text-white/50">{error}</p>
          <button
            onClick={() => fetchBaseline(walletId, periodType, startDate, endDate)}
            className="px-3 py-1.5 bg-violet-600 text-white text-xs rounded-lg hover:bg-violet-700 transition-colors"
          >
            {t('retry')}
          </button>
        </div>
      </div>
    );
  }

  if (!data) return null;

  const currentIncome        = data.currentIncome        ?? 0;
  const currentExpenses      = data.currentExpenses      ?? 0;
  const currentSavingsRate   = data.currentSavingsRate   ?? 0;
  const baselineIncome       = data.baselineIncome       ?? 0;
  const baselineExpenses     = data.baselineExpenses     ?? 0;
  const baselineSavingsRate  = data.baselineSavingsRate  ?? 0;

  const incomeDeltaDisplay        = data.incomeDeltaDisplay        ?? 'N/A';
  const incomeDeltaPercent        = data.incomeDeltaPercent        ?? null;
  const incomeDeltaAvailable      = data.incomeDeltaAvailable      ?? false;
  const expensesDeltaDisplay      = data.expensesDeltaDisplay      ?? 'N/A';
  const expensesDeltaPercent      = data.expensesDeltaPercent      ?? null;
  const expensesDeltaAvailable    = data.expensesDeltaAvailable    ?? false;
  const savingsRateDeltaDisplay   = data.savingsRateDeltaDisplay   ?? 'N/A';
  const savingsRateDeltaPercent   = data.savingsRateDeltaPercent   ?? null;
  const savingsRateDeltaAvailable = data.savingsRateDeltaAvailable ?? false;

  // hasBaseline: at least one delta was computable (compare period had data)
  const hasBaseline = incomeDeltaAvailable || expensesDeltaAvailable;

  // Empty: no historical baseline and no current-month data
  if (!hasBaseline && currentIncome === 0 && currentExpenses === 0) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5">
        <div className="flex items-center gap-2 mb-2">
          <TrendingUp className="w-4 h-4 text-violet-400 flex-shrink-0" />
          <span className="text-xs font-bold tracking-[0.12em] uppercase text-white/35">
            {t('baselineTitle')}
          </span>
        </div>
        <p className="text-[12px] text-white/25 ml-6">{t('baselineEmpty')}</p>
      </div>
    );
  }

  // Interpretation texts
  const incomeInterp = (() => {
    if (!incomeDeltaAvailable || incomeDeltaPercent == null) return null;
    if (Math.abs(incomeDeltaPercent) < 1) return t('baselineIncomeNormal');
    return incomeDeltaPercent > 0 ? t('baselineIncomeAbove') : t('baselineIncomeBelow');
  })();

  const expensesInterp = (() => {
    if (!expensesDeltaAvailable || expensesDeltaPercent == null) return null;
    if (Math.abs(expensesDeltaPercent) < 1) return t('baselineExpensesNormal');
    return expensesDeltaPercent > 0 ? t('baselineExpensesAbove') : t('baselineExpensesBelow');
  })();

  const savingsInterp = (() => {
    if (!savingsRateDeltaAvailable || savingsRateDeltaPercent == null) return null;
    if (Math.abs(savingsRateDeltaPercent) < 1) return t('baselineSavingsNormal');
    return savingsRateDeltaPercent > 0 ? t('baselineSavingsAbove') : t('baselineSavingsBelow');
  })();

  const summary    = buildSummary(data, t);
  const nowLabel   = t('baselineCurrent');
  const avgBarLabel = t('baselineAverage');

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5">

      {/* header */}
      <div className="flex items-center gap-2 mb-0.5">
        <TrendingUp className="w-4 h-4 text-violet-400 flex-shrink-0" />
        <span className="text-xs font-bold tracking-[0.12em] uppercase text-white/35">
          {t('baselineTitle')}
        </span>
      </div>
      <p className="text-[12px] text-white/25 mb-4 ml-6">
        {t('baselineSubtitle')}
      </p>

      {/* auto-generated summary sentence */}
      {summary && (
        <div className="bg-violet-500/[0.06] border border-violet-500/10 rounded-lg px-3 py-2 mb-4">
          <p className="text-[11px] text-violet-300/60 leading-snug">{summary}</p>
        </div>
      )}

      {/* 3 metric cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <MetricCard
          label={t('income')}
          current={currentIncome}
          baseline={baselineIncome}
          deltaDisplay={incomeDeltaDisplay}
          deltaPercent={incomeDeltaPercent}
          deltaAvailable={incomeDeltaAvailable}
          isExpenses={false}
          formatValue={formatCurrency}
          color="#818cf8"
          interpretation={incomeInterp}
          showBaseline={hasBaseline}
          nowLabel={nowLabel}
          avgLabel={avgBarLabel}
        />
        <MetricCard
          label={t('expenses')}
          current={currentExpenses}
          baseline={baselineExpenses}
          deltaDisplay={expensesDeltaDisplay}
          deltaPercent={expensesDeltaPercent}
          deltaAvailable={expensesDeltaAvailable}
          isExpenses={true}
          formatValue={formatCurrency}
          color="#f87171"
          interpretation={expensesInterp}
          showBaseline={hasBaseline}
          nowLabel={nowLabel}
          avgLabel={avgBarLabel}
        />
        <MetricCard
          label={t('savingsRate')}
          current={currentSavingsRate}
          baseline={baselineSavingsRate}
          deltaDisplay={savingsRateDeltaDisplay}
          deltaPercent={savingsRateDeltaPercent}
          deltaAvailable={savingsRateDeltaAvailable}
          isExpenses={false}
          formatValue={fmtPct}
          color="#4ade80"
          interpretation={savingsInterp}
          showBaseline={hasBaseline}
          nowLabel={nowLabel}
          avgLabel={avgBarLabel}
        />
      </div>
    </div>
  );
}
