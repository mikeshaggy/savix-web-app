// Stage 5.5 forecast explanation: the Stage 4 arithmetic shown as the user's own numbers, the baseline cycles and
// the one-offs with their Exclude action. Pure (node-testable). Every figure is a ForecastV2Dto field or exact
// arithmetic on two of them — the wallet balance is discretionaryNow + committed because the backend defines
// discretionaryNow = balance − committed.

import { roundTo10 } from './forecastV2.js';

const toNumber = (value) => {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
};

const add = (a, b) => (a === null || b === null ? null : Math.round((a + b) * 100) / 100);

/**
 * One-off row. `shareOfVariable` is in percentage points (19.33 = 19.33 %) — shown as is, never × 100.
 * `typicalIfExcluded` = expectedEndBalanceTypical + impactOnExpectedEndBalance (the backend's impact is
 * "without − with"), rounded to 10 PLN like every other forecast figure; null for already-excluded rows, whose
 * impact is 0 by construction.
 */
export function oneOffModel(oneOff, forecast) {
  const o = oneOff ?? {};
  const excluded = o.excluded === true;
  const typical = toNumber(forecast?.expectedEndBalanceTypical);
  const impact = toNumber(o.impactOnExpectedEndBalance);
  return {
    transactionId: o.transactionId ?? null,
    date: o.date ?? null,
    title: o.title ?? null,
    categoryName: o.categoryName ?? null,
    amount: toNumber(o.amount),
    sharePercent: toNumber(o.shareOfVariable),
    excluded,
    impactOnEndBalance: excluded ? null : impact,
    typicalIfExcluded: excluded ? null : roundTo10(add(typical, impact)),
  };
}

/** The Stage 5.5 breakdown of an open-cycle ForecastV2Dto. */
export function breakdownModel(forecast) {
  const f = forecast ?? {};
  const discretionaryNow = toNumber(f.discretionaryNow);
  const committed = toNumber(f.committed);
  const historyWeight = toNumber(f.historyWeight);
  const cycles = Array.isArray(f.baselineCycles) ? f.baselineCycles : [];
  return {
    balance: add(discretionaryNow, committed),
    committed,
    discretionaryNow,
    history: {
      median: toNumber(f.historicalMedianRemaining),
      cyclesUsed: toNumber(f.baselineCyclesUsed),
      // 4 dp ratio → whole percent for display
      weightPercent: historyWeight === null ? null : Math.round(historyWeight * 100),
    },
    pace: {
      perDay: toNumber(f.trimmedDailyPace),
      projection: toNumber(f.paceProjection),
      weightPercent: historyWeight === null ? null : 100 - Math.round(historyWeight * 100),
    },
    expectedVariable: {
      typical: toNumber(f.expectedVariableRemaining),
      // "low" / "high" name the END BALANCE path: low = pessimistic = MORE spend
      pessimistic: toNumber(f.expectedVariableRemainingLow),
      optimistic: toNumber(f.expectedVariableRemainingHigh),
    },
    endBalance: {
      typical: roundTo10(f.expectedEndBalanceTypical),
      low: roundTo10(f.expectedEndBalanceLow),
      high: roundTo10(f.expectedEndBalanceHigh),
    },
    baselineCycles: cycles.map((c) => ({
      start: c.start ?? null,
      end: c.end ?? null,
      lengthDays: toNumber(c.lengthDays),
      variableTotal: toNumber(c.variableTotal),
      remainingFromDay: toNumber(c.remainingFromDay),
      normalised: toNumber(c.normalised),
      used: c.normalised !== null && c.normalised !== undefined,
    })),
    oneOffs: (Array.isArray(f.oneOffs) ? f.oneOffs : []).map((o) => oneOffModel(o, f)),
  };
}

/**
 * Full PUT body for /transactions/{id} (TransactionUpdateRequest replaces every field) built from the current
 * TransactionResponse, changing nothing but `excludedFromPace`.
 */
export function excludeRequestBody(transaction, excluded = true) {
  const tx = transaction ?? {};
  return {
    walletId: tx.walletId,
    categoryId: tx.categoryId,
    title: tx.title,
    amount: tx.amount,
    transactionDate: tx.transactionDate,
    notes: tx.notes ?? null,
    importance: tx.importance ?? null,
    excludedFromPace: excluded,
  };
}

/**
 * Exclude-from-pace action with in-flight de-duplication. `load(id)` reads the persisted transaction,
 * `save(id, body)` writes it, `refresh()` refetches the forecast so every panel re-reads the backend's state —
 * the only source of the excluded flag. A second call for an id already in flight returns the same promise
 * instead of issuing another write. A failed write rejects with the write's error; a failed refresh after a
 * successful write rejects with `code: 'REFRESH_FAILED'`.
 */
export function createExcludeAction({ load, save, refresh }) {
  const inFlight = new Map();
  return function exclude(transactionId) {
    if (transactionId === null || transactionId === undefined) {
      return Promise.reject(new Error('transactionId is required'));
    }
    if (inFlight.has(transactionId)) return inFlight.get(transactionId);
    const run = (async () => {
      const transaction = await load(transactionId);
      if (transaction?.excludedFromPace !== true) {
        await save(transactionId, excludeRequestBody(transaction, true));
      }
      try {
        await refresh();
      } catch (cause) {
        // the exclusion is persisted — only the re-read failed; callers must not report it as a failed write
        const err = new Error('forecast refresh failed after exclusion');
        err.code = 'REFRESH_FAILED';
        err.cause = cause;
        throw err;
      }
    })().finally(() => inFlight.delete(transactionId));
    inFlight.set(transactionId, run);
    return run;
  };
}
