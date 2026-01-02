export const getTransactionType = (transaction, categories) => {
  if (!transaction || !categories) return null;
  const category = categories.find(c => c.id === transaction.categoryId);
  return category?.type || null;
};

export const enrichTransactionsWithType = (transactions, categories) => {
  if (!transactions || !categories) return [];
  
  return transactions.map(transaction => ({
    ...transaction,
    type: getTransactionType(transaction, categories)
  }));
};

export const formatCurrency = (amount) => {
  return new Intl.NumberFormat('pl-PL', {
    style: 'currency',
    currency: 'PLN',
    currencyDisplay: 'code'
  }).format(amount);
};

export const formatDate = (date) => {
  if (!date) return '';
  
  const dateObj = typeof date === 'string' ? new Date(date) : date;
  return dateObj.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
};

export const formatDateForAPI = (date) => {
  if (!date) return '';
  
  const dateObj = typeof date === 'string' ? new Date(date) : date;
  return dateObj.toISOString().split('T')[0];
};

export const getTodayForAPI = () => {
  return new Date().toISOString().split('T')[0];
};

export const formatImportance = (importance) => {
  const map = {
    'ESSENTIAL': 'Essential',
    'HAVE_TO_HAVE': 'Have to Have',
    'NICE_TO_HAVE': 'Nice to Have',
    'SHOULDNT_HAVE': "Shouldn't Have"
  };
  return map[importance] || importance;
};

export const formatCycle = (cycle) => {
  const map = {
    'ONE_TIME': 'One Time',
    'WEEKLY': 'Weekly',
    'MONTHLY': 'Monthly',
    'YEARLY': 'Yearly',
    'IRREGULAR': 'Irregular'
  };
  return map[cycle] || cycle;
};

export const calculateTotal = (transactions, type = null) => {
  return transactions
    .filter(tx => !type || tx.type === type)
    .reduce((sum, tx) => sum + (parseFloat(tx.amount) || 0), 0);
};

export const getCurrentMonthTransactions = (transactions) => {
  const now = new Date();
  const currentMonth = now.getMonth();
  const currentYear = now.getFullYear();
  
  return transactions.filter(tx => {
    const txDate = new Date(tx.transactionDate);
    return txDate.getMonth() === currentMonth && txDate.getFullYear() === currentYear;
  });
};

export const validateTransaction = (transaction) => {
  const errors = {};
  
  if (!transaction.title?.trim()) {
    errors.title = 'Title is required';
  }
  
  if (!transaction.amount || parseFloat(transaction.amount) <= 0) {
    errors.amount = 'Amount must be greater than 0';
  }
  
  if (!transaction.categoryId) {
    errors.categoryId = 'Category is required';
  }
  
  if (!transaction.transactionDate) {
    errors.transactionDate = 'Date is required';
  }
  
  if (!transaction.importance) {
    errors.importance = 'Importance level is required';
  }
  
  if (!transaction.cycle) {
    errors.cycle = 'Cycle is required';
  }
  
  if (!transaction.type) {
    errors.type = 'Type is required';
  }
  
  return {
    isValid: Object.keys(errors).length === 0,
    errors
  };
};

export const debounce = (func, wait) => {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
};

export default {
  formatCurrency,
  formatDate,
  formatDateForAPI,
  getTodayForAPI,
  formatImportance,
  formatCycle,
  calculateTotal,
  getCurrentMonthTransactions,
  validateTransaction,
  debounce
};
