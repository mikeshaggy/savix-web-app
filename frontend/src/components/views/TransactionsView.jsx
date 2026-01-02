import React from 'react';
import { Plus } from 'lucide-react';
import TransactionFilters from '../transactions/TransactionFilters';
import TransactionTable from '../transactions/TransactionTable';
import TransactionStatsCards from '../cards/TransactionStatsCards';

export default function TransactionsView({
    filteredTransactions = [],
    allTransactions = [],
    categories = [],
    typeFilter,
    setTypeFilter,
    categoryFilter,
    setCategoryFilter,
    importanceFilter,
    setImportanceFilter,
    dateFilter,
    setDateFilter,
    datePreset,
    setDatePreset,
    customFromDate,
    setCustomFromDate,
    customToDate,
    setCustomToDate,
    sortBy,
    setSortBy,
    sortOrder,
    setSortOrder,
    onNewTransaction
}) {
    return (
        <div className="space-y-6">
            {/* Transactions Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold">All Transactions</h2>
                    <p className="text-gray-400 text-sm mt-1">
                        Showing {filteredTransactions.length} of {allTransactions.length} transactions
                    </p>
                </div>
                <button 
                    onClick={onNewTransaction}
                    className="px-4 py-2 bg-violet-500 hover:bg-violet-600 rounded-lg transition-colors flex items-center gap-2"
                >
                    <Plus className="w-4 h-4" />
                    New Transaction
                </button>
            </div>

            {/* Advanced Filters */}
            <TransactionFilters
                typeFilter={typeFilter}
                setTypeFilter={setTypeFilter}
                categoryFilter={categoryFilter}
                setCategoryFilter={setCategoryFilter}
                importanceFilter={importanceFilter}
                setImportanceFilter={setImportanceFilter}
                dateFilter={dateFilter}
                setDateFilter={setDateFilter}
                datePreset={datePreset}
                setDatePreset={setDatePreset}
                customFromDate={customFromDate}
                setCustomFromDate={setCustomFromDate}
                customToDate={customToDate}
                setCustomToDate={setCustomToDate}
                sortBy={sortBy}
                setSortBy={setSortBy}
                sortOrder={sortOrder}
                setSortOrder={setSortOrder}
                categories={categories}
            />

            {/* Transactions Table */}
            <TransactionTable
                filteredTransactions={filteredTransactions}
                categories={categories}
                sortBy={sortBy}
                setSortBy={setSortBy}
                sortOrder={sortOrder}
                setSortOrder={setSortOrder}
                typeFilter={typeFilter}
                categoryFilter={categoryFilter}
                importanceFilter={importanceFilter}
                dateFilter={dateFilter}
            />

            {/* Transaction Stats Summary */}
            <TransactionStatsCards filteredTransactions={filteredTransactions} />
        </div>
    );
}
