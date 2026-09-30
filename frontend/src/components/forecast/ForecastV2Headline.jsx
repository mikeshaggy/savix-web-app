'use client';
import React from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useForecastCurrency } from '@/hooks/useForecastCurrency';
import { formatWholeCurrency } from '@/lib/forecastV2';

// Forecast v2 headline pair (Stage 5.1 / 5.2), shared by the Dashboard and Forecast heroes so the two cannot
// drift: "Left until payday" (discretionaryNow) and "Expected left at payday" (typical + rounded range).

export const TONE_CFG = {
  amber: {
    border: 'border-amber-400/35',
    glow: 'rgba(245,158,11,0.10)',
    text: 'text-amber-400',
    dot: 'bg-amber-400',
    accent: '#f59e0b',
  },
  red: {
    border: 'border-rose-400/40',
    glow: 'rgba(244,63,94,0.12)',
    text: 'text-rose-400',
    dot: 'bg-rose-400',
    accent: '#f43f5e',
  },
};

/** TIGHT / SHORT status word; renders nothing for FINE, null or an unknown status. */
export function ForecastStatusPill({ model }) {
  const t = useTranslations('forecast');
  if (!model.status || !model.tone) return null;
  const cfg = TONE_CFG[model.tone];
  return (
    <span className="flex items-center gap-2" data-testid="forecast-status">
      <span className={`w-2 h-2 rounded-full ${cfg.dot} shrink-0`} />
      <span className={`text-[11px] font-bold tracking-[0.12em] uppercase ${cfg.text}`}>
        {t(`status_${model.status}`)}
      </span>
    </span>
  );
}

/** One-line explanation under a TIGHT / SHORT verdict. */
export function ForecastStatusHint({ model, className = '' }) {
  const t = useTranslations('forecast');
  if (!model.status || !model.tone) return null;
  return (
    <div className={`text-xs ${TONE_CFG[model.tone].text} opacity-80 ${className}`}>
      {t(`statusHint_${model.status}`)}
    </div>
  );
}

const moneyClass = (value) => (value !== null && value < 0 ? 'text-rose-400' : 'text-white');

export default function ForecastV2Headline({ model }) {
  const t = useTranslations('forecast');
  const locale = useLocale();
  const formatCurrency = useForecastCurrency();
  const lang = locale === 'pl' ? 'pl' : 'en';
  const whole = (v) => formatWholeCurrency(v, lang);

  const {
    discretionaryNow, discretionaryPerDay, daysRemaining, typical, rangeLow, rangeHigh, hasRange, rangeBasisKey,
  } = model;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-px bg-white/[0.035]" data-testid="forecast-v2-headline">
      {/* Left until payday */}
      <div className="bg-[#0e0e1c] px-6 md:px-8 py-6 md:py-7 min-w-0">
        <div className="text-[9px] tracking-[0.12em] uppercase text-white/35 mb-3">{t('leftUntilPayday')}</div>
        <div
          className={`font-mono text-[clamp(24px,5vw,40px)] font-bold tracking-[-1px] leading-none tabular-nums ${moneyClass(discretionaryNow)}`}
          style={{ overflowWrap: 'break-word' }}
          data-testid="left-until-payday"
        >
          {discretionaryNow !== null ? formatCurrency(discretionaryNow) : '—'}
        </div>
        {/* last day of the cycle (R = 0): no per-day split — the backend divides by max(R, 1) */}
        {discretionaryPerDay !== null && daysRemaining !== null && daysRemaining > 0 && (
          <div className="text-[11px] text-white/40 mt-2.5 tabular-nums">
            {t('perDayForDays', { amount: formatCurrency(discretionaryPerDay), days: daysRemaining })}
          </div>
        )}
      </div>

      {/* Expected left at payday — typical, range rounded to 10 PLN */}
      <div className="bg-[#0e0e1c] px-6 md:px-8 py-6 md:py-7 min-w-0">
        <div className="text-[9px] tracking-[0.12em] uppercase text-white/35 mb-3">{t('expectedLeftAtPayday')}</div>
        <div
          className={`font-mono text-[clamp(20px,4vw,32px)] font-bold tracking-[-0.5px] leading-none tabular-nums ${moneyClass(typical)}`}
          style={{ overflowWrap: 'break-word' }}
          data-testid="expected-left-at-payday"
        >
          {typical !== null ? t('approxAmount', { amount: whole(typical) }) : '—'}
        </div>
        {hasRange && (
          <div className="text-[11px] text-white/40 mt-2.5 tabular-nums">
            {t('typicalRange', { low: whole(rangeLow), high: whole(rangeHigh) })}
            {/* Stage 5.7: "typical for you" only with ≥ 3 closed cycles; weaker bases say what they rest on */}
            {rangeBasisKey && <span className="text-white/25"> · {t(rangeBasisKey)}</span>}
          </div>
        )}
      </div>
    </div>
  );
}
