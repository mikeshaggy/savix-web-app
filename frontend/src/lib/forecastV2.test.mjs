// Stage 5.1–5.3 presentation logic. Run with `npm test` (node --test, no extra dependencies).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  dashboardHeroVariant,
  forecastPageVariant,
  forecastStatusTone,
  formatWholeCurrency,
  headlineModel,
  paceRowsModel,
  rangeBasisKey,
  roundTo10,
} from './forecastV2.js';

// ─── Fixtures shaped like the Stage 4.7 payloads ────────────────────────────────────────────────────────────
const OPEN_PERIOD = { type: 'PAY_CYCLE', reporting: false, daysElapsed: 6, daysRemaining: 24, daysInPeriod: 30 };

// Dashboard cycleHealth with forecast-v2 on: legacy `status` null, v2 verdict in `forecastStatus`.
const v2CycleHealth = (overrides = {}) => ({
  status: null,
  currentBalance: 7902.62,
  safeToSpend: 1572.43,
  safeToSpendPerDay: 65.52,
  projectedEndBalance: -3448.8,
  spendingPaceDeltaPercent: 41.2,
  projectionAvailable: true,
  projectionReason: null,
  forecastStatus: 'FINE',
  discretionaryNow: 5102.62,
  discretionaryPerDay: 212.61,
  expectedEndBalanceTypical: 1232.48,
  expectedEndBalanceLow: 384.1,
  expectedEndBalanceHigh: 2106.95,
  confidence: 'HIGH',
  oneOffCount: 1,
  committed: 2800,
  ...overrides,
});

// Legacy dashboard cycleHealth (flag off): no v2 fields.
const legacyCycleHealth = {
  status: 'DANGER',
  currentBalance: 7902.62,
  safeToSpend: 1572.43,
  safeToSpendPerDay: 65.52,
  projectedEndBalance: -3448.8,
  spendingPaceDeltaPercent: 41.2,
  projectionAvailable: true,
  projectionReason: null,
};

// Forecast page ForecastV2Dto (OPEN).
const v2Forecast = (overrides = {}) => ({
  status: 'FINE',
  confidence: 'HIGH',
  discretionaryNow: 5102.62,
  discretionaryPerDay: 212.61,
  committed: 2800,
  expectedVariableRemaining: 3870.14,
  expectedEndBalanceTypical: 1232.48,
  expectedEndBalanceLow: 384.1,
  expectedEndBalanceHigh: 2106.95,
  trimmedDailyPace: 326.2,
  rawDailyBurnRate: 258.67,
  historicalTypicalPerDay: 120.02,
  historyWeight: 0.8,
  baselineCyclesUsed: 6,
  baselineCycles: [],
  oneOffs: [{ transactionId: 1, amount: 300, shareOfVariable: 19.33, excluded: false }],
  committedOccurrences: [],
  projectionReason: null,
  ...overrides,
});

const openProjection = (forecast) => ({
  periodType: 'PAY_CYCLE',
  projectionAvailable: true,
  projectionReason: null,
  daysElapsed: 6,
  daysRemaining: 24,
  daysInPeriod: 30,
  // deprecated legacy fields still served during the comparison window
  dailyBurnRate: 258.67,
  safeToSpendPerDay: 65.52,
  safeToSpendToday: 1572.43,
  projectedEndBalance: -3448.8,
  projectedVariableRemaining: 6208.08,
  forecast,
});

// ─── Status tone: only TIGHT / SHORT render a verdict ───────────────────────────────────────────────────────
test('FINE, null and unknown statuses render no verdict', () => {
  for (const s of ['FINE', null, undefined, 'WATCH', 'ON_TRACK', 'WARNING', 'DANGER', 'fine']) {
    assert.equal(forecastStatusTone(s), null, `status ${s}`);
  }
});

test('TIGHT is amber, SHORT is red', () => {
  assert.equal(forecastStatusTone('TIGHT'), 'amber');
  assert.equal(forecastStatusTone('SHORT'), 'red');
});

// ─── Rounding / formatting ──────────────────────────────────────────────────────────────────────────────────
test('roundTo10 rounds to the nearest 10 PLN, symmetric around zero', () => {
  assert.equal(roundTo10(1232.48), 1230);
  assert.equal(roundTo10(1235), 1240);
  assert.equal(roundTo10('2106.95'), 2110);
  assert.equal(roundTo10(-1235), -1240);
  assert.equal(roundTo10(-4), 0);
  assert.ok(!Object.is(roundTo10(-4), -0), 'no negative zero');
  assert.equal(roundTo10(null), null);
  assert.equal(roundTo10(undefined), null);
  assert.equal(roundTo10('abc'), null);
});

test('formatWholeCurrency drops the decimals in both locales', () => {
  // Intl separates with (narrow) no-break spaces; normalise for the comparison
  const plain = (s) => s.replace(/[\u00a0\u202f]/g, ' ');
  assert.equal(plain(formatWholeCurrency(1230, 'en')), 'PLN 1,230');
  assert.match(plain(formatWholeCurrency(1230, 'pl')), /^1 ?230 PLN$/);
  // Stage 5.7: typographic minus, so "≈ −PLN 380" never reads "~-PLN 380"
  assert.equal(plain(formatWholeCurrency(-380, 'en')), '\u2212PLN 380');
  assert.match(plain(formatWholeCurrency(-380, 'pl')), /^\u2212380 PLN$/);
  assert.ok(!formatWholeCurrency(-380, 'en').includes('-'));
});

// ─── Dashboard hero gating (5.1) ────────────────────────────────────────────────────────────────────────────
test('flag off: open salary cycle keeps the legacy hero', () => {
  assert.equal(dashboardHeroVariant({ forecastV2: false, cycleHealth: legacyCycleHealth, period: OPEN_PERIOD }), 'legacy');
});

test('flag on: open salary cycle renders the v2 hero even though legacy status is null', () => {
  assert.equal(dashboardHeroVariant({ forecastV2: true, cycleHealth: v2CycleHealth(), period: OPEN_PERIOD }), 'v2');
});

test('AWAITING_SALARY keeps the Stage 2 hero in both flag states (no invented verdict)', () => {
  const awaiting = {
    status: null, currentBalance: 812.5, projectionAvailable: false, projectionReason: 'AWAITING_SALARY',
    forecastStatus: null, discretionaryNow: -120, committed: 932.5,
  };
  for (const forecastV2 of [false, true]) {
    assert.equal(dashboardHeroVariant({ forecastV2, cycleHealth: awaiting, period: OPEN_PERIOD }), 'awaiting');
  }
});

test('reporting periods and non-salary wallets stay reporting in both flag states', () => {
  for (const forecastV2 of [false, true]) {
    // MONTHLY / CUSTOM / LAST_PAY_CYCLE → period.reporting
    assert.equal(
      dashboardHeroVariant({ forecastV2, cycleHealth: null, period: { type: 'MONTHLY', reporting: true } }),
      'reporting');
    // reporting flag wins even if a cycleHealth object were present
    assert.equal(
      dashboardHeroVariant({ forecastV2, cycleHealth: v2CycleHealth(), period: { type: 'LAST_PAY_CYCLE', reporting: true } }),
      'reporting');
    // non-salary wallet: MONTHLY + cycleHealth null (Stage 2 T7)
    assert.equal(
      dashboardHeroVariant({ forecastV2, cycleHealth: null, period: { type: 'MONTHLY', reporting: true, salaryWallet: false } }),
      'reporting');
  }
});

// ─── Forecast page gating (5.2 / 5.3) ───────────────────────────────────────────────────────────────────────
test('forecast page: no data → nothing; reporting when no projection; v2 only with the flag', () => {
  assert.equal(forecastPageVariant({ forecastV2: true, projData: null }), null);
  assert.equal(forecastPageVariant({ forecastV2: false, projData: openProjection(null) }), 'legacy');
  assert.equal(forecastPageVariant({ forecastV2: true, projData: openProjection(v2Forecast()) }), 'v2');
});

test('forecast page: AWAITING_SALARY, MONTHLY, LAST_PAY_CYCLE and non-salary stay reporting with the flag on', () => {
  const awaiting = {
    periodType: 'PAY_CYCLE', projectionAvailable: false, projectionReason: 'AWAITING_SALARY',
    forecast: { status: null, discretionaryNow: -120, committed: 932.5, projectionReason: 'AWAITING_SALARY' },
  };
  const monthly = { periodType: 'MONTHLY', projectionAvailable: false, projectionReason: 'REPORTING_PERIOD', forecast: null };
  const last = { periodType: 'LAST_PAY_CYCLE', projectionAvailable: false, projectionReason: 'CLOSED_CYCLE', forecast: null };
  // non-salary wallet: resolved as MONTHLY (Stage 2 T7) → reporting, no forecast
  const nonSalary = { periodType: 'MONTHLY', projectionAvailable: false, projectionReason: 'REPORTING_PERIOD', forecast: null };
  for (const projData of [awaiting, monthly, last, nonSalary]) {
    assert.equal(forecastPageVariant({ forecastV2: true, projData }), 'reporting');
    assert.equal(forecastPageVariant({ forecastV2: false, projData }), 'reporting');
  }
});

// ─── Headline model (5.1 / 5.2) ─────────────────────────────────────────────────────────────────────────────
test('dashboard headline reads the v2 fields, never the legacy verdict figures', () => {
  const ch = v2CycleHealth();
  const m = headlineModel(ch, { status: ch.forecastStatus, daysRemaining: 24 });
  assert.equal(m.discretionaryNow, 5102.62); // not currentBalance, not legacy safeToSpend
  assert.equal(m.discretionaryPerDay, 212.61); // not legacy safeToSpendPerDay
  assert.equal(m.daysRemaining, 24);
  assert.equal(m.typical, 1230); // not legacy projectedEndBalance
  assert.equal(m.rangeLow, 380);
  assert.equal(m.rangeHigh, 2110);
  assert.equal(m.hasRange, true);
  assert.equal(m.status, null, 'FINE renders no status word');
  assert.equal(m.tone, null);
});

test('forecast-page headline and dashboard headline agree for the same v2 result', () => {
  const ch = v2CycleHealth();
  const f = v2Forecast();
  assert.deepEqual(
    headlineModel(f, { status: f.status, daysRemaining: 24 }),
    headlineModel(ch, { status: ch.forecastStatus, daysRemaining: 24 }));
});

test('TIGHT / SHORT carry a verdict; a null legacy status is never treated as one', () => {
  const tight = headlineModel(v2CycleHealth({ forecastStatus: 'TIGHT' }), { status: 'TIGHT', daysRemaining: 24 });
  assert.equal(tight.status, 'TIGHT');
  assert.equal(tight.tone, 'amber');
  const short = headlineModel(v2Forecast({ status: 'SHORT', discretionaryNow: -40 }), { status: 'SHORT', daysRemaining: 24 });
  assert.equal(short.status, 'SHORT');
  assert.equal(short.tone, 'red');
  assert.equal(short.discretionaryNow, -40);
  // legacy status null + forecastStatus null → no verdict, no "health_null"
  const none = headlineModel(v2CycleHealth({ forecastStatus: null }), { status: null, daysRemaining: 24 });
  assert.equal(none.status, null);
  assert.equal(none.tone, null);
});

test('range is always ordered low → high; missing bounds hide the range', () => {
  const swapped = headlineModel({ expectedEndBalanceLow: 900, expectedEndBalanceHigh: 100 }, {});
  assert.equal(swapped.rangeLow, 100);
  assert.equal(swapped.rangeHigh, 900);
  const partial = headlineModel({ expectedEndBalanceTypical: 500, expectedEndBalanceLow: 100 }, {});
  assert.equal(partial.hasRange, false);
});

test('empty / AWAITING-shaped forecast yields nulls, not zeros', () => {
  const m = headlineModel({ status: null, discretionaryNow: 812.5, projectionReason: 'AWAITING_SALARY' }, { status: null });
  assert.equal(m.discretionaryNow, 812.5);
  assert.equal(m.discretionaryPerDay, null);
  assert.equal(m.typical, null);
  assert.equal(m.hasRange, false);
  assert.equal(m.status, null);
  const empty = headlineModel(null, {});
  assert.equal(empty.discretionaryNow, null);
  assert.equal(empty.daysRemaining, null);
});

// ─── Pace panel rows (5.3) ──────────────────────────────────────────────────────────────────────────────────
test('pace rows are the plan rows in order, mapped to the v2 fields', () => {
  const rows = paceRowsModel(v2Forecast(), openProjection());
  assert.deepEqual(rows.map((r) => r.key),
    ['discretionaryPerDay', 'typicalPace', 'trimmedPace', 'rawAverage', 'committed', 'daysToPayday']);
  const byKey = Object.fromEntries(rows.map((r) => [r.key, r]));
  assert.equal(byKey.discretionaryPerDay.value, 212.61);
  assert.equal(byKey.typicalPace.value, 120.02); // historicalTypicalPerDay
  assert.equal(byKey.trimmedPace.value, 326.2); // trimmedDailyPace
  assert.equal(byKey.rawAverage.value, 258.67); // rawDailyBurnRate
  assert.equal(byKey.rawAverage.muted, true);
  assert.equal(byKey.committed.value, 2800);
  assert.equal(byKey.daysToPayday.value, 24);
  assert.equal(byKey.daysToPayday.kind, 'days');
});

test('pace rows never surface the retired legacy figures', () => {
  const rows = paceRowsModel(v2Forecast(), openProjection());
  const keys = rows.map((r) => r.key);
  for (const retired of ['reductionNeeded', 'requiredReduction', 'safeToSpendPerDay', 'safeDailyBudget', 'safeToSpend']) {
    assert.ok(!keys.includes(retired), retired);
  }
  const values = rows.map((r) => r.value);
  // legacy dailyBurnRate equals rawDailyBurnRate in the fixture; the other legacy numbers must not appear
  for (const legacy of [65.52, 1572.43, -3448.8, 6208.08]) {
    assert.ok(!values.includes(legacy), `legacy value ${legacy}`);
  }
});

test('zero-history user: typical pace unavailable (null), not zero', () => {
  const rows = paceRowsModel(v2Forecast({ historicalTypicalPerDay: null, baselineCyclesUsed: 0 }), openProjection());
  assert.equal(rows.find((r) => r.key === 'typicalPace').value, null);
});

// ─── Tester additions ───────────────────────────────────────────────────────────────────────────────────────
test('BigDecimal-as-string payloads are handled; blank / non-numeric never become 0', () => {
  const m = headlineModel({
    discretionaryNow: '5102.62', discretionaryPerDay: '', expectedEndBalanceTypical: 'n/a',
    expectedEndBalanceLow: '384.10', expectedEndBalanceHigh: '2106.95',
  }, { status: 'TIGHT', daysRemaining: '24' });
  assert.equal(m.discretionaryNow, 5102.62);
  assert.equal(m.discretionaryPerDay, null);
  assert.equal(m.typical, null);
  assert.equal(m.daysRemaining, 24);
  assert.equal(m.rangeLow, 380);
  assert.equal(m.rangeHigh, 2110);
});

test('a range that rounds to a single value still renders as a range (low == high)', () => {
  const m = headlineModel({ expectedEndBalanceLow: 501, expectedEndBalanceHigh: 504 }, {});
  assert.equal(m.hasRange, true);
  assert.equal(m.rangeLow, 500);
  assert.equal(m.rangeHigh, 500);
});

test('negative expected balances keep their sign after rounding (SHORT scenario)', () => {
  const m = headlineModel({
    discretionaryNow: -40, expectedEndBalanceTypical: -496, expectedEndBalanceLow: -904, expectedEndBalanceHigh: -96,
  }, { status: 'SHORT', daysRemaining: 3 });
  assert.equal(m.typical, -500);
  assert.equal(m.rangeLow, -900);
  assert.equal(m.rangeHigh, -100);
  assert.equal(m.tone, 'red');
});

test('flag on with the v2 dashboard shape but forecastStatus absent shows no verdict (legacy status not consulted)', () => {
  const ch = v2CycleHealth({ status: 'DANGER', forecastStatus: undefined });
  const m = headlineModel(ch, { status: ch.forecastStatus, daysRemaining: 24 });
  assert.equal(m.status, null);
  assert.equal(m.tone, null);
});

test('pace rows tolerate a missing forecast without inventing zeros', () => {
  const rows = paceRowsModel(null, { daysRemaining: 24 });
  for (const r of rows.filter((x) => x.key !== 'daysToPayday')) assert.equal(r.value, null, r.key);
  assert.equal(rows.find((r) => r.key === 'daysToPayday').value, 24);
});

// ─── Flag / payload mismatch (stale /api/me after a backend flag flip) ──────────────────────────────────────
test('stale flag on + legacy payload → legacy hero (no empty v2 placeholders)', () => {
  assert.equal(dashboardHeroVariant({ forecastV2: true, cycleHealth: legacyCycleHealth, period: OPEN_PERIOD }), 'legacy');
  assert.equal(forecastPageVariant({ forecastV2: true, projData: openProjection(null) }), 'legacy');
});

test('stale flag off + v2 payload (legacy status null) → v2 hero, never dashboard.health_null', () => {
  assert.equal(dashboardHeroVariant({ forecastV2: false, cycleHealth: v2CycleHealth(), period: OPEN_PERIOD }), 'v2');
});

test('flag off + legacy payload that also carries a legacy verdict stays legacy', () => {
  assert.equal(
    dashboardHeroVariant({ forecastV2: false, cycleHealth: { ...legacyCycleHealth, discretionaryNow: 10 }, period: OPEN_PERIOD }),
    'legacy');
});

test('last day of the cycle (R = 0) keeps daysRemaining 0 so the headline can hide the per-day split', () => {
  const m = headlineModel(v2CycleHealth({ discretionaryPerDay: 5102.62 }), { status: 'FINE', daysRemaining: 0 });
  assert.equal(m.daysRemaining, 0);
  const src = readFileSync(new URL('../components/forecast/ForecastV2Headline.jsx', import.meta.url), 'utf8');
  assert.match(src, /daysRemaining > 0/);
});

// ─── Stage 5.7 confidence-sensitive range basis ─────────────────────────────────────────────────────────────
test('range basis: only HIGH says "typical for you"; MEDIUM / LOW say what the range rests on; null says nothing', () => {
  assert.equal(rangeBasisKey('HIGH'), 'typicalForYou');
  assert.equal(rangeBasisKey('MEDIUM'), 'basisLimitedHistory');
  assert.equal(rangeBasisKey('LOW'), 'basisThisCycleOnly');
  for (const c of [null, undefined, 'WATCH', 'high']) assert.equal(rangeBasisKey(c), null);
  assert.equal(headlineModel(v2CycleHealth({ confidence: 'LOW' }), {}).rangeBasisKey, 'basisThisCycleOnly');
  assert.equal(headlineModel(v2Forecast({ confidence: 'MEDIUM' }), {}).rangeBasisKey, 'basisLimitedHistory');
  assert.equal(headlineModel({ expectedEndBalanceLow: 1, expectedEndBalanceHigh: 2 }, {}).rangeBasisKey, null);
});
