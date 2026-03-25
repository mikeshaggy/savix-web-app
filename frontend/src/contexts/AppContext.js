'use client';
import React, { createContext, useContext, useMemo, useCallback, useState } from 'react';
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
    const { currentWallet, fetchWallets } = useWallets();
    const { user, isAuthenticated, isLoading: userLoading } = useUser();
    const [walletMutationVersion, setWalletMutationVersion] = useState(0);
    
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

    const notifyWalletMutation = useCallback(() => {
        setWalletMutationVersion(prev => prev + 1);
    }, []);

    const handleCreateTransaction = useCallback(async (transactionData) => {
        try {
            const { transactionApi } = await import('../lib/api');
            await transactionApi.createTransaction(transactionData);
            await Promise.all([refetchDashboard(true), fetchWallets()]);
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to create transaction:', error);
            throw error;
        }
    }, [refetchDashboard, fetchWallets, notifyWalletMutation]);

    const handleUpdateTransaction = useCallback(async (id, transactionData) => {
        try {
            const { transactionApi } = await import('../lib/api');
            await transactionApi.updateTransaction(id, transactionData);
            await Promise.all([refetchDashboard(true), fetchWallets()]);
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to update transaction:', error);
            throw error;
        }
    }, [refetchDashboard, fetchWallets, notifyWalletMutation]);

    const handleDeleteTransaction = useCallback(async (id) => {
        try {
            const { transactionApi } = await import('../lib/api');
            await transactionApi.deleteTransaction(id);
            await Promise.all([refetchDashboard(true), fetchWallets()]);
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to delete transaction:', error);
            throw error;
        }
    }, [refetchDashboard, fetchWallets, notifyWalletMutation]);

    const handleCreateTransfer = useCallback(async (transferData) => {
        try {
            const { transferApi } = await import('../lib/api');
            await transferApi.createTransfer(transferData);
            await Promise.all([refetchDashboard(true), fetchWallets()]);
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to create transfer:', error);
            throw error;
        }
    }, [refetchDashboard, fetchWallets, notifyWalletMutation]);

    const handleUpdateTransfer = useCallback(async (id, transferData) => {
        try {
            const { transferApi } = await import('../lib/api');
            await transferApi.updateTransfer(id, transferData);
            await Promise.all([refetchDashboard(true), fetchWallets()]);
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to update transfer:', error);
            throw error;
        }
    }, [refetchDashboard, fetchWallets, notifyWalletMutation]);

    const handleDeleteTransfer = useCallback(async (id) => {
        try {
            const { transferApi } = await import('../lib/api');
            await transferApi.deleteTransfer(id);
            await Promise.all([refetchDashboard(true), fetchWallets()]);
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to delete transfer:', error);
            throw error;
        }
    }, [refetchDashboard, fetchWallets, notifyWalletMutation]);

    const handleRefresh = useCallback(async () => {
        await Promise.all([refetchDashboard(true), fetchWallets()]);
    }, [refetchDashboard, fetchWallets]);

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
        walletMutationVersion,
        
        onCreateTransaction: handleCreateTransaction,
        onUpdateTransaction: handleUpdateTransaction,
        onDeleteTransaction: handleDeleteTransaction,
        onCreateTransfer: handleCreateTransfer,
        onUpdateTransfer: handleUpdateTransfer,
        onDeleteTransfer: handleDeleteTransfer,
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
        walletMutationVersion,
        handleCreateTransaction,
        handleUpdateTransaction,
        handleDeleteTransaction,
        handleCreateTransfer,
        handleUpdateTransfer,
        handleDeleteTransfer,
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
