'use client';
import React, { useState, Suspense, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Wallet, Receipt, AlertTriangle } from 'lucide-react';
import TransactionsView from '@/components/views/TransactionsView';
import TransactionModal from '@/components/modals/TransactionModal';
import { useWallets } from '@/contexts/WalletContext';
import { useAppContext } from '@/contexts/AppContext';
import { useServerTransactions } from '@/hooks/useServerTransactions';
import { Loading } from '@/components/common/Loading';
import EmptyState from '@/components/common/EmptyState';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n/LanguageProvider';

function TransactionsPageInner() {
    const t = useTranslations();
    const { lang } = useLanguage();
    const { currentWallet, wallets, loading: walletsLoading } = useWallets();
    const { 
        categories, 
        onCreateTransaction,
        onUpdateTransaction,
        onDeleteTransaction,
        onNewTransfer,
        walletMutationVersion
    } = useAppContext();
    const router = useRouter();
    const [showTransactionModal, setShowTransactionModal] = useState(false);
    const [editingTransaction, setEditingTransaction] = useState(null);
    const [deleteConfirm, setDeleteConfirm] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);
    const hasHandledMutationRef = useRef(false);

    const serverTx = useServerTransactions(currentWallet?.id);

    useEffect(() => {
        if (!hasHandledMutationRef.current) {
            hasHandledMutationRef.current = true;
            return;
        }
        if (!currentWallet?.id) return;
        serverTx.refetch();
    }, [walletMutationVersion, currentWallet?.id]);

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

    if (!walletsLoading && wallets.length === 0) {
        return (
            <EmptyState
                icon={Receipt}
                title={t('transactionsPage.noTransactionsYet')}
                description={t('transactionsPage.createFirstWallet')}
                action={{ label: t('transactionsPage.createYourFirstWallet'), onClick: () => router.push('/wallets') }}
            />
        );
    }

    if (!currentWallet && wallets.length > 0) {
        return (
            <EmptyState
                icon={Wallet}
                title={t('transactionsPage.noWalletSelected')}
                description={t('transactionsPage.selectWalletPrompt')}
                action={{ label: t('transactionsPage.manageWallets'), onClick: () => router.push('/wallets') }}
            />
        );
    }

    return (
        <div>
            {/* Transactions View */}
            <TransactionsView 
                groups={serverTx.groups}
                totalElements={serverTx.totalElements}
                totalPages={serverTx.totalPages}
                hasNext={serverTx.hasNext}
                hasPrevious={serverTx.hasPrevious}
                loading={serverTx.loading}
                error={serverTx.error}
                categories={categories || []}

                page={serverTx.page}
                size={serverTx.size}
                sort={serverTx.sort}
                types={serverTx.types}
                categoryIds={serverTx.categoryIds}
                importances={serverTx.importances}
                startDate={serverTx.startDate}
                endDate={serverTx.endDate}
                searchInput={serverTx.searchInput}
                activeFilterCount={serverTx.activeFilterCount}

                setPage={serverTx.setPage}
                updateSize={serverTx.updateSize}
                updateSort={serverTx.updateSort}
                updateTypes={serverTx.updateTypes}
                updateCategoryIds={serverTx.updateCategoryIds}
                updateImportances={serverTx.updateImportances}
                updateStartDate={serverTx.updateStartDate}
                updateEndDate={serverTx.updateEndDate}
                setSearchInput={serverTx.setSearchInput}
                clearSearch={serverTx.clearSearch}
                clearAllFilters={serverTx.clearAllFilters}

                onNewTransaction={handleNewTransaction}
                onNewTransfer={onNewTransfer}
                onEditTransaction={handleEditTransaction}
                onDeleteTransaction={handleDeleteClick}
                onRetry={serverTx.refetch}
            />

            {/* Transaction Modal */}
            {showTransactionModal && (
                <TransactionModal
                    isOpen={showTransactionModal}
                    onClose={handleCloseModal}
                    onSave={handleSaveTransaction}
                    categories={categories || []}
                    transaction={editingTransaction}
                    onLinkChange={serverTx.refetch}
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
                        {/* Top accent line - red for delete */}
                        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-red-400/55" />
                        
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
                                className="flex-1 px-4 py-3 bg-[#131325] border border-white/[0.055] rounded-xl text-base font-semibold text-white/50 transition-all hover:border-white/[0.12] hover:text-white disabled:opacity-50 cursor-pointer"
                            >
                                {t('transactionsPage.cancel')}
                            </button>
                            <button
                                onClick={handleConfirmDelete}
                                disabled={deleteLoading}
                                className="flex-1 px-4 py-3 rounded-xl text-base font-bold text-white bg-[linear-gradient(135deg,#ef4444,#f87171)] shadow-[0_4px_20px_rgba(248,113,113,0.3)] transition-all disabled:opacity-50 flex items-center justify-center gap-2 hover:-translate-y-px cursor-pointer"
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

export default function TransactionsPage() {
    return (
        <Suspense fallback={<Loading message="Loading..." />}>
            <TransactionsPageInner />
        </Suspense>
    );
}
