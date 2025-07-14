// API Configuration
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000';

// Check if backend is running
export const checkBackendHealth = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/api/users`);
    return response.ok;
  } catch (error) {
    console.warn('Backend not accessible, falling back to mock data');
    return false;
  }
};

// Generic API request handler
async function apiRequest(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  
  const config = {
    headers: {
      'Content-Type': 'application/json',
      // Add CORS headers if needed
      'Accept': 'application/json',
      ...options.headers,
    },
    ...options,
  };

  try {
    const response = await fetch(url, config);
    
    if (!response.ok) {
      const errorText = await response.text();
      let errorMessage = `HTTP error! status: ${response.status}`;
      
      // Try to parse error response
      try {
        const errorData = JSON.parse(errorText);
        errorMessage = errorData.message || errorData.error || errorMessage;
      } catch {
        errorMessage = errorText || errorMessage;
      }
      
      throw new Error(errorMessage);
    }
    
    // Handle empty responses for DELETE requests
    if (response.status === 204) {
      return null;
    }
    
    return await response.json();
  } catch (error) {
    console.error('API request failed:', error);
    throw error;
  }
}

// User API functions
export const userApi = {
  getAllUsers: () => apiRequest('/api/users'),
  getUserById: (id) => apiRequest(`/api/users/${id}`),
  getUserByUsername: (username) => apiRequest(`/api/users/username/${username}`),
  createUser: (userData) => apiRequest('/api/users', {
    method: 'POST',
    body: JSON.stringify(userData),
  }),
  updateUser: (id, userData) => apiRequest(`/api/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(userData),
  }),
  deleteUser: (id) => apiRequest(`/api/users/${id}`, {
    method: 'DELETE',
  }),
};

// Category API functions
export const categoryApi = {
  getAllCategories: () => apiRequest('/api/categories'),
  getCategoryById: (id) => apiRequest(`/api/categories/${id}`),
  getCategoriesByUserId: (userId) => apiRequest(`/api/categories/user/${userId}`),
  createCategory: (categoryData) => apiRequest('/api/categories', {
    method: 'POST',
    body: JSON.stringify(categoryData),
  }),
  updateCategory: (id, categoryData) => apiRequest(`/api/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(categoryData),
  }),
  deleteCategory: (id) => apiRequest(`/api/categories/${id}`, {
    method: 'DELETE',
  }),
};

// Transaction API functions
export const transactionApi = {
  getAllTransactions: () => apiRequest('/api/transactions'),
  getTransactionById: (id) => apiRequest(`/api/transactions/${id}`),
  getTransactionsByUserId: (userId) => apiRequest(`/api/transactions/user/${userId}`),
  createTransaction: (transactionData) => apiRequest('/api/transactions', {
    method: 'POST',
    body: JSON.stringify(transactionData),
  }),
  updateTransaction: (id, transactionData) => apiRequest(`/api/transactions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(transactionData),
  }),
  deleteTransaction: (id) => apiRequest(`/api/transactions/${id}`, {
    method: 'DELETE',
  }),
};

// Combined API functions for dashboard
export const dashboardApi = {
  getUserDashboardData: async (userId) => {
    try {
      const [transactions, categories] = await Promise.all([
        transactionApi.getTransactionsByUserId(userId),
        categoryApi.getCategoriesByUserId(userId),
      ]);
      
      return { transactions, categories };
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error);
      throw error;
    }
  },
};

// Mock endpoints (since some features might need these but don't exist in backend yet)
export const mockApi = {
  // Mock analytics data
  getAnalytics: (userId) => {
    return Promise.resolve({
      monthlySpending: [
        { month: 'Jan', amount: 2500 },
        { month: 'Feb', amount: 3200 },
        { month: 'Mar', amount: 2800 },
        { month: 'Apr', amount: 3500 },
        { month: 'May', amount: 2900 },
        { month: 'Jun', amount: 3800 },
      ],
      categoryBreakdown: [
        { category: 'Groceries', amount: 1200, percentage: 35 },
        { category: 'Transport', amount: 800, percentage: 23 },
        { category: 'Utilities', amount: 600, percentage: 17 },
        { category: 'Entertainment', amount: 400, percentage: 12 },
        { category: 'Other', amount: 450, percentage: 13 },
      ],
      savingsGoal: {
        target: 5000,
        current: 3250,
        percentage: 65,
      },
    });
  },
  
  // Mock budget data
  getBudgets: (userId) => {
    return Promise.resolve([
      { id: 1, category: 'Groceries', budgetAmount: 1500, spentAmount: 1200, month: '2025-01' },
      { id: 2, category: 'Transport', budgetAmount: 1000, spentAmount: 800, month: '2025-01' },
      { id: 3, category: 'Entertainment', budgetAmount: 500, spentAmount: 400, month: '2025-01' },
    ]);
  },
};
