// Stage 5.4 trajectory model: cumulative pace-eligible variable spend of the open cycle against the historical
// p25–p75 band and median (ForecastV2Dto.trajectory), with the committed fixed payments as annotations at their
// due days. Pure (node-testable). Every plotted value comes from the backend series — nothing is extrapolated
// past today and no band is invented for days no baseline cycle reached.

const toNumber = (value) => {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
};

/** Whole days from `start` to `date` + 1 (day 1 = the cycle's first day); null for unparsable input. */
export function cycleDayIndex(start, date) {
  if (!start || !date) return null;
  const a = Date.UTC(...String(start).split('-').map((v, i) => (i === 1 ? Number(v) - 1 : Number(v))));
  const b = Date.UTC(...String(date).split('-').map((v, i) => (i === 1 ? Number(v) - 1 : Number(v))));
  if (!Number.isFinite(a) || !Number.isFinite(b)) return null;
  return Math.round((b - a) / 86400000) + 1;
}

/**
 * @param forecast  ForecastV2Dto (open cycle)
 * @param projData  the enclosing SpendingProjectionDto (startDate, daysInPeriod, daysElapsed)
 * @returns {{
 *   rows: Array<{ day: number, date: string|null, actual: number|null, band: [number, number]|null,
 *                 median: number|null, cycles: number|null }>,
 *   markers: Array<{ day: number, date: string, amount: number|null, title: string|null }>,
 *   today: number|null, daysInPeriod: number, hasActual: boolean, hasBand: boolean,
 *   stats: { spentSoFar: number|null, typicalByToday: number|null, typicalCyclesByToday: number|null,
 *            committedRemaining: number|null }
 * }}
 */
export function trajectoryModel(forecast, projData) {
  const f = forecast ?? {};
  const trajectory = f.trajectory ?? {};
  const current = Array.isArray(trajectory.current) ? trajectory.current : [];
  const typical = Array.isArray(trajectory.typical) ? trajectory.typical : [];
  const daysInPeriod = Math.max(0, toNumber(projData?.daysInPeriod) ?? 0,
    ...typical.map((p) => toNumber(p.dayIndex) ?? 0),
    ...current.map((p) => toNumber(p.dayIndex) ?? 0));

  const byDay = new Map();
  const row = (day) => {
    if (!byDay.has(day)) {
      byDay.set(day, { day, date: null, actual: null, band: null, median: null, cycles: null });
    }
    return byDay.get(day);
  };
  for (const p of current) {
    const day = toNumber(p.dayIndex);
    if (day === null) continue;
    const r = row(day);
    r.date = p.date ?? r.date;
    r.actual = toNumber(p.cumulative);
  }
  for (const p of typical) {
    const day = toNumber(p.dayIndex);
    const p25 = toNumber(p.p25);
    const p75 = toNumber(p.p75);
    if (day === null) continue;
    const r = row(day);
    r.date = r.date ?? p.date ?? null;
    r.band = p25 !== null && p75 !== null ? [Math.min(p25, p75), Math.max(p25, p75)] : null;
    r.median = toNumber(p.median);
    r.cycles = toNumber(p.cycles);
  }
  const markers = (Array.isArray(f.committedOccurrences) ? f.committedOccurrences : [])
    .map((o) => ({
      day: cycleDayIndex(projData?.startDate, o.dueDate),
      date: o.dueDate,
      amount: toNumber(o.expectedAmount),
      title: o.title ?? null,
    }))
    // Stage 5.4 "fixed payments as annotations at due dates": markers, not steps — the plotted curve is variable
    // spend only, so fixed amounts must not be stepped into it. An OVERDUE occurrence due before day 1 lies outside
    // the cycle's x-domain and is not drawn (never moved to day 1); it still counts in `committedRemaining` below.
    .filter((m) => m.day !== null && m.day >= 1 && m.day <= daysInPeriod);

  // a marker day with no series point still gets an (empty) row so its tooltip is reachable — no values invented
  for (const m of markers) {
    const r = row(m.day);
    r.date = r.date ?? m.date;
  }
  const rows = [...byDay.values()].sort((a, b) => a.day - b.day);

  const lastActual = [...rows].reverse().find((r) => r.actual !== null) ?? null;
  const today = lastActual?.day ?? null;
  const todayBand = today !== null ? rows.find((r) => r.day === today && r.median !== null) ?? null : null;

  return {
    rows,
    markers,
    today,
    daysInPeriod,
    hasActual: lastActual !== null,
    hasBand: rows.some((r) => r.band !== null),
    stats: {
      // the pace-eligible spend so far: the same Σ the pace, forecast and one-off impacts use
      spentSoFar: toNumber(f.variableToDate) ?? lastActual?.actual ?? null,
      typicalByToday: todayBand?.median ?? null,
      typicalCyclesByToday: todayBand?.cycles ?? null,
      committedRemaining: toNumber(f.committed),
    },
  };
}
