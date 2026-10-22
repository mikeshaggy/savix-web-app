// Stage 6.5–6.7 dashboard v2 view models. Pure selection / shaping of figures the backend already computed
// (dashboard summary + balance sparkline): no financial maths lives here.

/** Backend and frontend agree: dashboard v2 needs Forecast v2 (unsupported combination = legacy layout). */
export function dashboardLayoutVariant(features) {
  return features?.dashboardV2 === true && features?.forecastV2 === true ? 'v2' : 'legacy';
}

const toNumber = (value) => {
  if (value == null || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
};

/**
 * The v2 layout's sections in DOM (= visual) order. Optional sections follow the summary: `fixed` needs
 * `fixedPayments`, `cycleSoFar` / `savings` their own non-null DTO section. `funds` is always listed; the funds
 * widget renders nothing when the user has no fund.
 */
export function dashboardV2Sections(summary) {
  const sections = [
    { id: 'hero', testId: 'dash-hero' },
    { id: 'sparkline', testId: 'dash-sparkline' },
  ];
  if (summary?.fixedPayments) sections.push({ id: 'fixed', testId: 'dash-fixed' });
  if (summary?.cycleSoFar) sections.push({ id: 'cycleSoFar', testId: 'dash-cycle-so-far' });
  sections.push({ id: 'insights', testId: 'dash-insights' });
  if (summary?.savings) sections.push({ id: 'savings', testId: 'dash-savings' });
  sections.push({ id: 'funds', testId: 'dash-funds' });
  return sections;
}

const dayNumber = (isoDate) => {
  const [y, m, d] = String(isoDate).split('-').map(Number);
  const ms = Date.UTC(y, m - 1, d);
  return Number.isFinite(ms) ? ms / 86400000 : null;
};

/**
 * Sparkline from `GET …/balance-history/sparkline`: x is a day number so payday markers and minima sit at their
 * real dates whatever days the line has points for. With fewer than two points there is no line and no annotation
 * at all. The caption counts the minima the backend returned (closed cycles only), never a fixed 3.
 */
export function sparklineModel(data) {
  const points = (data?.points ?? [])
    .map((p) => ({ date: p?.date, x: p?.date ? dayNumber(p.date) : null, balance: toNumber(p?.balance) }))
    .filter((p) => p.x !== null && p.balance !== null);
  const minimaAll = (data?.cycleMinima ?? [])
    .map((m) => ({
      cycleStart: m?.cycleStart,
      date: m?.date,
      x: m?.date ? dayNumber(m.date) : null,
      balance: toNumber(m?.minBalance),
    }))
    .filter((m) => m.x !== null && m.balance !== null);

  if (points.length < 2) {
    return { hasLine: false, points, paydays: [], minima: [], minimaCount: 0, minimaValues: [] };
  }
  const first = points[0].x;
  const last = points[points.length - 1].x;
  const inRange = (x) => x >= first && x <= last;
  const paydays = (data?.paydays ?? [])
    .map((date) => ({ date, x: date ? dayNumber(date) : null }))
    .filter((p) => p.x !== null && inRange(p.x));
  return {
    hasLine: true,
    points,
    paydays,
    minima: minimaAll.filter((m) => inRange(m.x)),
    minimaCount: minimaAll.length,
    minimaValues: minimaAll.map((m) => m.balance),
    from: points[0].date,
    to: points[points.length - 1].date,
  };
}

/** Savings line: sign of the salary wallet's current-cycle net with the savings wallet (> 0 = moved from savings). */
export function savingsModel(savings) {
  if (!savings) return null;
  const net = toNumber(savings.netFlowThisCycle) ?? 0;
  return {
    name: savings.counterWalletName,
    balance: toNumber(savings.balance),
    direction: net > 0 ? 'fromSavings' : net < 0 ? 'toSavings' : 'none',
    amount: Math.abs(net),
  };
}

/**
 * "Spent so far" line. `day` is the cycle day the figures are read at (`period.daysElapsed`). A missing typical
 * (no baseline cycle reached that day) is stated as such; `deltaVsTypical` is deliberately not part of the model —
 * the line is neutral, the verdict belongs to the hero.
 */
export function cycleSoFarModel(cycleSoFar, period) {
  if (!cycleSoFar) return null;
  const typical = toNumber(cycleSoFar.typicalVariableByToday);
  return {
    fixedSpent: toNumber(cycleSoFar.fixedSpent) ?? 0,
    variableSpent: toNumber(cycleSoFar.variableSpent) ?? 0,
    day: toNumber(period?.daysElapsed),
    typical,
    hasTypical: typical !== null,
  };
}

export const FIXED_TILE_V2_ROWS = 3;

/**
 * Compact committed list: the first 3 unpaid occurrences of `fixedPayments.upcomingOccurrences` (already the
 * server's pending-in-window rows in due order — never re-bucketed here), the overdue total the DTO carries, and
 * one coverage line from the Forecast v2 verdict: FINE / TIGHT mean nothing is uncoverable, SHORT that something
 * is, anything else (awaiting salary, reporting) says nothing.
 *
 * A reporting period (LAST_PAY_CYCLE is the only one the backend sends `fixedPayments` for) is a closed window:
 * no coverage verdict and no forward-looking "before payday" copy — an empty list reads as "no unpaid payments
 * in this period". `emptyNote` is the key of the line shown when there are no rows (null: no line, e.g. while an
 * overdue total is shown, so "none left" never contradicts it).
 */
export function fixedTileV2Model(fixedPayments, forecastStatus, { reporting = false } = {}) {
  const rows = (fixedPayments?.upcomingOccurrences ?? [])
    .filter((occ) => occ.status === 'PENDING' || occ.status === 'OVERDUE')
    .slice(0, FIXED_TILE_V2_ROWS);
  const overdue = toNumber(fixedPayments?.overdueAmount);
  const overdueAmount = overdue !== null && overdue > 0 ? overdue : null;
  const coverage = reporting
    ? null
    : forecastStatus === 'FINE' || forecastStatus === 'TIGHT'
      ? 'covered'
      : forecastStatus === 'SHORT'
        ? 'notCovered'
        : null;
  const emptyNote = rows.length > 0 || overdueAmount !== null
    ? null
    : reporting ? 'fixedNoneUnpaidInPeriod' : 'fixedNoneUpcoming';
  return { rows, overdueAmount, coverage, emptyNote };
}
