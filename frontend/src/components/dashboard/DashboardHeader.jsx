'use client';
import React from 'react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';
import PeriodSelector from '@/components/common/PeriodSelector';

export default function DashboardHeader({
  period,
  periodType,
  currentBalance,
  selectedMonth,
  onPeriodTypeChange,
  onMonthChange,
  onCustomDateChange,
  // customStartDate / customEndDate: set in DashboardPage state when CUSTOM is active
  customStartDate,
  customEndDate,
}) {
  const t = useTranslations();

  return (
    <div
      className="flex flex-col gap-4 md:grid md:grid-cols-[1fr_auto] md:gap-5 md:items-end mb-6"
      style={{ animation: 'fadeUp 0.4s ease both' }}
    >
      {/* Left: current balance display */}
      <div>
        <div className="flex items-center gap-2 text-[10px] font-bold tracking-[0.14em] uppercase text-white/25 mb-2">
          <div className="w-7 h-px bg-white/[0.12]" />
          {t('dashboard.currentBalance')}
          <div className="w-7 h-px bg-white/[0.12]" />
        </div>
        <div className="font-mono text-[clamp(42px,5vw,60px)] font-medium tracking-[-2px] leading-none bg-gradient-to-br from-white/100 via-white/80 to-purple-300 bg-clip-text text-transparent">
          {currentBalance !== null && currentBalance !== undefined
            ? formatCurrency(currentBalance)
            : 'N/A'}
        </div>
      </div>

      {/* Right: period selector — pill above, segmented tabs below */}
      <PeriodSelector
        periodType={periodType}
        selectedMonth={selectedMonth}
        startDate={customStartDate ?? null}
        endDate={customEndDate ?? null}
        // For non-CUSTOM periods show the API-resolved dates in the pill so
        // users always see the actual range (e.g. "2026-04-25 • 2026-05-24").
        displayStart={period?.startDate ?? null}
        displayEnd={period?.endDate ?? null}
        onPeriodTypeChange={onPeriodTypeChange}
        onMonthChange={onMonthChange}
        onCustomDateChange={onCustomDateChange}
        align="end"
      />
    </div>
  );
}
