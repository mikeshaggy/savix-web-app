'use client';
import React, { useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useForecastCurrency } from '@/hooks/useForecastCurrency';
import { formatWholeCurrency } from '@/lib/forecastV2';
import { breakdownModel } from '@/lib/forecastBreakdown';

// Stage 5.5 explanation (`forecast-v2` on): the Stage 4 arithmetic with the user's numbers, the baseline cycles
// and the one-offs with an Exclude action. The excluded flag shown is always the backend's (`OneOffDto.excluded`
// after a refetch) — the component only tracks which write is in flight.

function fmtDate(str, locale) {
  if (!str) return '';
  try {
    return new Date(str + 'T12:00:00').toLocaleDateString(locale === 'pl' ? 'pl-PL' : 'en-US', {
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return str;
  }
}

const moneyColor = (v) => (v !== null && v < 0 ? 'text-rose-400' : 'text-white/80');

function Row({ label, sub, value, valueClass = 'text-white/80', emphasis = false, sign }) {
  return (
    <div className={`flex items-baseline justify-between gap-4 py-1.5 ${emphasis ? 'border-t border-white/[0.08] mt-1 pt-2.5' : ''}`}>
      <div className="min-w-0">
        <span className={`text-sm ${emphasis ? 'text-white/70 font-medium' : 'text-white/50'}`}>
          {sign && <span className="font-mono text-white/35 mr-1.5">{sign}</span>}
          {label}
        </span>
        {sub && <div className="text-[11px] text-white/30 mt-0.5">{sub}</div>}
      </div>
      <span className={`font-mono text-sm tabular-nums flex-shrink-0 ${emphasis ? 'font-bold' : 'font-medium'} ${valueClass}`}>
        {value}
      </span>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div className="min-w-0">
      <div className="text-[10px] font-bold tracking-[0.12em] uppercase text-white/30 mb-1.5">{title}</div>
      {children}
    </div>
  );
}

export default function ForecastBreakdownV2({ projData, onExcludeOneOff }) {
  const t = useTranslations('forecast');
  const locale = useLocale();
  const lang = locale === 'pl' ? 'pl' : 'en';
  const formatCurrency = useForecastCurrency();
  const whole = (v) => formatWholeCurrency(v, lang);
  const m = useMemo(() => breakdownModel(projData.forecast), [projData]);
  const [pendingId, setPendingId] = useState(null);
  const [error, setError] = useState(null); // { id, key }

  const money = (v) => (v === null ? '—' : formatCurrency(v));
  const approx = (v) => (v === null ? '—' : t('approxAmount', { amount: whole(v) }));
  const days = projData.daysRemaining ?? 0;

  const handleExclude = async (transactionId) => {
    if (!onExcludeOneOff || pendingId !== null) return;
    setPendingId(transactionId);
    setError(null);
    try {
      await onExcludeOneOff(transactionId);
    } catch (err) {
      console.error('Failed to exclude one-off from pace:', err);
      setError({ id: transactionId, key: err?.code === 'REFRESH_FAILED' ? 'excludeSavedRefreshFailed' : 'excludeFailed' });
    } finally {
      setPendingId(null);
    }
  };

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 relative overflow-hidden" data-testid="breakdown-v2">
      <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-4">{t('breakdownTitle')}</div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="flex flex-col gap-5 min-w-0">
          <Section title={t('leftUntilPayday')}>
            <Row label={t('balance')} value={money(m.balance)} />
            <Row sign="−" label={t('committed')} value={money(m.committed)} valueClass="text-violet-300" />
            <Row label={t('leftUntilPayday')} value={money(m.discretionaryNow)} valueClass={moneyColor(m.discretionaryNow)} emphasis />
          </Section>

          <Section title={t('expectedVariable')}>
            <Row
              label={t('historyMedian')}
              sub={m.history.cyclesUsed !== null && m.history.weightPercent !== null
                ? t('historyWeight', { cycles: m.history.cyclesUsed, days, weight: m.history.weightPercent })
                : null}
              value={money(m.history.median)}
            />
            <Row
              sign="+"
              label={t('paceProjectionLabel', { days })}
              sub={m.pace.perDay !== null && m.pace.weightPercent !== null
                ? t('paceWeight', { perDay: formatCurrency(m.pace.perDay), weight: m.pace.weightPercent })
                : null}
              value={money(m.pace.projection)}
            />
            <Row label={t('expectedVariable')} value={money(m.expectedVariable.typical)} valueClass="text-orange-300" emphasis />
          </Section>

          <Section title={t('expectedLeftAtPayday')}>
            <Row label={t('pessimistic')} value={approx(m.endBalance.low)} valueClass={moneyColor(m.endBalance.low)} />
            <Row label={t('typical')} value={approx(m.endBalance.typical)} valueClass={moneyColor(m.endBalance.typical)} />
            <Row label={t('optimistic')} value={approx(m.endBalance.high)} valueClass={moneyColor(m.endBalance.high)} />
          </Section>
        </div>

        <div className="flex flex-col gap-5 min-w-0 lg:border-l lg:border-white/[0.06] lg:pl-6">
          <Section title={t('oneOffsTitle')}>
            {m.oneOffs.length === 0 ? (
              <p className="text-sm text-white/35 py-1.5">{t('noOneOffs')}</p>
            ) : (
              <ul className="flex flex-col gap-2.5" data-testid="one-offs">
                {m.oneOffs.map((o) => (
                  <li
                    key={o.transactionId ?? `${o.date}-${o.title}`}
                    className="rounded-lg border border-white/[0.06] bg-white/[0.02] px-3.5 py-3"
                    data-testid="one-off"
                    data-excluded={o.excluded ? 'true' : 'false'}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="text-sm text-white/75 truncate">{o.title ?? '—'}</div>
                        <div className="text-[11px] text-white/35 mt-0.5">
                          {fmtDate(o.date, locale)}
                          {o.categoryName ? ` · ${o.categoryName}` : ''}
                          {o.sharePercent !== null
                            ? ` · ${t('oneOffShare', { share: o.sharePercent.toLocaleString(lang === 'pl' ? 'pl-PL' : 'en-US', { maximumFractionDigits: 1 }) })}`
                            : ''}
                        </div>
                      </div>
                      <div className="font-mono text-sm font-semibold text-white/80 flex-shrink-0">{money(o.amount)}</div>
                    </div>
                    {o.excluded ? (
                      <div className="mt-2 inline-flex items-center gap-1.5 text-[11px] text-emerald-400/80">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400/80" />
                        {t('oneOffExcluded')}
                      </div>
                    ) : (
                      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                        {o.typicalIfExcluded !== null && (
                          <p className="text-[11px] text-white/45 min-w-0">
                            {t('oneOffImpact', {
                              title: o.title ?? '—',
                              amount: money(o.amount),
                              date: fmtDate(o.date, locale),
                              typical: approx(o.typicalIfExcluded),
                            })}
                          </p>
                        )}
                        {onExcludeOneOff && o.transactionId !== null && (
                          <button
                            type="button"
                            onClick={() => handleExclude(o.transactionId)}
                            disabled={pendingId !== null}
                            className="text-[11px] font-semibold px-2.5 py-1 rounded-md border border-violet-400/30 text-violet-300 hover:bg-violet-400/10 disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
                            data-testid="exclude-one-off"
                          >
                            {pendingId === o.transactionId ? t('excluding') : t('excludeOneOff')}
                          </button>
                        )}
                        {error?.id === o.transactionId && (
                          <p className="basis-full text-[11px] text-rose-400">{t(error.key)}</p>
                        )}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Section>

          {m.baselineCycles.length > 0 && (
            <Section title={t('baselineCyclesTitle')}>
              <ul className="flex flex-col" data-testid="baseline-cycles">
                {m.baselineCycles.map((c) => (
                  <li
                    key={`${c.start}-${c.end}`}
                    className="flex flex-col sm:flex-row sm:items-baseline sm:justify-between gap-x-3 gap-y-0.5 py-1.5 border-b border-white/[0.04] last:border-0"
                  >
                    <div className="min-w-0">
                      <div className="text-xs text-white/60 font-mono whitespace-nowrap">
                        {fmtDate(c.start, locale)} – {fmtDate(c.end, locale)}
                      </div>
                      <div className="text-[11px] text-white/30">
                        {c.lengthDays !== null ? t('cycleLength', { days: c.lengthDays }) : ''}
                        {c.variableTotal !== null ? ` · ${t('cycleSpent', { amount: money(c.variableTotal) })}` : ''}
                      </div>
                    </div>
                    <div className="text-[11px] sm:text-right flex-shrink-0">
                      {c.used ? (
                        <span title={c.remainingFromDay !== null ? money(c.remainingFromDay) : undefined}>
                          {/* the normalised value is what enters the median */}
                          <span className="text-white/30">{t('cycleFromToday')} </span>
                          <span className="font-mono text-white/60">{money(c.normalised)}</span>
                        </span>
                      ) : (
                        <span className="text-white/30">{t('cycleTooShort')}</span>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </Section>
          )}
        </div>
      </div>
    </div>
  );
}
