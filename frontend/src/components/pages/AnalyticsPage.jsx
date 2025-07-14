'use client';
import React from 'react';
import { AnalyticsView } from '../PlaceholderViews';
import { useAppContext } from '../../contexts/AppContext';
import { Loading } from '../Loading';

export default function AnalyticsPage() {
    const { 
        dashboardData, 
        allTransactions, 
        categories,
        isLoading,
        hasError
    } = useAppContext();

    // Show loading state
    if (isLoading) {
        return <Loading message="Loading analytics..." />;
    }

    // Show error state  
    if (hasError) {
        return (
            <div className="flex items-center justify-center p-8">
                <div className="text-center">
                    <h2 className="text-xl font-semibold mb-2">Error loading analytics</h2>
                    <p className="text-gray-400">Please try refreshing the page</p>
                </div>
            </div>
        );
    }
    
    return (
        <AnalyticsView 
            dashboardData={dashboardData}
            allTransactions={allTransactions}
            categories={categories}
        />
    );
}
