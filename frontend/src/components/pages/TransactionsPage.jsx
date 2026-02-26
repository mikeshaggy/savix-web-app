'use client';
import React, { useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { Wallet, Plus, Receipt, AlertTriangle } from 'lucide-react';
import TransactionsView from '@/components/views/TransactionsView';
import TransactionModal from '@/components/modals/TransactionModal';
import { useWallets } from '@/contexts/WalletContext';
import { useAppContext } from '@/contexts/AppContext';
import { useTransactionFilters } from '@/hooks/useTransactionFilters';
import { Loading } from '@/components/common/Loading';
import { SORT_OPTIONS, SORT_ORDERS, DATE_PRESETS } from '@/constants';
import { enrichTransactionsWithType } from '@/utils/helpers';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n/LanguageProvider';

export default function TransactionsPage() {
    const t = useTranslations();
    const { lang } = useLanguage();
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
        return <Loading message={t('transactionsPage.loadingWallets')} />;
    }

    if (dashboardLoading) {
        return <Loading message={t('transactionsPage.loadingTransactions')} />;
    }

    if (!walletsLoading && wallets.length === 0) {
        return (
            <div className="flex items-center justify-center min-h-[400px] p-8">
                <div className="text-center max-w-md">
                    <div className="mb-6">
                        <Receipt className="w-16 h-16 text-violet-400 mx-auto mb-4" />
                        <h2 className="text-2xl font-semibold text-white mb-2">{t('transactionsPage.noTransactionsYet')}</h2>
                        <p className="text-gray-400 mb-6">
                            {t('transactionsPage.createFirstWallet')}
                        </p>
                    </div>
                    <button 
                        onClick={() => router.push('/wallets')}
                        className="inline-flex items-center px-6 py-3 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors font-medium"
                    >
                        <Plus className="w-5 h-5 mr-2" />
                        {t('transactionsPage.createYourFirstWallet')}
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
                        <h2 className="text-xl font-semibold text-white mb-2">{t('transactionsPage.noWalletSelected')}</h2>
                        <p className="text-gray-400 mb-6">
                            {t('transactionsPage.selectWalletPrompt')}
                        </p>
                    </div>
                    <button 
                        onClick={() => router.push('/wallets')}
                        className="inline-flex items-center px-6 py-3 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors font-medium"
                    >
                        <Wallet className="w-5 h-5 mr-2" />
                        {t('transactionsPage.manageWallets')}
                    </button>
                </div>
            </div>
        );
    }

    if (dashboardError) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold text-white mb-2">{t('transactionsPage.errorLoadingTransactions')}</h2>
                    <p className="text-gray-400 mb-4">{dashboardError}</p>
                    <button 
                        onClick={() => window.location.reload()} 
                        className="px-4 py-2 bg-violet-600 text-white rounded-lg hover:bg-violet-700"
                    >
                        {t('transactionsPage.retry')}
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div>
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
                <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-6">
                    <div 
                        className="bg-[#0e0e1c] border border-white/[0.12] rounded-3xl w-full max-w-md p-7 relative overflow-hidden"
                        style={{
                            boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(248,113,113,0.1)',
                            animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both'
                        }}
                    >
                        {/* Top glow line - red for delete */}
                        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-gradient-to-r from-transparent via-red-400 to-transparent opacity-60" />
                        
                        <div className="flex items-center gap-3 mb-4">
                            <div className="p-2.5 bg-red-400/10 border border-red-400/20 rounded-xl">
                                <AlertTriangle className="w-5 h-5 text-red-400" />
                            </div>
                            <h3 className="text-xl font-bold tracking-[-0.3px]">{t('transactionsPage.deleteTransaction')}</h3>
                        </div>
                        <p className="text-white/50 text-[15px] mb-6 leading-relaxed">
                            {t('transactionsPage.deleteConfirmation', { title: deleteConfirm.title })}
                        </p>
                        <div className="flex gap-3">
                            <button
                                onClick={() => setDeleteConfirm(null)}
                                disabled={deleteLoading}
                                className="flex-1 px-4 py-3 bg-[#131325] border border-white/[0.055] rounded-xl text-base font-semibold text-white/50 transition-all hover:border-white/[0.12] hover:text-white disabled:opacity-50"
                            >
                                {t('transactionsPage.cancel')}
                            </button>
                            <button
                                onClick={handleConfirmDelete}
                                disabled={deleteLoading}
                                className="flex-1 px-4 py-3 rounded-xl text-base font-bold text-white transition-all disabled:opacity-50 flex items-center justify-center gap-2 hover:-translate-y-px"
                                style={{
                                    background: 'linear-gradient(135deg, #ef4444, #f87171)',
                                    boxShadow: '0 4px 20px rgba(248,113,113,0.3)'
                                }}
                            >
                                {deleteLoading ? t('transactionsPage.deleting') : t('transactionsPage.delete')}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
