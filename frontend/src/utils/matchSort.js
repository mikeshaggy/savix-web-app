// Shared ranking helpers for matching a transaction to a fixed payment
// occurrence (and vice-versa). Lower score = better match.

const parseDateOnly = (value) => {
  if (!value) return null;
  const [y, m, d] = String(value).slice(0, 10).split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d);
};

export const daysApart = (a, b) => {
  const da = parseDateOnly(a);
  const db = parseDateOnly(b);
  if (!da || !db) return 9999;
  return Math.abs(Math.round((da - db) / 86400000));
};

/**
 * Scores a candidate against a target. Both take { amount, date, categoryId }.
 * Amount proximity is weighted most, then date proximity, with a small penalty
 * when the category differs.
 */
export const matchScore = (candidate, target) => {
  const amountDiff = Math.abs(Number(candidate.amount ?? 0) - Number(target.amount ?? 0));
  const dateDiff = daysApart(candidate.date, target.date);
  const categoryMismatch =
    target.categoryId != null && candidate.categoryId === target.categoryId ? 0 : 1;
  return amountDiff * 2 + dateDiff + categoryMismatch * 25;
};

/**
 * Returns a new array sorted best-match-first. `toCandidate` maps an item to a
 * { amount, date, categoryId } shape.
 */
export const sortByMatch = (items, target, toCandidate) =>
  [...items].sort((a, b) => matchScore(toCandidate(a), target) - matchScore(toCandidate(b), target));
