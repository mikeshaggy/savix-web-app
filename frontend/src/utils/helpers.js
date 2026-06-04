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

// ─── Color helpers (used by Funds accent styling + ColorPickerField) ──────────

// Accepts #rgb or #rrggbb (case-insensitive). Returns true only for valid hex.
export const isValidHexColor = (value) =>
  typeof value === 'string' && /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value.trim());

// Expands #rgb → #rrggbb, lowercases. Returns null if not a valid hex.
export const normalizeHexColor = (value) => {
  if (!isValidHexColor(value)) return null;
  let hex = value.trim().toLowerCase();
  if (hex.length === 4) {
    hex = '#' + hex.slice(1).split('').map(c => c + c).join('');
  }
  return hex;
};

// Always returns a usable 6-digit hex: the normalized value, or the fallback
// (violet) for empty/invalid input. Never throws — safe for inline styles.
export const resolveAccentColor = (value, fallback = '#7c3aed') =>
  normalizeHexColor(value) || fallback;

// Converts a hex color to an rgba() string at the given alpha. Falls back to
// violet when the input is invalid, so styles never break.
export const hexToRgba = (value, alpha = 1) => {
  const hex = resolveAccentColor(value);
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
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
  isValidHexColor,
  normalizeHexColor,
  resolveAccentColor,
  hexToRgba,
};
