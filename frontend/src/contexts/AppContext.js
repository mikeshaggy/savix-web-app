'use client';
import React, { createContext, useContext, useMemo, useCallback } from 'react';
import { useDashboard, useTransactions, useCategories } from '../hooks/useApi';

const AppContext = createContext();

export const useAppContext = () => {
    const context = useContext(AppContext);
    if (!context) {
        throw new Error('useAppContext must be used within an AppProvider');
    }
    return context;
};

export const AppProvider = ({ children, globalState, globalActions }) => {
    // Data hooks
    const { 
        data: dashboardData, 
        loading: dashboardLoading, 
        error: dashboardError, 
        refetch: refetchDashboard, 
        usingMockData: dashboardMockData 
    } = useDashboard();

    const { 
        transactions, 
        loading: transactionsLoading, 
        error: transactionsError, 
        createTransaction, 
        refetch: refetchTransactions, 
        usingMockData: transactionsMockData 
    } = useTransactions();

    const { 
        categories, 
        loading: categoriesLoading, 
        error: categoriesError, 
        usingMockData: categoriesMockData 
    } = useCategories();

    // Computed values
    const allTransactions = useMemo(() => 
        transactions.length > 0 ? transactions : (dashboardData?.transactions || []), 
        [transactions, dashboardData?.transactions]
    );

    const isLoading = useMemo(() => 
        dashboardLoading || transactionsLoading || categoriesLoading, 
        [dashboardLoading, transactionsLoading, categoriesLoading]
    );

    const hasError = useMemo(() => 
        dashboardError || transactionsError || categoriesError, 
        [dashboardError, transactionsError, categoriesError]
    );

    const isUsingMockData = useMemo(() => 
        dashboardMockData || transactionsMockData || categoriesMockData, 
        [dashboardMockData, transactionsMockData, categoriesMockData]
    );

    // Action handlers
    const handleCreateTransaction = useCallback(async (transactionData) => {
        try {
            await createTransaction(transactionData);
            refetchDashboard();
            refetchTransactions();
        } catch (error) {
            console.error('Failed to create transaction:', error);
            throw error;
        }
    }, [createTransaction, refetchDashboard, refetchTransactions]);

    const handleRefresh = useCallback(() => {
        refetchDashboard();
        refetchTransactions();
    }, [refetchDashboard, refetchTransactions]);

    // Memoized context value
    const contextValue = useMemo(() => ({
        // Data
        dashboardData: dashboardData || {},
        allTransactions,
        transactions,
        categories: categories || [],
        
        // Loading states
        isLoading,
        dashboardLoading,
        transactionsLoading,
        categoriesLoading,
        
        // Error states
        hasError,
        dashboardError,
        transactionsError,
        categoriesError,
        
        // Status
        isUsingMockData,
        
        // Actions
        onCreateTransaction: handleCreateTransaction,
        onRefresh: handleRefresh,
        
        // Global state from parent
        ...globalState,
        
        // Global actions from parent
        ...globalActions
    }), [
        dashboardData,
        allTransactions,
        transactions,
        categories,
        isLoading,
        dashboardLoading,
        transactionsLoading,
        categoriesLoading,
        hasError,
        dashboardError,
        transactionsError,
        categoriesError,
        isUsingMockData,
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
