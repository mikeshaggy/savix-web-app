'use client';
import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error) {
        return { hasError: true, error };
    }

    componentDidCatch(error, errorInfo) {
        console.error('Error caught by boundary:', error, errorInfo);
    }

    render() {
        if (this.state.hasError) {
            return (
                <div className="min-h-screen bg-gray-950 text-white flex items-center justify-center">
                    <div className="flex flex-col items-center gap-4 text-center max-w-md">
                        <AlertTriangle className="w-16 h-16 text-red-500" />
                        <h2 className="text-2xl font-bold">Something went wrong</h2>
                        <p className="text-gray-400">
                            {this.state.error?.message || 'An unexpected error occurred'}
                        </p>
                        <button
                            onClick={() => window.location.reload()}
                            className="px-4 py-2 bg-violet-500 hover:bg-violet-600 rounded-lg transition-colors flex items-center gap-2"
                        >
                            <RefreshCw className="w-4 h-4" />
                            Reload Page
                        </button>
                        {process.env.NODE_ENV === 'development' && (
                            <details className="mt-4 text-xs text-gray-500">
                                <summary className="cursor-pointer">Error Details</summary>
                                <pre className="mt-2 p-2 bg-gray-900 rounded text-left overflow-auto">
                                    {this.state.error?.stack}
                                </pre>
                            </details>
                        )}
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;
