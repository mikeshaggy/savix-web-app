'use client';
import React, { useMemo } from 'react';
import {
  Area,
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useLocale, useTranslations } from 'next-intl';
import { useForecastCurrency } from '@/hooks/useForecastCurrency';
import { formatWholeCurrency } from '@/lib/forecastV2';
import { trajectoryModel } from '@/lib/forecastTrajectory';

// Stage 5.4 trajectory (`forecast-v2` on): cumulative pace-eligible variable spend of this cycle vs the
// historical p25–p75 band and median by cycle day; committed fixed payments marked at their due days. Nothing is
// drawn past today — the forecast figures live in the hero and the cards, not in an extrapolated line.

const ACTUAL = '#f87171';
const TYPICAL = '#a78bfa';
const FIXED = '#60a5fa';

function fmtShort(str, locale) {
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

function TrajectoryTooltip({ active, payload, label, markersByDay, locale }) {
  const t = useTranslations('forecast');
  const formatCurrency = useForecastCurrency();
  if (!active || !payload?.length) return null;
  const row = payload[0]?.payload ?? {};
  const due = markersByDay.get(label) ?? [];
  return (
    <div className="bg-[#14142a] border border-white/10 rounded-lg px-3 py-2 text-xs shadow-xl">
      <div className="text-white/50 mb-1">
        {t('trajectoryDay', { day: label })}
        {row.date ? ` · ${fmtShort(row.date, locale)}` : ''}
      </div>
      {row.actual != null && (
        <div style={{ color: ACTUAL }}>{t('trajectoryActual')}: {formatCurrency(row.actual)}</div>
      )}
      {row.median != null && (
        <div style={{ color: TYPICAL }}>{t('trajectoryTypical')}: {formatCurrency(row.median)}</div>
      )}
      {row.band && (
        <div className="text-white/40">
          {t('trajectoryBand')}: {formatCurrency(row.band[0])} – {formatCurrency(row.band[1])}
        </div>
      )}
      {due.map((m, i) => (
        <div key={i} style={{ color: FIXED }}>
          {t('trajectoryFixed')}: {m.title ? `${m.title} ` : ''}{m.amount != null ? formatCurrency(m.amount) : ''}
        </div>
      ))}
    </div>
  );
}

function StatCell({ label, value, subtext, color }) {
  return (
    <div className="flex flex-col gap-1 min-w-0">
      <div className="text-[11px] font-semibold uppercase tracking-[0.09em] text-white/35 truncate">{label}</div>
      <div className="font-mono text-sm font-bold leading-none" style={{ color }}>{value}</div>
      {subtext && <div className="text-[10px] text-white/30 truncate">{subtext}</div>}
    </div>
  );
}

function LegendItem({ children, swatch }) {
  return (
    <div className="flex items-center gap-1.5 text-xs text-white/40">
      {swatch}
      <span>{children}</span>
    </div>
  );
}

export default function ForecastTrajectoryV2({ projData }) {
  const t = useTranslations('forecast');
  const locale = useLocale();
  const lang = locale === 'pl' ? 'pl' : 'en';
  const formatCurrency = useForecastCurrency();
  const model = useMemo(() => trajectoryModel(projData.forecast, projData), [projData]);
  const markersByDay = useMemo(() => {
    const map = new Map();
    for (const m of model.markers) map.set(m.day, [...(map.get(m.day) ?? []), m]);
    return map;
  }, [model.markers]);

  const { stats } = model;
  const empty = !model.hasActual && !model.hasBand;

  return (
    <div
      className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-6 relative overflow-hidden flex flex-col"
      data-testid="trajectory-v2"
    >
      <div className="mb-4">
        <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-1">{t('trajectoryTitle')}</div>
        <div className="text-[11px] text-white/25">{t('trajectorySubtitle')}</div>
      </div>

      {empty ? (
        <p className="text-sm text-white/35 py-10 text-center">{t('trajectoryEmpty')}</p>
      ) : (
        <div className="h-[220px] -ml-2">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={model.rows} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid stroke="rgba(255,255,255,0.06)" vertical={false} />
              <XAxis
                dataKey="day"
                type="number"
                domain={[1, Math.max(model.daysInPeriod, 1)]}
                allowDecimals={false}
                tickLine={false}
                axisLine={false}
                tick={{ fill: 'rgba(255,255,255,0.35)', fontSize: 11 }}
                tickFormatter={(day) => t('trajectoryDay', { day })}
                interval="preserveStartEnd"
              />
              <YAxis
                tickLine={false}
                axisLine={false}
                width={72}
                tick={{ fill: 'rgba(255,255,255,0.35)', fontSize: 11 }}
                tickFormatter={(value) => formatWholeCurrency(value, lang)}
              />
              <Tooltip
                content={<TrajectoryTooltip markersByDay={markersByDay} locale={locale} />}
                cursor={{ stroke: 'rgba(255,255,255,0.15)' }}
              />
              {model.hasBand && (
                <Area
                  dataKey="band"
                  name={t('trajectoryBand')}
                  stroke="none"
                  fill={TYPICAL}
                  fillOpacity={0.14}
                  isAnimationActive={false}
                  connectNulls
                />
              )}
              {model.hasBand && (
                <Line
                  dataKey="median"
                  name={t('trajectoryTypical')}
                  stroke={TYPICAL}
                  strokeWidth={1.5}
                  strokeDasharray="5 4"
                  dot={false}
                  isAnimationActive={false}
                  connectNulls
                />
              )}
              {model.markers.map((m, i) => (
                <ReferenceLine
                  key={`${m.day}-${i}`}
                  x={m.day}
                  stroke={FIXED}
                  strokeOpacity={0.45}
                  strokeDasharray="2 3"
                />
              ))}
              {model.today !== null && (
                <ReferenceLine
                  x={model.today}
                  stroke="rgba(255,255,255,0.25)"
                  label={{ value: t('trajectoryToday'), position: 'top', fill: 'rgba(255,255,255,0.35)', fontSize: 10 }}
                />
              )}
              <Line
                dataKey="actual"
                name={t('trajectoryActual')}
                stroke={ACTUAL}
                strokeWidth={2.5}
                dot={false}
                activeDot={{ r: 4 }}
                isAnimationActive={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}

      <div className="flex flex-wrap gap-x-4 gap-y-2 mt-3 mb-4">
        <LegendItem swatch={<div className="w-4 h-[2.5px] rounded-full" style={{ background: ACTUAL }} />}>
          {t('trajectoryActual')}
        </LegendItem>
        {model.hasBand && (
          <>
            <LegendItem
              swatch={(
                <svg width="16" height="3" className="flex-shrink-0">
                  <line x1="0" y1="1.5" x2="16" y2="1.5" stroke={TYPICAL} strokeWidth="1.5" strokeDasharray="4 3" />
                </svg>
              )}
            >
              {t('trajectoryTypical')}
            </LegendItem>
            <LegendItem swatch={<div className="w-4 h-2.5 rounded-sm" style={{ background: TYPICAL, opacity: 0.25 }} />}>
              {t('trajectoryBand')}
            </LegendItem>
          </>
        )}
        {model.markers.length > 0 && (
          <LegendItem
            swatch={(
              <svg width="3" height="12" className="flex-shrink-0">
                <line x1="1.5" y1="0" x2="1.5" y2="12" stroke={FIXED} strokeWidth="1.5" strokeDasharray="2 2" />
              </svg>
            )}
          >
            {t('trajectoryFixed')}
          </LegendItem>
        )}
      </div>
      {!empty && !model.hasBand && (
        <p className="text-[11px] text-white/30 -mt-2 mb-4">{t('trajectoryNoHistory')}</p>
      )}

      <div className="h-px bg-white/[0.06] mb-4" />

      <div className="grid grid-cols-2 gap-x-4 gap-y-3 md:grid-cols-3" data-testid="trajectory-v2-stats">
        <StatCell
          label={t('spentSoFarVariable')}
          value={stats.spentSoFar !== null ? formatCurrency(stats.spentSoFar) : '—'}
          color={ACTUAL}
        />
        <StatCell
          label={t('typicalByToday')}
          value={stats.typicalByToday !== null ? formatCurrency(stats.typicalByToday) : '—'}
          subtext={stats.typicalCyclesByToday !== null ? t('pastCycles', { count: stats.typicalCyclesByToday }) : null}
          color={TYPICAL}
        />
        <StatCell
          label={t('committedRemaining')}
          value={stats.committedRemaining !== null ? formatCurrency(stats.committedRemaining) : '—'}
          color={FIXED}
        />
      </div>
    </div>
  );
}
