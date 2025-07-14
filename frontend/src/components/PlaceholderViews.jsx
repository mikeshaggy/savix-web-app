import React from 'react';
import { PieChart, Settings } from 'lucide-react';

export function AnalyticsView() {
    return (
        <div className="text-center py-12">
            <PieChart className="w-16 h-16 text-gray-600 mx-auto mb-4" />
            <h3 className="text-xl font-semibold mb-2">Analytics Coming Soon</h3>
            <p className="text-gray-400">Advanced analytics and insights will be available here.</p>
        </div>
    );
}

export function SettingsView() {
    return (
        <div className="text-center py-12">
            <Settings className="w-16 h-16 text-gray-600 mx-auto mb-4" />
            <h3 className="text-xl font-semibold mb-2">Settings Coming Soon</h3>
            <p className="text-gray-400">Application settings and preferences will be available here.</p>
        </div>
    );
}
