import React from 'react';
import { RefreshCw, Bell, User } from 'lucide-react';

export default function TopBar({ 
    onRefresh 
}) {
    return (
        <header className="bg-gray-900/50 border-b border-gray-800 px-8 py-4">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4 flex-1">
                    {/* Search functionality removed */}
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
