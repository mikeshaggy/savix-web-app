import React, { useMemo, useState, useEffect } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Plus, Edit3, Trash2, Tag, AlertCircle, RefreshCw, Search, ArrowUpDown, Anchor, EyeOff, CalendarDays } from 'lucide-react';
import { useCategories } from '@/hooks/useApi';
import { useWallets } from '@/contexts/WalletContext';
import CategoryModal from '@/components/modals/CategoryModal';
import { Loading } from '@/components/common/Loading';
import { useTranslations } from 'next-intl';

const TYPE_STYLES = {
  INCOME: 'bg-green-400/10 border-green-400/25 text-green-300',
  EXPENSE: 'bg-rose-400/10 border-rose-400/25 text-rose-300',
};

const SORT_OPTIONS = ['name', 'createdAt', 'type'];

export default function CategoryManagementView() {
  const t = useTranslations();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { currentWallet } = useWallets();
  const { categories, loading, error, createCategory, updateCategory, deleteCategory, refetch } = useCategories();
  const [showModal, setShowModal] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [deletingCategory, setDeletingCategory] = useState(null);
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState('name');
  const [isRefreshing, setIsRefreshing] = useState(false);

  useEffect(() => {
    if (searchParams.get('open') !== 'create') return;
    setEditingCategory(null);
    setShowModal(true);
    router.replace(pathname);
  }, [pathname, router, searchParams]);

  const filteredCategories = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();

    return categories
      .filter(category => {
        const matchesType = typeFilter === 'ALL' || category.type === typeFilter;
        const matchesSearch = !query || category.name.toLowerCase().includes(query);
        return matchesType && matchesSearch;
      })
      .sort((a, b) => {
        if (sortBy === 'createdAt') {
          return new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
        }
        if (sortBy === 'type') {
          return `${a.type}-${a.name}`.localeCompare(`${b.type}-${b.name}`);
        }
        return a.name.localeCompare(b.name);
      });
  }, [categories, searchQuery, sortBy, typeFilter]);

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

      <div className="bg-[#0e0e1c] border border-white/[0.055] rounded-[16px] p-3 sm:p-4 flex flex-col gap-3">
        <div className="flex gap-[6px] flex-wrap">
          {[
            { value: 'ALL', label: t('common.all'), count: categories.length },
            { value: 'INCOME', label: t('categoryType.income'), count: incomeCount, dot: 'bg-[#22c55e]' },
            { value: 'EXPENSE', label: t('categoryType.expense'), count: expenseCount, dot: 'bg-[#f43f5e]' },
          ].map(filter => (
            <button
              key={filter.value}
              onClick={() => setTypeFilter(filter.value)}
              className={`flex items-center gap-[7px] px-[13px] py-[7px] rounded-[8px] text-[13px] font-medium cursor-pointer transition-all ${
                typeFilter === filter.value
                  ? 'bg-gradient-to-br from-[#7c3aed] to-[#a855f7] border border-transparent text-white shadow-[0_4px_16px_rgba(124,58,237,0.25)]'
                  : 'bg-[#13131f] border border-white/[0.06] text-[#6b6b8a] hover:border-white/[0.12] hover:text-[#9898b8]'
              }`}
            >
              {filter.dot && <div className={`w-[7px] h-[7px] rounded-full ${filter.dot}`} />}
              {filter.label}
              <span className={`font-mono text-[11px] rounded-[4px] px-[5px] py-[1px] ${
                typeFilter === filter.value ? 'bg-white/20' : 'bg-white/[0.06]'
              }`}>
                {filter.count}
              </span>
            </button>
          ))}
        </div>

        <div className="flex flex-col sm:flex-row gap-2">
          <label className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/25 pointer-events-none" />
            <input
              type="search"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder={t('category.searchPlaceholder')}
              className="w-full h-10 bg-[#13131f] border border-white/[0.06] rounded-[9px] pl-9 pr-3 text-[13.5px] text-white placeholder:text-[#6b6b8a] outline-none transition-all focus:border-purple-400/45 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.12)]"
            />
          </label>
          <label className="relative sm:w-[190px]">
            <ArrowUpDown className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/25 pointer-events-none" />
            <select
              value={sortBy}
              onChange={(event) => setSortBy(event.target.value)}
              className="w-full h-10 appearance-none bg-[#13131f] border border-white/[0.06] rounded-[9px] pl-9 pr-3 text-[13.5px] text-white outline-none transition-all focus:border-purple-400/45 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.12)]"
            >
              {SORT_OPTIONS.map(option => (
                <option key={option} value={option}>{t(`category.sort.${option}`)}</option>
              ))}
            </select>
          </label>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 2xl:grid-cols-3 gap-3 items-stretch">
        {filteredCategories.map((category) => (
          <CategoryCard
            key={category.id}
            category={category}
            t={t}
            onEdit={openEditModal}
            onDelete={setDeletingCategory}
          />
        ))}
      </div>

      {filteredCategories.length === 0 && categories.length > 0 && (
        <div className="text-center py-12">
          <Tag className="w-16 h-16 text-[#6b6b8a] mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">{searchQuery ? t('category.noMatchingCategories') : t('category.noTypeCategories', { type: typeFilter.toLowerCase() })}</h3>
          <p className="text-[#6b6b8a] mb-4">
            {searchQuery ? t('category.noMatchingCategoriesDesc') : t('category.noTypeCategoriesDesc', { type: typeFilter.toLowerCase() })}
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

      <CategoryModal
        isOpen={showModal}
        onClose={closeModal}
        onSave={editingCategory ? handleUpdateCategory : handleCreateCategory}
        category={editingCategory}
        loading={loading}
      />

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

function CategoryCard({ category, t, onEdit, onDelete }) {
  const isIncome = category.type === 'INCOME';

  return (
    <div className="group bg-[#13131f] border border-white/[0.06] rounded-[12px] p-3.5 flex flex-col gap-3 transition-all hover:border-white/[0.12] hover:bg-[#171729] hover:-translate-y-[1px] hover:shadow-[0_8px_24px_rgba(0,0,0,0.24)]">
      <div className="flex items-start gap-3">
        <div className={`w-11 h-11 shrink-0 rounded-[12px] flex items-center justify-center text-[24px] leading-none shadow-[inset_0_0_0_1px_rgba(255,255,255,0.04)] ${
          isIncome ? 'bg-green-400/[0.09]' : 'bg-rose-400/[0.1]'
        }`}>
          {category.emoji || (isIncome ? '💰' : '💸')}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 min-w-0">
            <h3 className="text-[15px] font-semibold text-white truncate">{category.name}</h3>
            <span className={`shrink-0 px-2 py-[2px] rounded-full border text-[11px] font-semibold ${TYPE_STYLES[category.type]}`}>
              {isIncome ? t('categoryType.income') : t('categoryType.expense')}
            </span>
          </div>
          <div className="flex items-center gap-1.5 mt-1 text-[12px] text-[#6b6b8a]">
            <CalendarDays className="w-3.5 h-3.5" />
            {t('wallet.created', { date: category.createdAt ? new Date(category.createdAt).toLocaleDateString() : '-' })}
          </div>
        </div>
        <div className="flex gap-1 shrink-0">
          <button
            onClick={() => onEdit(category)}
            className="w-8 h-8 rounded-[8px] bg-white/[0.04] border border-white/[0.06] flex items-center justify-center cursor-pointer text-[#9898b8] transition-all hover:bg-white/[0.08] hover:text-white"
            title={t('category.editCategory')}
          >
            <Edit3 className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => onDelete(category)}
            className="w-8 h-8 rounded-[8px] bg-white/[0.04] border border-white/[0.06] flex items-center justify-center cursor-pointer text-[#9898b8] transition-all hover:bg-[rgba(244,63,94,0.12)] hover:text-[#f43f5e] hover:border-[rgba(244,63,94,0.25)]"
            title={t('category.deleteCategory')}
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-1.5 text-[12px]">
        <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-[4px] ${
          category.isCycleAnchor ? 'bg-purple-400/10 border-purple-400/25 text-purple-300' : 'bg-white/[0.035] border-white/[0.055] text-white/35'
        }`}>
          <Anchor className="w-3.5 h-3.5" />
          {category.isCycleAnchor ? t('category.cycleAnchor') : t('category.notCycleAnchor')}
        </span>
        <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-[4px] ${
          category.excludedFromTopCategories ? 'bg-amber-400/10 border-amber-400/25 text-amber-300' : 'bg-white/[0.035] border-white/[0.055] text-white/35'
        }`}>
          <EyeOff className="w-3.5 h-3.5" />
          {category.excludedFromTopCategories ? t('category.hiddenFromTop') : t('category.visibleInTop')}
        </span>
      </div>
    </div>
  );
}
