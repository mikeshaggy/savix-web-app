'use client';
import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { AlertTriangle, AlertCircle, Info, CheckCircle, Lightbulb, ChevronRight } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { analyticsApi } from '@/lib/api';

/**
 * @typedef {'INFO' | 'WARN' | 'ALERT'} InsightSeverity
 *
 * @typedef {'CATEGORY_SPIKE' | 'HIGH_IMPULSE_SPENDING' | 'SPENDING_PACE_ABOVE_BASELINE' | 'SAFE_TO_SPEND_WARNING' | string} InsightType
 *
 * @typedef {Object} Insight
 * @property {InsightType} type
 * @property {InsightSeverity} severity
 * @property {string} title
 * @property {string} description
 * @property {number | null} relatedCategoryId
 * @property {number | null} relatedAmount
 *
 * @typedef {Object} InsightResponse
 * @property {string} month
 * @property {Insight[]} insights
 */

// ─── severity config ──────────────────────────────────────────────────────────

const SEVERITY_CONFIG = {
  ALERT: {
    Icon: AlertCircle,
    cardClass: 'border-red-500/20 bg-red-500/[0.04]',
    iconBg: 'bg-red-500/15',
    iconColor: '#f87171',
  },
  WARN: {
    Icon: AlertTriangle,
    cardClass: 'border-amber-500/20 bg-amber-500/[0.04]',
    iconBg: 'bg-amber-500/15',
    iconColor: '#fbbf24',
  },
  INFO: {
    Icon: Info,
    cardClass: 'border-blue-500/20 bg-blue-500/[0.03]',
    iconBg: 'bg-blue-500/15',
    iconColor: '#60a5fa',
  },
};

// ─── page navigation targets ─────────────────────────────────────────────────
// Insights now link to the appropriate analytics subpage instead of
// scrolling to in-page anchors (which no longer exist after the split).

const PAGE_TARGETS = {
  CATEGORY_SPIKE:              '/analytics/breakdown',
  HIGH_IMPULSE_SPENDING:       '/analytics/breakdown',
  SPENDING_PACE_ABOVE_BASELINE:'/analytics/forecast',
  SAFE_TO_SPEND_WARNING:       '/analytics/forecast',
};

const ACTION_KEYS = {
  CATEGORY_SPIKE:              'insightActionCategorySpike',
  HIGH_IMPULSE_SPENDING:       'insightActionHighImpulse',
  SPENDING_PACE_ABOVE_BASELINE:'insightActionSpendingPace',
  SAFE_TO_SPEND_WARNING:       'insightActionSafeToSpend',
};

// ─── single insight card ──────────────────────────────────────────────────────

function InsightCard({ insight, t, searchParamsStr }) {
  const config = SEVERITY_CONFIG[insight.severity] ?? SEVERITY_CONFIG.INFO;
  const { Icon, cardClass, iconBg, iconColor } = config;
  const pageTarget = PAGE_TARGETS[insight.type];
  const actionKey  = ACTION_KEYS[insight.type];

  const actionHref = pageTarget
    ? (searchParamsStr ? `${pageTarget}?${searchParamsStr}` : pageTarget)
    : null;

  return (
    <div className={`flex gap-3 p-4 rounded-xl border ${cardClass}`}>
      <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5 ${iconBg}`}>
        <Icon className="w-4 h-4" style={{ color: iconColor }} />
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-[13px] font-semibold text-white/85 leading-tight mb-1">
          {insight.title}
        </p>
        <p className="text-[12px] text-white/50 leading-relaxed">
          {insight.description}
        </p>

        {actionHref && actionKey && (
          <Link
            href={actionHref}
            className="inline-flex items-center gap-1 mt-2 text-[11px] font-semibold text-violet-400/70 hover:text-violet-300 transition-colors"
          >
            {t(actionKey)}
            <ChevronRight className="w-3 h-3" />
          </Link>
        )}
      </div>
    </div>
  );
}

// ─── main component ───────────────────────────────────────────────────────────

export default function InsightCards({ walletId, periodType, startDate, endDate }) {
  const t = useTranslations('analytics');
  const searchParams = useSearchParams();
  const searchParamsStr = searchParams.toString();

  const [insights, setInsights] = useState(null);
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState(null);

  const fetchInsights = useCallback(async (wId, pType, sDate, eDate) => {
    setLoading(true);
    setError(null);
    try {
      const result = await analyticsApi.getInsights(wId, pType, sDate, eDate);
      setInsights(result?.insights ?? []);
    } catch (err) {
      console.error('Failed to fetch insights:', err);
      setError(err.message || t('insightsErrorLoading'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!walletId || !periodType) return;
    if (periodType === 'CUSTOM' && (!startDate || !endDate)) return;
    fetchInsights(walletId, periodType, startDate, endDate);
  }, [walletId, periodType, startDate, endDate, fetchInsights]);

  // ── loading skeleton
  if (loading) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5 animate-pulse h-32" />
    );
  }

  // ── error — non-blocking inline strip, does not break the page
  if (error) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl px-4 py-3 mb-5">
        <div className="flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-red-400/50 flex-shrink-0" />
          <p className="text-[12px] text-white/30">{t('insightsErrorLoading')}</p>
          <button
            onClick={() => fetchInsights(walletId, periodType, startDate, endDate)}
            className="ml-auto text-[11px] text-violet-400/60 hover:text-violet-300 transition-colors"
          >
            {t('retry')}
          </button>
        </div>
      </div>
    );
  }

  // ── not yet loaded (null) → nothing
  if (insights === null) return null;

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 mb-5">

      {/* header */}
      <div className="flex items-center gap-2 mb-0.5">
        <Lightbulb className="w-4 h-4 text-amber-400 flex-shrink-0" />
        <span className="text-xs font-bold tracking-[0.12em] uppercase text-white/35">
          {t('insightsTitle')}
        </span>
        <span
          className="inline-block text-[9px] font-bold tracking-[0.08em] uppercase px-1.5 py-0.5 rounded-md bg-violet-500/15 text-violet-400/70 border border-violet-500/20 ml-1"
          title="Insight thresholds are rule-based and may not apply to all spending patterns"
        >
          Beta
        </span>
      </div>
      <p className="text-[12px] text-white/25 mb-4 ml-6">
        {t('insightsSubtitle')}
      </p>

      {/* empty state */}
      {insights.length === 0 && (
        <div className="flex items-start gap-3 py-1">
          <CheckCircle className="w-5 h-5 text-emerald-400/40 flex-shrink-0 mt-0.5" />
          <div>
            <p className="text-[13px] font-medium text-white/50">{t('insightsEmpty')}</p>
            <p className="text-[12px] text-white/25 mt-0.5">{t('insightsEmptyDesc')}</p>
          </div>
        </div>
      )}

      {/* insight list */}
      {insights.length > 0 && (
        <div className="flex flex-col gap-2.5">
          {insights.map((insight, i) => (
            <InsightCard
              key={`${insight.type}-${insight.severity}-${i}`}
              insight={insight}
              t={t}
              searchParamsStr={searchParamsStr}
            />
          ))}
        </div>
      )}
    </div>
  );
}
