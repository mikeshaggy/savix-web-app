'use client';
import React, { useState } from 'react';
import TransactionsView from '../TransactionsView';
import { useAppContext } from '../../contexts/AppContext';
import { useTransactionFilters } from '../../hooks/useTransactionFilters';
import { Loading } from '../Loading';
import { FILTER_OPTIONS, SORT_OPTIONS, SORT_ORDERS } from '../../constants';

export default function TransactionsPage() {
    const { 
        allTransactions, 
        transactions, 
        categories,
        isLoading,
        hasError
    } = useAppContext();

    // Local state for transaction-specific filters
    const [typeFilter, setTypeFilter] = useState('ALL');
    const [categoryFilter, setCategoryFilter] = useState('ALL');
    const [importanceFilter, setImportanceFilter] = useState('ALL');
    const [dateFilter, setDateFilter] = useState('ALL');
    const [sortBy, setSortBy] = useState(SORT_OPTIONS.DATE);
    const [sortOrder, setSortOrder] = useState(SORT_ORDERS.DESC);

    // Use the custom filtering hook
    const { filteredTransactions, filterStats } = useTransactionFilters(allTransactions, {
        typeFilter,
        categoryFilter,
        importanceFilter,
        dateFilter,
        sortBy,
        sortOrder
    });

    // Show loading state
    if (isLoading) {
        return <Loading message="Loading transactions..." />;
    }

    // Show error state
    if (hasError) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold mb-2">Error loading transactions</h2>
                    <p className="text-gray-400">Please try refreshing the page</p>
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Filter Statistics */}
            <div className="bg-gray-900 rounded-lg p-4">
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                        <span className="text-sm text-gray-400">
                            Showing {filterStats.filtered} of {filterStats.total} transactions
                        </span>
                        <span className="text-sm text-gray-400">
                            ({filterStats.percentage}% of total)
                        </span>
                    </div>
                    <div className="flex items-center gap-4 text-sm">
                        <span className="text-green-400">Income: {filterStats.income}</span>
                        <span className="text-red-400">Expenses: {filterStats.expense}</span>
                    </div>
                </div>
            </div>

            {/* Transactions View */}
            <TransactionsView 
                filteredTransactions={filteredTransactions}
                allTransactions={allTransactions}
                transactions={transactions}
                categories={categories}
                typeFilter={typeFilter}
                setTypeFilter={setTypeFilter}
                categoryFilter={categoryFilter}
                setCategoryFilter={setCategoryFilter}
                importanceFilter={importanceFilter}
                setImportanceFilter={setImportanceFilter}
                dateFilter={dateFilter}
                setDateFilter={setDateFilter}
                sortBy={sortBy}
                setSortBy={setSortBy}
                sortOrder={sortOrder}
                setSortOrder={setSortOrder}
                filterStats={filterStats}
            />
        </div>
    );
}
