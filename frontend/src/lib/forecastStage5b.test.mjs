// Stage 5.4–5.6 presentation logic: trajectory, breakdown / one-offs / Exclude, projection cards.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cycleDayIndex, trajectoryModel } from './forecastTrajectory.js';
import { breakdownModel, createExcludeAction, excludeRequestBody, oneOffModel } from './forecastBreakdown.js';
import { projectionCardsModel } from './forecastCards.js';
import { forecastPageVariant } from './forecastV2.js';

// ─── Fixture: real local shape (2026-09-29, daily wallet), trimmed ─────────────────────────────────────────
const openForecast = (overrides = {}) => ({
  status: 'FINE',
  confidence: 'HIGH',
  discretionaryNow: 5284.6,
  discretionaryPerDay: 587.18,
  committed: 612.29,
  expectedVariableRemaining: 240.14,
  expectedVariableRemainingLow: 271.08, // pessimistic path = MORE spend
  expectedVariableRemainingHigh: 219.21, // optimistic path = LESS spend
  expectedEndBalanceTypical: 5044.46,
  expectedEndBalanceLow: 5013.52,
  expectedEndBalanceHigh: 5065.39,
  trimmedDailyPace: 0,
  rawDailyBurnRate: 59.62,
  historicalTypicalPerDay: 88.94,
  historyWeight: 0.3,
  baselineCyclesUsed: 6,
  variableToDate: 1252.02,
  historicalMedianRemaining: 800.46,
  paceProjection: 0,
  baselineCycles: [
    { start: '2026-08-10', end: '2026-09-08', lengthDays: 30, variableTotal: 3600.6, remainingFromDay: 900, normalised: 900 },
    { start: '2026-07-10', end: '2026-08-09', lengthDays: 31, variableTotal: 3000, remainingFromDay: 0, normalised: null },
  ],
  oneOffs: [
    {
      transactionId: 1282, date: '2026-09-13', title: 'Repayment', categoryName: 'misc', amount: 300,
      shareOfVariable: 19.33, impactOnExpectedVariableRemaining: 0, impactOnExpectedEndBalance: 0, excluded: true,
    },
  ],
  committedOccurrences: [
    { occurrenceId: 1, title: 'Rent', expectedAmount: 400, dueDate: '2026-10-01', bucket: 'LATER_THIS_CYCLE' },
    { occurrenceId: 2, title: 'Phone', expectedAmount: 212.29, dueDate: '2026-10-05', bucket: 'LATER_THIS_CYCLE' },
    { occurrenceId: 3, title: 'Overdue', expectedAmount: 10, dueDate: '2026-09-01', bucket: 'OVERDUE' },
  ],
  trajectory: {
    current: [
      { dayIndex: 1, date: '2026-09-09', cumulative: 94.98 },
      { dayIndex: 2, date: '2026-09-10', cumulative: 94.98 },
      { dayIndex: 3, date: '2026-09-11', cumulative: 430.62 },
    ],
    typical: [
      { dayIndex: 1, date: '2026-09-09', p25: 50, median: 80, p75: 120, cycles: 6 },
      { dayIndex: 2, date: '2026-09-10', p25: 100, median: 150, p75: 210, cycles: 6 },
      { dayIndex: 3, date: '2026-09-11', p25: 140, median: 200, p75: 260, cycles: 6 },
      { dayIndex: 4, date: '2026-09-12', p25: 180, median: 260, p75: 330, cycles: 5 },
    ],
  },
  projectionReason: null,
  ...overrides,
});

const projection = (forecast, overrides = {}) => ({
  periodType: 'PAY_CYCLE',
  projectionAvailable: true,
  startDate: '2026-09-09',
  endDate: '2026-10-08',
  daysInPeriod: 30,
  daysElapsed: 3,
  daysRemaining: 27,
  // deprecated legacy figures still served — must never feed a v2 model
  safeToSpendToday: 4619.41,
  safeToSpendPerDay: 513.27,
  projectedEndBalance: 4619.41,
  projectedPeriodExpenses: 4825.4,
  incomeForPeriod: 9444.81,
  forecast,
  ...overrides,
});

// ─── Gating shared by trajectory / breakdown / cards ───────────────────────────────────────────────────────
test('flag off keeps every Stage 5.4–5.6 panel legacy; flag on + forecast renders v2', () => {
  assert.equal(forecastPageVariant({ forecastV2: false, projData: projection(null) }), 'legacy');
  assert.equal(forecastPageVariant({ forecastV2: false, projData: projection(openForecast()) }), 'legacy');
  assert.equal(forecastPageVariant({ forecastV2: true, projData: projection(openForecast()) }), 'v2');
});

test('AWAITING_SALARY, reporting periods and non-salary wallets stay reporting (no trajectory/breakdown/cards)', () => {
  const awaiting = projection(
    { status: null, discretionaryNow: -120, committed: 932.5, projectionReason: 'AWAITING_SALARY', trajectory: null },
    { projectionAvailable: false, projectionReason: 'AWAITING_SALARY' });
  const monthly = projection(null, { periodType: 'MONTHLY', projectionAvailable: false, projectionReason: 'REPORTING_PERIOD' });
  const last = projection(null, { periodType: 'LAST_PAY_CYCLE', projectionAvailable: false, projectionReason: 'CLOSED_CYCLE' });
  for (const projData of [awaiting, monthly, last]) {
    for (const forecastV2 of [true, false]) {
      assert.equal(forecastPageVariant({ forecastV2, projData }), 'reporting');
    }
  }
});

// ─── 5.4 trajectory ─────────────────────────────────────────────────────────────────────────────────────────
test('cycleDayIndex counts the salary day as day 1 and survives month ends', () => {
  assert.equal(cycleDayIndex('2026-09-09', '2026-09-09'), 1);
  assert.equal(cycleDayIndex('2026-09-09', '2026-10-01'), 23);
  assert.equal(cycleDayIndex('2026-09-09', '2026-09-01'), -7);
  assert.equal(cycleDayIndex(null, '2026-09-01'), null);
});

test('trajectory: actual series through today, band + median from history, nothing past today', () => {
  const m = trajectoryModel(openForecast(), projection());
  assert.equal(m.today, 3);
  assert.equal(m.daysInPeriod, 30);
  assert.equal(m.hasActual, true);
  assert.equal(m.hasBand, true);
  const day3 = m.rows.find((r) => r.day === 3);
  assert.deepEqual(day3.band, [140, 260]);
  assert.equal(day3.median, 200);
  assert.equal(day3.actual, 430.62);
  const day4 = m.rows.find((r) => r.day === 4);
  assert.equal(day4.actual, null, 'no extrapolated actual after today');
  assert.equal(day4.median, 260);
  // rows exist only where the backend sent data (days 1–4) or a fixed payment is due (23, 27) — no padding
  assert.deepEqual(m.rows.map((r) => r.day), [1, 2, 3, 4, 23, 27]);
  const due = m.rows.find((r) => r.day === 23);
  assert.deepEqual({ actual: due.actual, band: due.band, median: due.median, date: due.date },
    { actual: null, band: null, median: null, date: '2026-10-01' });
});

test('trajectory stats: pace-eligible spend so far, typical by today (median + cycles), committed', () => {
  const { stats } = trajectoryModel(openForecast(), projection());
  assert.equal(stats.spentSoFar, 1252.02); // variableToDate (excluded one-off left out), not legacy expensesToDate
  assert.equal(stats.typicalByToday, 200);
  assert.equal(stats.typicalCyclesByToday, 6);
  assert.equal(stats.committedRemaining, 612.29);
});

test('trajectory markers: committed occurrences at their due day; overdue (before day 1) is off-chart', () => {
  const m = trajectoryModel(openForecast(), projection());
  assert.deepEqual(m.markers.map((x) => [x.day, x.title, x.amount]), [[23, 'Rent', 400], [27, 'Phone', 212.29]]);
});

test('trajectory without history: actual only, no band, no typical-by-today', () => {
  const f = openForecast({ baselineCyclesUsed: 0, confidence: 'LOW', trajectory: { current: openForecast().trajectory.current, typical: [] } });
  const m = trajectoryModel(f, projection());
  assert.equal(m.hasBand, false);
  assert.equal(m.hasActual, true);
  assert.equal(m.stats.typicalByToday, null);
});

test('trajectory empty / missing: no rows, no fabricated zeros', () => {
  const m = trajectoryModel(openForecast({ trajectory: null, variableToDate: null, committed: null }), projection());
  // only the fixed-payment days remain, as empty rows (no actual / band / median values)
  assert.ok(m.rows.every((r) => r.actual === null && r.band === null && r.median === null));
  assert.deepEqual(trajectoryModel(openForecast({ trajectory: null, committedOccurrences: [] }), projection()).rows, []);
  assert.equal(m.hasActual, false);
  assert.equal(m.hasBand, false);
  assert.equal(m.today, null);
  assert.equal(m.stats.spentSoFar, null);
  assert.equal(m.stats.committedRemaining, null);
});

test('trajectory model never reads legacy projection / income figures', () => {
  const m = trajectoryModel(openForecast(), projection());
  const json = JSON.stringify(m);
  for (const legacy of ['4619.41', '4825.4', '9444.81', '513.27']) assert.ok(!json.includes(legacy), legacy);
});

// ─── 5.5 breakdown ──────────────────────────────────────────────────────────────────────────────────────────
test('breakdown: balance − committed = left until payday (exact v2 arithmetic)', () => {
  const m = breakdownModel(openForecast());
  assert.equal(m.balance, 5896.89);
  assert.equal(m.committed, 612.29);
  assert.equal(m.discretionaryNow, 5284.6);
  assert.equal(Math.round((m.balance - m.committed) * 100) / 100, m.discretionaryNow);
});

test('breakdown: history median (weight w) + pace projection (weight 1 − w) = expected variable', () => {
  const m = breakdownModel(openForecast({ historyWeight: 0.8, historicalMedianRemaining: 2880.48, paceProjection: 7828.8, expectedVariableRemaining: 3870.14, trimmedDailyPace: 326.2 }));
  assert.equal(m.history.weightPercent, 80);
  assert.equal(m.pace.weightPercent, 20);
  assert.equal(m.history.median, 2880.48);
  assert.equal(m.pace.projection, 7828.8);
  assert.equal(m.pace.perDay, 326.2);
  const blended = Math.round((0.8 * m.history.median + 0.2 * m.pace.projection) * 100) / 100;
  assert.equal(blended, m.expectedVariable.typical);
});

test('breakdown: low / typical / high end balances rounded to 10; low is pessimistic (more spend)', () => {
  const m = breakdownModel(openForecast());
  assert.deepEqual(m.endBalance, { typical: 5040, low: 5010, high: 5070 });
  assert.equal(m.expectedVariable.pessimistic, 271.08);
  assert.equal(m.expectedVariable.optimistic, 219.21);
  assert.ok(m.expectedVariable.pessimistic > m.expectedVariable.optimistic);
});

test('breakdown: negative end balances keep their sign', () => {
  const m = breakdownModel(openForecast({ expectedEndBalanceTypical: -384.4, expectedEndBalanceLow: -912, expectedEndBalanceHigh: -96 }));
  assert.deepEqual(m.endBalance, { typical: -380, low: -910, high: -100 });
});

test('breakdown: baseline cycles listed; a cycle too short for today is marked unused', () => {
  const m = breakdownModel(openForecast());
  assert.deepEqual(m.baselineCycles.map((c) => c.used), [true, false]);
  assert.equal(m.baselineCycles[0].normalised, 900);
});

test('one-off shareOfVariable is percentage points — 19.33 stays 19.33, never 1933 or 0.1933', () => {
  const o = oneOffModel(openForecast().oneOffs[0], openForecast());
  assert.equal(o.sharePercent, 19.33);
});

test('included one-off: typical-if-excluded = typical + impact (backend impact is without − with), rounded to 10', () => {
  const f = openForecast({ expectedEndBalanceTypical: 1232.48 });
  const o = oneOffModel({ transactionId: 300, amount: 300, shareOfVariable: 19.33, impactOnExpectedEndBalance: 462.33, excluded: false }, f);
  assert.equal(o.excluded, false);
  assert.equal(o.impactOnEndBalance, 462.33);
  assert.equal(o.typicalIfExcluded, 1690); // 1232.48 + 462.33 = 1694.81 → 1690
});

test('excluded one-off: listed as excluded, no impact sentence (its impact is 0 by construction)', () => {
  const o = oneOffModel(openForecast().oneOffs[0], openForecast());
  assert.equal(o.excluded, true);
  assert.equal(o.impactOnEndBalance, null);
  assert.equal(o.typicalIfExcluded, null);
});

test('no one-offs → empty list', () => {
  assert.deepEqual(breakdownModel(openForecast({ oneOffs: [] })).oneOffs, []);
});

// ─── 5.5 Exclude mutation ───────────────────────────────────────────────────────────────────────────────────
const persisted = {
  id: 1282, walletId: 1, walletName: 'daily', categoryId: 9, categoryName: 'misc', categoryType: 'EXPENSE',
  title: 'Repayment', amount: 300, transactionDate: '2026-09-13', notes: 'n', importance: 'OPTIONAL',
  fixedPaymentOccurrenceId: null, excludedFromPace: false, createdAt: 'x',
};

test('excludeRequestBody re-sends every TransactionUpdateRequest field unchanged except excludedFromPace', () => {
  assert.deepEqual(excludeRequestBody(persisted), {
    walletId: 1, categoryId: 9, title: 'Repayment', amount: 300, transactionDate: '2026-09-13',
    notes: 'n', importance: 'OPTIONAL', excludedFromPace: true,
  });
});

function harness({ transaction = persisted, failSave = false } = {}) {
  const calls = [];
  let release;
  const gate = new Promise((r) => { release = r; });
  const exclude = createExcludeAction({
    load: async (id) => { calls.push(['load', id]); return { ...transaction }; },
    save: async (id, body) => {
      calls.push(['save', id, body.excludedFromPace]);
      await gate;
      if (failSave) throw new Error('boom');
    },
    refresh: async () => { calls.push(['refresh']); },
  });
  return { calls, exclude, release };
}

test('Exclude: load → save(excludedFromPace: true) → refresh, in that order', async () => {
  const h = harness();
  const p = h.exclude(1282);
  h.release();
  await p;
  assert.deepEqual(h.calls, [['load', 1282], ['save', 1282, true], ['refresh']]);
});

test('Exclude: a second click while in flight shares the first write (no duplicate request)', async () => {
  const h = harness();
  const a = h.exclude(1282);
  const b = h.exclude(1282);
  assert.equal(a, b);
  h.release();
  await Promise.all([a, b]);
  assert.equal(h.calls.filter((c) => c[0] === 'save').length, 1);
  assert.equal(h.calls.filter((c) => c[0] === 'refresh').length, 1);
});

test('Exclude: already excluded in the backend → no write, just revalidate', async () => {
  const h = harness({ transaction: { ...persisted, excludedFromPace: true } });
  const p = h.exclude(1282);
  h.release();
  await p;
  assert.deepEqual(h.calls, [['load', 1282], ['refresh']]);
});

test('Exclude: a failed write rejects, skips the refresh and allows a retry', async () => {
  const h = harness({ failSave: true });
  const p = h.exclude(1282);
  h.release();
  await assert.rejects(p, /boom/);
  assert.equal(h.calls.some((c) => c[0] === 'refresh'), false);
  const retry = h.exclude(1282);
  assert.notEqual(retry, p, 'the in-flight entry was cleared');
  await assert.rejects(retry, /boom/);
});

test('Exclude: missing id rejects without any request', async () => {
  const h = harness();
  await assert.rejects(h.exclude(null));
  assert.deepEqual(h.calls, []);
});

// ─── 5.6 projection cards ───────────────────────────────────────────────────────────────────────────────────
test('cards: exactly the four plan cards, in order, from v2 fields only', () => {
  const cards = projectionCardsModel(openForecast());
  assert.deepEqual(cards.map((c) => c.key), ['leftUntilPayday', 'expectedAtPayday', 'committed', 'expectedVariable']);
  const [left, expected, committed, variable] = cards;
  assert.equal(left.balance, 5896.89);
  assert.equal(left.committed, 612.29);
  assert.equal(expected.pessimistic, 5010);
  assert.equal(expected.optimistic, 5070);
  assert.equal(committed.value, 612.29);
  assert.equal(committed.count, 3);
  assert.equal(variable.value, 240.14);
  assert.equal(variable.lessSpend, 219.21);
  assert.equal(variable.moreSpend, 271.08);
  const json = JSON.stringify(cards);
  for (const legacy of ['4619.41', '513.27', '4825.4', '9444.81']) assert.ok(!json.includes(legacy), legacy);
});

test('cards: the hero headline figures are never repeated as card figures (5.6 "remove duplicates of the hero")', () => {
  const f = openForecast();
  const cards = projectionCardsModel(f);
  const context = cards.filter((c) => c.kind === 'context').map((c) => c.key);
  const figures = cards.filter((c) => c.kind === 'figure').map((c) => c.key);
  assert.deepEqual(context, ['leftUntilPayday', 'expectedAtPayday']);
  assert.deepEqual(figures, ['committed', 'expectedVariable']);
  for (const c of cards.filter((x) => x.kind === 'context')) assert.equal('value' in c, false, `${c.key} has a figure`);
  // neither discretionaryNow nor the rounded typical end balance is a card value
  const values = cards.filter((c) => c.kind === 'figure').map((c) => c.value);
  assert.ok(!values.includes(5284.6));
  assert.ok(!values.includes(5040));
});

test('cards: negative expected balances stay negative and ordered', () => {
  const [, expected] = projectionCardsModel(openForecast({ expectedEndBalanceTypical: -384.4, expectedEndBalanceLow: -912, expectedEndBalanceHigh: -96 }));
  assert.equal(expected.pessimistic, -910);
  assert.equal(expected.optimistic, -100);
});

test('cards: basis wording follows confidence (HIGH typical, MEDIUM limited, LOW this cycle only, null none)', () => {
  const basis = (confidence) => projectionCardsModel(openForecast({ confidence }))[1].basisKey;
  assert.equal(basis('HIGH'), 'typicalForYou');
  assert.equal(basis('MEDIUM'), 'basisLimitedHistory');
  assert.equal(basis('LOW'), 'basisThisCycleOnly');
  assert.equal(basis(null), null);
});

test('cards: FINE, TIGHT, SHORT and null status do not change the card figures (verdict lives in the hero)', () => {
  const base = JSON.stringify(projectionCardsModel(openForecast()));
  for (const status of ['TIGHT', 'SHORT', null]) {
    assert.equal(JSON.stringify(projectionCardsModel(openForecast({ status }))), base);
  }
});

// ─── Stage 5.7 negative currency formatting ─────────────────────────────────────────────────────────────────
test('exact negative amounts get the same typographic minus as rounded ones; positives untouched', async () => {
  const { withTypographicMinus, formatWholeCurrency } = await import('./forecastV2.js');
  assert.equal(withTypographicMinus('-PLN 140.50'), '−PLN 140.50');
  assert.equal(withTypographicMinus('-140,50 PLN'), '−140,50 PLN');
  assert.equal(withTypographicMinus('PLN 5,284.60'), 'PLN 5,284.60');
  assert.equal(withTypographicMinus(null), null);
  assert.ok(formatWholeCurrency(-380, 'en').startsWith('−'));
});

test('Exclude: a failed refresh after a persisted write is REFRESH_FAILED, not a failed write', async () => {
  const calls = [];
  const exclude = createExcludeAction({
    load: async () => ({ ...persisted }),
    save: async () => { calls.push('save'); },
    refresh: async () => { throw new Error('network'); },
  });
  await assert.rejects(exclude(1282), (err) => err.code === 'REFRESH_FAILED' && err.cause.message === 'network');
  assert.deepEqual(calls, ['save']);
});

test('cards: the expected-variable spread is ordered less → more even if the bounds arrive swapped', () => {
  const [, , , variable] = projectionCardsModel(openForecast({ expectedVariableRemainingHigh: 300, expectedVariableRemainingLow: 200 }));
  assert.equal(variable.lessSpend, 200);
  assert.equal(variable.moreSpend, 300);
});
