import React from 'react';
import IncomeCard from '@/components/cards/IncomeCard';
import ExpenseCard from '@/components/cards/ExpenseCard';
import CurrentBalanceCard from '@/components/cards/CurrentBalanceCard';
import RecentActivityCard from '@/components/cards/RecentActivityCard';
import QuickStatsCards from '@/components/cards/QuickStatsCards';

export default function DashboardView({ 
    summary = {}, 
    allTransactions = [], 
    categories = [], 
    filteredTransactions = [] 
}) {
    return (
        <div className="grid grid-cols-12 gap-6">
            {/* Summary Cards */}
            <div className="col-span-8 grid grid-cols-2 gap-4">
                <IncomeCard income={summary.income || 0} />
                <ExpenseCard expenses={summary.expenses || 0} />
                <CurrentBalanceCard balance={summary.balance || 0} savingsRate={summary.savingsRate || 0} />
            </div>

            {/* Recent Transactions */}
            <RecentActivityCard transactions={allTransactions} />

            {/* Quick Stats */}
            <QuickStatsCards 
                categories={categories}
                filteredTransactions={filteredTransactions}
                allTransactions={allTransactions}
            />
        </div>
    );
}
