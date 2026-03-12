'use client';
import React from 'react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';

export default function SummaryCards({ summary }) {
  const t = useTranslations();
  
  if (!summary) return null;

  const segments = [
    {
      key: 'income',
      label: t('dashboard.income'),
      valueField: 'income',
      changeField: 'incomeChange',
      dotColor: '#4ade80',
      valueClass: 'text-green-400',
      floorColor: 'rgba(74,222,128,0.5)'
    },
    {
      key: 'expenses',
      label: t('dashboard.expenses'),
      valueField: 'expenses',
      changeField: 'expensesChange',
      dotColor: '#f87171',
      valueClass: 'text-red-400',
      floorColor: 'rgba(248,113,113,0.5)'
    },
    {
      key: 'saved',
      label: t('dashboard.saved'),
      valueField: 'saved',
      changeField: 'savedChange',
      dotColor: '#c084fc',
      valueClass: '', // uses gradient
      floorColor: 'rgba(192,132,252,0.5)',
      gradient: true
    },
    {
      key: 'savingsRate',
      label: t('dashboard.savingsRate'),
      valueField: 'savingsRate',
      dotColor: '#fbbf24',
      valueClass: 'text-amber-400',
      floorColor: 'rgba(251,191,36,0.45)',
      isPercentage: true
    }
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 rounded-[20px] overflow-hidden border border-white/[0.055] bg-[#0e0e1c] relative"
         style={{ animation: 'fadeUp 0.5s ease both', animationDelay: '0.08s' }}>
      {/* Glow */}
      <div className="absolute inset-0 pointer-events-none"
           style={{ background: 'radial-gradient(ellipse at 15% 50%, rgba(124,58,237,0.1) 0%, transparent 55%)' }} />
      
      {segments.map((seg, index) => {
        const value = summary[seg.valueField];
        const change = seg.changeField ? summary[seg.changeField] : null;
        
        return (
          <div key={seg.key}
               className="relative py-[18px] sm:py-[26px] px-4 sm:px-7 overflow-hidden transition-colors hover:bg-white/[0.02] cursor-default">
            {/* Divider */}
            {index > 0 && (
              <div className="absolute left-0 top-[15%] bottom-[15%] w-px bg-white/[0.055]" />
            )}
            
            {/* Label */}
            <div className="flex items-center gap-[7px] text-[10px] font-bold tracking-[0.14em] uppercase text-white/25 mb-4">
              <span className="w-1.5 h-1.5 rounded-full inline-block"
                    style={{ background: seg.dotColor, boxShadow: `0 0 7px ${seg.dotColor}` }} />
              {seg.label}
            </div>
            
            {/* Value */}
            <div className={`font-mono text-[clamp(22px,2.6vw,30px)] font-medium tracking-[-0.5px] leading-none mb-3 ${seg.gradient ? '' : seg.valueClass}`}
                 style={seg.gradient ? {
                   background: 'linear-gradient(90deg, #c084fc, #e879f9)',
                   WebkitBackgroundClip: 'text',
                   WebkitTextFillColor: 'transparent',
                   backgroundClip: 'text'
                 } : undefined}>
              {seg.isPercentage 
                ? <>{value}<span className="text-[15px] font-light opacity-55 tracking-normal">%</span></>
                : formatCurrency(value)
              }
            </div>
            
            {/* Change */}
            {change ? (
              <div className={`text-[11px] font-medium flex items-center gap-[5px] ${change.isPositive ? 'text-green-400' : 'text-red-400'}`}>
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                  {change.isPositive 
                    ? <polyline points="23 6 13.5 15.5 8.5 10.5 1 18" />
                    : <polyline points="23 18 13.5 8.5 8.5 13.5 1 6" />
                  }
                </svg>
                {t('dashboard.vsLastPeriod', { percentage: Math.abs(change.percentage) })}
              </div>
            ) : seg.isPercentage ? (
              <div className="text-[11px] font-medium text-white/25">{t('dashboard.ofTotalIncome')}</div>
            ) : null}
            
            {/* Floor line */}
            <div className="absolute bottom-0 left-0 right-0 h-0.5 opacity-50"
                 style={{ background: `linear-gradient(90deg, ${seg.floorColor}, transparent)` }} />
          </div>
        );
      })}
    </div>
  );
}
