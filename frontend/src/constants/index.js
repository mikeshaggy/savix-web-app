export const TRANSACTION_TYPES = {
    INCOME: 'INCOME',
    EXPENSE: 'EXPENSE',
    ALL: 'ALL'
};

export const IMPORTANCE_LEVELS = {
    ESSENTIAL: 'ESSENTIAL',
    HAVE_TO_HAVE: 'HAVE_TO_HAVE',
    NICE_TO_HAVE: 'NICE_TO_HAVE',
    SHOULDNT_HAVE: 'SHOULDNT_HAVE'
};

export const FILTER_OPTIONS = {
    ALL: 'ALL',
    TODAY: 'TODAY',
    WEEK: 'WEEK',
    MONTH: 'MONTH',
    YEAR: 'YEAR'
};

export const DATE_PRESETS = {
    ALL_TIME: 'all_time',
    THIS_WEEK: 'this_week',
    THIS_MONTH: 'this_month',
    LAST_MONTH: 'last_month',
    CUSTOM: 'custom'
};

export const SORT_OPTIONS = {
    DATE: 'date',
    AMOUNT: 'amount',
    TITLE: 'title',
    CATEGORY: 'category'
};

export const SORT_ORDERS = {
    ASC: 'asc',
    DESC: 'desc'
};

export const COLORS = {
    PRIMARY: {
        DEFAULT: 'violet-500',
        HOVER: 'violet-600',
        LIGHT: 'violet-400'
    },
    SUCCESS: {
        DEFAULT: 'green-500',
        HOVER: 'green-600',
        LIGHT: 'green-400'
    },
    ERROR: {
        DEFAULT: 'red-500',
        HOVER: 'red-600',
        LIGHT: 'red-400'
    },
    WARNING: {
        DEFAULT: 'yellow-500',
        HOVER: 'yellow-600',
        LIGHT: 'yellow-400'
    }
};

export const VALIDATION_RULES = {
    TRANSACTION_TITLE: {
        MIN_LENGTH: 1,
        MAX_LENGTH: 50
    },
    TRANSACTION_AMOUNT: {
        MIN: 0.01,
        MAX: 999999.99
    },
    CATEGORY_NAME: {
        MIN_LENGTH: 1,
        MAX_LENGTH: 30
    }
};
