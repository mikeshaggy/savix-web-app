import { useMemo } from 'react';
import { FILTER_OPTIONS, SORT_OPTIONS, SORT_ORDERS } from '../constants';

export const useTransactionFilters = (transactions, filters) => {
    const {
        searchTerm = '',
        typeFilter = 'ALL',
        categoryFilter = 'ALL',
        importanceFilter = 'ALL',
        dateFilter = 'ALL',
        sortBy = SORT_OPTIONS.DATE,
        sortOrder = SORT_ORDERS.DESC
    } = filters;

    const filteredTransactions = useMemo(() => {
        let filtered = transactions || [];

        // Apply search filter
        if (searchTerm) {
            const searchLower = searchTerm.toLowerCase();
            filtered = filtered.filter(transaction => 
                transaction.title?.toLowerCase().includes(searchLower) ||
                transaction.notes?.toLowerCase().includes(searchLower) ||
                transaction.categoryName?.toLowerCase().includes(searchLower)
            );
        }

        // Apply type filter
        if (typeFilter !== 'ALL') {
            filtered = filtered.filter(transaction => transaction.type === typeFilter);
        }

        // Apply category filter
        if (categoryFilter !== 'ALL') {
            filtered = filtered.filter(transaction => 
                transaction.categoryId === parseInt(categoryFilter)
            );
        }

        // Apply importance filter
        if (importanceFilter !== 'ALL') {
            filtered = filtered.filter(transaction => 
                transaction.importance === importanceFilter
            );
        }

        // Apply date filter
        if (dateFilter !== 'ALL') {
            const now = new Date();
            const filterDate = new Date();
            
            switch (dateFilter) {
                case FILTER_OPTIONS.TODAY:
                    filterDate.setHours(0, 0, 0, 0);
                    filtered = filtered.filter(transaction => 
                        new Date(transaction.transactionDate) >= filterDate
                    );
                    break;
                case FILTER_OPTIONS.WEEK:
                    filterDate.setDate(now.getDate() - 7);
                    filtered = filtered.filter(transaction => 
                        new Date(transaction.transactionDate) >= filterDate
                    );
                    break;
                case FILTER_OPTIONS.MONTH:
                    filterDate.setMonth(now.getMonth() - 1);
                    filtered = filtered.filter(transaction => 
                        new Date(transaction.transactionDate) >= filterDate
                    );
                    break;
                case FILTER_OPTIONS.YEAR:
                    filterDate.setFullYear(now.getFullYear() - 1);
                    filtered = filtered.filter(transaction => 
                        new Date(transaction.transactionDate) >= filterDate
                    );
                    break;
                default:
                    break;
            }
        }

        // Apply sorting
        filtered.sort((a, b) => {
            let aValue, bValue;

            switch (sortBy) {
                case SORT_OPTIONS.DATE:
                    aValue = new Date(a.transactionDate);
                    bValue = new Date(b.transactionDate);
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
                    aValue = new Date(a.transactionDate);
                    bValue = new Date(b.transactionDate);
            }

            if (sortOrder === SORT_ORDERS.ASC) {
                return aValue < bValue ? -1 : aValue > bValue ? 1 : 0;
            } else {
                return aValue > bValue ? -1 : aValue < bValue ? 1 : 0;
            }
        });

        return filtered;
    }, [transactions, searchTerm, typeFilter, categoryFilter, importanceFilter, dateFilter, sortBy, sortOrder]);

    // Calculate filter statistics
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
