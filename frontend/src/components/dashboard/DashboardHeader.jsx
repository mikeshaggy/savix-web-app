'use client';
import React from 'react';
import { useTranslations } from 'next-intl';
import PeriodSelector from '@/components/common/PeriodSelector';

function formatShortDate(dateStr) {
  if (!dateStr) return null;
  try {
    return new Date(dateStr).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  } catch {
    return dateStr;
  }
}

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

  // The pill shows the resolved period (for PAY_CYCLE the whole cycle, e.g. Sep 9 – Oct 8) plus a note:
  // "next salary expected Oct 9" for the open cycle, "reporting" for MONTHLY / CUSTOM / LAST_PAY_CYCLE.
  const displayEnd = period?.endDate ?? period?.cutoffDate ?? period?.asOfDate ?? null;
  const pillNote = period?.reporting
    ? t('dashboard.reportingPeriod')
    : period?.type === 'PAY_CYCLE' && period?.expectedPaydayDate
      ? t('dashboard.nextSalaryExpected', { date: formatShortDate(period.expectedPaydayDate) })
      : null;

  return (
    <div
      className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between mb-6"
      style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}
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
        pillNote={pillNote}
        onPeriodTypeChange={onPeriodTypeChange}
        onMonthChange={onMonthChange}
        onCustomDateChange={onCustomDateChange}
        align="end"
      />
    </div>
  );
}
