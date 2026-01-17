'use client';
import React, { useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { Wallet, Plus, Receipt, AlertTriangle, X } from 'lucide-react';
import TransactionsView from '@/components/views/TransactionsView';
import TransactionModal from '@/components/modals/TransactionModal';
import { useWallets } from '@/contexts/WalletContext';
import { useAppContext } from '@/contexts/AppContext';
import { useTransactionFilters } from '@/hooks/useTransactionFilters';
import { Loading } from '@/components/common/Loading';
import { SORT_OPTIONS, SORT_ORDERS, DATE_PRESETS } from '@/constants';
import { enrichTransactionsWithType } from '@/utils/helpers';

export default function TransactionsPage() {
    const { currentWallet, wallets, loading: walletsLoading } = useWallets();
    const { 
        dashboardData, 
        categories, 
        dashboardLoading, 
        dashboardError,
        onCreateTransaction,
        onUpdateTransaction,
        onDeleteTransaction
    } = useAppContext();
    const router = useRouter();
    const [showTransactionModal, setShowTransactionModal] = useState(false);
    const [editingTransaction, setEditingTransaction] = useState(null);
    const [deleteConfirm, setDeleteConfirm] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);

    const [typeFilter, setTypeFilter] = useState('ALL');
    const [categoryFilter, setCategoryFilter] = useState('ALL');
    const [importanceFilter, setImportanceFilter] = useState('ALL');
    const [dateFilter, setDateFilter] = useState('ALL');
    const [datePreset, setDatePreset] = useState(DATE_PRESETS.ALL_TIME);
    const [customFromDate, setCustomFromDate] = useState('');
    const [customToDate, setCustomToDate] = useState('');
    const [sortBy, setSortBy] = useState(SORT_OPTIONS.DATE);
    const [sortOrder, setSortOrder] = useState(SORT_ORDERS.DESC);

    const enrichedTransactions = useMemo(
        () => enrichTransactionsWithType(dashboardData?.transactions || [], categories || []),
        [dashboardData?.transactions, categories]
    );

    const { filteredTransactions, filterStats } = useTransactionFilters(enrichedTransactions, {
        typeFilter,
        categoryFilter,
        importanceFilter,
        dateFilter,
        datePreset,
        customFromDate,
        customToDate,
        sortBy,
        sortOrder
    });

    const handleNewTransaction = () => {
        setEditingTransaction(null);
        setShowTransactionModal(true);
    };

    const handleEditTransaction = (transaction) => {
        setEditingTransaction(transaction);
        setShowTransactionModal(true);
    };

    const handleSaveTransaction = async (transactionData, transactionId) => {
        try {
            if (transactionId) {
                await onUpdateTransaction(transactionId, transactionData);
            } else {
                await onCreateTransaction(transactionData);
            }
            setShowTransactionModal(false);
            setEditingTransaction(null);
        } catch (error) {
            console.error('Failed to save transaction:', error);
            throw error;
        }
    };

    const handleDeleteClick = (transaction) => {
        setDeleteConfirm(transaction);
    };

    const handleConfirmDelete = async () => {
        if (!deleteConfirm) return;
        
        try {
            setDeleteLoading(true);
            await onDeleteTransaction(deleteConfirm.id);
            setDeleteConfirm(null);
        } catch (error) {
            console.error('Failed to delete transaction:', error);
        } finally {
            setDeleteLoading(false);
        }
    };

    const handleCloseModal = () => {
        setShowTransactionModal(false);
        setEditingTransaction(null);
    };

    if (walletsLoading) {
        return <Loading message="Loading wallets..." />;
    }

    if (dashboardLoading) {
        return <Loading message="Loading transactions..." />;
    }

    if (!walletsLoading && wallets.length === 0) {
        return (
            <div className="flex items-center justify-center min-h-[400px] p-8">
                <div className="text-center max-w-md">
                    <div className="mb-6">
                        <Receipt className="w-16 h-16 text-violet-400 mx-auto mb-4" />
                        <h2 className="text-2xl font-semibold text-white mb-2">No Transactions Yet</h2>
                        <p className="text-gray-400 mb-6">
                            Create your first wallet to start tracking your transactions and manage your finances.
                        </p>
                    </div>
                    <button 
                        onClick={() => router.push('/wallets')}
                        className="inline-flex items-center px-6 py-3 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors font-medium"
                    >
                        <Plus className="w-5 h-5 mr-2" />
                        Create Your First Wallet
                    </button>
                </div>
            </div>
        );
    }

    if (!currentWallet && wallets.length > 0) {
        return (
            <div className="flex items-center justify-center min-h-[400px] p-8">
                <div className="text-center max-w-md">
                    <div className="mb-6">
                        <Wallet className="w-12 h-12 text-violet-400 mx-auto mb-4" />
                        <h2 className="text-xl font-semibold text-white mb-2">No Wallet Selected</h2>
                        <p className="text-gray-400 mb-6">
                            Please select a wallet from the top bar to view your transactions
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

    if (dashboardError) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold text-white mb-2">Error Loading Transactions</h2>
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
    }

    return (
        <div className="space-y-6">
            {/* Filter Statistics */}
            <div className="bg-gray-900 rounded-lg p-4">
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                        <span className="text-sm text-gray-400">
                            Showing {filterStats.filtered} of {filterStats.total} transactions
                        </span>
                        <span className="text-sm text-gray-400">
                            ({filterStats.percentage}% of total)
                        </span>
                    </div>
                    <div className="flex items-center gap-4 text-sm">
                        <span className="text-green-400">Income: {filterStats.income}</span>
                        <span className="text-red-400">Expenses: {filterStats.expense}</span>
                    </div>
                </div>
            </div>

            {/* Transactions View */}
            <TransactionsView 
                filteredTransactions={filteredTransactions}
                allTransactions={enrichedTransactions}
                categories={categories || []}
                typeFilter={typeFilter}
                setTypeFilter={setTypeFilter}
                categoryFilter={categoryFilter}
                setCategoryFilter={setCategoryFilter}
                importanceFilter={importanceFilter}
                setImportanceFilter={setImportanceFilter}
                dateFilter={dateFilter}
                setDateFilter={setDateFilter}
                datePreset={datePreset}
                setDatePreset={setDatePreset}
                customFromDate={customFromDate}
                setCustomFromDate={setCustomFromDate}
                customToDate={customToDate}
                setCustomToDate={setCustomToDate}
                sortBy={sortBy}
                setSortBy={setSortBy}
                sortOrder={sortOrder}
                setSortOrder={setSortOrder}
                filterStats={filterStats}
                onNewTransaction={handleNewTransaction}
                onEditTransaction={handleEditTransaction}
                onDeleteTransaction={handleDeleteClick}
            />

            {/* Transaction Modal */}
            {showTransactionModal && (
                <TransactionModal
                    isOpen={showTransactionModal}
                    onClose={handleCloseModal}
                    onSave={handleSaveTransaction}
                    categories={categories || []}
                    transaction={editingTransaction}
                />
            )}

            {/* Delete Confirmation Modal */}
            {deleteConfirm && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 w-full max-w-md">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="p-2 bg-red-500/20 rounded-lg">
                                <AlertTriangle className="w-6 h-6 text-red-400" />
                            </div>
                            <h3 className="text-lg font-semibold">Delete Transaction</h3>
                        </div>
                        <p className="text-gray-400 mb-6">
                            Are you sure you want to delete "<span className="text-white">{deleteConfirm.title}</span>"? 
                            This action cannot be undone.
                        </p>
                        <div className="flex gap-3">
                            <button
                                onClick={() => setDeleteConfirm(null)}
                                disabled={deleteLoading}
                                className="flex-1 px-4 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-lg transition-colors disabled:opacity-50"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleConfirmDelete}
                                disabled={deleteLoading}
                                className="flex-1 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                            >
                                {deleteLoading ? 'Deleting...' : 'Delete'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
