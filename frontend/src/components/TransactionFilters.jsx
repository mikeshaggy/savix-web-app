import React from 'react';
import { Filter } from 'lucide-react';

export default function TransactionFilters({
    categoryFilter,
    setCategoryFilter,
    importanceFilter,
    setImportanceFilter,
    dateFilter,
    setDateFilter,
    sortBy,
    setSortBy,
    sortOrder,
    setSortOrder,
    categories = [],
    typeFilter,
    setTypeFilter
}) {
    const hasActiveFilters = categoryFilter !== 'ALL' || importanceFilter !== 'ALL' || dateFilter !== 'ALL' || typeFilter !== 'ALL';

    // Filter categories based on selected type
    const filteredCategories = React.useMemo(() => {
        if (typeFilter === 'ALL') {
            return categories;
        }
        return categories.filter(category => category.type === typeFilter);
    }, [categories, typeFilter]);

    // Handle type filter change and reset category filter if needed
    const handleTypeFilterChange = (newType) => {
        setTypeFilter(newType);
        
        // If the current category filter is not compatible with the new type, reset it
        if (categoryFilter !== 'ALL') {
            const currentCategory = categories.find(cat => cat.name === categoryFilter);
            if (!currentCategory || (newType !== 'ALL' && currentCategory.type !== newType)) {
                setCategoryFilter('ALL');
            }
        }
    };

    const clearAllFilters = () => {
        setTypeFilter('ALL');
        setCategoryFilter('ALL');
        setImportanceFilter('ALL');
        setDateFilter('ALL');
        setSortBy('date');
        setSortOrder('desc');
    };

    return (
        <div className="bg-gray-900/50 border border-gray-800 rounded-xl p-6">
            <div className="flex items-center gap-4 mb-4">
                <Filter className="w-5 h-5 text-gray-400" />
                <h3 className="text-lg font-semibold">Filters & Sorting</h3>
            </div>
            
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-6 gap-4">
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

                {/* Category Filter */}
                <div>
                    <label className="block text-sm font-medium text-gray-300 mb-2">Category</label>
                    <select
                        value={categoryFilter}
                        onChange={(e) => setCategoryFilter(e.target.value)}
                        className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                    >
                        <option value="ALL">
                            {typeFilter === 'ALL' ? 'All Categories' : `All ${typeFilter.toLowerCase()} Categories`}
                        </option>
                        {filteredCategories.map(category => (
                            <option key={category.id} value={category.name}>
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

                {/* Date Filter */}
                <div>
                    <label className="block text-sm font-medium text-gray-300 mb-2">Date Range</label>
                    <select
                        value={dateFilter}
                        onChange={(e) => setDateFilter(e.target.value)}
                        className="w-full bg-gray-800/50 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                    >
                        <option value="ALL">All Time</option>
                        <option value="THIS_WEEK">This Week</option>
                        <option value="THIS_MONTH">This Month</option>
                        <option value="LAST_MONTH">Last Month</option>
                    </select>
                </div>

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
                        <option value="desc">Newest First</option>
                        <option value="asc">Oldest First</option>
                    </select>
                </div>
            </div>

            {/* Clear Filters */}
            <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-700">
                <div className="flex items-center gap-2 text-sm text-gray-400">
                    <span>Active filters:</span>
                    {hasActiveFilters ? (
                        <div className="flex gap-2">
                            {typeFilter !== 'ALL' && <span className="px-2 py-1 bg-violet-500/20 text-violet-400 rounded text-xs">Type: {typeFilter}</span>}
                            {categoryFilter !== 'ALL' && <span className="px-2 py-1 bg-violet-500/20 text-violet-400 rounded text-xs">Category: {categoryFilter}</span>}
                            {importanceFilter !== 'ALL' && <span className="px-2 py-1 bg-violet-500/20 text-violet-400 rounded text-xs">Importance: {importanceFilter.replace('_', ' ')}</span>}
                            {dateFilter !== 'ALL' && <span className="px-2 py-1 bg-violet-500/20 text-violet-400 rounded text-xs">Date: {dateFilter.replace('_', ' ')}</span>}
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
