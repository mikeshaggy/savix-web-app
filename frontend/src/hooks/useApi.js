 import { useState, useEffect, useCallback, useRef } from 'react';
import { dashboardApi, transactionApi, categoryApi, checkBackendHealth } from '@/lib/api';

export const useDashboard = (walletId = null) => {
  const [data, setData] = useState({
    transactions: [],
    categories: [],
    analytics: null,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const fetchedWalletIdRef = useRef(null);

  const fetchDashboardData = useCallback(async () => {
    if (!walletId) {
      setData({
        transactions: [],
        categories: [],
        analytics: null,
      });
      setLoading(false);
      setError(null);
      fetchedWalletIdRef.current = null;
      return;
    }

    if (fetchedWalletIdRef.current === walletId && data.transactions.length > 0) {
      return;
    }

    let cancelled = false;

    try {
      setLoading(true);
      setError(null);
      
      const backendAvailable = await checkBackendHealth();
      
      if (cancelled) return;
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const dashboardData = await dashboardApi.getWalletDashboardData(walletId);
      
      if (cancelled) return;
      
      setData({
        transactions: dashboardData.transactions || [],
        categories: dashboardData.categories || [],
        analytics: null,
      });
      fetchedWalletIdRef.current = walletId;
    } catch (err) {
      if (!cancelled) {
        console.error('Failed to fetch dashboard data:', err);
        setError(err.message);
      }
    } finally {
      if (!cancelled) {
        setLoading(false);
      }
    }

    return () => {
      cancelled = true;
    };
  }, [walletId]);

  useEffect(() => {
    let cancelled = false;
    
    const runFetch = async () => {
      await fetchDashboardData();
      if (cancelled) return;
    };
    
    runFetch();
    
    return () => {
      cancelled = true;
    };
  }, [walletId]);

  const refetch = useCallback(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  return { data, loading, error, refetch };
};

export const useTransactions = (walletId = null) => {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const fetchedWalletIdRef = useRef(null);

  const fetchTransactions = useCallback(async () => {
    if (!walletId) {
      setTransactions([]);
      fetchedWalletIdRef.current = null;
      return;
    }

    if (fetchedWalletIdRef.current === walletId && transactions.length > 0) {
      return;
    }

    let cancelled = false;

    try {
      setLoading(true);
      setError(null);
      
      const backendAvailable = await checkBackendHealth();
      
      if (cancelled) return;
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

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
      }
    }

    return () => {
      cancelled = true;
    };
  }, [walletId]);

  const createTransaction = useCallback(async (transactionData) => {
    // Don't create if no walletId is provided
    if (!walletId) {
      throw new Error('Cannot create transaction: No wallet selected');
    }

    try {
      setError(null);
      
      const backendAvailable = await checkBackendHealth();

      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

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
      
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

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
      
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

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

  return {
    transactions,
    loading,
    error,
    createTransaction,
    updateTransaction,
    deleteTransaction,
    refetch: fetchTransactions,
  };
};

export const useCategories = (userId = null) => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const fetchedUserIdRef = useRef(null);

  const fetchCategories = useCallback(async () => {
    if (!userId) {
      setCategories([]);
      setLoading(false);
      setError(null);
      fetchedUserIdRef.current = null;
      return;
    }

    if (fetchedUserIdRef.current === userId && categories.length > 0) {
      return;
    }

    let cancelled = false;

    try {
      setLoading(true);
      setError(null);
      
      const backendAvailable = await checkBackendHealth();
      
      if (cancelled) return;
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const data = await categoryApi.getCategoriesByUserId(userId);
      
      if (cancelled) return;
      
      setCategories(data || []);
      fetchedUserIdRef.current = userId;
    } catch (err) {
      if (!cancelled) {
        console.error('Failed to fetch categories:', err);
        setError(err.message);
      }
    } finally {
      if (!cancelled) {
        setLoading(false);
      }
    }

    return () => {
      cancelled = true;
    };
  }, [userId]);

  const createCategory = useCallback(async (categoryData, userId) => {
    if (!userId) {
      throw new Error('Cannot create category: User ID is required');
    }

    try {
      setError(null);
      
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const newCategory = await categoryApi.createCategory({
        ...categoryData,
        userId,
      });
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
      
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

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
      
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      await categoryApi.deleteCategory(id);
      setCategories(prev => prev.filter(c => c.id !== id));
    } catch (err) {
      console.error('Failed to delete category:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    
    const runFetch = async () => {
      await fetchCategories();
      if (cancelled) return;
    };
    
    runFetch();
    
    return () => {
      cancelled = true;
    };
  }, [userId]);

  return {
    categories,
    loading,
    error,
    createCategory,
    updateCategory,
    deleteCategory,
    refetch: fetchCategories,
  };
};

