import { useState, useEffect, useCallback } from 'react';
import { categoryBudgetsApi, onAuthStateChange, getAuthState } from '@/lib/api';

/**
 * Hook for CRUD operations on category budgets.
 * Mirrors the pattern of useFixedPayments.js.
 */
export const useCategoryBudgets = (walletId) => {
  const [budgets, setBudgets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchBudgets = useCallback(async () => {
    const isAuthenticated = getAuthState();

    if (!isAuthenticated || !walletId) {
      setBudgets([]);
      setLoading(false);
      setError(null);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const data = await categoryBudgetsApi.getAll(walletId);
      setBudgets(data || []);
    } catch (err) {
      console.error('Failed to fetch category budgets:', err);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [walletId]);

  const createBudget = useCallback(async (data) => {
    try {
      setError(null);
      const created = await categoryBudgetsApi.create(data);
      setBudgets((prev) => [...prev, created]);
      return created;
    } catch (err) {
      console.error('Failed to create budget:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const updateBudget = useCallback(async (id, data) => {
    try {
      setError(null);
      const updated = await categoryBudgetsApi.update(id, data);
      setBudgets((prev) => prev.map((b) => (b.id === id ? updated : b)));
      return updated;
    } catch (err) {
      console.error('Failed to update budget:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const deactivateBudget = useCallback(async (id) => {
    try {
      setError(null);
      await categoryBudgetsApi.deactivate(id);
      setBudgets((prev) => prev.filter((b) => b.id !== id));
    } catch (err) {
      console.error('Failed to deactivate budget:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  useEffect(() => {
    fetchBudgets();
  }, [fetchBudgets]);

  useEffect(() => {
    const unsubscribe = onAuthStateChange((authenticated) => {
      if (!authenticated) {
        setBudgets([]);
        setError(null);
      } else {
        fetchBudgets();
      }
    });
    return unsubscribe;
  }, [fetchBudgets]);

  return {
    budgets,
    loading,
    error,
    createBudget,
    updateBudget,
    deactivateBudget,
    refetch: fetchBudgets,
  };
};
