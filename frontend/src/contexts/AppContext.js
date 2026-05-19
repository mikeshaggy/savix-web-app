'use client';
import React, { createContext, useContext, useMemo, useCallback, useState } from 'react';
import { useCategories } from '@/hooks/useApi';
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
    const { fetchWallets } = useWallets();
    const { user, isAuthenticated, isLoading: userLoading } = useUser();
    const [walletMutationVersion, setWalletMutationVersion] = useState(0);

    const { 
        categories, 
        loading: categoriesLoading, 
        error: categoriesError,
        refetch: refetchCategories
    } = useCategories(user?.id);

    const isLoading = useMemo(() => 
        userLoading || categoriesLoading, 
        [userLoading, categoriesLoading]
    );

    const hasError = useMemo(() => 
        categoriesError, 
        [categoriesError]
    );

    const notifyWalletMutation = useCallback(() => {
        setWalletMutationVersion(prev => prev + 1);
    }, []);

    const handleCreateTransaction = useCallback(async (transactionData) => {
        try {
            const { transactionApi } = await import('../lib/api');
            await transactionApi.createTransaction(transactionData);
            await fetchWallets();
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to create transaction:', error);
            throw error;
        }
    }, [fetchWallets, notifyWalletMutation]);

    const handleUpdateTransaction = useCallback(async (id, transactionData) => {
        try {
            const { transactionApi } = await import('../lib/api');
            await transactionApi.updateTransaction(id, transactionData);
            await fetchWallets();
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to update transaction:', error);
            throw error;
        }
    }, [fetchWallets, notifyWalletMutation]);

    const handleDeleteTransaction = useCallback(async (id) => {
        try {
            const { transactionApi } = await import('../lib/api');
            await transactionApi.deleteTransaction(id);
            await fetchWallets();
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to delete transaction:', error);
            throw error;
        }
    }, [fetchWallets, notifyWalletMutation]);

    const handleCreateTransfer = useCallback(async (transferData) => {
        try {
            const { transferApi } = await import('../lib/api');
            await transferApi.createTransfer(transferData);
            await fetchWallets();
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to create transfer:', error);
            throw error;
        }
    }, [fetchWallets, notifyWalletMutation]);

    const handleUpdateTransfer = useCallback(async (id, transferData) => {
        try {
            const { transferApi } = await import('../lib/api');
            await transferApi.updateTransfer(id, transferData);
            await fetchWallets();
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to update transfer:', error);
            throw error;
        }
    }, [fetchWallets, notifyWalletMutation]);

    const handleDeleteTransfer = useCallback(async (id) => {
        try {
            const { transferApi } = await import('../lib/api');
            await transferApi.deleteTransfer(id);
            await fetchWallets();
            notifyWalletMutation();
        } catch (error) {
            console.error('Failed to delete transfer:', error);
            throw error;
        }
    }, [fetchWallets, notifyWalletMutation]);

    const handleRefresh = useCallback(async () => {
        await Promise.all([fetchWallets(), refetchCategories()]);
        notifyWalletMutation();
    }, [fetchWallets, refetchCategories, notifyWalletMutation]);

    const contextValue = useMemo(() => ({
        user,
        isAuthenticated,
        userLoading,
        
        categories: categories || [],
        
        isLoading,
        categoriesLoading,
        
        hasError,
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
        categories,
        isLoading,
        categoriesLoading,
        hasError,
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
