
export const getTransactionDate = (transaction) => {
  if (!transaction) return null;
  
  const dateStr = transaction.transactionDate || transaction.date || transaction.createdAt;
  
  if (!dateStr) return null;
  
  try {
    const date = new Date(dateStr);
    return isNaN(date.getTime()) ? null : date;
  } catch {
    return null;
  }
};

export const getTransactionCategoryId = (transaction) => {
  if (!transaction || transaction.categoryId == null) return null;
  
  const id = typeof transaction.categoryId === 'string' 
    ? parseInt(transaction.categoryId, 10)
    : transaction.categoryId;
    
  return isNaN(id) ? null : id;
};

const getStartOfDay = (date) => {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
};

const getEndOfDay = (date) => {
  const d = new Date(date);
  d.setHours(23, 59, 59, 999);
  return d;
};

const getStartOfWeek = (now) => {
  const date = new Date(now);
  const day = date.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  date.setDate(date.getDate() + diff);
  return getStartOfDay(date);
};

const getStartOfMonth = (now) => {
  const date = new Date(now);
  date.setDate(1);
  return getStartOfDay(date);
};

const getLastMonthRange = (now) => {
  const date = new Date(now);

  date.setDate(1);
  date.setMonth(date.getMonth() - 1);
  
  const from = getStartOfDay(date);
  

  const to = new Date(date);
  to.setMonth(to.getMonth() + 1);
  to.setDate(0);
  
  return {
    from,
    to: getEndOfDay(to)
  };
};

export const getPresetDateRange = (preset, now = new Date()) => {
  switch (preset) {
    case 'all_time':
      return {};
      
    case 'this_week':
      return {
        from: getStartOfWeek(now),
        to: getEndOfDay(now)
      };
      
    case 'this_month':
      return {
        from: getStartOfMonth(now),
        to: getEndOfDay(now)
      };
      
    case 'last_month':
      return getLastMonthRange(now);
      
    default:
      return {};
  }
};

export const isWithinRange = (txDate, from, to) => {
  if (!txDate) return false;
  
  const txTime = txDate.getTime();
  
  if (from && !to) {
    return txTime >= from.getTime();
  }
  
  if (!from && to) {
    return txTime <= to.getTime();
  }
  
  if (from && to) {
    return txTime >= from.getTime() && txTime <= to.getTime();
  }
  
  return true;
};

export const filterByDate = (transactions, preset, customFrom = null, customTo = null) => {
  if (!transactions || !Array.isArray(transactions)) return [];
  
  if (preset === 'all_time') {
    return transactions;
  }
  
  let from, to;
  
  if (preset === 'custom') {
    from = customFrom ? getStartOfDay(new Date(customFrom)) : null;
    to = customTo ? getEndOfDay(new Date(customTo)) : null;
  } else {
    const range = getPresetDateRange(preset);
    from = range.from;
    to = range.to;
  }
  
  return transactions.filter(tx => {
    const txDate = getTransactionDate(tx);
    
    if (!txDate) return false;
    return isWithinRange(txDate, from, to);
  });
};
