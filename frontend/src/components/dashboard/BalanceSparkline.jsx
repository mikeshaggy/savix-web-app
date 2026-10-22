'use client';
import React, { useEffect, useMemo, useState } from 'react';
import {
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceDot,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useLocale, useTranslations } from 'next-intl';
import { useAppContext } from '@/contexts/AppContext';
import { useForecastCurrency } from '@/hooks/useForecastCurrency';
import { walletEntryApi } from '@/lib/api';
import { formatWholeCurrency } from '@/lib/forecastV2';
import { sparklineModel } from '@/lib/dashboardV2';

// Stage 6.6: 90-day closing-balance line with actual payday markers and the lowest balance of each closed cycle.
// Everything drawn comes from the sparkline endpoint as returned; nothing is derived here.

const DAYS = 90;
const LINE = '#a78bfa';
const PAYDAY = '#34d399';
const LOWEST = '#f87171';

function dateFromX(x, locale) {
  return new Date(x * 86400000).toLocaleDateString(locale === 'pl' ? 'pl-PL' : 'en-US', {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });
}

export default function BalanceSparkline({ walletId }) {
  const t = useTranslations('dashboard');
  const locale = useLocale();
  const lang = locale === 'pl' ? 'pl' : 'en';
  const formatCurrency = useForecastCurrency();
  const { walletMutationVersion } = useAppContext();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!walletId) return undefined;
    let cancelled = false;
    setLoading(true);
    setError(false);
    walletEntryApi.getBalanceSparkline(walletId, DAYS)
      .then((res) => { if (!cancelled) setData(res); })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [walletId, walletMutationVersion]);

  const model = useMemo(() => sparklineModel(data), [data]);
  // tooltip tags for the marked days (actual paydays, closed-cycle minima) — as returned, nothing derived
  const paydayXs = useMemo(() => new Set(model.paydays.map((p) => p.x)), [model]);
  const lowestXs = useMemo(() => new Set(model.minima.map((m) => m.x)), [model]);

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-[14px] px-5 py-4">
      <div className="text-[15px] font-bold tracking-[-0.2px] text-white">{t('sparklineTitle', { days: DAYS })}</div>
      {loading ? (
        <div className="h-[140px] mt-3 animate-pulse bg-white/[0.03] rounded-lg" />
      ) : error ? (
        <p className="text-[12px] text-white/35 py-6">{t('sparklineError')}</p>
      ) : !model.hasLine ? (
        <p className="text-[12px] text-white/35 py-6">{t('sparklineEmpty')}</p>
      ) : (
        <>
          <div className="h-[140px] mt-3 -ml-2">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={model.points} margin={{ top: 18, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid stroke="rgba(255,255,255,0.06)" vertical={false} />
                <XAxis
                  dataKey="x"
                  type="number"
                  scale="linear"
                  domain={[model.points[0].x, model.points[model.points.length - 1].x]}
                  tickLine={false}
                  axisLine={false}
                  tick={{ fill: 'rgba(255,255,255,0.35)', fontSize: 11 }}
                  tickFormatter={(x) => dateFromX(x, locale)}
                />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  width={72}
                  tick={{ fill: 'rgba(255,255,255,0.35)', fontSize: 11 }}
                  tickFormatter={(value) => formatWholeCurrency(value, lang)}
                />
                <Tooltip
                  cursor={{ stroke: 'rgba(255,255,255,0.15)' }}
                  labelFormatter={(x) => [
                    dateFromX(x, locale),
                    paydayXs.has(x) ? t('sparklinePayday') : null,
                    lowestXs.has(x) ? t('sparklineLowestOfCycle') : null,
                  ].filter(Boolean).join(' · ')}
                  separator=""
                  formatter={(value) => [formatCurrency(value), '']}
                  contentStyle={{ background: '#14142a', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, fontSize: 12 }}
                />
                {model.paydays.map((p) => (
                  <ReferenceLine
                    key={p.date}
                    x={p.x}
                    stroke={PAYDAY}
                    strokeOpacity={0.55}
                    strokeDasharray="2 3"
                    label={{ value: t('sparklinePayday'), position: 'top', fill: PAYDAY, fontSize: 9 }}
                  />
                ))}
                <Line dataKey="balance" stroke={LINE} strokeWidth={2} dot={false} activeDot={{ r: 3 }} isAnimationActive={false} />
                {model.minima.map((m) => (
                  <ReferenceDot key={m.date} x={m.x} y={m.balance} r={4} fill={LOWEST} stroke="none" />
                ))}
              </ComposedChart>
            </ResponsiveContainer>
          </div>
          {model.minimaCount > 0 && (
            <p className="text-[12px] text-white/50 mt-2 tabular-nums">
              {t('lowestBeforePayday', {
                count: model.minimaCount,
                values: model.minimaValues.map((v) => formatCurrency(v)).join(' / '),
              })}
            </p>
          )}
          <p className="text-[10px] text-white/25 mt-1">{t('sparklineAsOf')}</p>
        </>
      )}
    </div>
  );
}
