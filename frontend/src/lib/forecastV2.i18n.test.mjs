// Stage 5.1–5.3: every translation key the Forecast v2 components can render exists in en and pl, and every new
// `forecast.*` message is valid ICU — so no raw key (e.g. `dashboard.health_null`) can reach the UI.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
// next-intl is a declared dependency and formats exactly as the app does; errors throw instead of falling back
import { createTranslator } from 'next-intl';
import { paceRowsModel, rangeBasisKey } from './forecastV2.js';

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..');
const LOCALES = ['en', 'pl'];
const messages = Object.fromEntries(
  LOCALES.map((l) => [l, JSON.parse(readFileSync(join(SRC, 'messages', `${l}.json`), 'utf8'))]));

const lookup = (locale, path) => path.split('.').reduce((node, k) => (node == null ? undefined : node[k]), messages[locale]);

// source (optionally a [from, to) section of it) → namespace bound to each translator identifier there
const FILES = [
  { file: 'components/forecast/ForecastV2Headline.jsx', bindings: { t: 'forecast' } },
  { file: 'components/dashboard/CycleHealthHero.jsx', bindings: { t: '' } },
  { file: 'components/analytics/CycleForecastHero.jsx', bindings: { t: 'analytics', tf: 'forecast' } },
  { file: 'components/analytics/SpendingPacePanel.jsx', bindings: { t: 'analytics', tf: 'forecast' } },
  // Stage 5.4–5.6
  { file: 'components/forecast/ForecastTrajectoryV2.jsx', bindings: { t: 'forecast' } },
  { file: 'components/forecast/ForecastBreakdownV2.jsx', bindings: { t: 'forecast' } },
  { file: 'components/analytics/SpendingTrajectoryChart.jsx', bindings: { t: 'analytics' } },
  { file: 'components/analytics/ForecastBreakdown.jsx', bindings: { t: 'analytics' } },
  {
    file: 'components/analytics/ProjectionCards.jsx',
    section: ['function ProjectionCardsV2', 'export default function ProjectionCards'],
    bindings: { t: 'forecast' },
  },
];

const sourceOf = ({ file, section }) => {
  const src = readFileSync(join(SRC, file), 'utf8');
  if (!section) return src;
  const start = src.indexOf(section[0]);
  const end = src.indexOf(section[1], start + 1);
  assert.ok(start >= 0 && end > start, `${file}: section ${section[0]}`);
  return src.slice(start, end);
};

function literalKeys(entry) {
  const src = sourceOf(entry);
  const keys = [];
  for (const [ident, ns] of Object.entries(entry.bindings)) {
    const re = new RegExp(`\\b${ident}\\(\\s*'([^']+)'`, 'g');
    for (const m of src.matchAll(re)) keys.push(ns ? `${ns}.${m[1]}` : m[1]);
  }
  return keys;
}

test('every literal key used by the Stage 5 components exists in en and pl', () => {
  let checked = 0;
  for (const entry of FILES) {
    for (const key of literalKeys(entry)) {
      for (const locale of LOCALES) {
        assert.equal(typeof lookup(locale, key), 'string', `${locale}: missing ${key} (${entry.file})`);
      }
      checked += 1;
    }
  }
  assert.ok(checked > 80, `scanned ${checked} keys`);
});

test('dynamic Forecast v2 keys (status, hint, confidence, pace rows, row subtexts) exist in en and pl', () => {
  const dynamic = [
    'forecast.status_TIGHT', 'forecast.status_SHORT',
    'forecast.statusHint_TIGHT', 'forecast.statusHint_SHORT',
    'forecast.confidence_HIGH', 'forecast.confidence_MEDIUM', 'forecast.confidence_LOW',
    ...paceRowsModel({}, {}).map((r) => `forecast.${r.key}`),
    // Stage 5.7 confidence-sensitive range basis (t(model.rangeBasisKey) / t(card.basisKey))
    ...['HIGH', 'MEDIUM', 'LOW'].map((c) => `forecast.${rangeBasisKey(c)}`),
    // Stage 5.5 Exclude errors (t(error.key))
    'forecast.excludeFailed', 'forecast.excludeSavedRefreshFailed',
  ];
  const pacePanel = readFileSync(join(SRC, 'components/analytics/SpendingPacePanel.jsx'), 'utf8');
  for (const m of pacePanel.matchAll(/subtext: '([A-Za-z]+)'/g)) dynamic.push(`forecast.${m[1]}`);
  for (const key of dynamic) {
    for (const locale of LOCALES) {
      assert.equal(typeof lookup(locale, key), 'string', `${locale}: missing ${key}`);
    }
  }
});

test('en and pl define the same forecast.* keys', () => {
  assert.deepEqual(Object.keys(messages.pl.forecast).sort(), Object.keys(messages.en.forecast).sort());
});

test('every forecast.* message is valid ICU and formats with sample values', () => {
  const sample = {
    amount: 'PLN 212.61', days: 24, low: 'PLN 380', high: 'PLN 2,110', count: 6, level: 'high', day: 7,
    cycles: 6, weight: 30, perDay: 'PLN 88.94', share: '19.3', title: 'Repayment', date: 'Sep 13',
    typical: '≈ PLN 5,040', balance: 'PLN 5,896.89', committed: 'PLN 612.29', less: 'PLN 219.21', more: 'PLN 271.08',
  };
  const translator = (locale) => createTranslator({
    locale, messages: messages[locale], namespace: 'forecast', onError: (e) => { throw e; },
  });
  for (const locale of LOCALES) {
    const tf = translator(locale);
    for (const key of Object.keys(messages[locale].forecast)) {
      const out = tf(key, sample);
      assert.equal(typeof out, 'string', `${locale}.forecast.${key}`);
      assert.ok(!/[{}]/.test(out), `${locale}.forecast.${key} left placeholders: ${out}`);
    }
  }
  assert.equal(translator('en')('cyclesUsed', { count: 1 }), '1 cycle used');
  assert.equal(translator('pl')('cyclesUsed', { count: 3 }), '3 cykle w bazie');
  assert.equal(translator('pl')('cyclesUsed', { count: 6 }), '6 cykli w bazie');
});

test('the v2 dashboard hero never builds a dashboard.health_* key', () => {
  const src = readFileSync(join(SRC, 'components/dashboard/CycleHealthHero.jsx'), 'utf8');
  const v2 = src.slice(src.indexOf('function CycleHealthHeroV2'), src.indexOf('export default function CycleHealthHero'));
  assert.ok(v2.length > 0);
  assert.ok(!v2.includes('health_'), 'v2 hero must not map a legacy status');
  assert.ok(!/safeToSpend|projectedEndBalance|spendingPace/.test(v2), 'v2 hero must not read legacy figures');
});

// Tester additions: the v2 sections of every touched component must not reference retired legacy copy or figures
// (Stage 5 acceptance: no "Safe to spend", "Required daily reduction", "Over pace", "% of income", "action needed").
test('v2 component sections reference no retired legacy keys or fields', () => {
  const section = (file, from, to) => {
    const src = readFileSync(join(SRC, file), 'utf8');
    const start = src.indexOf(from);
    const end = src.indexOf(to, start + from.length);
    assert.ok(start >= 0 && end > start, `${file}: section ${from}`);
    return src.slice(start, end);
  };
  const sections = [
    section('components/dashboard/CycleHealthHero.jsx', 'function CycleHealthHeroV2', 'export default function CycleHealthHero'),
    section('components/analytics/CycleForecastHero.jsx', 'function CycleForecastHeroV2', 'export default function CycleForecastHero'),
    section('components/analytics/SpendingPacePanel.jsx', 'const V2_ROW_CFG', 'export default function SpendingPacePanel'),
    readFileSync(join(SRC, 'components/forecast/ForecastV2Headline.jsx'), 'utf8'),
    // Stage 5.4–5.6
    readFileSync(join(SRC, 'components/forecast/ForecastTrajectoryV2.jsx'), 'utf8'),
    readFileSync(join(SRC, 'components/forecast/ForecastBreakdownV2.jsx'), 'utf8'),
    section('components/analytics/ProjectionCards.jsx', 'const V2_CARD_CFG', 'export default function ProjectionCards'),
    readFileSync(join(SRC, 'lib/forecastTrajectory.js'), 'utf8'),
    readFileSync(join(SRC, 'lib/forecastBreakdown.js'), 'utf8'),
    readFileSync(join(SRC, 'lib/forecastCards.js'), 'utf8'),
  ];
  const retired = /safeToSpend|safeDaily|requiredReduction|reductionNeeded|overPace|actionNeeded|percentOfIncome|incomePercent|spendingPace(?!')|projectedEndBalance|projectedCycle|health_/;
  for (const s of sections) {
    const hit = s.match(retired);
    // `t('spendingPace')` is the panel's section title (existing analytics key), not the removed dashboard pace ±%
    assert.ok(!hit || hit[0] === 'spendingPace', `retired reference: ${hit && hit[0]}`);
  }
});

// Stage 5.7: the legacy strings the plan deprecates (deleted in §9) — none may be rendered by a v2 path.
const DEPRECATED_KEYS = [
  'dashboard.safeToSpend', 'dashboard.safeToSpendTotal', 'dashboard.projectedCycleSurplus',
  'dashboard.projectedCycleDeficit', 'dashboard.spendingPace', 'dashboard.health_ON_TRACK',
  'dashboard.health_WARNING', 'dashboard.health_DANGER',
  'analytics.requiredReduction', 'analytics.perDayToBudget', 'analytics.safeToSpend', 'analytics.safeToSpendPerDay',
  'analytics.safePace', 'analytics.safePaceToday', 'analytics.overSafePace', 'analytics.underSafePace',
  'analytics.overIncome', 'analytics.underIncome', 'analytics.incomeLimit', 'analytics.projectedSpend',
  'analytics.projectedSurplus', 'analytics.projectedDeficit', 'analytics.ofIncome', 'analytics.overspendWarning',
  'analytics.projectedCycleSurplus', 'analytics.projectedCycleDeficit', 'analytics.projectedTotalSpend',
  'analytics.projectedVariable', 'analytics.projectedPeriodSpend', 'analytics.dailyBurnRate', 'analytics.basedOnPace',
  'analytics.totalRemaining', 'analytics.projectedVariableRemaining',
];

test('Stage 5.7: no v2 component section renders a deprecated legacy key', () => {
  const v2Entries = [
    { file: 'components/dashboard/CycleHealthHero.jsx', section: ['function CycleHealthHeroV2', 'export default function CycleHealthHero'], bindings: { t: '' } },
    { file: 'components/analytics/CycleForecastHero.jsx', section: ['function CycleForecastHeroV2', 'export default function CycleForecastHero'], bindings: { t: 'analytics', tf: 'forecast' } },
    { file: 'components/analytics/SpendingPacePanel.jsx', section: ['function SpendingPacePanelV2', 'export default function SpendingPacePanel'], bindings: { t: 'analytics', tf: 'forecast' } },
    { file: 'components/analytics/ProjectionCards.jsx', section: ['function ProjectionCardsV2', 'export default function ProjectionCards'], bindings: { t: 'forecast' } },
    { file: 'components/forecast/ForecastV2Headline.jsx', bindings: { t: 'forecast' } },
    { file: 'components/forecast/ForecastTrajectoryV2.jsx', bindings: { t: 'forecast' } },
    { file: 'components/forecast/ForecastBreakdownV2.jsx', bindings: { t: 'forecast' } },
  ];
  for (const entry of v2Entries) {
    for (const key of literalKeys(entry)) {
      assert.ok(!DEPRECATED_KEYS.includes(key), `${entry.file} renders deprecated ${key}`);
    }
  }
  // the deprecated keys still exist (flag-off legacy UI keeps rendering until §9)
  for (const key of DEPRECATED_KEYS) {
    for (const locale of LOCALES) assert.equal(typeof lookup(locale, key), 'string', `${locale}: ${key}`);
  }
});

test('Stage 5.7 wording: approximate negatives, "to" ranges and confidence-sensitive basis in both locales', () => {
  const tr = (locale) => createTranslator({ locale, messages: messages[locale], namespace: 'forecast', onError: (e) => { throw e; } });
  assert.equal(tr('en')('approxAmount', { amount: '−PLN 380' }), '≈ −PLN 380');
  assert.equal(tr('pl')('approxAmount', { amount: '−380 PLN' }), '≈ −380 PLN');
  assert.equal(tr('en')('typicalRange', { low: '−PLN 910', high: '−PLN 100' }), 'Range −PLN 910 to −PLN 100');
  assert.equal(tr('pl')('typicalRange', { low: '−910 PLN', high: '−100 PLN' }), 'Zakres od −910 PLN do −100 PLN');
  assert.equal(tr('en')(rangeBasisKey('HIGH')), 'typical for you');
  for (const c of ['MEDIUM', 'LOW']) {
    for (const locale of LOCALES) {
      assert.ok(!/typical for you|typowo dla Ciebie/.test(tr(locale)(rangeBasisKey(c))), `${locale} ${c} must not claim "typical for you"`);
    }
  }
  assert.equal(tr('pl')('pastCycles', { count: 5 }), 'mediana z 5 poprzednich cykli');
  assert.equal(tr('pl')('pastCycles', { count: 1 }), 'mediana z 1 poprzedniego cyklu');
  assert.equal(tr('en')('pastCycles', { count: 1 }), 'median of 1 past cycle');
  // the two historical measures must read as different things (spent so far vs rest of cycle)
  assert.equal(tr('en')('typicalByToday'), 'Usually spent by now');
  assert.equal(tr('en')('historyMedian'), 'Usually spent in the rest of the cycle');
  assert.equal(tr('pl')('historyWeight', { cycles: 6, days: 9, weight: 30 }),
    'mediana z 6 poprzednich cykli, przeliczona na 9 dni · waga 30%');
  assert.equal(tr('pl')('cardCommittedSubtext', { count: 2 }), '2 płatności przed wypłatą');
  assert.equal(tr('en')('cardCommittedSubtext', { count: 1 }), '1 payment before payday');
});

// Final cleanup pass: historical wording says "median of N past cycles" with correct Polish plural/case, and the
// removed cardLeftSubtext key is gone from both locales.
test('historical wording: median-of-past-cycles plurals (en + pl genitive) and scaled-days; cardLeftSubtext removed', () => {
  const tr = (locale) => createTranslator({ locale, messages: messages[locale], namespace: 'forecast', onError: (e) => { throw e; } });
  const en = tr('en');
  const pl = tr('pl');
  assert.equal(en('pastCycles', { count: 1 }), 'median of 1 past cycle');
  assert.equal(en('pastCycles', { count: 6 }), 'median of 6 past cycles');
  assert.equal(pl('pastCycles', { count: 1 }), 'mediana z 1 poprzedniego cyklu');
  for (const n of [2, 3, 4, 5, 6, 12, 22, 25]) {
    assert.equal(pl('pastCycles', { count: n }), `mediana z ${n} poprzednich cykli`, `pl n=${n}`);
  }
  assert.equal(
    en('historyWeight', { cycles: 6, days: 1, weight: 30 }),
    'median of 6 past cycles, scaled to 1 day · weight 30%');
  assert.equal(
    pl('historyWeight', { cycles: 1, days: 5, weight: 30 }),
    'mediana z 1 poprzedniego cyklu, przeliczona na 5 dni · waga 30%');
  assert.equal(
    pl('historyWeight', { cycles: 6, days: 1, weight: 70 }),
    'mediana z 6 poprzednich cykli, przeliczona na 1 dzień · waga 70%');
  assert.equal(en('typicalByToday'), 'Usually spent by now');
  assert.equal(en('trajectoryTypical'), 'Past cycles (median)');
  assert.equal(en('historyMedian'), 'Usually spent in the rest of the cycle');
  for (const locale of LOCALES) {
    assert.equal(lookup(locale, 'forecast.cardLeftSubtext'), undefined, `${locale}: cardLeftSubtext must be removed`);
    assert.equal(typeof lookup(locale, 'forecast.cardLeftNote'), 'string');
  }
  // no code path still references the removed key
  for (const f of ['components/analytics/ProjectionCards.jsx', 'components/forecast/ForecastFactCard.jsx']) {
    assert.ok(!readFileSync(join(SRC, f), 'utf8').includes('cardLeftSubtext'), f);
  }
});
