// Forecast v2 presentation logic (Stage 5.1–5.3), kept free of React / Next / alias imports so it runs under
// `node --test` as well as in the app. Semantics follow the Stage 4.7 backend contract (ForecastV2Dto,
// DashboardCycleHealthDto v2 fields) — nothing here derives a figure the backend does not state.

/** Verdicts that render a status word. FINE deliberately renders nothing (no celebratory badge). */
const STATUS_TONES = {
  TIGHT: 'amber',
  SHORT: 'red',
};

/** Tone for a Forecast v2 status: 'amber' (TIGHT), 'red' (SHORT), or null — FINE, null and unknown values show no verdict. */
export function forecastStatusTone(status) {
  return STATUS_TONES[status] ?? null;
}

const toNumber = (value) => {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
};

/** Rounds to the nearest 10 PLN, half away from zero; null stays null. */
export function roundTo10(value) {
  const n = toNumber(value);
  if (n === null) return null;
  const rounded = Math.sign(n) * Math.round(Math.abs(n) / 10) * 10;
  return rounded === 0 ? 0 : rounded;
}

/**
 * Whole-PLN currency string for rounded forecast figures, locale-matched to `formatCurrency` (en "PLN 1,230",
 * pl "1 230 PLN"). Negatives carry a typographic minus (U+2212) so an approximate marker in front of them reads
 * "≈ −PLN 380", never "~-PLN 380".
 */
export function formatWholeCurrency(amount, lang = 'en') {
  const locale = lang === 'pl' ? 'pl-PL' : 'en-US';
  return withTypographicMinus(new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'PLN',
    currencyDisplay: 'code',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amount));
}

/** Replaces the ASCII hyphen-minus of a formatted negative amount with U+2212. */
export function withTypographicMinus(formatted) {
  return typeof formatted === 'string' ? formatted.replace('-', '\u2212') : formatted;
}

const CONFIDENCES = ['HIGH', 'MEDIUM', 'LOW'];

/**
 * Wording for what the expected range rests on (Stage 5.7). Only HIGH (≥ 3 closed cycles) may say "typical for
 * you"; MEDIUM (1–2 cycles) says the history is limited; LOW (no closed cycle — the range is this cycle's pace
 * ±25 %) says so. Unknown / null → no basis line at all.
 */
export function rangeBasisKey(confidence) {
  switch (confidence) {
    case 'HIGH': return 'typicalForYou';
    case 'MEDIUM': return 'basisLimitedHistory';
    case 'LOW': return 'basisThisCycleOnly';
    default: return null;
  }
}

/**
 * Which dashboard hero renders. Reporting and AWAITING_SALARY branches precede the flag so both keep their
 * Stage 2 behaviour; only the open salary-wallet cycle switches between the legacy and the v2 hero.
 *
 * The v2 hero needs a v2 payload (`discretionaryNow`), so a client whose `/api/me` flag is stale after a
 * backend flag flip never renders empty v2 placeholders (flag on, legacy payload → legacy hero) nor maps a
 * null legacy status to `dashboard.health_null` (flag off, v2 payload with `status` null → v2 hero). With both
 * sides agreeing — the only steady state — the flag alone decides.
 */
export function dashboardHeroVariant({ forecastV2, cycleHealth, period }) {
  if (!cycleHealth || period?.reporting) return 'reporting';
  if (cycleHealth.projectionReason === 'AWAITING_SALARY') return 'awaiting';
  const hasV2 = cycleHealth.discretionaryNow != null;
  if (!hasV2) return 'legacy';
  return forecastV2 || cycleHealth.status == null ? 'v2' : 'legacy';
}

/**
 * Which Forecast-page hero / pace panel renders: reporting (no projection), v2 or legacy. v2 needs the flag
 * and a `forecast` object; the legacy page stays renderable either way because its fields are always served.
 */
export function forecastPageVariant({ forecastV2, projData }) {
  if (!projData) return null;
  if (!projData.projectionAvailable) return 'reporting';
  return forecastV2 && projData.forecast != null ? 'v2' : 'legacy';
}

/**
 * Headline pair shared by both heroes: "Left until payday" (discretionaryNow, per day, days) and
 * "Expected left at payday" (typical, rounded range). `source` is either the dashboard cycleHealth or the
 * ForecastV2Dto — both use the same figure names. The v2 verdict is passed explicitly (`cycleHealth.forecastStatus`
 * vs `forecast.status`) so the legacy `cycleHealth.status` — null while v2 is on — is never read.
 */
export function headlineModel(source, { status, daysRemaining } = {}) {
  const s = source ?? {};
  const confidence = CONFIDENCES.includes(s.confidence) ? s.confidence : null;
  const typical = roundTo10(s.expectedEndBalanceTypical);
  const low = roundTo10(s.expectedEndBalanceLow);
  const high = roundTo10(s.expectedEndBalanceHigh);
  return {
    discretionaryNow: toNumber(s.discretionaryNow),
    discretionaryPerDay: toNumber(s.discretionaryPerDay),
    daysRemaining: toNumber(daysRemaining),
    typical,
    // low = pessimistic end balance, high = optimistic; order defensively so the range always reads low → high
    rangeLow: low !== null && high !== null ? Math.min(low, high) : low,
    rangeHigh: low !== null && high !== null ? Math.max(low, high) : high,
    hasRange: low !== null && high !== null,
    confidence,
    rangeBasisKey: rangeBasisKey(confidence),
    tone: forecastStatusTone(status),
    // only verdicts that render a word survive; FINE / null / unknown → null
    status: forecastStatusTone(status) ? status : null,
  };
}

/**
 * Stage 5.3 pace rows, in plan order. Values are raw numbers (null = not available); the component formats
 * them. `forecast` is ForecastV2Dto, `projData` the enclosing SpendingProjectionDto (daysRemaining only).
 */
export function paceRowsModel(forecast, projData) {
  const f = forecast ?? {};
  return [
    { key: 'discretionaryPerDay', kind: 'money', value: toNumber(f.discretionaryPerDay) },
    { key: 'typicalPace', kind: 'money', value: toNumber(f.historicalTypicalPerDay) },
    { key: 'trimmedPace', kind: 'money', value: toNumber(f.trimmedDailyPace) },
    { key: 'rawAverage', kind: 'money', value: toNumber(f.rawDailyBurnRate), muted: true },
    { key: 'committed', kind: 'money', value: toNumber(f.committed) },
    { key: 'daysToPayday', kind: 'days', value: toNumber(projData?.daysRemaining) },
  ];
}
