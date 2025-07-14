'use client';
import React from 'react';
import { SettingsView } from '../PlaceholderViews';
import { useAppContext } from '../../contexts/AppContext';
import { Loading } from '../Loading';

export default function SettingsPage() {
    const { 
        dashboardData, 
        categories, 
        onRefresh,
        isLoading,
        hasError
    } = useAppContext();

    // Show loading state
    if (isLoading) {
        return <Loading message="Loading settings..." />;
    }

    // Show error state  
    if (hasError) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold mb-2">Error loading settings</h2>
                    <p className="text-gray-400">Please try refreshing the page</p>
                </div>
            </div>
        );
    }
    
    return (
        <SettingsView 
            dashboardData={dashboardData}
            categories={categories}
            onRefresh={onRefresh}
        />
    );
}
