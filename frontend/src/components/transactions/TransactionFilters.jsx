import React from 'react';
import { DATE_PRESETS } from '@/constants';
import { useTranslations } from 'next-intl';

export default function TransactionFilters({
    categoryFilter,
    setCategoryFilter,
    importanceFilter,
    setImportanceFilter,
    dateFilter,
    setDateFilter,
    datePreset,
    setDatePreset,
    customFromDate,
    setCustomFromDate,
    customToDate,
    setCustomToDate,
    sortBy,
    setSortBy,
    sortOrder,
    setSortOrder,
    categories = [],
    typeFilter,
    setTypeFilter
}) {
    const t = useTranslations();
    const hasActiveFilters = categoryFilter !== 'ALL' || importanceFilter !== 'ALL' || 
                            (datePreset && datePreset !== DATE_PRESETS.ALL_TIME) || 
                            typeFilter !== 'ALL';

    const filteredCategories = React.useMemo(() => {
        if (typeFilter === 'ALL') return categories;
        return categories.filter(category => category.type === typeFilter);
    }, [categories, typeFilter]);

    const handleTypeFilterChange = (newType) => {
        setTypeFilter(newType);
        if (categoryFilter !== 'ALL') {
            const selectedCategoryId = parseInt(categoryFilter, 10);
            const currentCategory = categories.find(cat => cat.id === selectedCategoryId);
            if (!currentCategory || (newType !== 'ALL' && currentCategory.type !== newType)) {
                setCategoryFilter('ALL');
            }
        }
    };

    const handleDatePresetChange = (newPreset) => {
        setDatePreset(newPreset);
        if (newPreset !== DATE_PRESETS.CUSTOM) {
            setCustomFromDate('');
            setCustomToDate('');
        }
    };

    const handleSortChange = (combined) => {
        const [field, order] = combined.split(':');
        setSortBy(field);
        setSortOrder(order);
    };

    const clearAllFilters = () => {
        setTypeFilter('ALL');
        setCategoryFilter('ALL');
        setImportanceFilter('ALL');
        setDatePreset(DATE_PRESETS.ALL_TIME);
        setCustomFromDate('');
        setCustomToDate('');
        setSortBy('date');
        setSortOrder('desc');
    };

    const chipBase = "appearance-none outline-none rounded-[10px] px-[13px] py-[7px] text-[13px] font-medium cursor-pointer transition-all whitespace-nowrap";
    const chipDefault = "bg-[#0e0e1c] border border-white/[0.055] text-white/50 hover:border-white/[0.12] hover:text-white";
    const chipActive = "bg-[rgba(124,58,237,0.14)] border border-[rgba(124,58,237,0.35)] text-purple-300";

    return (
        <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2 flex-wrap">
                {/* Type filter */}
                <select
                    value={typeFilter}
                    onChange={(e) => handleTypeFilterChange(e.target.value)}
                    className={`${chipBase} ${typeFilter !== 'ALL' ? chipActive : chipDefault}`}
                >
                    <option value="ALL">{t('filters.allTypes')}</option>
                    <option value="EXPENSE">{t('categoryType.expense')}</option>
                    <option value="INCOME">{t('categoryType.income')}</option>
                </select>

                {/* Category filter */}
                <select
                    value={categoryFilter}
                    onChange={(e) => setCategoryFilter(e.target.value)}
                    className={`${chipBase} ${categoryFilter !== 'ALL' ? chipActive : chipDefault} max-w-[220px]`}
                >
                    <option value="ALL">{t('filters.allCategories')}</option>
                    {filteredCategories.map(category => (
                        <option key={category.id} value={category.id}>
                            {category.emoji ? `${category.emoji} ${category.name}` : category.name}
                        </option>
                    ))}
                </select>

                {/* Importance filter */}
                <select
                    value={importanceFilter}
                    onChange={(e) => setImportanceFilter(e.target.value)}
                    className={`${chipBase} ${importanceFilter !== 'ALL' ? chipActive : chipDefault}`}
                >
                    <option value="ALL">{t('filters.allLevels')}</option>
                    <option value="ESSENTIAL">{t('importance.essential')}</option>
                    <option value="HAVE_TO_HAVE">{t('importance.haveToHave')}</option>
                    <option value="NICE_TO_HAVE">{t('importance.niceToHave')}</option>
                    <option value="SHOULDNT_HAVE">{t('importance.shouldntHave')}</option>
                </select>

                {/* Separator */}
                <div className="w-px h-6 bg-white/[0.12] mx-0.5" />

                {/* Date filter */}
                <select
                    value={datePreset || DATE_PRESETS.ALL_TIME}
                    onChange={(e) => handleDatePresetChange(e.target.value)}
                    className={`${chipBase} ${datePreset && datePreset !== DATE_PRESETS.ALL_TIME ? chipActive : chipDefault}`}
                >
                    <option value={DATE_PRESETS.ALL_TIME}>{t('filters.allTime')}</option>
                    <option value={DATE_PRESETS.THIS_WEEK}>{t('filters.thisWeek')}</option>
                    <option value={DATE_PRESETS.THIS_MONTH}>{t('filters.thisMonth')}</option>
                    <option value={DATE_PRESETS.LAST_MONTH}>{t('filters.lastMonth')}</option>
                    <option value={DATE_PRESETS.CUSTOM}>{t('filters.customRange')}</option>
                </select>

                {/* Clear button */}
                {hasActiveFilters && (
                    <button
                        onClick={clearAllFilters}
                        className="text-[12px] font-semibold text-white/22 bg-transparent border-none cursor-pointer px-[10px] py-[7px] transition-colors hover:text-red-400"
                    >
                        ✕ {t('filters.clearAll')}
                    </button>
                )}

                {/* Sort chip - pushed right */}
                <select
                    value={`${sortBy}:${sortOrder}`}
                    onChange={(e) => handleSortChange(e.target.value)}
                    className={`${chipBase} ${chipDefault} ml-auto`}
                >
                    <option value="date:desc">{t('sort.date')} ↓</option>
                    <option value="date:asc">{t('sort.date')} ↑</option>
                    <option value="amount:desc">{t('sort.amount')} ↓</option>
                    <option value="amount:asc">{t('sort.amount')} ↑</option>
                    <option value="title:asc">{t('sort.title')} A–Z</option>
                    <option value="title:desc">{t('sort.title')} Z–A</option>
                </select>
            </div>

            {/* Custom date range inputs */}
            {datePreset === DATE_PRESETS.CUSTOM && (
                <div className="flex items-center gap-3">
                    <input
                        type="date"
                        value={customFromDate}
                        onChange={(e) => setCustomFromDate(e.target.value)}
                        className="bg-[#131325] border border-white/[0.055] rounded-[10px] px-3.5 py-[7px] text-[13px] text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] [color-scheme:dark]"
                    />
                    <span className="text-white/22 text-xs">→</span>
                    <input
                        type="date"
                        value={customToDate}
                        onChange={(e) => setCustomToDate(e.target.value)}
                        className="bg-[#131325] border border-white/[0.055] rounded-[10px] px-3.5 py-[7px] text-[13px] text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] [color-scheme:dark]"
                    />
                </div>
            )}
        </div>
    );
}
