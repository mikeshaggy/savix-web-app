'use client';
import React, { useState } from 'react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';

const formatDateForInput = (dateString) => {
  if (!dateString) return '';
  const date = new Date(dateString);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

export default function DashboardHeader({ 
  period, 
  periodType,
  currentBalance,
  onPeriodTypeChange,
  onCustomDateChange
}) {
  const t = useTranslations();
  const [isEditingDates, setIsEditingDates] = useState(false);
  const [tempStartDate, setTempStartDate] = useState('');
  const [tempEndDate, setTempEndDate] = useState('');

  const handleStartEditing = () => {
    setIsEditingDates(true);
    setTempStartDate(formatDateForInput(period?.startDate) || '');
    setTempEndDate(formatDateForInput(period?.endDate) || '');
  };

  const handleApplyDates = () => {
    if (tempStartDate && tempEndDate) {
      onCustomDateChange(tempStartDate, tempEndDate);
      setIsEditingDates(false);
    }
  };

  const handleCancel = () => {
    setIsEditingDates(false);
    setTempStartDate('');
    setTempEndDate('');
  };

  const periodButtons = [
    { value: 'PAY_CYCLE', label: t('dashboard.payCycle') },
    { value: 'LAST_PAY_CYCLE', label: t('dashboard.lastCycle') },
    { value: 'CUSTOM', label: t('dashboard.custom') }
  ];

  return (
    <div className="grid grid-cols-[1fr_auto] gap-5 items-end mb-6"
         style={{ animation: 'fadeUp 0.4s ease both' }}>
      {/* Left: Balance */}
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

      {/* Right: Period picker + Cycle tabs */}
      <div className="flex flex-col items-end gap-2">
        {/* Period date picker */}
        {!isEditingDates ? (
          <div 
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-white/[0.055] bg-[#0e0e1c] text-xs cursor-pointer transition-all hover:border-purple-500/30 hover:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]"
            onClick={handleStartEditing}
          >
            <span className="text-white/50">{t('dashboard.period')}</span>
            <span className="font-semibold text-white">{period?.startDate || 'N/A'}</span>
            <span className="w-[3px] h-[3px] rounded-full bg-white/25" />
            <span className="font-semibold text-white">{period?.endDate || 'N/A'}</span>
          </div>
        ) : (
          <div className="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-purple-500/30 bg-[#0e0e1c] shadow-[0_0_0_3px_rgba(124,58,237,0.1)]">
            <input 
              type="date" 
              className="bg-transparent border-0 text-white outline-none text-xs font-medium [color-scheme:dark]"
              value={tempStartDate}
              onChange={(e) => setTempStartDate(e.target.value)}
            />
            <span className="w-[3px] h-[3px] rounded-full bg-white/25" />
            <input 
              type="date" 
              className="bg-transparent border-0 text-white outline-none text-xs font-medium [color-scheme:dark]"
              value={tempEndDate}
              onChange={(e) => setTempEndDate(e.target.value)}
            />
            <button 
              onClick={handleApplyDates}
              className="ml-1 px-2.5 py-1 text-[11px] font-semibold bg-purple-600 text-white rounded-lg hover:bg-purple-500 transition-colors"
            >
              {t('dashboard.apply')}
            </button>
            <button 
              onClick={handleCancel}
              className="px-2.5 py-1 text-[11px] font-semibold text-white/40 hover:text-white/70 transition-colors"
            >
              {t('common.cancel')}
            </button>
          </div>
        )}

        {/* Cycle tabs */}
        <div className="flex items-center bg-[#0e0e1c] border border-white/[0.055] rounded-xl p-1 gap-0.5">
          {periodButtons.map((btn) => {
            const isActive = periodType === btn.value;
            const isCustom = btn.value === 'CUSTOM';
            const canClick = !isCustom;
            
            return (
              <button 
                key={btn.value}
                onClick={() => canClick && onPeriodTypeChange(btn.value)}
                disabled={isCustom && !isActive}
                className={`px-4 py-[7px] rounded-[9px] text-xs font-semibold transition-all whitespace-nowrap border-none font-[inherit] ${
                  isActive
                    ? 'bg-purple-600 text-white shadow-[0_2px_12px_rgba(124,58,237,0.3)]'
                    : 'bg-transparent text-white/25'
                } ${
                  canClick ? 'cursor-pointer hover:text-white/50' : 'cursor-not-allowed'
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
