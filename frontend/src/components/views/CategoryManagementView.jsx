import React, { useState } from 'react';
import { Plus, Edit, Trash2, Tag, AlertCircle, RefreshCw } from 'lucide-react';
import { useCategories } from '@/hooks/useApi';
import { useWallets } from '@/contexts/WalletContext';
import { useUser } from '@/contexts/UserContext';
import CategoryModal from '@/components/modals/CategoryModal';
import { Loading } from '@/components/common/Loading';
import { useTranslations } from 'next-intl';

export default function CategoryManagementView() {
  const t = useTranslations();
  const { currentWallet } = useWallets();
  const { user } = useUser();
  const { categories, loading, error, createCategory, updateCategory, deleteCategory, refetch } = useCategories();
  const [showModal, setShowModal] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [deletingCategory, setDeletingCategory] = useState(null);
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [isRefreshing, setIsRefreshing] = useState(false);

  const filteredCategories = categories.filter(category => {
    if (typeFilter === 'ALL') return true;
    return category.type === typeFilter;
  });

  const incomeCount = categories.filter(c => c.type === 'INCOME').length;
  const expenseCount = categories.filter(c => c.type === 'EXPENSE').length;

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      await refetch();
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleCreateCategory = async (categoryData) => {
    try {
      await createCategory(categoryData);
      setShowModal(false);
    } catch (error) {
      console.error('Failed to create category:', error);
      throw error;
    }
  };

  const handleUpdateCategory = async (categoryData) => {
    try {
      await updateCategory(editingCategory.id, categoryData);
      setEditingCategory(null);
      setShowModal(false);
    } catch (error) {
      console.error('Failed to update category:', error);
      throw error;
    }
  };

  const handleDeleteCategory = async (category) => {
    try {
      await deleteCategory(category.id);
      setDeletingCategory(null);
    } catch (error) {
      console.error('Failed to delete category:', error);
    }
  };

  const openEditModal = (category) => {
    setEditingCategory(category);
    setShowModal(true);
  };

  const openCreateModal = () => {
    setEditingCategory(null);
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingCategory(null);
  };

  if (loading) {
    return <Loading message={t('category.loadingCategories')} />;
  }

  if (!currentWallet) {
    return (
      <div className="flex items-center justify-center min-h-[400px] p-8">
        <div className="text-center max-w-md">
          <div className="mb-6">
            <Tag className="w-16 h-16 text-[#a855f7] mx-auto mb-4" />
            <h2 className="text-2xl font-semibold text-white mb-2">{t('category.noWalletSelected')}</h2>
            <p className="text-[#6b6b8a] mb-6">
              {t('category.selectWalletManage')}
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center p-8">
        <div className="text-center">
          <AlertCircle className="w-16 h-16 text-[#f43f5e] mx-auto mb-4" />
          <h2 className="text-xl font-semibold mb-2">{t('errors.failedToLoadCategories')}</h2>
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
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-xl sm:text-[22px] font-semibold tracking-[-0.3px] mb-1">
            {t('category.management')}
          </div>
          <div className="text-[13px] text-[#6b6b8a]">
            {currentWallet
              ? t('category.managingFor', { wallet: currentWallet.name, count: categories.length })
              : t('category.noWalletCategoryMsg')}
          </div>
        </div>
        {currentWallet && (
          <div className="flex items-center gap-2">
            <button
              onClick={handleRefresh}
              disabled={isRefreshing}
              className="w-[34px] h-[34px] rounded-[8px] bg-[#13131f] border border-white/[0.06] flex items-center justify-center cursor-pointer text-[#6b6b8a] transition-all hover:border-white/[0.12] hover:text-[#9898b8] disabled:opacity-50"
              title={t('category.refreshCategories')}
            >
              <RefreshCw className={`w-[14px] h-[14px] ${isRefreshing ? 'animate-spin' : ''}`} />
            </button>
            <button
              onClick={openCreateModal}
              className="bg-gradient-to-br from-[#7c3aed] to-[#a855f7] border-none rounded-[8px] px-4 py-2 text-white text-[13.5px] font-medium cursor-pointer flex items-center gap-[6px] shadow-[0_4px_16px_rgba(124,58,237,0.25)] transition-all hover:opacity-90 hover:-translate-y-[1px]"
            >
              <Plus className="w-[13px] h-[13px]" strokeWidth={2.5} />
              {t('category.addCategory')}
            </button>
          </div>
        )}
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-[6px] flex-wrap">
        <button
          onClick={() => setTypeFilter('ALL')}
          className={`flex items-center gap-[7px] px-[14px] py-[7px] rounded-[8px] text-[13px] font-medium cursor-pointer transition-all ${
            typeFilter === 'ALL'
              ? 'bg-gradient-to-br from-[#7c3aed] to-[#a855f7] border border-transparent text-white shadow-[0_4px_16px_rgba(124,58,237,0.25)]'
              : 'bg-[#13131f] border border-white/[0.06] text-[#6b6b8a] hover:border-white/[0.12] hover:text-[#9898b8]'
          }`}
        >
          {t('common.all')}
          <span className={`font-mono text-[11px] rounded-[4px] px-[5px] py-[1px] ${
            typeFilter === 'ALL' ? 'bg-white/20' : 'bg-white/[0.06]'
          }`}>
            {categories.length}
          </span>
        </button>
        <button
          onClick={() => setTypeFilter('INCOME')}
          className={`flex items-center gap-[7px] px-[14px] py-[7px] rounded-[8px] text-[13px] font-medium cursor-pointer transition-all ${
            typeFilter === 'INCOME'
              ? 'bg-gradient-to-br from-[#7c3aed] to-[#a855f7] border border-transparent text-white shadow-[0_4px_16px_rgba(124,58,237,0.25)]'
              : 'bg-[#13131f] border border-white/[0.06] text-[#6b6b8a] hover:border-white/[0.12] hover:text-[#9898b8]'
          }`}
        >
          <div className="w-[7px] h-[7px] rounded-full bg-[#22c55e]" />
          {t('categoryType.income')}
          <span className={`font-mono text-[11px] rounded-[4px] px-[5px] py-[1px] ${
            typeFilter === 'INCOME' ? 'bg-white/20' : 'bg-white/[0.06]'
          }`}>
            {incomeCount}
          </span>
        </button>
        <button
          onClick={() => setTypeFilter('EXPENSE')}
          className={`flex items-center gap-[7px] px-[14px] py-[7px] rounded-[8px] text-[13px] font-medium cursor-pointer transition-all ${
            typeFilter === 'EXPENSE'
              ? 'bg-gradient-to-br from-[#7c3aed] to-[#a855f7] border border-transparent text-white shadow-[0_4px_16px_rgba(124,58,237,0.25)]'
              : 'bg-[#13131f] border border-white/[0.06] text-[#6b6b8a] hover:border-white/[0.12] hover:text-[#9898b8]'
          }`}
        >
          <div className="w-[7px] h-[7px] rounded-full bg-[#f43f5e]" />
          {t('categoryType.expense')}
          <span className={`font-mono text-[11px] rounded-[4px] px-[5px] py-[1px] ${
            typeFilter === 'EXPENSE' ? 'bg-white/20' : 'bg-white/[0.06]'
          }`}>
            {expenseCount}
          </span>
        </button>
      </div>

      {/* Categories Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-[10px]">
        {filteredCategories.map((category) => (
          <div
            key={category.id}
            className="group bg-[#13131f] border border-white/[0.06] rounded-[12px] px-4 py-[14px] flex items-center justify-between cursor-pointer transition-all hover:border-white/[0.12] hover:bg-[#1a1a2a] hover:-translate-y-[1px] hover:shadow-[0_6px_24px_rgba(0,0,0,0.25)]"
          >
            <div className="flex items-center gap-3">
              {/* Icon with type dot */}
              <div className="relative w-[38px] h-[38px] flex-shrink-0">
                <div className={`w-[38px] h-[38px] rounded-[10px] flex items-center justify-center text-[18px] ${
                  category.type === 'INCOME'
                    ? 'bg-[rgba(34,197,94,0.1)]'
                    : 'bg-[rgba(244,63,94,0.1)]'
                }`}>
                  {category.emoji || (category.type === 'INCOME' ? '💰' : '💸')}
                </div>
                <div className={`absolute -bottom-[1px] -right-[1px] w-[10px] h-[10px] rounded-full border-2 border-[#13131f] ${
                  category.type === 'INCOME' ? 'bg-[#22c55e]' : 'bg-[#f43f5e]'
                }`} />
              </div>
              <div>
                <div className="text-[13.5px] font-medium text-white mb-[2px]">
                  {category.name}
                </div>
                <div className="text-[11.5px] text-[#6b6b8a]">
                  {t('wallet.created', { date: new Date(category.createdAt).toLocaleDateString() })}
                </div>
              </div>
            </div>

            {/* Actions — visible on hover */}
            <div className="flex gap-1 md:opacity-0 md:group-hover:opacity-100 transition-opacity duration-150">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  openEditModal(category);
                }}
                className="w-7 h-7 rounded-[7px] bg-white/[0.04] border border-white/[0.06] flex items-center justify-center cursor-pointer text-[#6b6b8a] transition-all hover:bg-white/[0.08] hover:text-white"
                title={t('category.editCategory')}
              >
                <Edit className="w-3 h-3" />
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setDeletingCategory(category);
                }}
                className="w-7 h-7 rounded-[7px] bg-white/[0.04] border border-white/[0.06] flex items-center justify-center cursor-pointer text-[#6b6b8a] transition-all hover:bg-[rgba(244,63,94,0.12)] hover:text-[#f43f5e] hover:border-[rgba(244,63,94,0.25)]"
                title={t('category.deleteCategory')}
              >
                <Trash2 className="w-3 h-3" />
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Empty State — filtered */}
      {filteredCategories.length === 0 && categories.length > 0 && (
        <div className="text-center py-12">
          <Tag className="w-16 h-16 text-[#6b6b8a] mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">{t('category.noTypeCategories', { type: typeFilter.toLowerCase() })}</h3>
          <p className="text-[#6b6b8a] mb-4">
            {t('category.noTypeCategoriesDesc', { type: typeFilter.toLowerCase() })}
          </p>
          <button
            onClick={openCreateModal}
            className="px-4 py-2 bg-gradient-to-br from-[#7c3aed] to-[#a855f7] rounded-[8px] text-white text-[13.5px] font-medium cursor-pointer flex items-center gap-[6px] mx-auto shadow-[0_4px_16px_rgba(124,58,237,0.25)] transition-all hover:opacity-90 hover:-translate-y-[1px]"
          >
            <Plus className="w-4 h-4" />
            {t('category.createTypeCategory', { type: typeFilter.toLowerCase() })}
          </button>
        </div>
      )}
      
      {/* Completely Empty State */}
      {categories.length === 0 && (
        <div className="text-center py-12">
          <Tag className="w-16 h-16 text-[#6b6b8a] mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">{t('category.noCategoriesYet')}</h3>
          <p className="text-[#6b6b8a] mb-4">
            {t('category.noCategoriesDesc')}
          </p>
          <button
            onClick={openCreateModal}
            className="px-4 py-2 bg-gradient-to-br from-[#7c3aed] to-[#a855f7] rounded-[8px] text-white text-[13.5px] font-medium cursor-pointer flex items-center gap-[6px] mx-auto shadow-[0_4px_16px_rgba(124,58,237,0.25)] transition-all hover:opacity-90 hover:-translate-y-[1px]"
          >
            <Plus className="w-4 h-4" />
            {t('category.createFirstCategory')}
          </button>
        </div>
      )}

      {/* Category Modal */}
      <CategoryModal
        isOpen={showModal}
        onClose={closeModal}
        onSave={editingCategory ? handleUpdateCategory : handleCreateCategory}
        category={editingCategory}
        loading={loading}
      />

      {/* Delete Confirmation Modal */}
      {deletingCategory && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-[#13131f] border border-white/[0.06] rounded-[14px] p-6 w-full max-w-md">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-12 h-12 bg-[rgba(244,63,94,0.15)] rounded-[10px] flex items-center justify-center">
                <Trash2 className="w-6 h-6 text-[#f43f5e]" />
              </div>
              <div>
                <h3 className="text-lg font-semibold">{t('category.deleteCategory')}</h3>
                <p className="text-[#6b6b8a] text-sm">{t('category.cannotBeUndone')}</p>
              </div>
            </div>
            
            <p className="text-[#9898b8] mb-6">
              {t('category.deleteCategoryConfirm', { name: `${deletingCategory.emoji ? `${deletingCategory.emoji} ` : ''}${deletingCategory.name}` })}
            </p>
            
            <div className="flex gap-3">
              <button
                onClick={() => setDeletingCategory(null)}
                className="flex-1 px-4 py-2 bg-white/[0.05] border border-white/[0.06] hover:border-white/[0.12] text-[#9898b8] rounded-[8px] transition-colors cursor-pointer"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={() => handleDeleteCategory(deletingCategory)}
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
