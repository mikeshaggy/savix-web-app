import React, { useState } from 'react';
import { useWallets } from '@/contexts/WalletContext';
import { Wallet, Plus, Edit3, Trash2, DollarSign } from 'lucide-react';

export default function WalletManagementView() {
  const { 
    wallets, 
    currentWallet, 
    loading, 
    error, 
    createWallet, 
    updateWallet, 
    deleteWallet, 
    setCurrentWallet
  } = useWallets();

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingWallet, setEditingWallet] = useState(null);

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

  const handleDeleteWallet = async (id) => {
    if (window.confirm('Are you sure you want to delete this wallet? All associated categories and transactions will be deleted.')) {
      try {
        await deleteWallet(id);
      } catch (error) {
        console.error('Failed to delete wallet:', error);
      }
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-violet-500"></div>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto p-6">
      {/* Header */}
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Switch Wallet</h1>
          <p className="text-gray-400">Select a wallet to make it active, or manage your wallets</p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="bg-violet-600 hover:bg-violet-700 text-white px-4 py-2 rounded-lg flex items-center gap-2 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Add Wallet
        </button>
      </div>

      {error && (
        <div className="bg-red-500/10 border border-red-500 text-red-500 px-4 py-2 rounded-lg mb-6">
          {error}
        </div>
      )}

      {/* Current Wallet Display */}
      {currentWallet && (
        <div className="bg-gradient-to-r from-violet-600 to-purple-600 rounded-xl p-6 mb-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-violet-100 text-sm">Current Wallet</p>
              <h2 className="text-white text-2xl font-bold">{currentWallet.name}</h2>
            </div>
            <div className="text-right">
              <p className="text-violet-100 text-sm">Balance</p>
              <p className="text-white text-2xl font-bold">
                {currentWallet.balance?.toLocaleString() || '0.00'} PLN
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Wallet Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {wallets.map((wallet) => (
          <div
            key={wallet.id}
            className={`bg-gray-800 rounded-lg p-6 border transition-all cursor-pointer ${
              currentWallet?.id === wallet.id
                ? 'border-violet-500 bg-violet-500/10'
                : 'border-gray-700 hover:border-gray-600'
            }`}
            onClick={() => handleWalletSelect(wallet)}
          >
            <div className="flex items-start justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className={`p-2 rounded-lg ${
                  currentWallet?.id === wallet.id ? 'bg-violet-500' : 'bg-gray-700'
                }`}>
                  <Wallet className="w-5 h-5 text-white" />
                </div>
                <div>
                  <h3 className="text-white font-semibold">{wallet.name}</h3>
                  <p className="text-gray-400 text-sm">
                    Created {new Date(wallet.createdAt).toLocaleDateString()}
                  </p>
                </div>
              </div>
              
              <div className="flex gap-1">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setEditingWallet(wallet);
                  }}
                  className="p-1 hover:bg-gray-700 rounded text-gray-400 hover:text-white transition-colors"
                >
                  <Edit3 className="w-4 h-4" />
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDeleteWallet(wallet.id);
                  }}
                  className="p-1 hover:bg-gray-700 rounded text-gray-400 hover:text-red-500 transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <DollarSign className="w-4 h-4 text-green-500" />
              <span className="text-white font-bold">
                {wallet.balance?.toLocaleString() || '0.00'} PLN
              </span>
            </div>
            
            {currentWallet?.id === wallet.id && (
              <div className="mt-2">
                <span className="inline-flex items-center px-2 py-1 rounded-full text-xs bg-violet-500/20 text-violet-400">
                  Active
                </span>
              </div>
            )}
          </div>
        ))}

        {wallets.length === 0 && (
          <div className="col-span-full text-center py-12">
            <Wallet className="w-12 h-12 text-gray-600 mx-auto mb-4" />
            <p className="text-gray-400">No wallets found. Create your first wallet to get started!</p>
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
    </div>
  );
}

function WalletModal({ isOpen, onClose, onSave, wallet = null }) {
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
      newErrors.name = 'Wallet name is required';
    }
    if (formData.balance < 0) {
      newErrors.balance = 'Balance cannot be negative';
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
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
      <div className="bg-gray-800 rounded-lg p-6 w-full max-w-md">
        <h2 className="text-xl font-bold text-white mb-4">
          {wallet ? 'Edit Wallet' : 'Create New Wallet'}
        </h2>

        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Wallet Name *
            </label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg focus:outline-none focus:border-violet-500"
              placeholder="My Wallet"
            />
            {errors.name && <p className="text-red-400 text-sm mt-1">{errors.name}</p>}
          </div>

          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Initial Balance
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={formData.balance}
              onChange={(e) => setFormData({ ...formData, balance: e.target.value })}
              className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg focus:outline-none focus:border-violet-500"
              placeholder="0.00"
            />
            {errors.balance && <p className="text-red-400 text-sm mt-1">{errors.balance}</p>}
          </div>

          {errors.submit && (
            <div className="mb-4 text-red-400 text-sm">{errors.submit}</div>
          )}

          <div className="flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 border border-gray-600 text-gray-300 rounded-lg hover:bg-gray-700 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 px-4 py-2 bg-violet-600 hover:bg-violet-700 disabled:opacity-50 text-white rounded-lg transition-colors"
            >
              {submitting ? 'Saving...' : (wallet ? 'Update' : 'Create')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
