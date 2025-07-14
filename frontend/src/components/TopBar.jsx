import React from 'react';
import { Search, RefreshCw, Bell, User } from 'lucide-react';

export default function TopBar({ 
    searchTerm, 
    setSearchTerm, 
    selectedFilter, 
    setSelectedFilter, 
    onRefresh 
}) {
    return (
        <header className="bg-gray-900/50 border-b border-gray-800 px-8 py-4">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4 flex-1">
                    <div className="relative max-w-md flex-1">
                        <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-500" />
                        <input
                            type="text"
                            placeholder="Search transactions..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="w-full pl-12 pr-4 py-2.5 bg-gray-800/50 border border-gray-700 rounded-lg focus:border-violet-500 focus:outline-none transition-colors"
                        />
                    </div>

                    {/* <div className="flex items-center gap-2">
                        {['ALL', 'INCOME', 'EXPENSE'].map(filter => (
                            <button
                                key={filter}
                                onClick={() => setSelectedFilter(filter)}
                                className={`px-4 py-2 rounded-lg font-medium transition-all ${
                                    selectedFilter === filter
                                        ? 'bg-violet-500/20 text-violet-400 border border-violet-500/30'
                                        : 'bg-gray-800/50 text-gray-400 hover:text-white hover:bg-gray-700/50'
                                }`}
                            >
                                {filter === 'ALL' ? 'All' : filter.charAt(0) + filter.slice(1).toLowerCase()}
                            </button>
                        ))}
                    </div> */}
                </div>

                <div className="flex items-center gap-4 ml-8">
                    <button 
                        onClick={onRefresh}
                        className="p-2 hover:bg-gray-800 rounded-lg transition-colors"
                        title="Refresh data"
                    >
                        <RefreshCw className="w-5 h-5 text-gray-400" />
                    </button>
                    <button className="p-2 hover:bg-gray-800 rounded-lg transition-colors">
                        <Bell className="w-5 h-5 text-gray-400" />
                    </button>
                    <button className="p-2 hover:bg-gray-800 rounded-lg transition-colors">
                        <User className="w-5 h-5 text-gray-400" />
                    </button>
                </div>
            </div>
        </header>
    );
}
