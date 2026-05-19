import { useState, useEffect, useCallback, useRef } from 'react';
import { transactionApi, categoryApi, onAuthStateChange, getAuthState } from '@/lib/api';

export const useTransactions = (walletId = null) => {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const fetchedWalletIdRef = useRef(null);

  const fetchTransactions = useCallback(async (forceRefresh = false) => {
    if (!walletId) {
      setTransactions([]);
      fetchedWalletIdRef.current = null;
      return;
    }

    if (!forceRefresh && fetchedWalletIdRef.current === walletId && transactions.length > 0) {
      return;
    }

    let cancelled = false;

    try {
      if (forceRefresh) {
        setIsRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      const data = await transactionApi.getTransactionsByWalletId(walletId);
      
      if (cancelled) return;
      
      setTransactions(data || []);
      fetchedWalletIdRef.current = walletId;
    } catch (err) {
      if (!cancelled) {
        console.error('Failed to fetch transactions:', err);
        setError(err.message);
      }
    } finally {
      if (!cancelled) {
        setLoading(false);
        setIsRefreshing(false);
      }
    }

    return () => {
      cancelled = true;
    };
  }, [walletId]);

  const createTransaction = useCallback(async (transactionData) => {
    if (!walletId) {
      throw new Error('Cannot create transaction: No wallet selected');
    }

    try {
      setError(null);

      const newTransaction = await transactionApi.createTransaction(transactionData);
      setTransactions(prev => [newTransaction, ...prev]);
      return newTransaction;
    } catch (err) {
      console.error('Failed to create transaction:', err);
      setError(err.message);
      throw err;
    }
  }, [walletId]);

  const updateTransaction = useCallback(async (id, transactionData) => {
    try {
      setError(null);

      const updatedTransaction = await transactionApi.updateTransaction(id, transactionData);
      setTransactions(prev => prev.map(t => t.id === id ? updatedTransaction : t));
      return updatedTransaction;
    } catch (err) {
      console.error('Failed to update transaction:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const deleteTransaction = useCallback(async (id) => {
    try {
      setError(null);

      await transactionApi.deleteTransaction(id);
      setTransactions(prev => prev.filter(t => t.id !== id));
    } catch (err) {
      console.error('Failed to delete transaction:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    
    const runFetch = async () => {
      await fetchTransactions();
      if (cancelled) return;
    };
    
    runFetch();
    
    return () => {
      cancelled = true;
    };
  }, [walletId]);

  const forceRefetch = useCallback(() => {
    fetchedWalletIdRef.current = null;
    return fetchTransactions(true);
  }, [fetchTransactions]);

  return {
    transactions,
    loading,
    isRefreshing,
    error,
    createTransaction,
    updateTransaction,
    deleteTransaction,
    refetch: fetchTransactions,
    forceRefetch,
  };
};

export const useCategories = (userId = null) => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [initialized, setInitialized] = useState(false);
  const isAuthenticatedRef = useRef(getAuthState());
  const inFlightRef = useRef(false);
  const initializedRef = useRef(false);

  const fetchCategories = useCallback(async () => {
    if (inFlightRef.current) return;

    const isAuthenticated = getAuthState();
    isAuthenticatedRef.current = isAuthenticated;

    if (!isAuthenticated) {
      setCategories([]);
      setLoading(false);
      setError(null);
      return;
    }

    inFlightRef.current = true;

    try {
      setLoading(true);
      setError(null);

      const data = await categoryApi.getAllCategories();

      setCategories(data || []);
      initializedRef.current = true;
      setInitialized(true);
    } catch (err) {
      console.error('Failed to fetch categories:', err);
      setError(err.message);
      initializedRef.current = true;
      setInitialized(true);
    } finally {
      inFlightRef.current = false;
      setLoading(false);
    }
  }, []);

  const createCategory = useCallback(async (categoryData) => {
    try {
      setError(null);

      const newCategory = await categoryApi.createCategory(categoryData);
      setCategories(prev => [...prev, newCategory]);
      return newCategory;
    } catch (err) {
      console.error('Failed to create category:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const updateCategory = useCallback(async (id, categoryData) => {
    try {
      setError(null);

      const updatedCategory = await categoryApi.updateCategory(id, categoryData);
      setCategories(prev => prev.map(c => c.id === id ? updatedCategory : c));
      return updatedCategory;
    } catch (err) {
      console.error('Failed to update category:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const deleteCategory = useCallback(async (id) => {
    try {
      setError(null);

      await categoryApi.deleteCategory(id);
      setCategories(prev => prev.filter(c => c.id !== id));
    } catch (err) {
      console.error('Failed to delete category:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const resetCategories = useCallback(() => {
    inFlightRef.current = false;
    initializedRef.current = false;
    setCategories([]);
    setError(null);
    setInitialized(false);
  }, []);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  useEffect(() => {
    const unsubscribe = onAuthStateChange((authenticated) => {
      if (!authenticated) {
        resetCategories();
      } else if (!initializedRef.current && !inFlightRef.current) {
        fetchCategories();
      }
    });

    return unsubscribe;
  }, [fetchCategories, resetCategories]);

  return {
    categories,
    loading,
    error,
    initialized,
    createCategory,
    updateCategory,
    deleteCategory,
    refetch: fetchCategories,
    resetCategories,
  };
};
