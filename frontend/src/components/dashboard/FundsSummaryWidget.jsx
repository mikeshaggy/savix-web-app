'use client';
import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { Loader2, PiggyBank } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { formatCurrency, resolveAccentColor, hexToRgba } from '@/utils/helpers';
import { fundApi } from '@/lib/api';

// ─── Mini progress bar ────────────────────────────────────────────────────────
// Target-reached keeps the emerald convention; otherwise use the fund's accent
// color (falls back to violet for missing/invalid values).
function MiniBar({ percent, reached, color }) {
  const width = Math.min(Math.max(Number(percent) || 0, 0), 100);
  return (
    <div className="flex-1 h-[3px] bg-white/[0.07] rounded-full overflow-hidden">
      <div
        className={`h-full rounded-full transition-all ${reached ? 'bg-emerald-400' : ''}`}
        style={reached ? { width: `${width}%` } : { width: `${width}%`, backgroundColor: color }}
      />
    </div>
  );
}

// ─── Loading skeleton ─────────────────────────────────────────────────────────
function WidgetSkeleton() {
  return (
    <div className="animate-pulse space-y-3 px-5 py-4">
      <div className="h-[3px] w-full bg-white/[0.07] rounded-full" />
      {[0, 1, 2].map((i) => (
        <div key={i} className="flex items-center gap-3">
          <div className="w-6 h-6 bg-white/[0.06] rounded" />
          <div className="flex-1 h-[3px] bg-white/[0.06] rounded-full" />
          <div className="w-10 h-3 bg-white/[0.06] rounded" />
        </div>
      ))}
    </div>
  );
}

// ─── Main widget ──────────────────────────────────────────────────────────────
export default function FundsSummaryWidget() {
  const t = useTranslations();
  const { lang } = useLanguage();

  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchSummary = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fundApi.getSummary();
      setSummary(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchSummary(); }, [fetchSummary]);

  const overallPct = summary?.totalTarget > 0
    ? Math.min(Math.round((summary.totalSaved / summary.totalTarget) * 100), 100)
    : 0;

  const topFunds = summary?.topFunds ?? [];
  const nearDeadline = summary?.nearDeadlineFunds?.[0] ?? null;
  const isEmpty = !loading && !error && (summary?.activeFundsCount ?? 0) === 0;

  return (
    <div
      className="w-full bg-[#0e0e1c] border border-white/[0.07] rounded-[18px] overflow-hidden flex flex-col"
      style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both', animationDelay: '0.22s' }}
    >
      {/* ── Header ── */}
      <div className="flex items-center justify-between px-5 py-4 border-b border-white/[0.055]">
        <div>
          <div className="text-[15px] font-bold tracking-[-0.2px]">
            {t('funds.summaryTitle')}
          </div>
          <div className="text-[12px] text-white/25 mt-0.5">
            {loading
              ? t('funds.loadingSummary')
              : error
                ? t('funds.summaryError')
                : isEmpty
                  ? t('funds.noActiveFunds')
                  : t('funds.summaryActiveFunds', { count: summary.activeFundsCount })}
          </div>
        </div>
        <Link
          href="/funds"
          className="text-[12px] text-[#a855f7] opacity-80 hover:opacity-100 transition-opacity whitespace-nowrap"
        >
          {t('funds.viewAllFunds')}
        </Link>
      </div>

      {/* ── Body ── */}

      {/* Loading */}
      {loading && <WidgetSkeleton />}

      {/* Error */}
      {!loading && error && (
        <div className="flex items-center justify-between px-5 py-4 text-[12px] text-white/30">
          <span>{t('funds.summaryError')}</span>
          <button
            onClick={fetchSummary}
            className="text-[12px] text-violet-400 hover:text-violet-300 transition-colors"
          >
            {t('funds.retry')}
          </button>
        </div>
      )}

      {/* Empty state */}
      {isEmpty && (
        <div className="flex flex-col items-center justify-center gap-3 px-5 py-7">
          <div className="w-10 h-10 rounded-[12px] bg-white/[0.04] border border-white/[0.06] flex items-center justify-center">
            <PiggyBank className="w-5 h-5 text-white/20" />
          </div>
          <p className="text-[12px] text-white/25 text-center">
            {t('funds.noActiveFunds')}
          </p>
          <Link
            href="/funds"
            className="text-[12px] text-violet-400 hover:text-violet-300 transition-colors"
          >
            {t('funds.createFirstFromDashboard')}
          </Link>
        </div>
      )}

      {/* Populated */}
      {!loading && !error && !isEmpty && summary && (
        <>
          {/* Overall progress */}
          <div className="px-5 py-3 border-b border-white/[0.055]">
            <div className="flex items-center justify-between gap-2 mb-1.5 text-[11px]">
              <span className="text-white/40 truncate">
                {t('funds.savedOfTarget', {
                  saved: formatCurrency(summary.totalSaved, lang),
                  target: formatCurrency(summary.totalTarget, lang),
                })}
              </span>
              <span className={`font-medium shrink-0 ${overallPct >= 100 ? 'text-emerald-400' : 'text-violet-400'}`}>{overallPct}%</span>
            </div>
            <div className="h-[3px] bg-white/[0.07] rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all ${overallPct >= 100 ? 'bg-emerald-400' : 'bg-violet-500'}`}
                style={{ width: `${overallPct}%` }}
              />
            </div>
          </div>

          {/* Top funds list */}
          {topFunds.length > 0 && (
            <div className="flex flex-col divide-y divide-white/[0.035]">
              {topFunds.map((fund) => {
                const pct = Math.min(Math.round(Number(fund.progressPercent) || 0), 100);
                // Subtle per-fund accent; emerald stays the target-reached convention.
                const accent = fund.isTargetReached ? '#10b981' : resolveAccentColor(fund.color);
                return (
                  <div key={fund.id} className="flex items-center gap-3 px-5 py-2.5 transition-colors hover:bg-white/[0.02]">
                    {/* Emoji */}
                    <div
                      className="w-7 h-7 shrink-0 rounded-[8px] border border-white/[0.06] flex items-center justify-center text-[13px]"
                      style={{ backgroundColor: hexToRgba(accent, 0.12), borderColor: hexToRgba(accent, 0.25) }}
                    >
                      {fund.emoji || '🏦'}
                    </div>

                    {/* Name + bar */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-2 mb-1">
                        <span className="text-[12px] font-medium text-white/80 truncate">
                          {fund.name}
                          {fund.isTargetReached && (
                            <span className="ml-1.5 text-[9px] text-emerald-400 font-semibold">✓</span>
                          )}
                        </span>
                        <span className="text-[11px] font-mono text-white/40 shrink-0">{pct}%</span>
                      </div>
                      <MiniBar percent={pct} reached={fund.isTargetReached} color={accent} />
                    </div>

                    {/* Amount */}
                    <div className="text-right shrink-0 min-w-[70px]">
                      <div className="text-[12px] font-mono font-semibold text-white/70">
                        {formatCurrency(fund.currentAmount, lang)}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Near-deadline warning */}
          {nearDeadline && (nearDeadline.daysUntilDeadline ?? 0) >= 0 && (
            <div className="flex items-center gap-2 px-5 py-2.5 border-t border-white/[0.04] bg-amber-500/[0.04]">
              <div className="w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0 animate-pulse" />
              <span className="text-[11px] text-amber-400/80 truncate">
                {nearDeadline.daysUntilDeadline === 0
                  ? `${nearDeadline.name} — ${t('funds.dueToday')}`
                  : t('funds.nearDeadlineWarning', {
                      name: nearDeadline.name,
                      days: nearDeadline.daysUntilDeadline,
                    })}
              </span>
            </div>
          )}
        </>
      )}
    </div>
  );
}
