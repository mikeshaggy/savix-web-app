import React from 'react';
import { AlertCircle, CheckCircle, RefreshCw } from 'lucide-react';

const BackendStatusIndicator = ({ isUsingMockData, onRefresh }) => {
  if (isUsingMockData) {
    return (
      <div className="flex items-center gap-2 bg-yellow-500/10 border border-yellow-500/20 rounded-lg px-3 py-2 text-sm">
        <AlertCircle className="w-4 h-4 text-yellow-500" />
        <span className="text-yellow-400">Using mock data (backend offline)</span>
        <button
          onClick={onRefresh}
          className="ml-2 p-1 hover:bg-yellow-500/20 rounded transition-colors"
          title="Retry connection"
        >
          <RefreshCw className="w-4 h-4 text-yellow-400" />
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 bg-green-500/10 border border-green-500/20 rounded-lg px-3 py-2 text-sm">
      <CheckCircle className="w-4 h-4 text-green-500" />
      <span className="text-green-400">Connected to backend</span>
    </div>
  );
};

export default BackendStatusIndicator;
