import React, { useState, useEffect } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useWallets } from '@/contexts/WalletContext';
import { Wallet, Plus, Edit3, Trash2, RefreshCw, AlertCircle } from 'lucide-react';
import { formatCurrency } from '@/utils/helpers';
import { Loading } from '@/components/common/Loading';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';

export default function WalletManagementView() {
  const t = useTranslations();
  const { lang } = useLanguage();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { 
    wallets, 
    currentWallet, 
    loading, 
    error, 
    createWallet, 
    updateWallet, 
    updateWalletBalance,
    deleteWallet, 
    setCurrentWallet,
    fetchWallets
  } = useWallets();

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingWallet, setEditingWallet] = useState(null);
  const [balanceEditWallet, setBalanceEditWallet] = useState(null);
  const [deletingWallet, setDeletingWallet] = useState(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  useEffect(() => {
    if (searchParams.get('open') !== 'create') return;
    setShowCreateModal(true);
    router.replace(pathname);
  }, [pathname, router, searchParams]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      await fetchWallets();
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleWalletSelect = (wallet) => {
    setCurrentWallet(wallet);
  };

  const handleCreateWallet = async (walletData) => {
    try {
      await createWallet(walletData);
      setShowCreateModal(false);
    } catch (error) {
      console.error('Failed to create wallet:', error);
    }
  };

  const handleUpdateWallet = async (id, walletData) => {
    try {
      await updateWallet(id, walletData);
      setEditingWallet(null);
    } catch (error) {
      console.error('Failed to update wallet:', error);
    }
  };

  const handleDeleteWallet = async (wallet) => {
    try {
      await deleteWallet(wallet.id);
      setDeletingWallet(null);
    } catch (error) {
      console.error('Failed to delete wallet:', error);
    }
  };

  const handleUpdateBalance = async (id, newBalance) => {
    try {
      await updateWalletBalance(id, newBalance);
      setBalanceEditWallet(null);
    } catch (error) {
      console.error('Failed to update balance:', error);
    }
  };

  const handleViewHistory = (wallet) => {
    setCurrentWallet(wallet);
    router.push('/wallets/balance-history');
  };

  const walletColors = ['teal', 'amber', 'blue', 'rose'];
  const getWalletColor = (wallet, index) => {
    if (currentWallet?.id === wallet.id) return 'purple';
    return walletColors[index % walletColors.length];
  };

  const colorConfig = {
    purple: { bg: 'bg-[rgba(124,58,237,0.2)]', stroke: '#a855f7' },
    teal:   { bg: 'bg-[rgba(20,184,166,0.15)]', stroke: '#14b8a6' },
    amber:  { bg: 'bg-[rgba(245,158,11,0.15)]', stroke: '#f59e0b' },
    blue:   { bg: 'bg-[rgba(59,130,246,0.15)]', stroke: '#3b82f6' },
    rose:   { bg: 'bg-[rgba(244,63,94,0.15)]', stroke: '#f43f5e' },
  };

  if (loading) {
    return <Loading message={t('wallet.loadingWallets')} />;
  }

  if (error) {
    return (
      <div className="flex items-center justify-center p-8">
        <div className="text-center">
          <AlertCircle className="w-16 h-16 text-red-500 mx-auto mb-4" />
          <h2 className="text-xl font-semibold mb-2">{t('errors.failedToLoadWallets')}</h2>
          <p className="text-[#6b6b8a] mb-4">{error}</p>
          <button
            onClick={handleRefresh}
            className="px-4 py-2 bg-gradient-to-br from-[#7c3aed] to-[#a855f7] rounded-[8px] text-white text-[13.5px] font-medium cursor-pointer flex items-center gap-[6px] mx-auto shadow-[0_4px_16px_rgba(124,58,237,0.25)] transition-all hover:opacity-90 hover:-translate-y-[1px]"
          >
            <RefreshCw className="w-4 h-4" />
            {t('common.tryAgain')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-[18px]">
      {/* Page header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="text-xl sm:text-[26px] font-bold tracking-[-0.4px]">
            {t('wallet.wallets')}
          </div>
          <div className="text-[13px] sm:text-[14px] text-white/22 mt-[3px]">
            {t('wallet.switchWalletDesc')}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="w-[34px] h-[34px] rounded-[8px] border border-white/[0.055] bg-transparent flex items-center justify-center cursor-pointer text-[#6b6b8a] transition-all hover:border-white/[0.12] hover:text-[#9898b8] disabled:opacity-50"
            title={t('wallet.refreshWallets')}
          >
            <RefreshCw className={`w-[14px] h-[14px] ${isRefreshing ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={() => setShowCreateModal(true)}
            className="bg-gradient-to-br from-[#7c3aed] to-[#a855f7] border-none rounded-[8px] px-4 py-2 text-white text-[13.5px] font-medium cursor-pointer flex items-center gap-[6px] shadow-[0_4px_16px_rgba(124,58,237,0.25)] transition-all hover:opacity-90 hover:-translate-y-[1px]"
          >
            <Plus className="w-[13px] h-[13px]" strokeWidth={2.5} />
            {t('wallet.addWallet')}
          </button>
        </div>
      </div>

      {/* Active wallet banner */}
      {currentWallet && (
        <div className="relative bg-[#13131f] border border-white/[0.06] rounded-[14px] px-5 md:px-7 py-5 md:py-[22px] flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 overflow-hidden">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-purple-400/70" />
          <div className="absolute top-0 -right-20 w-[200px] h-[200px] bg-[#7c3aed]/10 blur-3xl pointer-events-none" />

          <div className="flex items-center gap-4">
            <div className="w-11 h-11 bg-[#7c3aed] rounded-[12px] flex items-center justify-center shadow-[0_4px_20px_rgba(124,58,237,0.25)]">
              <Wallet className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="font-mono text-[11px] uppercase tracking-[1.5px] text-[#6b6b8a] mb-[3px]">
                {t('wallet.currentWallet')}
              </div>
              <div className="text-[18px] font-semibold text-white">
                {currentWallet.name}
              </div>
            </div>
          </div>
          <div className="text-right flex flex-col items-end gap-2">
            <div className="font-mono text-[11px] uppercase tracking-[1.5px] text-[#6b6b8a] mb-[3px]">
              {t('wallet.balance')}
            </div>
            <div className="font-mono text-[24px] font-bold text-white tracking-[-0.5px]">
              {formatCurrency(currentWallet.balance ?? 0, lang)}
            </div>
            <button
              onClick={() => handleViewHistory(currentWallet)}
              className="text-[12px] text-[#6b6b8a] cursor-pointer border-none bg-transparent transition-colors hover:text-[#9898b8] underline decoration-transparent underline-offset-2 hover:decoration-white/[0.12]"
            >
              {t('wallet.viewHistory')}
            </button>
          </div>
        </div>
      )}

      {/* Section label */}
      <div className="font-mono text-[11px] uppercase tracking-[1.5px] text-[#6b6b8a] mt-1">
        {t('wallet.allWallets')}
      </div>

      {/* Wallets grid */}
      <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-[14px]">
        {wallets.map((wallet, index) => {
          const isActive = currentWallet?.id === wallet.id;
          const color = getWalletColor(wallet, index);
          const cfg = colorConfig[color];

          return (
            <div
              key={wallet.id}
              className={`group relative bg-[#13131f] border rounded-[14px] p-5 cursor-pointer transition-all overflow-hidden
                ${isActive
                  ? 'border-[rgba(124,58,237,0.4)]'
                  : 'border-white/[0.06] hover:border-white/[0.12] hover:bg-[#1a1a2a] hover:-translate-y-[2px] hover:shadow-[0_8px_32px_rgba(0,0,0,0.3)]'
                }`}
              onClick={() => handleWalletSelect(wallet)}
            >
              {/* Active card top gradient */}
              {isActive && (
                <div className="absolute top-0 left-0 right-0 h-[2px] bg-purple-400" />
              )}

              {/* Card top: icon + name + actions */}
              <div className="flex items-center justify-between mb-[18px]">
                <div className="flex items-center gap-3">
                  <div className={`w-[38px] h-[38px] rounded-[10px] flex items-center justify-center ${cfg.bg}`}>
                    <Wallet className="w-[18px] h-[18px]" style={{ color: cfg.stroke }} />
                  </div>
                  <div>
                    <div className="text-[14px] font-semibold text-white mb-[2px]">
                      {wallet.name}
                    </div>
                    <div className="text-[11.5px] text-[#6b6b8a]">
                      {t('wallet.created', { date: new Date(wallet.createdAt).toLocaleDateString() })}
                    </div>
                  </div>
                </div>

                {/* Action buttons — visible on hover */}
                <div className="flex gap-1 md:opacity-0 md:group-hover:opacity-100 transition-opacity duration-150">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setEditingWallet(wallet);
                    }}
                    className="w-7 h-7 rounded-[7px] bg-white/[0.05] border border-white/[0.06] flex items-center justify-center cursor-pointer text-[#6b6b8a] transition-all hover:bg-white/[0.08] hover:text-white"
                    title={t('wallet.editWallet')}
                  >
                    <Edit3 className="w-3 h-3" />
                  </button>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setDeletingWallet(wallet);
                    }}
                    className="w-7 h-7 rounded-[7px] bg-white/[0.05] border border-white/[0.06] flex items-center justify-center cursor-pointer text-[#6b6b8a] transition-all hover:bg-[rgba(244,63,94,0.15)] hover:text-[#f43f5e] hover:border-[rgba(244,63,94,0.3)]"
                    title={t('wallet.deleteWallet')}
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
              </div>

              {/* Divider */}
              <div className="h-px bg-white/[0.06] mb-4" />

              {/* Card bottom: balance + active badge / edit balance */}
              <div className="flex items-end justify-between">
                <div>
                  <div className="font-mono text-[11px] uppercase tracking-[1px] text-[#6b6b8a] mb-[2px]">
                    {t('wallet.balance')}
                  </div>
                  <div className="font-mono text-[20px] font-bold text-white tracking-[-0.3px]">
                    {formatCurrency(wallet.balance ?? 0, lang)}
                  </div>
                </div>
                <div className="flex flex-col items-end gap-[6px]">
                  {isActive && (
                    <div className="flex items-center gap-[5px] bg-[rgba(124,58,237,0.18)] border border-[rgba(124,58,237,0.3)] rounded-full px-[10px] py-1 text-[11px] font-medium text-[#a855f7]">
                      <div className="w-[5px] h-[5px] rounded-full bg-[#a855f7] shadow-[0_0_6px_#a855f7] animate-pulse" />
                      {t('common.active')}
                    </div>
                  )}
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setBalanceEditWallet(wallet);
                    }}
                    className="text-[11.5px] text-[#6b6b8a] cursor-pointer border-none bg-transparent transition-colors hover:text-[#9898b8] underline decoration-transparent underline-offset-2 hover:decoration-white/[0.12]"
                  >
                    {t('wallet.editBalance')}
                  </button>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleViewHistory(wallet);
                    }}
                    className="text-[11.5px] text-[#6b6b8a] cursor-pointer border-none bg-transparent transition-colors hover:text-[#9898b8] underline decoration-transparent underline-offset-2 hover:decoration-white/[0.12]"
                  >
                    {t('wallet.viewHistory')}
                  </button>
                </div>
              </div>
            </div>
          );
        })}

        {/* Add wallet card */}
        <div
          onClick={() => setShowCreateModal(true)}
          className="bg-transparent border border-dashed border-white/10 rounded-[14px] p-5 cursor-pointer transition-all flex items-center justify-center gap-[10px] min-h-[130px] text-[#6b6b8a] text-[13.5px] hover:border-[rgba(124,58,237,0.3)] hover:bg-[rgba(124,58,237,0.05)] hover:text-[#a855f7] group/add"
        >
          <div className="w-8 h-8 rounded-[8px] bg-white/[0.05] border border-white/[0.08] flex items-center justify-center transition-all group-hover/add:bg-[rgba(124,58,237,0.2)] group-hover/add:border-[rgba(124,58,237,0.3)]">
            <Plus className="w-[15px] h-[15px]" strokeWidth={2.5} />
          </div>
          {t('wallet.addNewWallet')}
        </div>

        {/* Empty state (no wallets at all) */}
        {wallets.length === 0 && (
          <div className="col-span-full text-center py-12">
            <Wallet className="w-16 h-16 text-[#6b6b8a] mx-auto mb-4" />
            <h3 className="text-xl font-semibold mb-2">{t('wallet.noWalletsFound')}</h3>
            <p className="text-[#6b6b8a] mb-4">{t('wallet.switchWalletDesc')}</p>
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {(showCreateModal || editingWallet) && (
        <WalletModal
          isOpen={true}
          onClose={() => {
            setShowCreateModal(false);
            setEditingWallet(null);
          }}
          onSave={editingWallet ? 
            (data) => handleUpdateWallet(editingWallet.id, data) : 
            handleCreateWallet
          }
          wallet={editingWallet}
        />
      )}

      {/* Balance Edit Modal */}
      {balanceEditWallet && (
        <BalanceModal
          isOpen={true}
          onClose={() => setBalanceEditWallet(null)}
          onSave={(newBalance) => handleUpdateBalance(balanceEditWallet.id, newBalance)}
          wallet={balanceEditWallet}
        />
      )}

      {/* Delete Confirmation Modal */}
      {deletingWallet && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-[#13131f] border border-white/[0.06] rounded-[14px] p-6 w-full max-w-md">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-12 h-12 bg-[rgba(244,63,94,0.15)] rounded-[10px] flex items-center justify-center">
                <Trash2 className="w-6 h-6 text-[#f43f5e]" />
              </div>
              <div>
                <h3 className="text-lg font-semibold">{t('wallet.deleteWallet')}</h3>
                <p className="text-[#6b6b8a] text-sm">{t('category.cannotBeUndone')}</p>
              </div>
            </div>
            
            <p className="text-[#9898b8] mb-6">
              {t('wallet.deleteConfirmMsg', { name: deletingWallet.name })}
            </p>
            
            <div className="flex gap-3">
              <button
                onClick={() => setDeletingWallet(null)}
                className="flex-1 px-4 py-2 bg-white/[0.05] border border-white/[0.06] hover:border-white/[0.12] text-[#9898b8] rounded-[8px] transition-colors cursor-pointer"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={() => handleDeleteWallet(deletingWallet)}
                className="flex-1 px-4 py-2 bg-[#f43f5e] hover:bg-[#e11d48] text-white rounded-[8px] transition-colors cursor-pointer"
              >
                {t('common.delete')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function WalletModal({ isOpen, onClose, onSave, wallet = null }) {
  const t = useTranslations();
  const [formData, setFormData] = useState({
    name: wallet?.name || '',
    balance: wallet?.balance || 0,
  });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    const newErrors = {};
    if (!formData.name.trim()) {
      newErrors.name = t('wallet.walletNameRequired');
    } else if (formData.name.trim().length > 50) {
      newErrors.name = t('wallet.walletNameTooLong');
    }
    if (formData.balance < 0) {
      newErrors.balance = t('errors.balanceNegative');
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    try {
      setSubmitting(true);
      setErrors({});
      await onSave({
        name: formData.name.trim(),
        balance: parseFloat(formData.balance) || 0,
      });
      onClose();
    } catch (error) {
      setErrors({ submit: error.message });
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-[#13131f] border border-white/[0.06] rounded-[14px] p-6 w-full max-w-md">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-12 h-12 bg-[rgba(124,58,237,0.2)] rounded-[10px] flex items-center justify-center">
            <Wallet className="w-6 h-6 text-[#a855f7]" />
          </div>
          <div>
            <h3 className="text-lg font-semibold">
              {wallet ? t('wallet.editWallet') : t('wallet.createNewWallet')}
            </h3>
            <p className="text-[#6b6b8a] text-sm">
              {wallet ? t('wallet.editWalletDesc') : t('wallet.createWalletDesc')}
            </p>
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label className="block text-sm font-medium text-[#9898b8] mb-2">
              {t('wallet.walletNameLabel')}
            </label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              className="w-full px-3 py-2 bg-[#1a1a2a] border border-white/[0.06] rounded-[8px] focus:outline-none focus:border-[#7c3aed] text-white"
              placeholder={t('wallet.walletNamePlaceholder')}
            />
            {errors.name && <p className="text-[#f43f5e] text-sm mt-1">{errors.name}</p>}
          </div>

          <div className="mb-6">
            <label className="block text-sm font-medium text-[#9898b8] mb-2">
              {t('wallet.initialBalance')}
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={formData.balance}
              onChange={(e) => setFormData({ ...formData, balance: e.target.value })}
              className="w-full px-3 py-2 bg-[#1a1a2a] border border-white/[0.06] rounded-[8px] focus:outline-none focus:border-[#7c3aed] text-white"
              placeholder="0.00"
            />
            {errors.balance && <p className="text-[#f43f5e] text-sm mt-1">{errors.balance}</p>}
          </div>

          {errors.submit && (
            <div className="mb-4 text-[#f43f5e] text-sm">{errors.submit}</div>
          )}

          <div className="flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 bg-white/[0.05] border border-white/[0.06] hover:border-white/[0.12] text-[#9898b8] rounded-[8px] transition-colors cursor-pointer"
            >
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 px-4 py-2 bg-gradient-to-br from-[#7c3aed] to-[#a855f7] disabled:opacity-50 text-white rounded-[8px] transition-all cursor-pointer shadow-[0_4px_16px_rgba(124,58,237,0.25)] hover:opacity-90"
            >
              {submitting ? t('common.saving') : (wallet ? t('common.update') : t('common.create'))}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function BalanceModal({ isOpen, onClose, onSave, wallet }) {
  const t = useTranslations();
  const [balance, setBalance] = useState(wallet?.balance || 0);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    const newBalance = parseFloat(balance);
    if (isNaN(newBalance) || newBalance < 0) {
      setError(t('errors.balanceInvalid'));
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await onSave(newBalance);
      onClose();
    } catch (err) {
      setError(err.message || t('errors.failedToUpdateBalance'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-[#13131f] border border-white/[0.06] rounded-[14px] p-6 w-full max-w-sm">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-12 h-12 bg-[rgba(124,58,237,0.2)] rounded-[10px] flex items-center justify-center">
            <Wallet className="w-6 h-6 text-[#a855f7]" />
          </div>
          <div>
            <h3 className="text-lg font-semibold">{t('wallet.updateBalance')}</h3>
            <p className="text-[#6b6b8a] text-sm">{wallet?.name}</p>
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label className="block text-sm font-medium text-[#9898b8] mb-2">
              {t('wallet.newBalance')}
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={balance}
              onChange={(e) => {
                setBalance(e.target.value);
                setError(null);
              }}
              className="w-full px-3 py-2 bg-[#1a1a2a] border border-white/[0.06] rounded-[8px] focus:outline-none focus:border-[#7c3aed] text-white"
              autoFocus
            />
            {error && <p className="text-[#f43f5e] text-sm mt-1">{error}</p>}
          </div>

          <div className="flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 bg-white/[0.05] border border-white/[0.06] hover:border-white/[0.12] text-[#9898b8] rounded-[8px] transition-colors cursor-pointer"
            >
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 px-4 py-2 bg-gradient-to-br from-[#7c3aed] to-[#a855f7] disabled:opacity-50 text-white rounded-[8px] transition-all cursor-pointer shadow-[0_4px_16px_rgba(124,58,237,0.25)] hover:opacity-90"
            >
              {submitting ? t('common.updating') : t('common.update')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
