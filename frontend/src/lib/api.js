import { checkBackendHealth } from './backendHealth';

export { checkBackendHealth };

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || '/api/proxy';

const sessionState = {
  refreshPromise: null,
  authChangeListeners: new Set(),
  isAuthenticated: null,
};

export const onAuthStateChange = (listener) => {
  sessionState.authChangeListeners.add(listener);
  return () => sessionState.authChangeListeners.delete(listener);
};

const notifyAuthStateChange = (isAuthenticated) => {
  if (sessionState.isAuthenticated !== isAuthenticated) {
    sessionState.isAuthenticated = isAuthenticated;
    sessionState.authChangeListeners.forEach(listener => {
      try {
        listener(isAuthenticated);
      } catch (error) {
        console.error('Auth state listener error:', error);
      }
    });
  }
};

export const markAuthenticated = () => {
  notifyAuthStateChange(true);
};

export const markUnauthenticated = () => {
  notifyAuthStateChange(false);
};

export const getAuthState = () => sessionState.isAuthenticated;

const attemptTokenRefresh = async () => {
  if (sessionState.refreshPromise) {
    return sessionState.refreshPromise;
  }

  sessionState.refreshPromise = performTokenRefresh();

  try {
    const result = await sessionState.refreshPromise;
    return result;
  } finally {
    sessionState.refreshPromise = null;
  }
};

const performTokenRefresh = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
    });

    if (response.ok) {
      notifyAuthStateChange(true);
      return true;
    }

    notifyAuthStateChange(false);
    return false;
  } catch (error) {
    console.error('Token refresh failed:', error);
    notifyAuthStateChange(false);
    return false;
  }
};

export class ApiError extends Error {
  constructor(status, message, details = null, code = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
    this.code = code;
    this.isApiError = true;
  }

  get isUnauthorized() {
    return this.status === 401;
  }

  get isForbidden() {
    return this.status === 403;
  }

  get isNotFound() {
    return this.status === 404;
  }

  get isValidationError() {
    return this.status === 400;
  }

  get isServerError() {
    return this.status >= 500;
  }
}

function safeParseJson(text) {
  if (!text || text.trim() === '') {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function parseErrorResponse(response) {
  let message = `HTTP error! status: ${response.status}`;
  let details = null;
  let code = null;

  try {
    const text = await response.text();
    const json = safeParseJson(text);

    if (json) {
      message = json.message || json.error || json.title || message;
      details = json.details || json.errors || null;
      code = json.code || json.errorCode || null;
    } else if (text) {
      message = text;
    }
  } catch {

  }

  return { message, details, code };
}

async function parseSuccessResponse(response) {
  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type');
  
  if (contentType && contentType.includes('application/json')) {
    const text = await response.text();
    return safeParseJson(text);
  }

  const text = await response.text();
  if (!text || text.trim() === '') {
    return null;
  }

  const json = safeParseJson(text);
  if (json !== null) {
    return json;
  }

  return text;
}

const NO_AUTO_REFRESH_ENDPOINTS = [
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/logout',
  '/auth/forgot-password',
  '/auth/reset-password',
];

const shouldSkipAutoRefresh = (endpoint) => {
  return NO_AUTO_REFRESH_ENDPOINTS.some(skip => endpoint.startsWith(skip));
};

const executeFetch = async (url, config) => {
  const response = await fetch(url, config);
  return response;
};

async function apiRequest(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  
  const {
    headers: customHeaders = {},
    body,
    skipJsonBody = false,
    _isRetry = false,
    ...restOptions
  } = options;

  const headers = {
    'Accept': 'application/json',
    ...customHeaders,
  };

  if (body !== undefined && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  const config = {
    ...restOptions,
    headers,
    credentials: 'include',
  };

  let processedBody;
  if (body !== undefined) {
    if (skipJsonBody || typeof body === 'string') {
      processedBody = body;
    } else {
      processedBody = JSON.stringify(body);
    }
    config.body = processedBody;
  }

  try {
    const response = await executeFetch(url, config);

    if (!response.ok) {
      const { message, details, code } = await parseErrorResponse(response);
      
      if (response.status === 401 && !_isRetry && !shouldSkipAutoRefresh(endpoint)) {
        const refreshSucceeded = await attemptTokenRefresh();
        
        if (refreshSucceeded) {
          return apiRequest(endpoint, {
            ...options,
            _isRetry: true,
          });
        }

      }
      
      if (response.status !== 401 && response.status !== 403) {
        // Non-auth errors don't affect auth state
      }
      
      throw new ApiError(response.status, message, details, code);
    }

    if (!shouldSkipAutoRefresh(endpoint)) {
      notifyAuthStateChange(true);
    }

    return await parseSuccessResponse(response);
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }

    console.error('API request failed:', error);
    throw new ApiError(
      0,
      error.message || 'Network error - please check your connection',
      null,
      'NETWORK_ERROR'
    );
  }
}

export const get = (endpoint, options = {}) => 
  apiRequest(endpoint, { ...options, method: 'GET' });

export const post = (endpoint, body, options = {}) => 
  apiRequest(endpoint, { ...options, method: 'POST', body });

export const put = (endpoint, body, options = {}) => 
  apiRequest(endpoint, { ...options, method: 'PUT', body });

export const patch = (endpoint, body, options = {}) => 
  apiRequest(endpoint, { ...options, method: 'PATCH', body });

export const del = (endpoint, options = {}) => 
  apiRequest(endpoint, { ...options, method: 'DELETE' });

export const authApi = {
  register: (data) => post('/auth/register', data),

  login: async (data) => {
    const result = await post('/auth/login', data);
    markAuthenticated();
    return result;
  },

  logout: async () => {
    try {
      const result = await post('/auth/logout');
      markUnauthenticated();
      return result;
    } catch (error) {
      markUnauthenticated();
      throw error;
    }
  },

  refresh: async () => {
    const result = await post('/auth/refresh');
    markAuthenticated();
    return result;
  },

  forgotPassword: (data) => post('/auth/forgot-password', data),

  resetPassword: (data) => post('/auth/reset-password', data),

  changePassword: async (data) => {
    const result = await post('/auth/change-password', data);
    markUnauthenticated();
    return result;
  },
};

export const meApi = {
  getCurrentUser: () => get('/me'),

  updateCurrentUser: (data) => patch('/me', data),

  deleteCurrentUser: () => del('/me'),
};

export const walletApi = {
  getAllWallets: () => get('/wallets'),

  getWalletById: (id) => get(`/wallets/${id}`),

  getWalletBalanceHistory: (walletId, params = {}) => {
    if (!walletId || walletId === 'undefined' || walletId === 'null') {
      throw new ApiError(400, 'Invalid walletId provided: ' + walletId, null, 'INVALID_WALLET_ID');
    }

    const searchParams = new URLSearchParams();
    if (params.from) searchParams.set('from', params.from);
    if (params.to) searchParams.set('to', params.to);
    if (params.page != null) searchParams.set('page', String(params.page));
    if (params.size != null) searchParams.set('size', String(params.size));

    const qs = searchParams.toString();
    return get(`/wallets/${walletId}/balance-history${qs ? `?${qs}` : ''}`);
  },

  createWallet: (walletData) => post('/wallets', walletData),

  updateWallet: (id, walletData) => put(`/wallets/${id}`, walletData),

  updateWalletBalance: (id, newBalance) => patch(`/wallets/${id}/balance`, { newBalance }),

  deleteWallet: (id) => del(`/wallets/${id}`),
};

export const categoryApi = {
  getAllCategories: (type) => {
    const endpoint = type ? `/categories?type=${type}` : '/categories';
    return get(endpoint);
  },

  getCategoryById: (id) => get(`/categories/${id}`),

  createCategory: (categoryData) => post('/categories', categoryData),

  updateCategory: (id, categoryData) => put(`/categories/${id}`, categoryData),

  deleteCategory: (id) => del(`/categories/${id}`),
};

export const transactionApi = {
  getAllTransactions: () => get('/transactions'),

  getTransactions: (params = {}) => {
    const searchParams = new URLSearchParams();
    
    if (params.walletId != null) searchParams.set('walletId', String(params.walletId));
    if (params.page != null) searchParams.set('page', String(params.page));
    if (params.size != null) searchParams.set('size', String(params.size));
    if (params.sort) searchParams.set('sort', params.sort);
    
    if (params.type && params.type.length > 0) {
      params.type.forEach(t => searchParams.append('types', t));
    }
    if (params.categoryId && params.categoryId.length > 0) {
      params.categoryId.forEach(id => searchParams.append('categoryIds', String(id)));
    }
    if (params.importance && params.importance.length > 0) {
      params.importance.forEach(imp => searchParams.append('importances', imp));
    }
    
    if (params.startDate) searchParams.set('startDate', params.startDate);
    if (params.endDate) searchParams.set('endDate', params.endDate);
    if (params.q) searchParams.set('q', params.q);
    
    const qs = searchParams.toString();
    return get(`/transactions${qs ? `?${qs}` : ''}`);
  },

  getTransactionById: (id) => get(`/transactions/${id}`),

  getTransactionsByWalletId: (walletId) => get(`/transactions/wallet/${walletId}`),

  createTransaction: (transactionData) => post('/transactions', transactionData),

  updateTransaction: (id, transactionData) => put(`/transactions/${id}`, transactionData),

  deleteTransaction: (id) => del(`/transactions/${id}`),
};

export const csvImportApi = {
  importCsv: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    
    return apiRequest('/csv-import/csv', {
      method: 'POST',
      body: formData,
      skipJsonBody: true,
      headers: {
      },
    });
  },
};

export const dashboardApi = {
  getDashboard: async (walletId, startDate, endDate, periodType = 'PAY_CYCLE') => {
    if (!walletId || walletId === 'undefined' || walletId === 'null') {
      throw new ApiError(400, 'Invalid walletId provided: ' + walletId, null, 'INVALID_WALLET_ID');
    }
    
    const params = new URLSearchParams({ walletId, periodType });
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);
    
    return get(`/dashboard?${params.toString()}`);
  },

  getWalletDashboardData: async (walletId) => {
    if (!walletId || walletId === 'undefined' || walletId === 'null') {
      throw new ApiError(400, 'Invalid walletId provided: ' + walletId, null, 'INVALID_WALLET_ID');
    }
    
    try {
      const [transactions, wallet] = await Promise.all([
        transactionApi.getTransactionsByWalletId(walletId),
        walletApi.getWalletById(walletId),
      ]);
      
      const categories = await categoryApi.getAllCategories();
      
      return { transactions, categories, wallet };
    } catch (error) {
      console.error('Failed to fetch wallet dashboard data:', error);
      throw error;
    }
  },

  getAuthenticatedDashboardData: async () => {
    try {
      const wallets = await walletApi.getAllWallets();
      
      if (!wallets || wallets.length === 0) {
        return { transactions: [], categories: [], wallets: [] };
      }

      const [allCategories, ...transactionsByWallet] = await Promise.all([
        categoryApi.getAllCategories(),
        ...wallets.map(wallet => transactionApi.getTransactionsByWalletId(wallet.id))
      ]);
      
      const allTransactions = transactionsByWallet.flat();
      
      return { 
        transactions: allTransactions, 
        categories: allCategories, 
        wallets 
      };
    } catch (error) {
      console.error('Failed to fetch authenticated dashboard data:', error);
      throw error;
    }
  },
};

export const fixedPaymentApi = {
  getTileData: (walletId) => get(`/fixed-payments/tile?walletId=${walletId}`),
  getAll: (walletId) => get(`/fixed-payments?walletId=${walletId}`),
  create: (data) => post('/fixed-payments', data),
  update: (id, data) => put(`/fixed-payments/${id}`, data),
  deactivate: (id) => del(`/fixed-payments/${id}`),
};

export const transferApi = {
  getAllTransfers: () => get('/transfers'),

  getTransferById: (id) => get(`/transfers/${id}`),

  getTransfersByWalletId: (walletId) => get(`/transfers/wallet/${walletId}`),

  createTransfer: (transferData) => post('/transfers', transferData),

  updateTransfer: (id, transferData) => put(`/transfers/${id}`, transferData),

  deleteTransfer: (id) => del(`/transfers/${id}`),
};

export const walletEntryApi = {
  getWalletBalanceHistory: (walletId) => get(`/wallet-entries/wallet/${walletId}`),
};