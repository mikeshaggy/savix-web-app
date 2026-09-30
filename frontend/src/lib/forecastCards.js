// Stage 5.6 v2 projection cards: Left until payday / Expected at payday (range) / Committed / Expected variable.
// Hierarchy (plan: "remove duplicates of the hero"): the hero is the one place the two headline figures appear
// large. Their cards are `context` cards with no headline figure — they decompose Left until payday
// (balance − committed) and put Expected at payday in context (pessimistic … optimistic + what the range rests
// on). Only Committed and Expected variable — not shown large anywhere else — are `figure` cards. Pure.

import { roundTo10, rangeBasisKey } from './forecastV2.js';

const toNumber = (value) => {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
};

export function projectionCardsModel(forecast) {
  const f = forecast ?? {};
  const lessSpendRaw = toNumber(f.expectedVariableRemainingHigh);
  const moreSpendRaw = toNumber(f.expectedVariableRemainingLow);
  const bothSpend = lessSpendRaw !== null && moreSpendRaw !== null;
  const discretionaryNow = toNumber(f.discretionaryNow);
  const committed = toNumber(f.committed);
  const occurrences = Array.isArray(f.committedOccurrences) ? f.committedOccurrences : [];
  const low = roundTo10(f.expectedEndBalanceLow);
  const high = roundTo10(f.expectedEndBalanceHigh);
  return [
    {
      key: 'leftUntilPayday',
      kind: 'context',
      balance: discretionaryNow === null || committed === null
        ? null
        : Math.round((discretionaryNow + committed) * 100) / 100,
      committed,
    },
    {
      key: 'expectedAtPayday',
      kind: 'context',
      // low = pessimistic end balance, high = optimistic; defensively ordered
      pessimistic: low !== null && high !== null ? Math.min(low, high) : low,
      optimistic: low !== null && high !== null ? Math.max(low, high) : high,
      basisKey: rangeBasisKey(f.confidence),
    },
    {
      key: 'committed',
      kind: 'figure',
      value: committed,
      count: occurrences.length,
    },
    {
      key: 'expectedVariable',
      kind: 'figure',
      value: toNumber(f.expectedVariableRemaining),
      // spend range: optimistic path spends less (…RemainingHigh), pessimistic spends more (…RemainingLow)
      lessSpend: bothSpend ? Math.min(lessSpendRaw, moreSpendRaw) : lessSpendRaw,
      moreSpend: bothSpend ? Math.max(lessSpendRaw, moreSpendRaw) : moreSpendRaw,
    },
  ];
}
