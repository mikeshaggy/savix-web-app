'use client';
import React, { useMemo } from 'react';
import DashboardView from '../DashboardView';
import { useAppContext } from '../../contexts/AppContext';
import { Loading } from '../Loading';
import { TRANSACTION_TYPES } from '../../constants';

export default function DashboardPage() {
    const { 
        dashboardData, 
        allTransactions, 
        transactions,
        categories,
        isLoading,
        hasError
    } = useAppContext();

    // Calculate summary metrics
    const summary = useMemo(() => {
        const transactionsList = allTransactions || [];
        
        const income = transactionsList
            .filter(t => t.type === TRANSACTION_TYPES.INCOME)
            .reduce((sum, t) => sum + parseFloat(t.amount || 0), 0);

        const expenses = transactionsList
            .filter(t => t.type === TRANSACTION_TYPES.EXPENSE)
            .reduce((sum, t) => sum + parseFloat(t.amount || 0), 0);

        const balance = income - expenses;
        const savingsRate = income > 0 ? ((balance / income) * 100).toFixed(1) : 0;

        return {
            income,
            expenses,
            balance,
            savingsRate
        };
    }, [allTransactions]);

    // Show loading state
    if (isLoading) {
        return <Loading message="Loading dashboard..." />;
    }

    // Show error state
    if (hasError) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold mb-2">Error loading dashboard</h2>
                    <p className="text-gray-400">Please try refreshing the page</p>
                </div>
            </div>
        );
    }

    return (
        <DashboardView 
            summary={summary}
            allTransactions={allTransactions}
            categories={categories}
            filteredTransactions={allTransactions}
        />
    );
}
