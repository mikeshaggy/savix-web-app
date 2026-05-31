'use client';
import React from 'react';
import { Filter } from 'lucide-react';

export default function FiltersPage() {
    return (
        <div className="text-center py-12">
            <Filter className="w-16 h-16 text-white/[0.15] mx-auto mb-4" />
            <h3 className="text-xl font-semibold mb-2">Saved Filters</h3>
            <p className="text-white/40">Save and manage your custom transaction filters.</p>
        </div>
    );
}
