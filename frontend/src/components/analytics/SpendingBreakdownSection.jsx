'use client';
import React from 'react';
import CategoryBreakdownChart from './CategoryBreakdownChart';
import ImportanceBreakdownChart from './ImportanceBreakdownChart';

export default function SpendingBreakdownSection({
  categoryData,
  importanceData,
  categoryLoading,
  importanceLoading,
  budgetMap,
}) {
  return (
    <div className="mb-5">
      <div className="grid grid-cols-1 md:grid-cols-[7fr_3fr] gap-5 items-stretch">
        <CategoryBreakdownChart data={categoryData} loading={categoryLoading} budgetMap={budgetMap} />
        <ImportanceBreakdownChart data={importanceData} loading={importanceLoading} />
      </div>
    </div>
  );
}
