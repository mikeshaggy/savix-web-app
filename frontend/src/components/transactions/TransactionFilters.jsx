import React from 'react';
import { Filter, Calendar } from 'lucide-react';
import { DATE_PRESETS } from '@/constants';

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
    const hasActiveFilters = categoryFilter !== 'ALL' || importanceFilter !== 'ALL' || 
                            (datePreset && datePreset !== DATE_PRESETS.ALL_TIME) || 
                            typeFilter !== 'ALL';

    const filteredCategories = React.useMemo(() => {
        if (typeFilter === 'ALL') {
            return categories;
        }
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

    return (
        <div className="bg-gray-900/50 border border-gray-800 rounded-xl p-6">
            <div className="flex items-center gap-4 mb-4">
                <Filter className="w-5 h-5 text-gray-400" />
                <h3 className="text-lg font-semibold">Filters & Sorting</h3>
            </div>
            
            <div className="space-y-4">
                {/* First Row: Type, Category, Importance */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    {/* Type Filter */}
                    <div>
                        <label className="block text-sm font-medium text-gray-300 mb-2">Type</label>
                        <select
                            value={typeFilter}
                            onChange={(e) => handleTypeFilterChange(e.target.value)}
                            className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                        >
                            <option value="ALL">All Types</option>
                            <option value="INCOME">Income</option>
                            <option value="EXPENSE">Expense</option>
                        </select>
                    </div>

                    {/* Category Filter - FIXED: Use category.id as value */}
                    <div>
                        <label className="block text-sm font-medium text-gray-300 mb-2">Category</label>
                        <select
                            value={categoryFilter}
                            onChange={(e) => setCategoryFilter(e.target.value)}
                            className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                        >
                            <option value="ALL">
                                {typeFilter === 'ALL' ? 'All Categories' : `All ${typeFilter.toLowerCase()} categories`}
                            </option>
                            {filteredCategories.map(category => (
                                <option key={category.id} value={category.id}>
                                    {category.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Importance Filter */}
                    <div>
                        <label className="block text-sm font-medium text-gray-300 mb-2">Importance</label>
                        <select
                            value={importanceFilter}
                            onChange={(e) => setImportanceFilter(e.target.value)}
                            className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                        >
                            <option value="ALL">All Levels</option>
                            <option value="ESSENTIAL">Essential</option>
                            <option value="HAVE_TO_HAVE">Have to Have</option>
                            <option value="NICE_TO_HAVE">Nice to Have</option>
                            <option value="SHOULDNT_HAVE">Shouldn't Have</option>
                        </select>
                    </div>
                </div>

                {/* Second Row: Date Filtering */}
                <div className="border-t border-gray-700 pt-4">
                    <div className="flex items-center gap-2 mb-3">
                        <Calendar className="w-4 h-4 text-gray-400" />
                        <label className="text-sm font-medium text-gray-300">Date Range</label>
                    </div>
                    
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                        {/* Date Preset Selector */}
                        <div>
                            <label className="block text-xs text-gray-400 mb-1">Preset</label>
                            <select
                                value={datePreset || DATE_PRESETS.ALL_TIME}
                                onChange={(e) => handleDatePresetChange(e.target.value)}
                                className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                            >
                                <option value={DATE_PRESETS.ALL_TIME}>All Time</option>
                                <option value={DATE_PRESETS.THIS_WEEK}>This Week</option>
                                <option value={DATE_PRESETS.THIS_MONTH}>This Month</option>
                                <option value={DATE_PRESETS.LAST_MONTH}>Last Month</option>
                                <option value={DATE_PRESETS.CUSTOM}>Custom Range</option>
                            </select>
                        </div>

                        {/* Custom Date Range - Only show when preset is "custom" */}
                        {datePreset === DATE_PRESETS.CUSTOM && (
                            <>
                                <div>
                                    <label className="block text-xs text-gray-400 mb-1">From Date</label>
                                    <input
                                        type="date"
                                        value={customFromDate}
                                        onChange={(e) => setCustomFromDate(e.target.value)}
                                        className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs text-gray-400 mb-1">To Date</label>
                                    <input
                                        type="date"
                                        value={customToDate}
                                        onChange={(e) => setCustomToDate(e.target.value)}
                                        className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                                    />
                                </div>
                            </>
                        )}
                    </div>
                </div>

                {/* Third Row: Sorting */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-gray-700 pt-4">
                    {/* Sort By */}
                    <div>
                        <label className="block text-sm font-medium text-gray-300 mb-2">Sort By</label>
                        <select
                            value={sortBy}
                            onChange={(e) => setSortBy(e.target.value)}
                            className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                        >
                            <option value="date">Date</option>
                            <option value="amount">Amount</option>
                            <option value="title">Title</option>
                            <option value="category">Category</option>
                        </select>
                    </div>

                    {/* Sort Order */}
                    <div>
                        <label className="block text-sm font-medium text-gray-300 mb-2">Order</label>
                        <select
                            value={sortOrder}
                            onChange={(e) => setSortOrder(e.target.value)}
                            className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                        >
                            <option value="desc">Descending</option>
                            <option value="asc">Ascending</option>
                        </select>
                    </div>
                </div>
            </div>

            {/* Clear Filters */}
            <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-700">
                <div className="flex items-center gap-2 text-sm text-gray-400">
                    <span>Active filters:</span>
                    {hasActiveFilters ? (
                        <div className="flex flex-wrap gap-2">
                            {typeFilter !== 'ALL' && (
                                <span className="px-2 py-1 bg-violet-500/20 text-violet-400 rounded text-xs">
                                    Type: {typeFilter}
                                </span>
                            )}
                            {categoryFilter !== 'ALL' && (
                                <span className="px-2 py-1 bg-violet-500/20 text-violet-400 rounded text-xs">
                                    Category: {categories.find(c => c.id === parseInt(categoryFilter))?.name || categoryFilter}
                                </span>
                            )}
                            {importanceFilter !== 'ALL' && (
                                <span className="px-2 py-1 bg-violet-500/20 text-violet-400 rounded text-xs">
                                    Importance: {importanceFilter.replace(/_/g, ' ')}
                                </span>
                            )}
                            {datePreset && datePreset !== DATE_PRESETS.ALL_TIME && (
                                <span className="px-2 py-1 bg-violet-500/20 text-violet-400 rounded text-xs">
                                    Date: {datePreset === DATE_PRESETS.CUSTOM 
                                        ? `${customFromDate || 'Any'} - ${customToDate || 'Any'}`
                                        : datePreset.replace(/_/g, ' ')}
                                </span>
                            )}
                        </div>
                    ) : (
                        <span className="text-gray-500">None</span>
                    )}
                </div>
                <button
                    onClick={clearAllFilters}
                    className="px-3 py-1 text-sm bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
                >
                    Clear All
                </button>
            </div>
        </div>
    );
}
