'use client';
import React from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';

// Matches importance badge colors used in TransactionTable across the app
const IMPORTANCE_COLORS = {
  ESSENTIAL: '#4ade80',    // green-400
  HAVE_TO_HAVE: '#c4b5fd', // violet-300
  NICE_TO_HAVE: '#facc15', // yellow-400
  SHOULDNT_HAVE: '#f87171', // red-400
  INVESTMENT: '#93c5fd',   // blue-300
};

const IMPORTANCE_ORDER = [
  'ESSENTIAL',
  'HAVE_TO_HAVE',
  'NICE_TO_HAVE',
  'SHOULDNT_HAVE',
  'INVESTMENT',
];

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const item = payload[0].payload;
  return (
    <div
      className="text-sm px-3 py-2 rounded-lg"
      style={{
        background: '#1a1a2e',
        border: `1px solid ${item.color}40`,
        color: '#fff',
      }}
    >
      <div className="font-medium" style={{ color: item.color }}>{item.label}</div>
      <div className="text-white/60 mt-0.5">{formatCurrency(item.amount)}</div>
    </div>
  );
}

export default function ImportanceBreakdownChart({ data, loading }) {
  const t = useTranslations('analytics');

  if (loading) {
    return (
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 animate-pulse h-[400px]" />
    );
  }

  const rawBreakdown = data?.breakdown ?? [];

  const breakdown = IMPORTANCE_ORDER
    .map((key) => {
      const item = rawBreakdown.find((b) => b.importance === key);
      if (!item) return null;
      return {
        ...item,
        color: IMPORTANCE_COLORS[key] ?? '#6b7280',
        label: t(`importance_${key}`),
      };
    })
    .filter(Boolean);

  return (
    <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-xl p-5 flex flex-col h-full md:h-[400px]">
      <div className="text-xs font-bold tracking-[0.12em] uppercase text-white/35 mb-4 flex-shrink-0">
        {t('importanceBreakdown')}
      </div>

      {breakdown.length === 0 ? (
        <p className="text-sm text-white/40">{t('noImportanceData')}</p>
      ) : (
        <div className="flex-1 flex flex-col justify-between">
          {/* Donut — takes remaining upper space */}
          <div className="flex-1">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={breakdown}
                  cx="50%"
                  cy="50%"
                  innerRadius={55}
                  outerRadius={82}
                  dataKey="amount"
                  paddingAngle={2}
                  stroke="none"
                >
                  {breakdown.map((item) => (
                    <Cell key={item.importance} fill={item.color} />
                  ))}
                </Pie>
                <Tooltip content={CustomTooltip} />
              </PieChart>
            </ResponsiveContainer>
          </div>

          {/* Legend — pinned at the bottom */}
          <div className="flex flex-col gap-2.5 flex-shrink-0">
            {breakdown.map((item) => (
              <div key={item.importance} className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span
                    className="w-2 h-2 rounded-full flex-shrink-0"
                    style={{ background: item.color }}
                  />
                  <span className="text-sm text-white/65 truncate">{item.label}</span>
                </div>
                <div className="flex items-center gap-2.5 flex-shrink-0 ml-2">
                  <span className="text-xs text-white/35">{item.share.toFixed(1)}%</span>
                  <span className="text-sm font-mono font-medium" style={{ color: item.color }}>
                    {formatCurrency(item.amount)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
