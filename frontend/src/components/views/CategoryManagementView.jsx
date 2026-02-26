import React, { useState } from 'react';
import { Plus, Edit, Trash2, Tag, AlertCircle, TrendingUp, TrendingDown, RefreshCw } from 'lucide-react';
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
            <Tag className="w-16 h-16 text-violet-400 mx-auto mb-4" />
            <h2 className="text-2xl font-semibold text-white mb-2">{t('category.noWalletSelected')}</h2>
            <p className="text-gray-400 mb-6">
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
          <AlertCircle className="w-16 h-16 text-red-500 mx-auto mb-4" />
          <h2 className="text-xl font-semibold mb-2">{t('errors.failedToLoadCategories')}</h2>
          <p className="text-gray-400 mb-4">{error}</p>
          <button
            onClick={handleRefresh}
            className="px-4 py-2 bg-violet-500 hover:bg-violet-600 rounded-lg transition-colors flex items-center gap-2 mx-auto"
          >
            <RefreshCw className="w-4 h-4" />
            {t('common.tryAgain')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">{t('category.management')}</h2>
          <p className="text-gray-400 text-sm mt-1">
            {currentWallet ? (
              <>
                {t('category.managingFor', { wallet: currentWallet.name, count: categories.length })}
              </>
            ) : (
              t('category.noWalletCategoryMsg')
            )}
          </p>
        </div>
        {currentWallet && (
          <div className="flex gap-2">
            <button
              onClick={handleRefresh}
              disabled={isRefreshing}
              className="p-2 hover:bg-gray-800 rounded-lg transition-colors disabled:opacity-50"
              title={t('category.refreshCategories')}
            >
              <RefreshCw className={`w-5 h-5 text-gray-400 ${isRefreshing ? 'animate-spin' : ''}`} />
            </button>
            <button
              onClick={openCreateModal}
              className="px-4 py-2 bg-violet-500 hover:bg-violet-600 rounded-lg transition-colors flex items-center gap-2"
            >
              <Plus className="w-4 h-4" />
              {t('category.addCategory')}
            </button>
          </div>
        )}
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2 p-1 bg-gray-900/50 rounded-lg w-fit">
        <button
          onClick={() => setTypeFilter('ALL')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            typeFilter === 'ALL' 
              ? 'bg-violet-500 text-white' 
              : 'text-gray-400 hover:text-white hover:bg-gray-800'
          }`}
        >
          {t('common.all')} ({categories.length})
        </button>
        <button
          onClick={() => setTypeFilter('INCOME')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors flex items-center gap-2 ${
            typeFilter === 'INCOME' 
              ? 'bg-green-500 text-white' 
              : 'text-gray-400 hover:text-white hover:bg-gray-800'
          }`}
        >
          <TrendingUp className="w-4 h-4" />
          {t('categoryType.income')} ({categories.filter(c => c.type === 'INCOME').length})
        </button>
        <button
          onClick={() => setTypeFilter('EXPENSE')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors flex items-center gap-2 ${
            typeFilter === 'EXPENSE' 
              ? 'bg-red-500 text-white' 
              : 'text-gray-400 hover:text-white hover:bg-gray-800'
          }`}
        >
          <TrendingDown className="w-4 h-4" />
          {t('categoryType.expense')} ({categories.filter(c => c.type === 'EXPENSE').length})
        </button>
      </div>

      {/* Categories Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredCategories.map((category) => (
          <div
            key={category.id}
            className="bg-gray-900/50 border border-gray-800 rounded-xl p-4 hover:border-gray-700 transition-colors"
          >
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-3">
                <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                  category.type === 'INCOME' 
                    ? 'bg-green-500/20' 
                    : 'bg-red-500/20'
                }`}>
                  {category.type === 'INCOME' ? (
                    <TrendingUp className="w-5 h-5 text-green-400" />
                  ) : (
                    <TrendingDown className="w-5 h-5 text-red-400" />
                  )}
                </div>
                <div>
                  <h3 className="font-medium text-white">
                    {category.emoji && <span className="mr-1">{category.emoji}</span>}
                    {category.name}
                  </h3>
                  <p className="text-sm text-gray-400">
                    {t('wallet.created', { date: new Date(category.createdAt).toLocaleDateString() })}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => openEditModal(category)}
                  className="p-2 hover:bg-gray-700 rounded-lg transition-colors"
                  title={t('category.editCategory')}
                >
                  <Edit className="w-4 h-4 text-gray-400 hover:text-white" />
                </button>
                <button
                  onClick={() => setDeletingCategory(category)}
                  className="p-2 hover:bg-gray-700 rounded-lg transition-colors"
                  title={t('category.deleteCategory')}
                >
                  <Trash2 className="w-4 h-4 text-gray-400 hover:text-red-400" />
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Empty State */}
      {filteredCategories.length === 0 && categories.length > 0 && (
        <div className="text-center py-12">
          <Tag className="w-16 h-16 text-gray-600 mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">{t('category.noTypeCategories', { type: typeFilter.toLowerCase() })}</h3>
          <p className="text-gray-400 mb-4">
            {t('category.noTypeCategoriesDesc', { type: typeFilter.toLowerCase() })}
          </p>
          <button
            onClick={openCreateModal}
            className="px-4 py-2 bg-violet-500 hover:bg-violet-600 rounded-lg transition-colors flex items-center gap-2 mx-auto"
          >
            <Plus className="w-4 h-4" />
            {t('category.createTypeCategory', { type: typeFilter.toLowerCase() })}
          </button>
        </div>
      )}
      
      {/* Completely Empty State */}
      {categories.length === 0 && (
        <div className="text-center py-12">
          <Tag className="w-16 h-16 text-gray-600 mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">{t('category.noCategoriesYet')}</h3>
          <p className="text-gray-400 mb-4">
            {t('category.noCategoriesDesc')}
          </p>
          <button
            onClick={openCreateModal}
            className="px-4 py-2 bg-violet-500 hover:bg-violet-600 rounded-lg transition-colors flex items-center gap-2 mx-auto"
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
          <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 w-full max-w-md">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-12 h-12 bg-red-500/20 rounded-lg flex items-center justify-center">
                <Trash2 className="w-6 h-6 text-red-400" />
              </div>
              <div>
                <h3 className="text-lg font-semibold">{t('category.deleteCategory')}</h3>
                <p className="text-gray-400 text-sm">{t('category.cannotBeUndone')}</p>
              </div>
            </div>
            
            <p className="text-gray-300 mb-6">
              {t('category.deleteCategoryConfirm', { name: `${deletingCategory.emoji ? `${deletingCategory.emoji} ` : ''}${deletingCategory.name}` })}
            </p>
            
            <div className="flex gap-3">
              <button
                onClick={() => setDeletingCategory(null)}
                className="flex-1 px-4 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-lg transition-colors"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={() => handleDeleteCategory(deletingCategory)}
                className="flex-1 px-4 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg transition-colors"
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
