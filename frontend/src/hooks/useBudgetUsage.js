import { useState, useEffect, useCallback } from 'react';
import { categoryBudgetsApi, onAuthStateChange, getAuthState } from '@/lib/api';

/**
 * Normalize the API response from GET /api/category-budgets/usage.
 * The backend returns { walletId, periodStart, periodEnd, budgets: [...] }.
 * We also handle a bare array in case the shape ever changes.
 */
function normalizeBudgetUsage(response) {
  if (Array.isArray(response)) {
    return { usage: response, meta: null };
  }
  if (response && Array.isArray(response.budgets)) {
    return {
      usage: response.budgets,
      meta: {
        walletId:    response.walletId,
        periodStart: response.periodStart,
        periodEnd:   response.periodEnd,
      },
    };
  }
  return { usage: [], meta: null };
}

/**
 * Fetches /api/category-budgets/usage for a given wallet + period.
 * Re-fetches whenever walletId or period params change.
 * Always returns usage as an array regardless of API envelope shape.
 */
export const useBudgetUsage = (walletId, { periodType, startDate, endDate } = {}) => {
  const [usage, setUsage]     = useState([]);
  const [meta,  setMeta]      = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState(null);

  const fetchUsage = useCallback(async () => {
    const isAuthenticated = getAuthState();

    if (!isAuthenticated || !walletId) {
      setUsage([]);
      setMeta(null);
      setLoading(false);
      setError(null);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const raw = await categoryBudgetsApi.getUsage(walletId, { periodType, startDate, endDate });
      const { usage: normalized, meta: normalizedMeta } = normalizeBudgetUsage(raw);
      setUsage(normalized);
      setMeta(normalizedMeta);
    } catch (err) {
      console.error('Failed to fetch budget usage:', err);
      setError(err.message);
      setUsage([]);
    } finally {
      setLoading(false);
    }
  }, [walletId, periodType, startDate, endDate]);

  useEffect(() => {
    fetchUsage();
  }, [fetchUsage]);

  useEffect(() => {
    const unsubscribe = onAuthStateChange((authenticated) => {
      if (!authenticated) {
        setUsage([]);
        setMeta(null);
        setError(null);
      } else {
        fetchUsage();
      }
    });
    return unsubscribe;
  }, [fetchUsage]);

  return { usage, meta, loading, error, refetch: fetchUsage };
};
