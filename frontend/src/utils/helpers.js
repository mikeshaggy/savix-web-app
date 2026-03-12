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

export const formatCurrency = (amount, lang = 'pl') => {
  const locale = lang === 'pl' ? 'pl-PL' : 'en-US';
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'PLN',
    currencyDisplay: 'code'
  }).format(amount);
};

export const formatDate = (date, lang = 'en') => {
  if (!date) return '';
  
  const dateObj = typeof date === 'string' ? new Date(date) : date;
  const locale = lang === 'pl' ? 'pl-PL' : 'en-US';
  return dateObj.toLocaleDateString(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
};

export const getImportanceKey = (importance) => {
  const keyMap = {
    'ESSENTIAL': 'essential',
    'HAVE_TO_HAVE': 'haveToHave',
    'NICE_TO_HAVE': 'niceToHave',
    'SHOULDNT_HAVE': 'shouldntHave',
    'INVESTMENT': 'investment'
  };
  return keyMap[importance] || importance?.toLowerCase() || 'essential';
};

export default {
  formatCurrency,
  formatDate,
  getImportanceKey,
};
