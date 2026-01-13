import { checkBackendHealth } from './backendHealth';

export { checkBackendHealth };

async function apiRequest(endpoint, options = {}) {
  const url = `${endpoint}`;
  
  const config = {
    headers: {
      'Content-Type': 'application/json',
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
      
      try {
        const errorData = JSON.parse(errorText);
        errorMessage = errorData.message || errorData.error || errorMessage;
      } catch {
        errorMessage = errorText || errorMessage;
      }
      
      throw new Error(errorMessage);
    }
    
    if (response.status === 204) {
      return null;
    }
    
    return await response.json();
  } catch (error) {
    console.error('API request failed:', error);
    throw error;
  }
}

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

export const categoryApi = {
  getAllCategories: () => apiRequest('/api/categories'),
  getCategoryById: (id) => apiRequest(`/api/categories/${id}`),
  getCategoriesByUserId: (userId) => apiRequest(`/api/categories/user/${userId}`),
  getCategoriesByUserIdAndType: (userId, type) => apiRequest(`/api/categories/user/${userId}/type/${type}`),
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

export const walletApi = {
  getAllWallets: () => apiRequest('/api/wallets'),
  getWalletById: (id) => apiRequest(`/api/wallets/${id}`),
  getWalletsByUserId: (userId) => apiRequest(`/api/wallets/user/${userId}`),
  createWallet: (walletData) => apiRequest('/api/wallets', {
    method: 'POST',
    body: JSON.stringify(walletData),
  }),
  updateWallet: (id, walletData) => apiRequest(`/api/wallets/${id}`, {
    method: 'PUT',
    body: JSON.stringify(walletData),
  }),
  updateWalletBalance: (id, newBalance) => apiRequest(`/api/wallets/${id}/balance`, {
    method: 'PATCH',
    body: JSON.stringify(newBalance),
  }),
  deleteWallet: (id) => apiRequest(`/api/wallets/${id}`, {
    method: 'DELETE',
  }),
};

export const transactionApi = {
  getAllTransactions: () => apiRequest('/api/transactions'),
  getTransactionById: (id) => apiRequest(`/api/transactions/${id}`),
  getTransactionsByWalletId: (walletId) => apiRequest(`/api/transactions/wallet/${walletId}`),
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

export const dashboardApi = {
  getUserDashboardData: async (userId) => {
    try {
      const wallets = await walletApi.getWalletsByUserId(userId);
      
      if (!wallets || wallets.length === 0) {
        return { transactions: [], categories: [], wallets: [] };
      }

      const [allCategories, ...transactionsByWallet] = await Promise.all([
        categoryApi.getCategoriesByUserId(userId),
        ...wallets.map(wallet => transactionApi.getTransactionsByWalletId(wallet.id))
      ]);
      
      const allTransactions = transactionsByWallet.flat();
      
      return { 
        transactions: allTransactions, 
        categories: allCategories, 
        wallets 
      };
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error);
      throw error;
    }
  },
  
  getWalletDashboardData: async (walletId) => {
    if (!walletId || walletId === 'undefined' || walletId === 'null') {
      throw new Error('Invalid walletId provided: ' + walletId);
    }
    
    try {
      const [transactions, wallet] = await Promise.all([
        transactionApi.getTransactionsByWalletId(walletId),
        walletApi.getWalletById(walletId),
      ]);
      
      const categories = await categoryApi.getCategoriesByUserId(wallet.userId);
      
      return { transactions, categories, wallet };
    } catch (error) {
      console.error('Failed to fetch wallet dashboard data:', error);
      throw error;
    }
  },
};


