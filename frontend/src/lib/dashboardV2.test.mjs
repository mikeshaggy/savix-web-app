// Stage 6.5–6.7: dashboard v2 gating, section order, view models and en/pl copy.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { createTranslator } from 'next-intl';
import {
  cycleSoFarModel,
  dashboardLayoutVariant,
  dashboardV2Sections,
  fixedTileV2Model,
  savingsModel,
  sparklineModel,
} from './dashboardV2.js';

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..');
const LOCALES = ['en', 'pl'];
const messages = Object.fromEntries(
  LOCALES.map((l) => [l, JSON.parse(readFileSync(join(SRC, 'messages', `${l}.json`), 'utf8'))]));

test('v2 layout needs dashboard-v2 AND forecast-v2', () => {
  assert.equal(dashboardLayoutVariant({ dashboardV2: true, forecastV2: true }), 'v2');
  assert.equal(dashboardLayoutVariant({ dashboardV2: true, forecastV2: false }), 'legacy');
  assert.equal(dashboardLayoutVariant({ dashboardV2: false, forecastV2: true }), 'legacy');
  assert.equal(dashboardLayoutVariant({ dashboardV2: false, forecastV2: false }), 'legacy');
  assert.equal(dashboardLayoutVariant(undefined), 'legacy');
});

test('sections come in the fixed order and optional ones follow the summary', () => {
  const ids = (s) => dashboardV2Sections(s).map((x) => x.id);
  assert.deepEqual(ids({ fixedPayments: {}, cycleSoFar: {}, savings: {} }),
    ['hero', 'sparkline', 'fixed', 'cycleSoFar', 'insights', 'savings', 'funds']);
  assert.deepEqual(ids({}), ['hero', 'sparkline', 'insights', 'funds']);
  assert.deepEqual(dashboardV2Sections({ fixedPayments: {}, cycleSoFar: {}, savings: {} }).map((x) => x.testId),
    ['dash-hero', 'dash-sparkline', 'dash-fixed', 'dash-cycle-so-far', 'dash-insights', 'dash-savings', 'dash-funds']);
});

test('sparkline: line, actual paydays, minima and the caption count', () => {
  const m = sparklineModel({
    points: [{ date: '2026-07-01', balance: 100 }, { date: '2026-07-02', balance: '90.5' }, { date: '2026-09-20', balance: 50 }],
    paydays: ['2026-07-10', '2026-06-01'],
    cycleMinima: [
      { cycleStart: '2026-07-10', date: '2026-07-15', minBalance: -93.29 },
      { cycleStart: '2026-06-10', date: '2026-06-20', minBalance: 12 },
    ],
  });
  assert.equal(m.hasLine, true);
  assert.deepEqual(m.paydays.map((p) => p.date), ['2026-07-10']);
  assert.deepEqual(m.minima.map((x) => x.date), ['2026-07-15']);
  assert.equal(m.minimaCount, 2); // actual count returned, even when one lies outside the drawn window
  assert.deepEqual(m.minimaValues, [-93.29, 12]);
});

test('sparkline: fewer than two points draws nothing and annotates nothing', () => {
  for (const data of [null, {}, { points: [{ date: '2026-07-01', balance: 1 }], paydays: ['2026-07-01'],
    cycleMinima: [{ date: '2026-07-01', minBalance: 1 }] }]) {
    const m = sparklineModel(data);
    assert.equal(m.hasLine, false);
    assert.deepEqual([m.paydays, m.minima, m.minimaCount], [[], [], 0]);
  }
});

test('sparkline: no minima means no caption count', () => {
  const m = sparklineModel({ points: [{ date: '2026-07-01', balance: 1 }, { date: '2026-07-02', balance: 2 }], cycleMinima: [] });
  assert.equal(m.minimaCount, 0);
});

test('savings sign semantics', () => {
  assert.equal(savingsModel(null), null);
  const s = (net) => savingsModel({ counterWalletName: 'Savings', balance: 460.01, netFlowThisCycle: net });
  assert.deepEqual([s(300).direction, s(300).amount], ['fromSavings', 300]);
  assert.deepEqual([s(-750).direction, s(-750).amount], ['toSavings', 750]);
  assert.deepEqual([s(0).direction, s(0).amount], ['none', 0]);
  assert.equal(s(0).balance, 460.01);
});

test('cycle so far: typical may be missing and no delta is exposed', () => {
  const withTypical = cycleSoFarModel({ fixedSpent: 1, variableSpent: 2, typicalVariableByToday: 3, deltaVsTypical: -1 }, { daysElapsed: 6 });
  assert.deepEqual(withTypical, { fixedSpent: 1, variableSpent: 2, day: 6, typical: 3, hasTypical: true });
  const none = cycleSoFarModel({ fixedSpent: 1, variableSpent: 2, typicalVariableByToday: null, deltaVsTypical: null }, { daysElapsed: 32 });
  assert.equal(none.hasTypical, false);
  assert.equal(cycleSoFarModel(null, {}), null);
  assert.equal(cycleSoFarModel({ typicalVariableByToday: 0 }, {}).hasTypical, true);
});

test('fixed tile: 3 rows, coverage only from FINE/TIGHT/SHORT, overdue from the DTO total', () => {
  const occ = (id, status = 'PENDING') => ({ id, status, name: `n${id}` });
  const fp = { upcomingOccurrences: [occ(1), occ(2), occ(3), occ(4)], overdueAmount: 25 };
  assert.deepEqual(fixedTileV2Model(fp, 'FINE').rows.map((r) => r.id), [1, 2, 3]);
  assert.equal(fixedTileV2Model(fp, 'FINE').coverage, 'covered');
  assert.equal(fixedTileV2Model(fp, 'TIGHT').coverage, 'covered');
  assert.equal(fixedTileV2Model(fp, 'SHORT').coverage, 'notCovered');
  assert.equal(fixedTileV2Model(fp, null).coverage, null);
  assert.equal(fixedTileV2Model(fp, undefined).coverage, null);
  assert.equal(fixedTileV2Model(fp, 'FINE').overdueAmount, 25);
  assert.equal(fixedTileV2Model({ upcomingOccurrences: [], overdueAmount: 0 }, 'FINE').overdueAmount, null);
  assert.deepEqual(fixedTileV2Model({ upcomingOccurrences: [occ(1, 'PAID')] }, null).rows, []);
});

const NEW_KEYS = {
  lowestBeforePayday: { count: 3, values: 'a / b / c' },
  sparklineTitle: { days: 90 },
  sparklineAsOf: {},
  sparklineEmpty: {},
  sparklineError: {},
  sparklinePayday: {},
  cycleSoFar: { fixed: '1', variable: '2' },
  typicalByToday: { day: 6, typical: '3' },
  typicalNone: { day: 6 },
  cycleSoFarHint: {},
  nothingUnusual: {},
  savingsBalance: { name: 'S', balance: '4' },
  savingsNetFlow: { direction: 'fromSavings', amount: '5' },
  fixedCovered: {},
  fixedNotCovered: {},
  fixedSeeForecast: {},
  fixedOverdue: { amount: '6' },
  fixedNoneUpcoming: {},
  fixedNoneUnpaidInPeriod: {},
  sparklineLowestOfCycle: {},
  nothingUnusualPeriod: {},
};

test('new dashboard copy exists in en and pl and formats without errors', () => {
  for (const locale of LOCALES) {
    const t = createTranslator({
      locale, messages: messages[locale], namespace: 'dashboard',
      onError: (e) => { throw e; }, getMessageFallback: ({ key }) => { throw new Error(`missing ${key}`); },
    });
    for (const [key, values] of Object.entries(NEW_KEYS)) {
      assert.ok(t(key, values).length > 0, `${locale}.dashboard.${key}`);
    }
    for (const count of [1, 2, 3, 5, 22]) {
      assert.ok(!t('lowestBeforePayday', { count, values: 'x' }).includes('{'), `${locale} plural ${count}`);
    }
    for (const direction of ['fromSavings', 'toSavings', 'none']) {
      assert.ok(t('savingsNetFlow', { direction, amount: '9' }).length > 0);
    }
  }
});

test('savings copy states the direction from the salary wallet side', () => {
  const t = createTranslator({ locale: 'en', messages: messages.en, namespace: 'dashboard' });
  assert.match(t('savingsNetFlow', { direction: 'fromSavings', amount: 'X' }), /moved from savings/);
  assert.match(t('savingsNetFlow', { direction: 'toSavings', amount: 'X' }), /moved to savings/);
  assert.match(t('savingsNetFlow', { direction: 'none', amount: 'X' }), /No transfers/);
});

test('obsolete Overview keys are gone and the orphan component is deleted', () => {
  for (const locale of LOCALES) {
    assert.equal(messages[locale].nav.analyticsOverview, undefined);
    assert.equal(messages[locale].analytics.navOverview, undefined);
    assert.deepEqual(Object.keys(messages[locale].analytics).filter((k) => k.startsWith('overview')), []);
    assert.ok(messages[locale].dashboard.vsLastPeriod, 'still referenced until §9');
  }
  assert.equal(existsSync(join(SRC, 'components/dashboard/TopCategories.jsx')), false);
});

// ---------------------------------------------------------------------------------------------------------------
// Tester additions: gating, section conditions, view-model edge cases, copy, legacy-layout safety.

const SECTION_ORDER = ['hero', 'sparkline', 'fixed', 'cycleSoFar', 'insights', 'savings', 'funds'];
const idsOf = (summary) => dashboardV2Sections(summary).map((s) => s.id);

test('gate: only literal true && true selects v2; null, partial and non-boolean features stay legacy', () => {
  for (const features of [{}, { dashboardV2: true }, { forecastV2: true }, { dashboardV2: 'true', forecastV2: 'true' },
    { dashboardV2: 1, forecastV2: 1 }, { payCycleV2: true, insightsV2: true }]) {
    assert.equal(dashboardLayoutVariant(features), 'legacy', JSON.stringify(features));
  }
  assert.equal(dashboardLayoutVariant({ dashboardV2: true, forecastV2: true, payCycleV2: false }), 'v2');
});

test('gate: null / undefined features are legacy, not a crash', () => {
  assert.equal(dashboardLayoutVariant(null), 'legacy');
  assert.equal(dashboardLayoutVariant(undefined), 'legacy');
});

test('sections: every optional section is independently conditional, order never changes', () => {
  const full = { fixedPayments: {}, cycleSoFar: {}, savings: {} };
  assert.deepEqual(idsOf(full), SECTION_ORDER);
  assert.deepEqual(idsOf({ ...full, fixedPayments: null }), SECTION_ORDER.filter((s) => s !== 'fixed'));
  assert.deepEqual(idsOf({ ...full, cycleSoFar: null }), SECTION_ORDER.filter((s) => s !== 'cycleSoFar'));
  assert.deepEqual(idsOf({ ...full, savings: null }), SECTION_ORDER.filter((s) => s !== 'savings'));
  assert.deepEqual(idsOf({ ...full, savings: undefined, cycleSoFar: undefined }), ['hero', 'sparkline', 'fixed', 'insights', 'funds']);
  assert.deepEqual(idsOf(null), ['hero', 'sparkline', 'insights', 'funds']);
  // reporting-only period (monthly/custom): backend sends fixedPayments null (or a tile), no cycleSoFar / savings
  assert.deepEqual(idsOf({ fixedPayments: null, cycleSoFar: null, savings: null }), ['hero', 'sparkline', 'insights', 'funds']);
});

test('sections: nothing legacy is ever listed, test ids are unique', () => {
  const all = dashboardV2Sections({ fixedPayments: {}, cycleSoFar: {}, savings: {} });
  assert.equal(new Set(all.map((s) => s.testId)).size, all.length);
  for (const s of all) {
    assert.doesNotMatch(s.id, /summaryCards|categoryPressure|previousCycle|kpi/i);
  }
});

test('sparkline: empty and sparse payloads', () => {
  for (const data of [undefined, { points: [] }, { points: null, paydays: null, cycleMinima: null }]) {
    const m = sparklineModel(data);
    assert.deepEqual([m.hasLine, m.points.length, m.paydays.length, m.minima.length, m.minimaCount], [false, 0, 0, 0, 0]);
  }
  // exactly two points is the smallest drawable line; missing paydays / minima arrays are fine
  const two = sparklineModel({ points: [{ date: '2026-07-01', balance: '10.00' }, { date: '2026-07-02', balance: '20.50' }] });
  assert.equal(two.hasLine, true);
  assert.deepEqual(two.points.map((p) => p.balance), [10, 20.5]);
  assert.deepEqual([two.paydays, two.minima, two.minimaCount, two.minimaValues], [[], [], 0, []]);
});

test('sparkline: unusable points are dropped instead of drawn as zero/NaN', () => {
  const m = sparklineModel({
    points: [{ date: '2026-07-01', balance: null }, { date: null, balance: 5 }, { date: '2026-07-02', balance: 'abc' },
      { date: '2026-07-03', balance: '0.00' }, { date: '2026-07-04', balance: -12.5 }],
  });
  assert.deepEqual(m.points.map((p) => p.date), ['2026-07-03', '2026-07-04']);
  assert.equal(m.points[0].balance, 0); // a real zero balance is kept
  assert.ok(m.points.every((p) => Number.isFinite(p.x) && Number.isFinite(p.balance)));
});

test('sparkline: x is a UTC day number, so consecutive dates are exactly 1 apart across DST changes and year ends', () => {
  const m = sparklineModel({
    points: [
      { date: '2026-03-28', balance: 1 }, { date: '2026-03-29', balance: 1 }, { date: '2026-03-30', balance: 1 },
      { date: '2026-10-24', balance: 1 }, { date: '2026-10-25', balance: 1 }, { date: '2026-10-26', balance: 1 },
      { date: '2026-12-31', balance: 1 }, { date: '2027-01-01', balance: 1 },
    ],
  });
  const x = Object.fromEntries(m.points.map((p) => [p.date, p.x]));
  assert.equal(x['2026-03-29'] - x['2026-03-28'], 1);
  assert.equal(x['2026-03-30'] - x['2026-03-29'], 1);
  assert.equal(x['2026-10-25'] - x['2026-10-24'], 1);
  assert.equal(x['2026-10-26'] - x['2026-10-25'], 1);
  assert.equal(x['2027-01-01'] - x['2026-12-31'], 1);
  assert.ok(Number.isInteger(x['2026-03-29']));
});

test('sparkline: payday and minimum markers sit at the x of their own date and only inside the drawn range', () => {
  const m = sparklineModel({
    points: [{ date: '2026-07-01', balance: 1 }, { date: '2026-07-31', balance: 2 }],
    paydays: ['2026-06-30', '2026-07-10', '2026-07-31', '2026-08-01'],
    cycleMinima: [{ cycleStart: '2026-06-10', date: '2026-06-25', minBalance: '5.00' },
      { cycleStart: '2026-07-10', date: '2026-07-20', minBalance: '-7.25' }],
  });
  assert.deepEqual(m.paydays.map((p) => p.date), ['2026-07-10', '2026-07-31']);
  const byDate = Object.fromEntries(m.points.map((p) => [p.date, p.x]));
  assert.equal(m.paydays[1].x, byDate['2026-07-31']);
  assert.equal(m.paydays[0].x - byDate['2026-07-01'], 9);
  assert.deepEqual(m.minima.map((x) => [x.date, x.balance]), [['2026-07-20', -7.25]]);
  assert.equal(m.minimaCount, 2); // caption counts what the backend returned, even a minimum left of the window
  assert.deepEqual(m.minimaValues, [5, -7.25]);
});

test('sparkline: caption count equals the returned minima for 0, 1, 2 and 3 and the client adds no current-cycle minimum', () => {
  const points = [{ date: '2026-07-01', balance: 100 }, { date: '2026-09-30', balance: 3 }]; // the current cycle bottoms out at 3
  const minimum = (i) => ({ cycleStart: `2026-0${i}-01`, date: `2026-07-0${i}`, minBalance: 10 * i });
  for (const n of [0, 1, 2, 3]) {
    const m = sparklineModel({ points, paydays: [], cycleMinima: Array.from({ length: n }, (_, i) => minimum(i + 1)) });
    assert.equal(m.minimaCount, n);
    assert.equal(m.minimaValues.length, n);
    assert.ok(!m.minimaValues.includes(3), 'the current cycle low is never inferred client-side');
    assert.ok(m.minima.length <= n);
  }
});

test('sparkline: the minimum list keeps backend order (oldest first) and does not sort or truncate', () => {
  const m = sparklineModel({
    points: [{ date: '2026-01-01', balance: 1 }, { date: '2026-12-31', balance: 1 }],
    cycleMinima: [3, 1, 2, 4].map((v, i) => ({ cycleStart: `2026-0${i + 1}-01`, date: `2026-0${i + 1}-15`, minBalance: v })),
  });
  assert.deepEqual(m.minimaValues, [3, 1, 2, 4]);
  assert.equal(m.minimaCount, 4);
});

test('savings: string decimals, null/absent net, negative zero and a missing balance', () => {
  const s = (net, balance = '460.01') => savingsModel({ counterWalletName: 'Savings', balance, netFlowThisCycle: net });
  assert.deepEqual([s('300.00').direction, s('300.00').amount], ['fromSavings', 300]);
  assert.deepEqual([s('-750.50').direction, s('-750.50').amount], ['toSavings', 750.5]);
  assert.deepEqual([s('0.00').direction, s('0.00').amount], ['none', 0]);
  assert.equal(s(null).direction, 'none');
  assert.equal(s(undefined).direction, 'none');
  assert.equal(s(-0).direction, 'none');
  assert.equal(s('0.01').direction, 'fromSavings');
  assert.equal(s(0, '460.01').balance, 460.01);
  assert.equal(s(0, null).balance, null);
  assert.equal(s(0).name, 'Savings');
  assert.equal(savingsModel(undefined), null);
});

test('savings copy: amount is always shown unsigned', () => {
  const t = createTranslator({ locale: 'en', messages: messages.en, namespace: 'dashboard' });
  const s = savingsModel({ counterWalletName: 'S', balance: 1, netFlowThisCycle: '-750.00' });
  const text = t('savingsNetFlow', { direction: s.direction, amount: String(s.amount) });
  assert.equal(text, '750 moved to savings this cycle');
  assert.ok(!text.includes('-'));
});

test('cycle so far: awaiting salary and a missing typical produce no verdict, delta never surfaces', () => {
  const m = cycleSoFarModel({ fixedSpent: '10.00', variableSpent: '999.00', typicalVariableByToday: null, deltaVsTypical: null },
    { daysElapsed: 34 });
  assert.deepEqual(Object.keys(m).sort(), ['day', 'fixedSpent', 'hasTypical', 'typical', 'variableSpent']);
  assert.equal(m.typical, null);
  assert.equal(m.hasTypical, false);
  assert.deepEqual([m.fixedSpent, m.variableSpent, m.day], [10, 999, 34]);
  // even with a delta in the payload the model has no field that could carry a verdict
  const withDelta = cycleSoFarModel({ fixedSpent: 0, variableSpent: 500, typicalVariableByToday: 100, deltaVsTypical: 400 }, { daysElapsed: 5 });
  assert.ok(!('delta' in withDelta) && !('deltaVsTypical' in withDelta));
  assert.equal(Object.values(withDelta).some((v) => v === 400), false);
});

test('cycle so far: string decimals, zero typical is a real value, missing period leaves the day open', () => {
  const m = cycleSoFarModel({ fixedSpent: '1200.00', variableSpent: '380.50', typicalVariableByToday: '0.00' }, undefined);
  assert.deepEqual([m.fixedSpent, m.variableSpent, m.typical, m.hasTypical, m.day], [1200, 380.5, 0, true, null]);
  const empty = cycleSoFarModel({}, { daysElapsed: 1 });
  assert.deepEqual([empty.fixedSpent, empty.variableSpent, empty.typical, empty.hasTypical], [0, 0, null, false]);
});

test('cycle so far copy: typical present vs absent', () => {
  const t = createTranslator({ locale: 'en', messages: messages.en, namespace: 'dashboard' });
  assert.equal(t('typicalByToday', { day: 6, typical: 'PLN 105.00' }), 'typical by day 6: PLN 105.00');
  assert.equal(t('typicalNone', { day: 34 }), 'no typical yet for day 34');
  assert.match(t('cycleSoFar', { fixed: 'F', variable: 'V' }), /^Spent so far: fixed F · variable V$/);
  // hint states that excluded-from-pace spending is not part of "variable"
  assert.match(t('cycleSoFarHint'), /excluded from pace is not included/);
});

test('fixed tile: coverage line per forecast status and no legacy fields leak into the model', () => {
  const fp = { upcomingOccurrences: [], overdueAmount: 0, atRisk: true, shortfallAmount: 100, balanceAfterRemainingFixedPayments: -5 };
  const expectations = { FINE: 'covered', TIGHT: 'covered', SHORT: 'notCovered', null: null, undefined: null, AWAITING: null, '': null };
  for (const [status, coverage] of Object.entries(expectations)) {
    const arg = status === 'null' ? null : status === 'undefined' ? undefined : status;
    assert.equal(fixedTileV2Model(fp, arg).coverage, coverage, `status ${status}`);
  }
  assert.deepEqual(Object.keys(fixedTileV2Model(fp, 'FINE')).sort(), ['coverage', 'emptyNote', 'overdueAmount', 'rows']);
});

test('fixed tile: rows keep server order, take at most three, include OVERDUE and skip settled rows', () => {
  const occ = (id, status) => ({ id, status });
  const rows = fixedTileV2Model({ upcomingOccurrences: [occ(9, 'OVERDUE'), occ(2, 'PAID'), occ(3, 'PENDING'), occ(1, 'PENDING'), occ(7, 'PENDING')] }, 'FINE').rows;
  assert.deepEqual(rows.map((r) => r.id), [9, 3, 1]);
  assert.deepEqual(fixedTileV2Model(null, 'FINE'), { rows: [], overdueAmount: null, coverage: 'covered', emptyNote: 'fixedNoneUpcoming' });
  assert.equal(fixedTileV2Model({ upcomingOccurrences: [], overdueAmount: '12.50' }, null).overdueAmount, 12.5);
});

const flatKeys = (obj, prefix = '') => Object.entries(obj).flatMap(([k, v]) =>
  (v && typeof v === 'object' ? flatKeys(v, `${prefix}${k}.`) : [`${prefix}${k}`]));

test('en and pl dashboard namespaces have identical keys and no value is empty or a raw key', () => {
  const en = flatKeys(messages.en.dashboard);
  const pl = flatKeys(messages.pl.dashboard);
  assert.deepEqual([...en].sort(), [...pl].sort());
  for (const locale of LOCALES) {
    for (const key of Object.keys(NEW_KEYS)) {
      const raw = messages[locale].dashboard[key];
      assert.equal(typeof raw, 'string');
      assert.ok(raw.trim().length > 0, `${locale}.${key}`);
      assert.notEqual(raw, key);
      assert.notEqual(raw, `dashboard.${key}`);
    }
  }
});

test('polish copy differs from english for every new key (no untranslated copy)', () => {
  for (const key of Object.keys(NEW_KEYS)) {
    assert.notEqual(messages.pl.dashboard[key], messages.en.dashboard[key], key);
  }
});

test('polish plural forms of the caption: one / few / many', () => {
  const t = createTranslator({ locale: 'pl', messages: messages.pl, namespace: 'dashboard' });
  const caption = (count) => t('lowestBeforePayday', { count, values: 'V' });
  assert.match(caption(1), /ostatni cykl: V$/);
  for (const n of [2, 3, 4, 22, 23, 24]) assert.match(caption(n), new RegExp(`ostatnie ${n} cykle: V$`), String(n));
  for (const n of [5, 11, 12, 14, 21, 25]) assert.match(caption(n), new RegExp(`ostatnie ${n} cykli: V$`), String(n));
});

test('english caption: singular for one cycle, count for several', () => {
  const t = createTranslator({ locale: 'en', messages: messages.en, namespace: 'dashboard' });
  assert.equal(t('lowestBeforePayday', { count: 1, values: 'A' }), 'Lowest before payday, last cycle: A');
  assert.equal(t('lowestBeforePayday', { count: 3, values: 'A / B / C' }), 'Lowest before payday, last 3 cycles: A / B / C');
});

test('removed keys are absent in both locales and no source file still consumes them', () => {
  const removed = [/analyticsOverview/, /navOverview/, /analytics\.overview[A-Z]/, /\bTopCategories\b(?!')/];
  const walk = (dir) => readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = join(dir, e.name);
    if (e.isDirectory()) return e.name === 'messages' ? [] : walk(p);
    return /\.(jsx?|mjs|tsx?)$/.test(e.name) && e.name !== 'dashboardV2.test.mjs' ? [p] : [];
  });
  for (const file of walk(SRC)) {
    const text = readFileSync(file, 'utf8');
    for (const pattern of removed) {
      if (pattern.source.includes('TopCategories')) {
        // CategoryModal legitimately uses excludedFromTopCategories / hideFromTopCategories
        assert.doesNotMatch(text.replace(/(excludedFrom|hideFrom)TopCategories\w*/g, ''), /(?:^|[^A-Za-z])TopCategories\b/, file);
      } else {
        assert.doesNotMatch(text, pattern, file);
      }
    }
    assert.doesNotMatch(text, /t\(\s*['"`](?:analytics\.)?overview[A-Z]/, file);
  }
  for (const locale of LOCALES) {
    assert.equal(messages[locale].nav.analyticsOverview, undefined);
    assert.equal(messages[locale].analytics.navOverview, undefined);
  }
});

// -- Legacy layout safety: static checks of DashboardPage.jsx ------------------------------------------------------

const dashboardPageSource = readFileSync(join(SRC, 'components/pages/DashboardPage.jsx'), 'utf8');
const v2Start = dashboardPageSource.indexOf('if (layoutV2) {');
const v2End = dashboardPageSource.indexOf('\n    return (', v2Start);
const v2Block = dashboardPageSource.slice(v2Start, v2End);
const legacyBlock = dashboardPageSource.slice(v2End);

test('DashboardPage: the v2 and legacy branches were located', () => {
  assert.ok(v2Start > 0 && v2End > v2Start, 'v2 branch not found');
  assert.ok(legacyBlock.includes('<SummaryCards'));
});

test('DashboardPage legacy branch still renders the legacy components, unchanged in props', () => {
  for (const tag of ['<DashboardHeader', '<CycleHealthHero', '<SummaryCards', '<FixedTransactionsTile',
    '<CategoryPressureCard', '<PreviousCyclePreview', '<InsightsCard', '<FundsSummaryWidget']) {
    assert.ok(legacyBlock.includes(tag), `legacy branch lost ${tag}`);
  }
  assert.match(legacyBlock, /<FundsSummaryWidget\s*\/>/, 'legacy funds widget must stay prop-less (always rendered)');
  assert.doesNotMatch(legacyBlock, /hideWhenEmpty|showTotals|variant=|BalanceSparkline|CycleSoFarLine|SavingsLine|data-testid/);
  assert.match(legacyBlock, /<InsightsCard insights=\{summary\.insights\} \/>/);
  assert.match(legacyBlock, /<CycleHealthHero cycleHealth=\{summary\.cycleHealth\} period=\{summary\.period\} kpis=\{summary\.kpis\} \/>/);
});

test('DashboardPage v2 branch renders only the specified sections, in DOM order, each behind its own condition', () => {
  for (const tag of ['<SummaryCards', '<CategoryPressureCard', '<PreviousCyclePreview']) {
    assert.ok(!v2Block.includes(tag), `v2 must not render ${tag}`);
  }
  assert.doesNotMatch(v2Block, /spendingPaceDelta|deltaPercent|deltaAmount|categoryPressure|previousCyclePreview|deltaVsTypical/);
  const order = ['data-testid="dashboard-v2"', 'data-testid="dash-hero"', 'data-testid="dash-sparkline"', 'data-testid="dash-fixed"',
    'data-testid="dash-cycle-so-far"', 'data-testid="dash-insights"', 'data-testid="dash-savings"', 'testId="dash-funds"'];
  const positions = order.map((needle) => v2Block.indexOf(needle));
  assert.ok(positions.every((p) => p >= 0), `missing marker: ${JSON.stringify(positions)}`);
  assert.deepEqual([...positions].sort((a, b) => a - b), positions, 'sections are out of order');
  const components = ['<DashboardHeader', '<CycleHealthHero', '<BalanceSparkline', '<FixedTransactionsTile', '<CycleSoFarLine',
    '<InsightsCard', '<SavingsLine', '<FundsSummaryWidget'].map((c) => v2Block.indexOf(c));
  assert.ok(components.every((p) => p >= 0));
  assert.deepEqual([...components].sort((a, b) => a - b), components);
  assert.match(v2Block, /summary\.fixedPayments && \(\s*<div[^>]*dash-fixed/);
  assert.match(v2Block, /summary\.cycleSoFar && \(\s*<div[^>]*dash-cycle-so-far/);
  assert.match(v2Block, /summary\.savings && \(\s*<div[^>]*dash-savings/);
  assert.match(v2Block, /<FundsSummaryWidget hideWhenEmpty/);
});

test('DashboardPage uses the shared gate and nothing else decides the layout', () => {
  assert.match(dashboardPageSource, /dashboardLayoutVariant\(features\) === 'v2'/);
  assert.equal((dashboardPageSource.match(/features\./g) ?? []).length, 0);
});

// Post-SHIP follow-up (reviewer MINOR-1): reporting periods (LAST_PAY_CYCLE is the only one with `fixedPayments`)
// are closed windows — no coverage verdict and no forward-looking "before payday" copy.
test('fixed tile, reporting period: no coverage verdict and no "before payday" copy', () => {
  const occ = (id, status = 'PENDING') => ({ id, status });
  const forward = /payday|wypłat/i;
  for (const status of ['FINE', 'TIGHT', 'SHORT', null, undefined]) {
    const empty = fixedTileV2Model({ upcomingOccurrences: [], overdueAmount: 0 }, status, { reporting: true });
    assert.equal(empty.coverage, null, `coverage ${status}`);
    assert.equal(empty.emptyNote, 'fixedNoneUnpaidInPeriod', `emptyNote ${status}`);
    const withRows = fixedTileV2Model({ upcomingOccurrences: [occ(1, 'OVERDUE'), occ(2)] }, status, { reporting: true });
    assert.deepEqual(withRows.rows.map((r) => r.id), [1, 2]);
    assert.equal(withRows.coverage, null);
    assert.equal(withRows.emptyNote, null);
  }
  const overdueOnly = fixedTileV2Model({ upcomingOccurrences: [], overdueAmount: '408.30' }, null, { reporting: true });
  assert.equal(overdueOnly.overdueAmount, 408.3);
  assert.equal(overdueOnly.emptyNote, null);
  for (const locale of LOCALES) {
    assert.doesNotMatch(messages[locale].dashboard.fixedNoneUnpaidInPeriod, forward, locale);
  }
});

test('fixed tile, open / awaiting cycle unchanged: coverage from status and "before payday" empty line', () => {
  const empty = { upcomingOccurrences: [], overdueAmount: 0 };
  assert.equal(fixedTileV2Model(empty, 'FINE').emptyNote, 'fixedNoneUpcoming');
  assert.equal(fixedTileV2Model(empty, 'FINE', { reporting: false }).coverage, 'covered');
  assert.equal(fixedTileV2Model(empty, 'SHORT', { reporting: false }).coverage, 'notCovered');
  // AWAITING_SALARY: forecastStatus null, not reporting → no verdict, empty line kept
  assert.equal(fixedTileV2Model(empty, null).coverage, null);
  assert.equal(fixedTileV2Model(empty, null).emptyNote, 'fixedNoneUpcoming');
  assert.equal(fixedTileV2Model({ upcomingOccurrences: [], overdueAmount: 5 }, 'FINE').emptyNote, null);
});

test('DashboardPage v2 passes the period reporting flag to the fixed tile and insights', () => {
  const tile = v2Block.slice(v2Block.indexOf('<FixedTransactionsTile'), v2Block.indexOf('/>', v2Block.indexOf('<FixedTransactionsTile')));
  assert.match(tile, /reporting=\{summary\.period\?\.reporting === true\}/);
  assert.match(v2Block, /<InsightsCard[^>]*reporting=\{summary\.period\?\.reporting === true\}/);
});
