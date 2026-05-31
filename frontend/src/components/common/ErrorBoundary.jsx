'use client';
import React from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { PAGE_CTA } from '@/components/common/formControls';
// Note: ErrorBoundary is a class component and cannot use hooks.
// Displayed strings use English fallbacks. If the app needs translated
// error boundary copy, wrap this component in a functional parent that
// reads translations and passes them as props (errorTitle, retryLabel).

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
                <div className="min-h-screen bg-[#06060f] text-white flex items-center justify-center">
                    <div className="flex flex-col items-center gap-4 text-center max-w-md">
                        <AlertCircle className="w-10 h-10 text-red-400/70" />
                        <h2 className="text-[18px] font-semibold tracking-[-0.2px]">Something went wrong</h2>
                        <p className="text-[13px] text-white/40 max-w-[280px] leading-relaxed">
                            {this.state.error?.message || 'An unexpected error occurred'}
                        </p>
                        <button
                            onClick={() => window.location.reload()}
                            className={`${PAGE_CTA}`}
                        >
                            <RefreshCw className="w-[13px] h-[13px]" />
                            Reload page
                        </button>
                        {process.env.NODE_ENV === 'development' && (
                            <details className="mt-4 text-xs text-white/30">
                                <summary className="cursor-pointer">Error Details</summary>
                                <pre className="mt-2 p-2 bg-[#0e0e1c] border border-white/[0.06] rounded text-left overflow-auto">
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
