'use client';
import React, { useState, useCallback } from 'react';
import { usePathname } from 'next/navigation';
import { AppProvider, useAppContext } from '@/contexts/AppContext';
import ErrorBoundary from '@/components/common/ErrorBoundary';
import { PageLoading } from '@/components/common/Loading';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import TransactionModal from '../modals/TransactionModal';
import TransferModal from '../modals/TransferModal';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { useTranslations } from 'next-intl';

const PUBLIC_ROUTES = ['/login', '/register', '/forgot-password', '/reset-password'];

const isPublicRoute = (pathname) => {
  return PUBLIC_ROUTES.some(route => pathname === route || pathname.startsWith(`${route}/`));
};

export default function AppLayout({ children }) {
    const pathname = usePathname();
    const [showTransactionModal, setShowTransactionModal] = useState(false);
    const [showTransferModal, setShowTransferModal] = useState(false);

    const handleNewTransaction = useCallback(() => {
        setShowTransactionModal(true);
    }, []);

    const handleNewTransfer = useCallback(() => {
        setShowTransferModal(true);
    }, []);

    const handleCloseModal = useCallback(() => {
        setShowTransactionModal(false);
    }, []);

    const handleCloseTransferModal = useCallback(() => {
        setShowTransferModal(false);
    }, []);

    if (isPublicRoute(pathname)) {
        return children;
    }

    const globalState = {
        showTransactionModal,
        showTransferModal
    };

    const globalActions = {
        onNewTransaction: handleNewTransaction,
        onNewTransfer: handleNewTransfer,
        onCloseModal: handleCloseModal,
        onCloseTransferModal: handleCloseTransferModal
    };

    return (
        <ErrorBoundary>
            <AppProvider globalState={globalState} globalActions={globalActions}>
                <AppLayoutContent 
                    pathname={pathname}
                    showTransactionModal={showTransactionModal}
                    showTransferModal={showTransferModal}
                    onNewTransaction={handleNewTransaction}
                    onNewTransfer={handleNewTransfer}
                    onCloseModal={handleCloseModal}
                    onCloseTransferModal={handleCloseTransferModal}
                >
                    {children}
                </AppLayoutContent>
            </AppProvider>
        </ErrorBoundary>
    );
}

function AppLayoutContent({ 
    children, 
    pathname, 
    showTransactionModal,
    showTransferModal,
    onNewTransaction,
    onNewTransfer,
    onCloseModal,
    onCloseTransferModal
}) {
    const t = useTranslations();
    const {
        categories,
        isLoading,
        hasError,
        dashboardError,
        transactionsError,
        categoriesError,
        onCreateTransaction,
        onCreateTransfer,
        onRefresh
    } = useAppContext();

    if (hasError) {
        return (
            <div className="min-h-screen bg-[#06060f] text-white flex items-center justify-center">
                <div className="flex flex-col items-center gap-4 text-center max-w-md">
                    <AlertCircle className="w-12 h-12 text-red-500" />
                    <h2 className="text-xl font-semibold">{t('errors.unableToConnectBackend')}</h2>
                    <p className="text-gray-400">
                        {dashboardError || transactionsError || categoriesError}
                    </p>
                    <button
                        onClick={onRefresh}
                        className="px-4 py-2 bg-violet-500 hover:bg-violet-600 rounded-lg transition-colors flex items-center gap-2"
                    >
                        <RefreshCw className="w-4 h-4" />
                        {t('common.tryAgain')}
                    </button>
                    <p className="text-xs text-gray-500">
                        {t('errors.backendHint')}
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-[#06060f] text-white flex overflow-x-hidden">
            {/* Sidebar */}
            <Sidebar 
                currentPath={pathname}
                onNewTransaction={onNewTransaction}
                onNewTransfer={onNewTransfer}
            />

            {/* Main Content */}
            <main className="flex-1 flex flex-col min-w-0">
                {/* Top Bar */}
                <TopBar 
                    onRefresh={onRefresh}
                />

                {/* Main Content Area */}
                <div className="flex-1 p-7 overflow-y-auto">
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

            {/* Transfer Modal */}
            <TransferModal
                isOpen={showTransferModal}
                onClose={onCloseTransferModal}
                onSave={onCreateTransfer}
            />
        </div>
    );
}

