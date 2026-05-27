'use client';
import React from 'react';
import { useTranslations } from 'next-intl';
import PeriodSelector from '@/components/common/PeriodSelector';

export default function DashboardHeader({
  walletName,
  period,
  periodType,
  selectedMonth,
  onPeriodTypeChange,
  onMonthChange,
  onCustomDateChange,
  customStartDate,
  customEndDate,
}) {
  const t = useTranslations();

  // Show the active data window in the pill, not the full future cycle end.
  // period.cutoffDate / period.asOfDate is the actual cutoff date used for data;
  // period.endDate is the billing end which may be in the future.
  const displayEnd = period?.cutoffDate ?? period?.asOfDate ?? period?.endDate ?? null;

  return (
    <div
      className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between mb-6"
      style={{ animation: 'fadeUp 0.4s ease both' }}
    >
      <div>
        <div className="text-[27px] font-bold tracking-[-0.5px] text-white leading-tight">
          {t('dashboard.pageTitle')}
        </div>
        {walletName && (
          <div className="text-[13px] text-white/40 mt-0.5 leading-tight">
            {walletName}
          </div>
        )}
      </div>

      <PeriodSelector
        periodType={periodType}
        selectedMonth={selectedMonth}
        startDate={customStartDate ?? null}
        endDate={customEndDate ?? null}
        displayStart={period?.startDate ?? null}
        displayEnd={displayEnd}
        onPeriodTypeChange={onPeriodTypeChange}
        onMonthChange={onMonthChange}
        onCustomDateChange={onCustomDateChange}
        align="end"
      />
    </div>
  );
}
