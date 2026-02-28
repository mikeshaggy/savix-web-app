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
    const { user, isAuthenticated, isLoading: userLoading } = useUser();
    
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
    } = useCategories(user?.id);

    const transactions = useMemo(() => 
        dashboardData?.transactions || [], 
        [dashboardData?.transactions]
    );

    const isLoading = useMemo(() => 
        userLoading || dashboardLoading || categoriesLoading, 
        [userLoading, dashboardLoading, categoriesLoading]
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

    const handleUpdateTransaction = useCallback(async (id, transactionData) => {
        try {
            const { transactionApi } = await import('../lib/api');
            await transactionApi.updateTransaction(id, transactionData);
            refetchDashboard();
        } catch (error) {
            console.error('Failed to update transaction:', error);
            throw error;
        }
    }, [refetchDashboard]);

    const handleDeleteTransaction = useCallback(async (id) => {
        try {
            const { transactionApi } = await import('../lib/api');
            await transactionApi.deleteTransaction(id);
            refetchDashboard();
        } catch (error) {
            console.error('Failed to delete transaction:', error);
            throw error;
        }
    }, [refetchDashboard]);

    const handleCreateTransfer = useCallback(async (transferData) => {
        try {
            const { transferApi } = await import('../lib/api');
            await transferApi.createTransfer(transferData);
            refetchDashboard();
        } catch (error) {
            console.error('Failed to create transfer:', error);
            throw error;
        }
    }, [refetchDashboard]);

    const handleRefresh = useCallback(() => {
        refetchDashboard();
    }, [refetchDashboard]);

    const contextValue = useMemo(() => ({
        user,
        isAuthenticated,
        userLoading,
        
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
        onUpdateTransaction: handleUpdateTransaction,
        onDeleteTransaction: handleDeleteTransaction,
        onCreateTransfer: handleCreateTransfer,
        onRefresh: handleRefresh,
        
        ...globalState,
        
        ...globalActions
    }), [
        user,
        isAuthenticated,
        userLoading,
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
        handleUpdateTransaction,
        handleDeleteTransaction,
        handleCreateTransfer,
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
