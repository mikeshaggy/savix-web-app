import React from 'react';
import { Loader2 } from 'lucide-react';

// Generic loading component
export const Loading = ({ message = 'Loading...', size = 'md' }) => {
    const sizeClasses = {
        sm: 'w-4 h-4',
        md: 'w-8 h-8',
        lg: 'w-12 h-12'
    };

    return (
        <div className="flex items-center justify-center p-8">
            <div className="flex flex-col items-center gap-3">
                <Loader2 className={`${sizeClasses[size]} animate-spin text-violet-500`} />
                <p className="text-gray-400 text-sm">{message}</p>
            </div>
        </div>
    );
};

// Full page loading
export const PageLoading = ({ message = 'Loading your data...' }) => (
    <div className="min-h-screen bg-gray-950 text-white flex items-center justify-center">
        <Loading message={message} size="lg" />
    </div>
);

// Card loading skeleton
export const CardLoading = () => (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 animate-pulse">
        <div className="flex items-center justify-between mb-4">
            <div className="h-4 bg-gray-800 rounded w-24"></div>
            <div className="w-10 h-10 bg-gray-800 rounded-lg"></div>
        </div>
        <div className="h-8 bg-gray-800 rounded w-32 mb-2"></div>
        <div className="h-3 bg-gray-800 rounded w-20"></div>
    </div>
);

// Table loading skeleton
export const TableLoading = ({ rows = 5, columns = 4 }) => (
    <div className="space-y-3">
        {Array.from({ length: rows }).map((_, index) => (
            <div key={index} className="flex gap-4 p-4 bg-gray-900 rounded-lg animate-pulse">
                {Array.from({ length: columns }).map((_, colIndex) => (
                    <div key={colIndex} className="h-4 bg-gray-800 rounded flex-1"></div>
                ))}
            </div>
        ))}
    </div>
);

// Button loading state
export const ButtonLoading = ({ children, loading, ...props }) => (
    <button disabled={loading} {...props}>
        {loading ? (
            <div className="flex items-center gap-2">
                <Loader2 className="w-4 h-4 animate-spin" />
                Loading...
            </div>
        ) : (
            children
        )}
    </button>
);
