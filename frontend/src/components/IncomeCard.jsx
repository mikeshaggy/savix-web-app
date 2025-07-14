import React from 'react';
import { ArrowUpRight } from 'lucide-react';
import { formatCurrency } from '../utils/helpers';

export default function IncomeCard({ income }) {
    return (
        <div className="bg-gradient-to-br from-green-500/10 to-emerald-500/10 border border-green-500/20 rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
                <h3 className="text-gray-400 text-sm font-medium">Total Income</h3>
                <div className="w-10 h-10 bg-green-500/20 rounded-lg flex items-center justify-center">
                    <ArrowUpRight className="w-5 h-5 text-green-400" />
                </div>
            </div>
            <p className="text-3xl font-bold text-green-400">{formatCurrency(income)}</p>
            <p className="text-xs text-gray-500 mt-2">+12% from last month</p>
        </div>
    );
}
