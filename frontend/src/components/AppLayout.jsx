'use client';
import React, { useState, useCallback } from 'react';
import { usePathname } from 'next/navigation';
import { AppProvider, useAppContext } from '../contexts/AppContext';
import ErrorBoundary from './ErrorBoundary';
import { PageLoading } from './Loading';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import TransactionModal from './TransactionModal';
import BackendStatusIndicator from './BackendStatusIndicator';
import { AlertCircle, RefreshCw } from 'lucide-react';

export default function AppLayout({ children }) {
    const pathname = usePathname();
    const [showTransactionModal, setShowTransactionModal] = useState(false);

    // Handle opening transaction modal
    const handleNewTransaction = useCallback(() => {
        setShowTransactionModal(true);
    }, []);

    // Handle closing transaction modal
    const handleCloseModal = useCallback(() => {
        setShowTransactionModal(false);
    }, []);

    // Global state to pass to context
    const globalState = {
        showTransactionModal
    };

    // Global actions to pass to context
    const globalActions = {
        onNewTransaction: handleNewTransaction,
        onCloseModal: handleCloseModal
    };

    return (
        <ErrorBoundary>
            <AppProvider globalState={globalState} globalActions={globalActions}>
                <AppLayoutContent 
                    pathname={pathname}
                    showTransactionModal={showTransactionModal}
                    onNewTransaction={handleNewTransaction}
                    onCloseModal={handleCloseModal}
                >
                    {children}
                </AppLayoutContent>
            </AppProvider>
        </ErrorBoundary>
    );
}

// Separated component to access context
function AppLayoutContent({ 
    children, 
    pathname, 
    showTransactionModal, 
    onNewTransaction, 
    onCloseModal 
}) {
    const {
        categories,
        isLoading,
        hasError,
        dashboardError,
        transactionsError,
        categoriesError,
        isUsingMockData,
        onCreateTransaction,
        onRefresh
    } = useAppContext();

    // Loading state
    if (isLoading) {
        return <PageLoading message="Loading your financial data..." />;
    }

    // Error state
    if (hasError) {
        return (
            <div className="min-h-screen bg-gray-950 text-white flex items-center justify-center">
                <div className="flex flex-col items-center gap-4 text-center max-w-md">
                    <AlertCircle className="w-12 h-12 text-red-500" />
                    <h2 className="text-xl font-semibold">Unable to Load Data</h2>
                    <p className="text-gray-400">
                        {dashboardError || transactionsError || categoriesError}
                    </p>
                    <button
                        onClick={onRefresh}
                        className="px-4 py-2 bg-violet-500 hover:bg-violet-600 rounded-lg transition-colors flex items-center gap-2"
                    >
                        <RefreshCw className="w-4 h-4" />
                        Try Again
                    </button>
                    <p className="text-xs text-gray-500">
                        Using demo data for now. Make sure your backend is running on port 8000.
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-950 text-white flex">
            {/* Sidebar */}
            <Sidebar 
                currentPath={pathname}
                onNewTransaction={onNewTransaction}
            />

            {/* Main Content */}
            <main className="flex-1 flex flex-col">
                {/* Top Bar */}
                <TopBar 
                    onRefresh={onRefresh}
                />

                {/* Backend Status Indicator */}
                <div className="px-8 py-2">
                    <BackendStatusIndicator 
                        isUsingMockData={isUsingMockData}
                        onRefresh={onRefresh}
                    />
                </div>

                {/* Main Content Area */}
                <div className="flex-1 p-8 overflow-y-auto">
                    {children}
                </div>
            </main>

            {/* Transaction Modal */}
            <TransactionModal
                isOpen={showTransactionModal}
                onClose={onCloseModal}
                onSave={onCreateTransaction}
                categories={categories}
            />
        </div>
    );
}

