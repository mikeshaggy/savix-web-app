import React from 'react';
import { formatCurrency } from '@/utils/helpers';

export default function TransactionStatsCards({ filteredTransactions = [] }) {
    const totalIncome = filteredTransactions
        .filter(t => t.type === 'INCOME')
        .reduce((sum, t) => sum + parseFloat(t.amount || 0), 0);

    const totalExpenses = filteredTransactions
        .filter(t => t.type === 'EXPENSE')
        .reduce((sum, t) => sum + parseFloat(t.amount || 0), 0);

    return (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-gray-900/50 border border-gray-800 rounded-xl p-6">
                <h4 className="text-lg font-semibold mb-2">Filtered Results</h4>
                <p className="text-3xl font-bold text-violet-400">{filteredTransactions.length}</p>
                <p className="text-sm text-gray-500">Total transactions</p>
            </div>
            <div className="bg-gray-900/50 border border-gray-800 rounded-xl p-6">
                <h4 className="text-lg font-semibold mb-2">Total Income</h4>
                <p className="text-3xl font-bold text-green-400">
                    {formatCurrency(totalIncome)}
                </p>
                <p className="text-sm text-gray-500">From filtered results</p>
            </div>
            <div className="bg-gray-900/50 border border-gray-800 rounded-xl p-6">
                <h4 className="text-lg font-semibold mb-2">Total Expenses</h4>
                <p className="text-3xl font-bold text-red-400">
                    {formatCurrency(totalExpenses)}
                </p>
                <p className="text-sm text-gray-500">From filtered results</p>
            </div>
        </div>
    );
}
