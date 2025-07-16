import { useState, useEffect, useCallback } from 'react';
import { dashboardApi, transactionApi, categoryApi, mockApi, checkBackendHealth } from '../lib/api';

// Global state to track backend availability
let backendAvailable = null;

// Custom hook for managing dashboard data
export const useDashboard = (userId = 1) => { // Default to user ID 1 for demo
  const [data, setData] = useState({
    transactions: [],
    categories: [],
    analytics: null,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [usingMockData, setUsingMockData] = useState(false);

  const fetchDashboardData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      
      // Check backend health if not already checked
      if (backendAvailable === null) {
        backendAvailable = await checkBackendHealth();
      }
      
      if (backendAvailable) {
        // Fetch real data from backend
        const dashboardData = await dashboardApi.getUserDashboardData(userId);
        
        // Fetch mock analytics (since this endpoint doesn't exist in backend)
        const analytics = await mockApi.getAnalytics(userId);
        
        setData({
          transactions: dashboardData.transactions || [],
          categories: dashboardData.categories || [],
          analytics,
        });
        setUsingMockData(false);
      } else {
        throw new Error('Backend not available');
      }
    } catch (err) {
      console.error('Failed to fetch dashboard data:', err);
      setError(err.message);
      
      // Fallback to mock data if API fails
      const mockTransactions = getMockTransactions();
      const mockCategories = getMockCategories();
      
      console.log('DEBUG - Using mock data:', { mockTransactions, mockCategories });
      
      setData({
        transactions: mockTransactions,
        categories: mockCategories,
        analytics: await mockApi.getAnalytics(userId),
      });
      setUsingMockData(true);
      backendAvailable = false;
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  const refetch = useCallback(() => {
    // Reset backend availability to recheck
    backendAvailable = null;
    fetchDashboardData();
  }, [fetchDashboardData]);

  return { data, loading, error, refetch, usingMockData };
};

// Custom hook for managing transactions
export const useTransactions = (userId = 1) => {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [usingMockData, setUsingMockData] = useState(false);

  const fetchTransactions = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      
      if (backendAvailable === null) {
        backendAvailable = await checkBackendHealth();
      }
      
      if (backendAvailable) {
        const data = await transactionApi.getTransactionsByUserId(userId);
        setTransactions(data || []);
        setUsingMockData(false);
      } else {
        throw new Error('Backend not available');
      }
    } catch (err) {
      console.error('Failed to fetch transactions:', err);
      setError(err.message);
      // Fallback to mock data
      const mockTransactions = getMockTransactions();
      console.log('DEBUG - useTransactions mock data:', mockTransactions);
      
      setTransactions(mockTransactions);
      setUsingMockData(true);
      backendAvailable = false;
    } finally {
      setLoading(false);
    }
  }, [userId]);

  const createTransaction = useCallback(async (transactionData) => {
    try {
      setError(null);
      
      if (backendAvailable) {
        const newTransaction = await transactionApi.createTransaction({
          ...transactionData,
          userId,
        });
        setTransactions(prev => [newTransaction, ...prev]);
        return newTransaction;
      } else {
        // Mock creation for offline mode
        const mockTransaction = {
          id: generateMockId(), // Generate consistent mock ID
          ...transactionData,
          userId,
          createdAt: new Date().toISOString(),
        };
        setTransactions(prev => [mockTransaction, ...prev]);
        return mockTransaction;
      }
    } catch (err) {
      console.error('Failed to create transaction:', err);
      setError(err.message);
      throw err;
    }
  }, [userId]);

  const updateTransaction = useCallback(async (id, transactionData) => {
    try {
      setError(null);
      
      if (backendAvailable) {
        const updatedTransaction = await transactionApi.updateTransaction(id, transactionData);
        setTransactions(prev => prev.map(t => t.id === id ? updatedTransaction : t));
        return updatedTransaction;
      } else {
        // Mock update for offline mode
        const updatedTransaction = { ...transactionData, id };
        setTransactions(prev => prev.map(t => t.id === id ? updatedTransaction : t));
        return updatedTransaction;
      }
    } catch (err) {
      console.error('Failed to update transaction:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const deleteTransaction = useCallback(async (id) => {
    try {
      setError(null);
      
      if (backendAvailable) {
        await transactionApi.deleteTransaction(id);
      }
      // Always remove from local state regardless of backend availability
      setTransactions(prev => prev.filter(t => t.id !== id));
    } catch (err) {
      console.error('Failed to delete transaction:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  useEffect(() => {
    fetchTransactions();
  }, [fetchTransactions]);

  return {
    transactions,
    loading,
    error,
    createTransaction,
    updateTransaction,
    deleteTransaction,
    refetch: fetchTransactions,
    usingMockData,
  };
};

// Custom hook for managing categories
export const useCategories = (userId = 1) => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [usingMockData, setUsingMockData] = useState(false);

  const fetchCategories = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      
      if (backendAvailable === null) {
        backendAvailable = await checkBackendHealth();
      }
      
      if (backendAvailable) {
        const data = await categoryApi.getCategoriesByUserId(userId);
        setCategories(data || []);
        setUsingMockData(false);
      } else {
        throw new Error('Backend not available');
      }
    } catch (err) {
      console.error('Failed to fetch categories:', err);
      setError(err.message);
      // Fallback to mock data
      setCategories(getMockCategories());
      setUsingMockData(true);
      backendAvailable = false;
    } finally {
      setLoading(false);
    }
  }, [userId]);

  const createCategory = useCallback(async (categoryData) => {
    try {
      setError(null);
      
      if (backendAvailable) {
        const newCategory = await categoryApi.createCategory({
          ...categoryData,
          userId,
        });
        setCategories(prev => [...prev, newCategory]);
        return newCategory;
      } else {
        // Mock creation for offline mode
        const mockCategory = {
          id: generateMockId(), // Generate consistent mock ID
          ...categoryData,
          userId,
          createdAt: new Date().toISOString(),
        };
        setCategories(prev => [...prev, mockCategory]);
        return mockCategory;
      }
    } catch (err) {
      console.error('Failed to create category:', err);
      setError(err.message);
      throw err;
    }
  }, [userId]);

  const updateCategory = useCallback(async (id, categoryData) => {
    try {
      setError(null);
      
      if (backendAvailable) {
        const updatedCategory = await categoryApi.updateCategory(id, categoryData);
        setCategories(prev => prev.map(c => c.id === id ? updatedCategory : c));
        return updatedCategory;
      } else {
        // Mock update for offline mode
        const updatedCategory = { ...categoryData, id };
        setCategories(prev => prev.map(c => c.id === id ? updatedCategory : c));
        return updatedCategory;
      }
    } catch (err) {
      console.error('Failed to update category:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const deleteCategory = useCallback(async (id) => {
    try {
      setError(null);
      
      if (backendAvailable) {
        await categoryApi.deleteCategory(id);
      }
      // Always remove from local state regardless of backend availability
      setCategories(prev => prev.filter(c => c.id !== id));
    } catch (err) {
      console.error('Failed to delete category:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  return {
    categories,
    loading,
    error,
    createCategory,
    updateCategory,
    deleteCategory,
    refetch: fetchCategories,
    usingMockData,
  };
};

// Mock data fallbacks
function getMockTransactions() {
  return [
    {
      id: 1,
      userId: 1,
      categoryId: 1,
      categoryName: 'Groceries',
      title: 'Weekly Grocery Shopping',
      amount: 125.50,
      transactionDate: '2025-01-02',
      notes: 'Weekly groceries from supermarket',
      importance: 'ESSENTIAL',
      cycle: 'WEEKLY',
      type: 'EXPENSE',
      createdAt: '2025-01-02T10:00:00'
    },
    {
      id: 2,
      userId: 1,
      categoryId: 2,
      categoryName: 'Salary',
      title: 'Monthly Salary',
      amount: 5000.00,
      transactionDate: '2025-01-01',
      notes: 'Monthly salary payment',
      importance: 'ESSENTIAL',
      cycle: 'MONTHLY',
      type: 'INCOME',
      createdAt: '2025-01-01T09:00:00'
    },
    {
      id: 3,
      userId: 1,
      categoryId: 3,
      categoryName: 'Entertainment',
      title: 'Movie Night',
      amount: 45.00,
      transactionDate: '2025-01-03',
      notes: 'Cinema tickets and snacks',
      importance: 'NICE_TO_HAVE',
      cycle: 'IRREGULAR',
      type: 'EXPENSE',
      createdAt: '2025-01-03T19:00:00'
    },
    {
      id: 4,
      userId: 1,
      categoryId: 4,
      categoryName: 'Utilities',
      title: 'Electricity Bill',
      amount: 150.00,
      transactionDate: '2024-12-28',
      notes: 'Monthly electricity bill',
      importance: 'ESSENTIAL',
      cycle: 'MONTHLY',
      type: 'EXPENSE',
      createdAt: '2024-12-28T14:00:00'
    },
    {
      id: 5,
      userId: 1,
      categoryId: 5,
      categoryName: 'Transport',
      title: 'Bus Pass',
      amount: 80.00,
      transactionDate: '2024-12-25',
      notes: 'Monthly bus pass',
      importance: 'HAVE_TO_HAVE',
      cycle: 'MONTHLY',
      type: 'EXPENSE',
      createdAt: '2024-12-25T08:00:00'
    },
    {
      id: 6,
      userId: 1,
      categoryId: 6,
      categoryName: 'Freelance',
      title: 'Web Development Project',
      amount: 1200.00,
      transactionDate: '2024-12-15',
      notes: 'Payment for web development project',
      importance: 'ESSENTIAL',
      cycle: 'IRREGULAR',
      type: 'INCOME',
      createdAt: '2024-12-15T16:00:00'
    }
  ];
}

function getMockCategories() {
  return [
    { id: 1, userId: 1, name: 'Groceries', type: 'EXPENSE', createdAt: '2024-01-01T00:00:00' },
    { id: 2, userId: 1, name: 'Salary', type: 'INCOME', createdAt: '2024-01-01T00:00:00' },
    { id: 3, userId: 1, name: 'Entertainment', type: 'EXPENSE', createdAt: '2024-01-01T00:00:00' },
    { id: 4, userId: 1, name: 'Utilities', type: 'EXPENSE', createdAt: '2024-01-01T00:00:00' },
    { id: 5, userId: 1, name: 'Transport', type: 'EXPENSE', createdAt: '2024-01-01T00:00:00' },
    { id: 6, userId: 1, name: 'Freelance', type: 'INCOME', createdAt: '2024-01-01T00:00:00' },
  ];
}

// Helper function to generate consistent mock IDs
export const generateMockId = () => Math.floor(Math.random() * 10000) + 1000;
