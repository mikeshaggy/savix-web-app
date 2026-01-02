'use client';
import React, { createContext, useContext, useMemo, useCallback } from 'react';
import { useDashboard, useCategories } from '@/hooks/useApi';
import { useWallets } from './WalletContext';
import { useUser } from './UserContext';

const AppContext = createContext();

export const useAppContext = () => {
    const context = useContext(AppContext);
    if (!context) {
        throw new Error('useAppContext must be used within an AppProvider');
    }
    return context;
};

export const AppProvider = ({ children, globalState, globalActions }) => {
    const { currentWallet } = useWallets();
    const { currentUser } = useUser();
    
    const { 
        data: dashboardData, 
        loading: dashboardLoading, 
        error: dashboardError, 
        refetch: refetchDashboard
    } = useDashboard(currentWallet?.id);

    const { 
        categories, 
        loading: categoriesLoading, 
        error: categoriesError
    } = useCategories(currentUser.id);

    const transactions = useMemo(() => 
        dashboardData?.transactions || [], 
        [dashboardData?.transactions]
    );

    const isLoading = useMemo(() => 
        dashboardLoading || categoriesLoading, 
        [dashboardLoading, categoriesLoading]
    );

    const hasError = useMemo(() => 
        dashboardError || categoriesError, 
        [dashboardError, categoriesError]
    );

    const handleCreateTransaction = useCallback(async (transactionData) => {
        try {
            const { transactionApi } = await import('../lib/api');
            await transactionApi.createTransaction(transactionData);
            refetchDashboard();
        } catch (error) {
            console.error('Failed to create transaction:', error);
            throw error;
        }
    }, [refetchDashboard]);

    const handleRefresh = useCallback(() => {
        refetchDashboard();
    }, [refetchDashboard]);

    const contextValue = useMemo(() => ({
        dashboardData: dashboardData || {},
        allTransactions: transactions,
        transactions,
        categories: categories || [],
        
        isLoading,
        dashboardLoading,
        categoriesLoading,
        
        hasError,
        dashboardError,
        categoriesError,
        
        onCreateTransaction: handleCreateTransaction,
        onRefresh: handleRefresh,
        
        ...globalState,
        
        ...globalActions
    }), [
        dashboardData,
        transactions,
        categories,
        isLoading,
        dashboardLoading,
        categoriesLoading,
        hasError,
        dashboardError,
        categoriesError,
        handleCreateTransaction,
        handleRefresh,
        globalState,
        globalActions
    ]);

    return (
        <AppContext.Provider value={contextValue}>
            {children}
        </AppContext.Provider>
    );
};

export default AppContext;
