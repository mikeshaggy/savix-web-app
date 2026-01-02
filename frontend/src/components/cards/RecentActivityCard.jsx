import React from 'react';
import { ArrowUpRight, ArrowDownRight, DollarSign, MoreVertical } from 'lucide-react';
import { formatCurrency } from '@/utils/helpers';

const importanceColors = {
    'ESSENTIAL': 'bg-red-500/20 text-red-400 border-red-500/30',
    'HAVE_TO_HAVE': 'bg-orange-500/20 text-orange-400 border-orange-500/30',
    'NICE_TO_HAVE': 'bg-blue-500/20 text-blue-400 border-blue-500/30',
    'SHOULDNT_HAVE': 'bg-gray-500/20 text-gray-400 border-gray-500/30'
};

export default function RecentActivityCard({ transactions = [] }) {
    console.log('DEBUG - RecentActivityCard transactions:', transactions);
    
    return (
        <div className="col-span-4">
            <div className="bg-gray-900/50 border border-gray-800 rounded-xl p-6">
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-lg font-semibold">Recent Activity</h3>
                    <button className="text-gray-400 hover:text-white">
                        <MoreVertical className="w-5 h-5" />
                    </button>
                </div>

                <div className="space-y-3 h-80 overflow-y-auto scrollbar-thin scrollbar-thumb-gray-600 scrollbar-track-gray-800">
                    {transactions.slice(0, 8).map((transaction) => (
                        <div key={transaction.id} className="flex items-center justify-between p-3 bg-gray-800/30 rounded-lg hover:bg-gray-800/50 transition-colors">
                            <div className="flex items-center gap-3">
                                <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                                    transaction.type === 'INCOME' ? 'bg-green-500/20' : 'bg-red-500/20'
                                }`}>
                                    {transaction.type === 'INCOME' ?
                                        <ArrowUpRight className="w-5 h-5 text-green-400" /> :
                                        <ArrowDownRight className="w-5 h-5 text-red-400" />
                                    }
                                </div>
                                <div>
                                    <p className="font-medium text-sm">{transaction.title || transaction.categoryName}</p>
                                    <p className="text-xs text-gray-500">{new Date(transaction.transactionDate || transaction.date).toLocaleDateString()}</p>
                                </div>
                            </div>
                            <div className="text-right">
                                <p className={`font-semibold text-sm ${
                                    transaction.type === 'INCOME' ? 'text-green-400' : 'text-red-400'
                                }`}>
                                    {transaction.type === 'INCOME' ? '+' : '-'}{formatCurrency(parseFloat(transaction.amount || 0))}
                                </p>
                                <span className={`text-xs px-2 py-0.5 rounded-full border ${importanceColors[transaction.importance]}`}>
                                    {transaction.importance?.split('_')[0] || 'N/A'}
                                </span>
                            </div>
                        </div>
                    ))}
                    
                    {transactions.length === 0 && (
                        <div className="text-center py-8">
                            <DollarSign className="w-8 h-8 text-gray-600 mx-auto mb-2" />
                            <p className="text-gray-400 text-sm">No recent transactions</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
