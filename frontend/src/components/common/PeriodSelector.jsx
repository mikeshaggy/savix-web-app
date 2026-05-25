'use client';
import React, { useState } from 'react';
import { useTranslations } from 'next-intl';

/**
 * Shared period selector used by both Dashboard and Analytics pages.
 *
 * Layout: period date pill stacked **above** the segmented Pay Cycle / Last
 * Cycle / Month / Custom tabs — matching the Dashboard design exactly.
 *
 * @param {Object} props
 * @param {string}      props.periodType           - 'PAY_CYCLE' | 'LAST_PAY_CYCLE' | 'MONTHLY' | 'CUSTOM'
 * @param {string}      props.selectedMonth        - 'YYYY-MM' value used when periodType === 'MONTHLY'
 * @param {string|null} props.startDate            - CUSTOM start date from URL params / component state
 * @param {string|null} props.endDate              - CUSTOM end date from URL params / component state
 * @param {string|null} [props.displayStart]       - Resolved start to show in the pill for non-CUSTOM types
 *                                                   (Dashboard passes the API-resolved period start).
 *                                                   Omit for Analytics (pill shows '—' for non-CUSTOM).
 * @param {string|null} [props.displayEnd]         - Same for end date.
 * @param {function}    props.onPeriodTypeChange   - Called with the new period type string
 * @param {function}    props.onMonthChange        - Called with new 'YYYY-MM' string
 * @param {function}    props.onCustomDateChange   - Called with (startDate, endDate) strings
 * @param {'start'|'end'} [props.align='end']      - Alignment of the stacked elements
 */
export default function PeriodSelector({
  periodType,
  selectedMonth,
  startDate,
  endDate,
  displayStart,
  displayEnd,
  onPeriodTypeChange,
  onMonthChange,
  onCustomDateChange,
  align = 'end',
}) {
  const t  = useTranslations('analytics');
  const tc = useTranslations('common');

  const [isEditing, setIsEditing] = useState(false);
  const [tempStart, setTempStart] = useState('');
  const [tempEnd,   setTempEnd]   = useState('');

  // ── Period type buttons ──────────────────────────────────────────────────
  const periodButtons = [
    { value: 'PAY_CYCLE',      label: t('payCycle') },
    { value: 'LAST_PAY_CYCLE', label: t('lastCycle') },
    { value: 'MONTHLY',        label: t('month') },
    { value: 'CUSTOM',         label: t('custom') },
  ];

  // ── Pill display values ───────────────────────────────────────────────────
  // displayStart/End props let Dashboard pass resolved API dates for non-CUSTOM modes.
  // Analytics doesn't pass them; non-CUSTOM falls back to '—'.
  const pillStart = displayStart ?? (periodType === 'CUSTOM' ? startDate : null) ?? '—';
  const pillEnd   = displayEnd   ?? (periodType === 'CUSTOM' ? endDate   : null) ?? '—';

  // ── Handlers ──────────────────────────────────────────────────────────────
  const handleStartEditing = () => {
    setIsEditing(true);
    // Pre-fill from resolved/URL dates whichever is available
    setTempStart(startDate || displayStart || '');
    setTempEnd(endDate     || displayEnd   || '');
  };

  const handleApply = () => {
    if (tempStart && tempEnd) {
      onCustomDateChange(tempStart, tempEnd);
      setIsEditing(false);
    }
  };

  const handleCancel = () => {
    setIsEditing(false);
    setTempStart('');
    setTempEnd('');
  };

  const handleButtonClick = (value) => {
    if (value === 'CUSTOM') return; // CUSTOM is activated only via the date editor
    if (isEditing) setIsEditing(false);
    onPeriodTypeChange(value);
  };

  const alignClass = align === 'end' ? 'items-end' : 'items-start';

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className={`flex flex-col ${alignClass} gap-2 flex-shrink-0`}>

      {/* ── Date pill / MONTHLY input / custom editor ────────────────────── */}
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

      ) : !isEditing ? (
        <div
          className="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-white/[0.055] bg-[#0e0e1c] text-xs cursor-pointer transition-all hover:border-purple-500/30 hover:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]"
          onClick={handleStartEditing}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && handleStartEditing()}
        >
          <span className="text-white/50">{t('period')}</span>
          <span className="font-semibold text-white">{pillStart}</span>
          <span className="w-[3px] h-[3px] rounded-full bg-white/25" />
          <span className="font-semibold text-white">{pillEnd}</span>
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

      {/* ── Segmented period-type tabs ────────────────────────────────────── */}
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
  );
}
