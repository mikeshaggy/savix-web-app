import { useMemo } from 'react';
import { FILTER_OPTIONS, SORT_OPTIONS, SORT_ORDERS, DATE_PRESETS } from '@/constants';
import { getTransactionDate, getTransactionCategoryId, filterByDate } from '@/utils/dateFilters';

export const useTransactionFilters = (transactions, filters) => {
    const {
        searchTerm = '',
        typeFilter = 'ALL',
        categoryFilter = 'ALL',
        importanceFilter = 'ALL',
        dateFilter = 'ALL',
        datePreset = DATE_PRESETS.ALL_TIME,
        customFromDate = null,
        customToDate = null,
        sortBy = SORT_OPTIONS.DATE,
        sortOrder = SORT_ORDERS.DESC
    } = filters;

    const filteredTransactions = useMemo(() => {
        let filtered = transactions || [];

        if (searchTerm) {
            const searchLower = searchTerm.toLowerCase();
            filtered = filtered.filter(transaction => 
                transaction.title?.toLowerCase().includes(searchLower) ||
                transaction.notes?.toLowerCase().includes(searchLower) ||
                transaction.categoryName?.toLowerCase().includes(searchLower)
            );
        }

        if (typeFilter !== 'ALL') {
            filtered = filtered.filter(transaction => transaction.type === typeFilter);
        }

        if (categoryFilter !== 'ALL') {
            const selectedCategoryId = parseInt(categoryFilter, 10);
            filtered = filtered.filter(transaction => {
                const txCategoryId = getTransactionCategoryId(transaction);
                return txCategoryId === selectedCategoryId;
            });
        }

        if (importanceFilter !== 'ALL') {
            filtered = filtered.filter(transaction => 
                transaction.importance === importanceFilter
            );
        }

        const preset = datePreset || dateFilter;
        
        if (preset === DATE_PRESETS.ALL_TIME || preset === 'ALL') {
            // no filtering needed
        } else if (preset === DATE_PRESETS.CUSTOM || preset === 'custom') {
            filtered = filterByDate(filtered, 'custom', customFromDate, customToDate);
        } else {
            let mappedPreset = preset;
            if (preset === 'WEEK' || preset === FILTER_OPTIONS.WEEK) {
                mappedPreset = DATE_PRESETS.THIS_WEEK;
            } else if (preset === 'MONTH' || preset === FILTER_OPTIONS.MONTH) {
                mappedPreset = DATE_PRESETS.THIS_MONTH;
            } else if (preset === 'TODAY' || preset === FILTER_OPTIONS.TODAY) {
                const today = new Date();
                const todayStr = today.toISOString().split('T')[0];
                filtered = filterByDate(filtered, 'custom', todayStr, todayStr);
            } else if (preset === 'THIS_WEEK') {
                mappedPreset = DATE_PRESETS.THIS_WEEK;
            } else if (preset === 'THIS_MONTH') {
                mappedPreset = DATE_PRESETS.THIS_MONTH;
            } else if (preset === 'LAST_MONTH') {
                mappedPreset = DATE_PRESETS.LAST_MONTH;
            }
            
            if (mappedPreset !== preset || [DATE_PRESETS.THIS_WEEK, DATE_PRESETS.THIS_MONTH, DATE_PRESETS.LAST_MONTH].includes(mappedPreset)) {
                filtered = filterByDate(filtered, mappedPreset, null, null);
            }
        }

        filtered.sort((a, b) => {
            let aValue, bValue;

            switch (sortBy) {
                case SORT_OPTIONS.DATE:
                    const aDate = getTransactionDate(a);
                    const bDate = getTransactionDate(b);

                    if (!aDate && !bDate) return 0;
                    if (!aDate) return 1;
                    if (!bDate) return -1;
                    aValue = aDate;
                    bValue = bDate;
                    break;
                case SORT_OPTIONS.AMOUNT:
                    aValue = parseFloat(a.amount);
                    bValue = parseFloat(b.amount);
                    break;
                case SORT_OPTIONS.TITLE:
                    aValue = a.title?.toLowerCase() || '';
                    bValue = b.title?.toLowerCase() || '';
                    break;
                case SORT_OPTIONS.CATEGORY:
                    aValue = a.categoryName?.toLowerCase() || '';
                    bValue = b.categoryName?.toLowerCase() || '';
                    break;
                default:
                    const aDefaultDate = getTransactionDate(a);
                    const bDefaultDate = getTransactionDate(b);
                    if (!aDefaultDate && !bDefaultDate) return 0;
                    if (!aDefaultDate) return 1;
                    if (!bDefaultDate) return -1;
                    aValue = aDefaultDate;
                    bValue = bDefaultDate;
            }

            if (sortOrder === SORT_ORDERS.ASC) {
                return aValue < bValue ? -1 : aValue > bValue ? 1 : 0;
            } else {
                return aValue > bValue ? -1 : aValue < bValue ? 1 : 0;
            }
        });

        return filtered;
    }, [transactions, searchTerm, typeFilter, categoryFilter, importanceFilter, dateFilter, datePreset, customFromDate, customToDate, sortBy, sortOrder]);

    const filterStats = useMemo(() => {
        const total = transactions?.length || 0;
        const filtered = filteredTransactions.length;
        const income = filteredTransactions.filter(t => t.type === 'INCOME').length;
        const expense = filteredTransactions.filter(t => t.type === 'EXPENSE').length;

        return {
            total,
            filtered,
            income,
            expense,
            percentage: total > 0 ? Math.round((filtered / total) * 100) : 0
        };
    }, [transactions, filteredTransactions]);

    return {
        filteredTransactions,
        filterStats
    };
};

export default useTransactionFilters;
