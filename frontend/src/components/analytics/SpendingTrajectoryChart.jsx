'use client';
import React from 'react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';

// Format "YYYY-MM-DD" → locale short date, e.g. "May 1".
// T12:00:00 prevents timezone drift when parsing date-only strings.
function fmtShort(str) {
  try {
    return new Date(str + 'T12:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  } catch {
    return str ?? '';
  }
}

// ─── SVG Trajectory ──────────────────────────────────────────────────────────
//
// Coordinate system:
//   x-axis  = time (day 0 → daysInPeriod)
//   y-axis  = cumulative spend (0 → yMax), SVG y inverted (0 at top)
//
// Lines drawn:
//   1. Actual spend       — solid coral,   (0,0) → (daysElapsed, spentSoFar)
//   2. Projected spend    — dashed coral,  (daysElapsed, spentSoFar) → (daysInPeriod, projectedTotal)
//   3. Income ceiling     — solid green,   horizontal at y = income across full width
//   4. Safe-pace diagonal — dashed green,  (0,0) → (daysInPeriod, income)  [subtle reference]
//   5. Today marker       — faint vertical at daysElapsed
//
function TrajectorySVG({
  daysInPeriod,
  daysElapsed,
  expensesToDate,
  projectedTotal,
  income,
  todayLabel,
  incomeLabel,
}) {
  if (!daysInPeriod || daysInPeriod <= 0) return null;

  // Canvas dims (viewBox units)
  const W = 400;
  const H = 140;
  const PT = 24; // top padding — room for "Today" label
  const PB = 20; // bottom padding — room for date labels
  const PL = 4;  // left padding
  const PR = 10; // right padding — small breathing room at end of period

  const CW = W - PL - PR; // chart width
  const CH = H - PT - PB; // chart height

  const yMax = Math.max(
    income > 0 ? income * 1.2 : projectedTotal * 1.2,
    projectedTotal * 1.08,
    1
  );

  // Pixel helpers
  const xAt = (d) => PL + (Math.min(d, daysInPeriod) / daysInPeriod) * CW;
  const yAt = (v) => PT + CH * (1 - Math.min(v, yMax) / yMax);
  const yBase = PT + CH; // bottom of chart (spend = 0)

  const x0       = xAt(0);
  const xNow     = xAt(daysElapsed);
  const xEnd     = xAt(daysInPeriod); // = PL + CW
  const yNow     = yAt(expensesToDate);
  const yEnd     = yAt(projectedTotal);
  const yIncome  = income > 0 ? yAt(income) : null;
  // ySafe kept as alias so the rest of the code is unchanged
  const ySafe    = yIncome;

  const isPast = daysElapsed >= daysInPeriod;

  // Area path helpers (filled regions beneath lines)
  const actualAreaD  = `M ${x0},${yBase} L ${xNow},${yNow} L ${xNow},${yBase} Z`;
  const projAreaD    = `M ${xNow},${yBase} L ${xNow},${yNow} L ${xEnd},${yEnd} L ${xEnd},${yBase} Z`;

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      width="100%"
      preserveAspectRatio="xMidYMid meet"
      style={{ display: 'block', overflow: 'visible' }}
    >
      {/* ── Area fills (drawn first, behind lines) ── */}
      <path d={actualAreaD} fill="#f87171" fillOpacity={0.1} />
      {!isPast && (
        <path d={projAreaD} fill="#f87171" fillOpacity={0.04} />
      )}

      {/* ── Safe-pace diagonal (subtle reference) ── */}
      {ySafe !== null && (
        <line
          x1={x0}   y1={yBase}
          x2={xEnd} y2={ySafe}
          stroke="#4ade80"
          strokeWidth={1.5}
          strokeDasharray="5 3"
          opacity={0.22}
        />
      )}

      {/* ── Income ceiling (horizontal limit) ── */}
      {yIncome !== null && (
        <line
          x1={x0}   y1={yIncome}
          x2={xEnd} y2={yIncome}
          stroke="#4ade80"
          strokeWidth={1.5}
          opacity={0.5}
        />
      )}

      {/* ── Today vertical marker ── */}
      {!isPast && (
        <line
          x1={xNow} y1={PT}
          x2={xNow} y2={yBase}
          stroke="white"
          strokeWidth={1}
          strokeDasharray="2 3"
          opacity={0.12}
        />
      )}

      {/* ── Actual spend line ── */}
      <line
        x1={x0}   y1={yBase}
        x2={xNow} y2={yNow}
        stroke="#f87171"
        strokeWidth={2.5}
        strokeLinecap="round"
      />

      {/* ── Projected spend line (dashed, faded) ── */}
      {!isPast && (
        <line
          x1={xNow} y1={yNow}
          x2={xEnd} y2={yEnd}
          stroke="#f87171"
          strokeWidth={2}
          strokeDasharray="5 4"
          strokeLinecap="round"
          opacity={0.5}
        />
      )}

      {/* ── Dots ── */}
      {/* Today: filled circle */}
      <circle cx={xNow} cy={yNow} r={4.5} fill="#f87171" />
      {/* Projected end: open ring */}
      {!isPast && (
        <circle
          cx={xEnd} cy={yEnd} r={4}
          fill="none"
          stroke="#f87171"
          strokeWidth={1.5}
          opacity={0.55}
        />
      )}

      {/* ── Labels ── */}
      {/* "Today" above the vertical marker */}
      {!isPast && daysElapsed > 0 && (
        <text
          x={xNow}
          y={PT - 7}
          textAnchor="middle"
          fill="white"
          fillOpacity={0.3}
          fontSize={9}
          fontFamily="ui-monospace,SFMono-Regular,monospace"
        >
          {todayLabel}
        </text>
      )}

      {/* Income limit label — anchored at the left side of the ceiling line */}
      {yIncome !== null && (
        <text
          x={x0 + 4}
          y={yIncome - 5}
          textAnchor="start"
          fill="#4ade80"
          fillOpacity={0.65}
          fontSize={9}
          fontFamily="ui-monospace,SFMono-Regular,monospace"
        >
          {incomeLabel}
        </text>
      )}
    </svg>
  );
}

// ─── Stat cell ───────────────────────────────────────────────────────────────

function StatCell({ label, value, color }) {
  return (
    <div className="flex flex-col gap-1 min-w-0">
      <div className="text-[11px] font-semibold uppercase tracking-[0.09em] text-white/35 truncate">
        {label}
      </div>
      <div className="font-mono text-sm font-bold leading-none" style={{ color }}>
        {value}
      </div>
    </div>
  );
}

// ─── Main export ─────────────────────────────────────────────────────────────

export default function SpendingTrajectoryChart({ projData, loading }) {
  const t = useTranslations('analytics');

  if (loading) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-6 animate-pulse h-72" />
    );
  }

  if (!projData) return null;

  const daysInPeriod   = projData.daysInPeriod ?? 0;
  const daysElapsed    = projData.daysElapsed  ?? 0;
  const spentSoFar     = projData.expensesToDate ?? 0;
  const projectedTotal = projData.projectedPeriodExpenses ?? 0;
  const income         = projData.incomeForPeriod ?? 0;

  const pct     = income > 0 ? (projectedTotal / income) * 100 : null;
  const isOver  = pct !== null && pct > 100;
  const statusColor = isOver ? '#f87171' : pct !== null && pct > 80 ? '#fbbf24' : '#4ade80';

  // Safe-pace today: linear spend rate toward income
  const safePaceToday =
    daysInPeriod > 0 && income > 0
      ? (daysElapsed / daysInPeriod) * income
      : null;

  // Positive = spending faster than safe pace (over), negative = under
  const paceDelta = safePaceToday !== null ? spentSoFar - safePaceToday : null;
  const isOverPace = paceDelta !== null && paceDelta > 0;

  const vsIncome = income > 0 ? projectedTotal - income : null;

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-6 relative overflow-hidden flex flex-col">

      {/* ── Header ─────────────────────────────────────── */}
      <div className="flex items-start justify-between mb-4">
        <div>
          <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-2">
            {t('spendingTrajectory')}
          </div>
          <div className="flex items-baseline gap-2 flex-wrap">
            <span
              className="font-mono text-2xl font-semibold"
              style={{ color: statusColor }}
            >
              {formatCurrency(projectedTotal)}
            </span>
            {income > 0 && (
              <span className="text-white/30 text-sm font-mono">
                / {formatCurrency(income)}
              </span>
            )}
          </div>
        </div>

        {pct !== null && (
          <div
            className="text-sm font-mono font-bold px-3 py-1.5 rounded-lg flex-shrink-0 ml-3"
            style={{ color: statusColor, background: `${statusColor}18` }}
          >
            {pct.toFixed(1)}%
          </div>
        )}
      </div>

      {/* ── SVG trajectory chart ───────────────────────── */}
      <div className="w-full">
        <TrajectorySVG
          daysInPeriod={daysInPeriod}
          daysElapsed={daysElapsed}
          expensesToDate={spentSoFar}
          projectedTotal={projectedTotal}
          income={income}
          todayLabel={t('today')}
          incomeLabel={t('incomeLimit')}
        />
      </div>

      {/* Date axis labels */}
      <div className="flex justify-between text-[11px] font-mono text-white/25 -mt-1 mb-4 px-1">
        <span>{fmtShort(projData.startDate)}</span>
        <span>{fmtShort(projData.endDate)}</span>
      </div>

      {/* ── Legend ─────────────────────────────────────── */}
      <div className="flex flex-wrap gap-4 mb-4">
        <div className="flex items-center gap-1.5 text-xs text-white/40">
          <div className="w-4 h-[2.5px] rounded-full bg-[#f87171]" />
          <span>{t('actualSpend')}</span>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-white/40">
          <svg width="16" height="3" className="flex-shrink-0">
            <line x1="0" y1="1.5" x2="16" y2="1.5"
              stroke="#f87171" strokeWidth="2"
              strokeDasharray="4 3" opacity="0.55" />
          </svg>
          <span>{t('projectedSpend')}</span>
        </div>
        {income > 0 && (
          <div className="flex items-center gap-1.5 text-xs text-white/40">
            <div className="w-4 h-[1.5px] rounded-full bg-[#4ade80] opacity-60" />
            <span>{t('incomeLimit')}</span>
          </div>
        )}
        {income > 0 && (
          <div className="flex items-center gap-1.5 text-xs text-white/40">
            <svg width="16" height="3" className="flex-shrink-0">
              <line x1="0" y1="1.5" x2="16" y2="1.5"
                stroke="#4ade80" strokeWidth="1.5"
                strokeDasharray="4 3" opacity="0.35" />
            </svg>
            <span>{t('safePace')}</span>
          </div>
        )}
      </div>

      {/* ── Divider ────────────────────────────────────── */}
      <div className="h-px bg-white/[0.06] mb-4" />

      {/* ── Pace-focused bottom stats ───────────────────── */}
      <div className="grid grid-cols-2 gap-x-4 gap-y-3 md:grid-cols-4">
        <StatCell
          label={t('spentSoFar')}
          value={formatCurrency(spentSoFar)}
          color="#f87171"
        />
        <StatCell
          label={t('safePaceToday')}
          value={safePaceToday !== null ? formatCurrency(safePaceToday) : '—'}
          color="#4ade80"
        />
        <StatCell
          label={isOverPace ? t('overSafePace') : t('underSafePace')}
          value={paceDelta !== null ? formatCurrency(Math.abs(paceDelta)) : '—'}
          color={isOverPace ? '#f87171' : '#4ade80'}
        />
        <StatCell
          label={isOver ? t('overIncome') : t('underIncome')}
          value={vsIncome !== null ? formatCurrency(Math.abs(vsIncome)) : '—'}
          color={isOver ? '#f87171' : '#4ade80'}
        />
      </div>

    </div>
  );
}
