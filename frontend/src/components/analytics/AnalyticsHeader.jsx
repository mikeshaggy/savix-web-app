'use client';
import React, { useState } from 'react';
import { useTranslations } from 'next-intl';

const formatDateForInput = (dateString) => {
  if (!dateString) return '';
  const d = new Date(dateString);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
};

export default function AnalyticsHeader({
  period,
  periodType,
  selectedMonth,
  onPeriodTypeChange,
  onMonthChange,
  onCustomDateChange,
}) {
  const t = useTranslations('analytics');
  const tc = useTranslations('common');
  const [isEditingDates, setIsEditingDates] = useState(false);
  const [tempStart, setTempStart] = useState('');
  const [tempEnd, setTempEnd] = useState('');

  const periodButtons = [
    { value: 'PAY_CYCLE', label: t('payCycle') },
    { value: 'LAST_PAY_CYCLE', label: t('lastCycle') },
    { value: 'MONTHLY', label: t('month') },
    { value: 'CUSTOM', label: t('custom') },
  ];

  const handleStartEditing = () => {
    setIsEditingDates(true);
    setTempStart(formatDateForInput(period?.startDate) || '');
    setTempEnd(formatDateForInput(period?.endDate) || '');
  };

  const handleApply = () => {
    if (tempStart && tempEnd) {
      onCustomDateChange(tempStart, tempEnd);
      setIsEditingDates(false);
    }
  };

  const handleCancel = () => {
    setIsEditingDates(false);
    setTempStart('');
    setTempEnd('');
  };

  const handleButtonClick = (value) => {
    if (value === 'CUSTOM') return;
    if (isEditingDates) setIsEditingDates(false);
    onPeriodTypeChange(value);
  };

  return (
    <div
      className="flex flex-col gap-4 md:grid md:grid-cols-[1fr_auto] md:gap-5 md:items-end mb-6"
      style={{ animation: 'fadeUp 0.4s ease both' }}
    >
      {/* Left: title + subtitle */}
      <div>
        <h1 className="text-2xl font-semibold text-white">{t('title')}</h1>
        <p className="text-sm text-white/40 mt-0.5">{t('subtitle')}</p>
      </div>

      {/* Right: period controls */}
      <div className="flex flex-col items-start md:items-end gap-2">
        {/* Period date display / editor */}
        {periodType === 'MONTHLY' ? (
          <div className="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-white/[0.055] bg-[#0e0e1c]">
            <span className="text-white/50 text-xs">{t('period')}</span>
            <input
              type="month"
              value={selectedMonth}
              onChange={(e) => onMonthChange(e.target.value)}
              className="bg-transparent border-0 text-white outline-none text-xs font-medium [color-scheme:dark]"
            />
          </div>
        ) : !isEditingDates ? (
          <div
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-white/[0.055] bg-[#0e0e1c] text-xs cursor-pointer transition-all hover:border-purple-500/30 hover:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]"
            onClick={handleStartEditing}
          >
            <span className="text-white/50">{t('period')}</span>
            <span className="font-semibold text-white">
              {period?.startDate || '—'}
            </span>
            <span className="w-[3px] h-[3px] rounded-full bg-white/25" />
            <span className="font-semibold text-white">
              {period?.endDate || '—'}
            </span>
          </div>
        ) : (
          <div className="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-purple-500/30 bg-[#0e0e1c] shadow-[0_0_0_3px_rgba(124,58,237,0.1)]">
            <input
              type="date"
              className="bg-transparent border-0 text-white outline-none text-xs font-medium [color-scheme:dark]"
              value={tempStart}
              onChange={(e) => setTempStart(e.target.value)}
            />
            <span className="w-[3px] h-[3px] rounded-full bg-white/25" />
            <input
              type="date"
              className="bg-transparent border-0 text-white outline-none text-xs font-medium [color-scheme:dark]"
              value={tempEnd}
              onChange={(e) => setTempEnd(e.target.value)}
            />
            <button
              onClick={handleApply}
              className="ml-1 px-2.5 py-1 text-[11px] font-semibold bg-purple-600 text-white rounded-lg hover:bg-purple-500 transition-colors"
            >
              {t('apply')}
            </button>
            <button
              onClick={handleCancel}
              className="px-2.5 py-1 text-[11px] font-semibold text-white/40 hover:text-white/70 transition-colors"
            >
              {tc('cancel')}
            </button>
          </div>
        )}

        {/* Period type tabs */}
        <div className="flex items-center bg-[#0e0e1c] border border-white/[0.055] rounded-xl p-1 gap-0.5">
          {periodButtons.map((btn) => {
            const isActive = periodType === btn.value;
            const isCustom = btn.value === 'CUSTOM';
            return (
              <button
                key={btn.value}
                onClick={() => handleButtonClick(btn.value)}
                disabled={isCustom && !isActive}
                className={`px-4 py-[7px] rounded-[9px] text-xs font-semibold transition-all whitespace-nowrap border-none font-[inherit] ${
                  isActive
                    ? 'bg-purple-600 text-white shadow-[0_2px_12px_rgba(124,58,237,0.3)]'
                    : 'bg-transparent text-white/25'
                } ${
                  isCustom && !isActive ? 'cursor-not-allowed' : 'cursor-pointer hover:text-white/50'
                }`}
              >
                {btn.label}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
