'use client';
import React from 'react';
import { BarChart3 } from 'lucide-react';

export default function ReportsPage() {
    return (
        <div className="text-center py-12">
            <BarChart3 className="w-16 h-16 text-gray-600 mx-auto mb-4" />
            <h3 className="text-xl font-semibold mb-2">Transaction Reports</h3>
            <p className="text-gray-400">Detailed transaction reports and insights coming soon.</p>
        </div>
    );
}
