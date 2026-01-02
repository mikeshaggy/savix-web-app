'use client';
import React, { useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { Wallet, Plus } from 'lucide-react';
import DashboardView from '../views/DashboardView';
import { useWallets } from '../../contexts/WalletContext';
import { useAppContext } from '../../contexts/AppContext';
import { Loading } from '../common/Loading';
import { TRANSACTION_TYPES } from '../../constants';
import { enrichTransactionsWithType } from '../../utils/helpers';

export default function DashboardPage() {
    const { currentWallet, wallets, loading: walletsLoading } = useWallets();
    const { dashboardData, categories, dashboardLoading, dashboardError } = useAppContext();
    const router = useRouter();

    // Calculate summary metrics using data from AppContext
    const summary = useMemo(() => {
        const transactionsList = dashboardData?.transactions || [];
        const categoriesList = categories || [];
        
        // Enrich transactions with type information from categories
        const enrichedTransactions = enrichTransactionsWithType(transactionsList, categoriesList);
        
        const income = enrichedTransactions
            .filter(t => t.type === TRANSACTION_TYPES.INCOME)
            .reduce((sum, t) => sum + parseFloat(t.amount || 0), 0);

        const expenses = enrichedTransactions
            .filter(t => t.type === TRANSACTION_TYPES.EXPENSE)
            .reduce((sum, t) => sum + parseFloat(t.amount || 0), 0);

        const balance = currentWallet?.balance || 0;
        const savingsRate = income > 0 ? ((balance / income) * 100).toFixed(1) : 0;

        return {
            income,
            expenses,
            balance,
            savingsRate
        };
    }, [dashboardData?.transactions, categories, currentWallet]);

    // Memoize enriched transactions for rendering
    const enrichedTransactions = useMemo(
        () => enrichTransactionsWithType(dashboardData?.transactions || [], categories || []),
        [dashboardData?.transactions, categories]
    );

    // Show loading state for wallets
    if (walletsLoading) {
        return <Loading message="Loading wallets..." />;
    }

    // Show loading state for dashboard data
    if (dashboardLoading) {
        return <Loading message="Loading dashboard..." />;
    }

    // Show no wallets state - user hasn't created any wallets yet
    if (!walletsLoading && wallets.length === 0) {
        return (
            <div className="flex items-center justify-center min-h-[400px] p-8">
                <div className="text-center max-w-md">
                    <div className="mb-6">
                        <Wallet className="w-16 h-16 text-violet-400 mx-auto mb-4" />
                        <h2 className="text-2xl font-semibold text-white mb-2">Welcome to Savix!</h2>
                        <p className="text-gray-400 mb-6">
                            You don't have any wallets yet. Create your first wallet to start tracking your expenses and income.
                        </p>
                    </div>
                    <button 
                        onClick={() => router.push('/wallets')}
                        className="inline-flex items-center px-6 py-3 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors font-medium"
                    >
                        <Plus className="w-5 h-5 mr-2" />
                        Create Your First Wallet
                    </button>
                    <p className="text-sm text-gray-500 mt-4">
                        A wallet helps you organize different accounts or budgets
                    </p>
                </div>
            </div>
        );
    }

    // Show no wallet selected state (user has wallets but none selected)
    if (!currentWallet && wallets.length > 0) {
        return (
            <div className="flex items-center justify-center min-h-[400px] p-8">
                <div className="text-center max-w-md">
                    <div className="mb-6">
                        <Wallet className="w-12 h-12 text-violet-400 mx-auto mb-4" />
                        <h2 className="text-xl font-semibold text-white mb-2">No Wallet Selected</h2>
                        <p className="text-gray-400 mb-6">
                            Please select a wallet from the top bar to view your dashboard
                        </p>
                    </div>
                    <button 
                        onClick={() => router.push('/wallets')}
                        className="inline-flex items-center px-6 py-3 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors font-medium"
                    >
                        <Wallet className="w-5 h-5 mr-2" />
                        Manage Wallets
                    </button>
                </div>
            </div>
        );
    }

    // Show error state
    if (dashboardError) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold text-white mb-2">Error Loading Dashboard</h2>
                    <p className="text-gray-400 mb-4">{dashboardError}</p>
                    <button 
                        onClick={() => window.location.reload()} 
                        className="px-4 py-2 bg-violet-600 text-white rounded-lg hover:bg-violet-700"
                    >
                        Retry
                    </button>
                </div>
            </div>
        );
    }    return (
        <DashboardView 
            summary={summary}
            allTransactions={enrichedTransactions}
            categories={categories || []}
            filteredTransactions={enrichedTransactions}
        />
    );
}
