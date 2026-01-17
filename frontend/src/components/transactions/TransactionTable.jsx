import React from 'react';
import { ArrowUpRight, ArrowDownRight, DollarSign, Pencil, Trash2 } from 'lucide-react';
import { formatCurrency } from '@/utils/helpers';

const importanceColors = {
    'ESSENTIAL': 'bg-green-700/20 text-green-500 border-green-500/30',
    'HAVE_TO_HAVE': 'bg-yellow-700/20 text-yellow-500 border-yellow-500/30',
    'NICE_TO_HAVE': 'bg-orange-700/20 text-orange-500 border-orange-500/30',
    'SHOULDNT_HAVE': 'bg-gray-500/20 text-gray-400 border-gray-500/30'
};

export default function TransactionTable({ 
    filteredTransactions = [], 
    categories = [],
    sortBy, 
    setSortBy, 
    sortOrder, 
    setSortOrder,
    typeFilter,
    categoryFilter,
    importanceFilter,
    dateFilter,
    onEdit,
    onDelete
}) {
    const handleSort = (field) => {
        setSortBy(field);
        setSortOrder(sortBy === field && sortOrder === 'desc' ? 'asc' : 'desc');
    };

    const getCategoryName = (categoryId) => {
        const category = categories.find(cat => cat.id === categoryId);
        return category ? category.name : 'Unknown Category';
    };

    const hasActiveFilters = typeFilter !== 'ALL' || categoryFilter !== 'ALL' || importanceFilter !== 'ALL' || dateFilter !== 'ALL';

    return (
        <div className="bg-gray-900/50 border border-gray-800 rounded-xl overflow-hidden">
            <div className="overflow-x-auto">
                <table className="w-full">
                    <thead className="bg-gray-800/50 border-b border-gray-700">
                        <tr>
                            <th className="text-left py-4 px-6 font-medium text-gray-300">
                                <button 
                                    onClick={() => handleSort('date')}
                                    className="flex items-center gap-2 hover:text-white transition-colors"
                                >
                                    Date
                                    {sortBy === 'date' && (
                                        <span className="text-violet-400">
                                            {sortOrder === 'desc' ? '↓' : '↑'}
                                        </span>
                                    )}
                                </button>
                            </th>
                            <th className="text-left py-4 px-6 font-medium text-gray-300">
                                <button 
                                    onClick={() => handleSort('title')}
                                    className="flex items-center gap-2 hover:text-white transition-colors"
                                >
                                    Title
                                    {sortBy === 'title' && (
                                        <span className="text-violet-400">
                                            {sortOrder === 'desc' ? '↓' : '↑'}
                                        </span>
                                    )}
                                </button>
                            </th>
                            <th className="text-left py-4 px-6 font-medium text-gray-300">
                                <button 
                                    onClick={() => handleSort('category')}
                                    className="flex items-center gap-2 hover:text-white transition-colors"
                                >
                                    Category
                                    {sortBy === 'category' && (
                                        <span className="text-violet-400">
                                            {sortOrder === 'desc' ? '↓' : '↑'}
                                        </span>
                                    )}
                                </button>
                            </th>
                            <th className="text-left py-4 px-6 font-medium text-gray-300">Type</th>
                            <th className="text-left py-4 px-6 font-medium text-gray-300">
                                <button 
                                    onClick={() => handleSort('amount')}
                                    className="flex items-center gap-2 hover:text-white transition-colors"
                                >
                                    Amount
                                    {sortBy === 'amount' && (
                                        <span className="text-violet-400">
                                            {sortOrder === 'desc' ? '↓' : '↑'}
                                        </span>
                                    )}
                                </button>
                            </th>
                            <th className="text-left py-4 px-6 font-medium text-gray-300">Importance</th>
                            <th className="text-left py-4 px-6 font-medium text-gray-300">Cycle</th>
                            <th className="text-left py-4 px-6 font-medium text-gray-300">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-700">
                        {filteredTransactions.map((transaction) => (
                            <tr key={transaction.id} className="hover:bg-gray-800/30 transition-colors group">
                                <td className="py-4 px-6 text-sm text-gray-300">
                                    <div className="flex flex-col">
                                        <span className="font-medium">
                                            {new Date(transaction.transactionDate || transaction.date).toLocaleDateString('en-US', {
                                                month: 'short',
                                                day: 'numeric',
                                                year: 'numeric'
                                            })}
                                        </span>
                                        <span className="text-xs text-gray-500">
                                            {new Date(transaction.transactionDate || transaction.date).toLocaleDateString('en-US', {
                                                weekday: 'short'
                                            })}
                                        </span>
                                    </div>
                                </td>
                                <td className="py-4 px-6">
                                    <div className="flex items-center gap-3">
                                        <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                                            transaction.type === 'INCOME' ? 'bg-green-500/20' : 'bg-red-500/20'
                                        }`}>
                                            {transaction.type === 'INCOME' ?
                                                <ArrowUpRight className="w-4 h-4 text-green-400" /> :
                                                <ArrowDownRight className="w-4 h-4 text-red-400" />
                                            }
                                        </div>
                                        <div>
                                            <p className="font-medium text-white">{transaction.title}</p>
                                            {transaction.notes && (
                                                <p className="text-xs text-gray-500 max-w-xs truncate">{transaction.notes}</p>
                                            )}
                                        </div>
                                    </div>
                                </td>
                                <td className="py-4 px-6 text-sm text-gray-300">
                                    <span className="px-2 py-1 bg-gray-700 rounded-md text-xs font-medium">
                                        {getCategoryName(transaction.categoryId)}
                                    </span>
                                </td>
                                <td className="py-4 px-6 text-sm">
                                    <span className={`px-2 py-1 rounded-md text-xs font-medium border ${
                                        transaction.type === 'INCOME' 
                                            ? 'bg-green-500/20 text-green-400 border-green-500/30' 
                                            : 'bg-red-500/20 text-red-400 border-red-500/30'
                                    }`}>
                                        {transaction.type}
                                    </span>
                                </td>
                                <td className="py-4 px-6 text-sm font-semibold">
                                    <span className={transaction.type === 'INCOME' ? 'text-green-400' : 'text-red-400'}>
                                        {transaction.type === 'INCOME' ? '+' : '-'}{formatCurrency(parseFloat(transaction.amount || 0))}
                                    </span>
                                </td>
                                <td className="py-4 px-6 text-sm">
                                    <span className={`text-xs px-2 py-0.5 rounded-full border ${importanceColors[transaction.importance]}`}>
                                        {transaction.importance?.split('_')[0] || 'N/A'}
                                    </span>
                                </td>
                                <td className="py-4 px-6 text-sm text-gray-300">
                                    <span className="px-2 py-1 bg-gray-700 rounded-md text-xs">
                                        {transaction.cycle?.replace('_', ' ')}
                                    </span>
                                </td>
                                <td className="py-4 px-6 text-sm">
                                    <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button 
                                            onClick={() => onEdit?.(transaction)}
                                            className="p-1 hover:bg-gray-700 rounded transition-colors" 
                                            title="Edit"
                                        >
                                            <Pencil className="w-4 h-4 text-gray-400" />
                                        </button>
                                        <button 
                                            onClick={() => onDelete?.(transaction)}
                                            className="p-1 hover:bg-red-600 rounded transition-colors" 
                                            title="Delete"
                                        >
                                            <Trash2 className="w-4 h-4 text-gray-400 hover:text-red-400" />
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                
                {filteredTransactions.length === 0 && (
                    <div className="text-center py-12">
                        <DollarSign className="w-12 h-12 text-gray-600 mx-auto mb-4" />
                        <p className="text-gray-400 text-lg font-medium">No transactions found</p>
                        <p className="text-gray-500 text-sm mt-2">
                            {hasActiveFilters
                                ? 'Try adjusting your filters or search terms'
                                : 'Create your first transaction to get started'
                            }
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
}
