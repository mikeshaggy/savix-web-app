import React from 'react';
import { Search, X } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { getPresetDateRange } from '@/utils/dateFilters';
import MultiSelectDropdown from '@/components/common/MultiSelectDropdown';

export default function TransactionFilters({
    // Server-driven filter state
    types = [],
    updateTypes,
    categoryIds = [],
    updateCategoryIds,
    importances = [],
    updateImportances,
    startDate = '',
    updateStartDate,
    endDate = '',
    updateEndDate,
    sort = 'transactionDate,desc',
    updateSort,
    searchInput = '',
    setSearchInput,
    clearSearch,
    activeFilterCount = 0,
    clearAllFilters,
    categories = [],
}) {
    const t = useTranslations();

    const [customRangeOpen, setCustomRangeOpen] = React.useState(false);

    const datePreset = React.useMemo(() => {
        if (customRangeOpen) return 'custom';
        if (!startDate && !endDate) return 'all_time';
        const now = new Date();
        const presets = ['this_week', 'this_month', 'last_month'];
        for (const preset of presets) {
            const range = getPresetDateRange(preset, now);
            if (range.from && range.to) {
                const fromStr = range.from.toISOString().split('T')[0];
                const toStr = range.to.toISOString().split('T')[0];
                if (fromStr === startDate && toStr === endDate) return preset;
            }
        }
        if (startDate || endDate) return 'custom';
        return 'all_time';
    }, [startDate, endDate, customRangeOpen]);

    const filteredCategories = React.useMemo(() => {
        if (types.length === 0) return categories;
        return categories.filter(cat => types.includes(cat.type));
    }, [categories, types]);

    const typeOptions = React.useMemo(() => [
        { value: 'EXPENSE', label: t('categoryType.expense') },
        { value: 'INCOME', label: t('categoryType.income') },
    ], [t]);

    const categoryOptions = React.useMemo(() =>
        filteredCategories.map(cat => ({
            value: String(cat.id),
            label: cat.name,
            emoji: cat.emoji || undefined,
        })),
    [filteredCategories]);

    const importanceOptions = React.useMemo(() => [
        { value: 'ESSENTIAL', label: t('importance.essential') },
        { value: 'HAVE_TO_HAVE', label: t('importance.haveToHave') },
        { value: 'NICE_TO_HAVE', label: t('importance.niceToHave') },
        { value: 'SHOULDNT_HAVE', label: t('importance.shouldntHave') },
        { value: 'INVESTMENT', label: t('importance.investment') },
    ], [t]);

    const handleTypesChange = (newTypes) => {
        updateTypes(newTypes);
        if (categoryIds.length > 0 && newTypes.length > 0) {
            const compatibleIds = categories
                .filter(cat => newTypes.includes(cat.type))
                .map(cat => String(cat.id));
            const filtered = categoryIds.filter(id => compatibleIds.includes(id));
            if (filtered.length !== categoryIds.length) {
                updateCategoryIds(filtered);
            }
        }
    };

    const handleCategoryIdsChange = (newIds) => {
        updateCategoryIds(newIds.map(id => parseInt(id, 10)));
    };

    const handleDatePresetChange = (newPreset) => {
        if (newPreset === 'all_time') {
            setCustomRangeOpen(false);
            updateStartDate('');
            updateEndDate('');
        } else if (newPreset === 'custom') {
            setCustomRangeOpen(true);
        } else {
            setCustomRangeOpen(false);
            const range = getPresetDateRange(newPreset, new Date());
            if (range.from && range.to) {
                updateStartDate(range.from.toISOString().split('T')[0]);
                updateEndDate(range.to.toISOString().split('T')[0]);
            }
        }
    };

    const handleSortChange = (combined) => {
        const [field, order] = combined.split(':');
        const fieldMap = { date: 'transactionDate', amount: 'amount', title: 'title' };
        const mappedField = fieldMap[field] || field;
        updateSort(`${mappedField},${order}`);
    };

    const currentSortDisplay = React.useMemo(() => {
        const [field, dir] = sort.split(',');
        const reverseMap = { transactionDate: 'date', amount: 'amount', title: 'title' };
        return `${reverseMap[field] || 'date'}:${dir || 'desc'}`;
    }, [sort]);

    const hasActiveFilters = activeFilterCount > 0;

    const chipBase = "appearance-none outline-none rounded-[10px] px-[13px] py-[7px] text-[13px] font-medium cursor-pointer transition-all whitespace-nowrap";
    const chipDefault = "bg-[#0e0e1c] border border-white/[0.055] text-white/50 hover:border-white/[0.12] hover:text-white";
    const chipActive = "bg-[rgba(124,58,237,0.14)] border border-[rgba(124,58,237,0.35)] text-purple-300";

    const categoryIdStrings = React.useMemo(
        () => categoryIds.map(String),
        [categoryIds]
    );

    return (
        <div className="flex flex-col gap-3">
            {/* Search bar */}
            <div className="relative">
                <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-white/25 pointer-events-none" />
                <input
                    type="text"
                    value={searchInput}
                    onChange={(e) => setSearchInput(e.target.value)}
                    placeholder={t('search.placeholder')}
                    className="w-full bg-[#0e0e1c] border border-white/[0.055] rounded-[12px] pl-10 pr-10 py-[10px] text-[14px] text-white placeholder:text-white/22 outline-none transition-all focus:border-violet-500/40 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.08)]"
                />
                {searchInput && (
                    <button
                        onClick={clearSearch}
                        className="absolute right-3 top-1/2 -translate-y-1/2 w-5 h-5 rounded-full flex items-center justify-center text-white/30 hover:text-white/60 hover:bg-white/[0.06] transition-all"
                    >
                        <X className="w-3.5 h-3.5" />
                    </button>
                )}
            </div>

            {/* Filter chips row */}
            <div className="flex items-center gap-2 flex-wrap">
                {/* Type multi-select */}
                <MultiSelectDropdown
                    options={typeOptions}
                    selected={types}
                    onChange={handleTypesChange}
                    allLabel={t('filters.allTypes')}
                    nSelectedLabel={(n) => t('filters.nSelected', { count: n })}
                />

                {/* Category multi-select */}
                <MultiSelectDropdown
                    options={categoryOptions}
                    selected={categoryIdStrings}
                    onChange={handleCategoryIdsChange}
                    allLabel={t('filters.allCategories')}
                    nSelectedLabel={(n) => t('filters.nSelected', { count: n })}
                    className="max-w-[260px]"
                />

                {/* Importance multi-select */}
                <MultiSelectDropdown
                    options={importanceOptions}
                    selected={importances}
                    onChange={updateImportances}
                    allLabel={t('filters.allLevels')}
                    nSelectedLabel={(n) => t('filters.nSelected', { count: n })}
                />

                {/* Separator */}
                <div className="w-px h-6 bg-white/[0.12] mx-0.5" />

                {/* Date filter */}
                <select
                    value={datePreset}
                    onChange={(e) => handleDatePresetChange(e.target.value)}
                    className={`${chipBase} ${datePreset !== 'all_time' ? chipActive : chipDefault}`}
                >
                    <option value="all_time">{t('filters.allTime')}</option>
                    <option value="this_week">{t('filters.thisWeek')}</option>
                    <option value="this_month">{t('filters.thisMonth')}</option>
                    <option value="last_month">{t('filters.lastMonth')}</option>
                    <option value="custom">{t('filters.customRange')}</option>
                </select>

                {/* Clear button + filter count badge */}
                {hasActiveFilters && (
                    <button
                        onClick={() => { setCustomRangeOpen(false); clearAllFilters(); }}
                        className="text-[12px] font-semibold text-white/22 bg-transparent border-none cursor-pointer px-[10px] py-[7px] transition-colors hover:text-red-400 flex items-center gap-1.5"
                    >
                        <span className="inline-flex items-center justify-center w-[18px] h-[18px] rounded-full bg-violet-500/20 text-violet-300 text-[11px] font-bold">
                            {activeFilterCount}
                        </span>
                        ✕ {t('filters.clearAll')}
                    </button>
                )}

                {/* Sort chip - pushed right */}
                <select
                    value={currentSortDisplay}
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
            {datePreset === 'custom' && (
                <div className="flex items-center gap-3">
                    <input
                        type="date"
                        value={startDate}
                        onChange={(e) => updateStartDate(e.target.value)}
                        className="bg-[#131325] border border-white/[0.055] rounded-[10px] px-3.5 py-[7px] text-[13px] text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] [color-scheme:dark]"
                    />
                    <span className="text-white/22 text-xs">→</span>
                    <input
                        type="date"
                        value={endDate}
                        onChange={(e) => updateEndDate(e.target.value)}
                        className="bg-[#131325] border border-white/[0.055] rounded-[10px] px-3.5 py-[7px] text-[13px] text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] [color-scheme:dark]"
                    />
                </div>
            )}
        </div>
    );
}
