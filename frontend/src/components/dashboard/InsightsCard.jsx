'use client';
import React from 'react';
import { formatCurrency } from '@/utils/helpers';
import { useTranslations } from 'next-intl';
import { CheckCircle } from 'lucide-react';

const SEVERITY_CFG = {
  INFO: {
    leftBorder: '2px solid rgba(139,92,246,0.5)',
    bgClass: 'bg-violet-500/[0.05]',
    dotClass: 'bg-violet-400',
    titleClass: 'text-violet-300',
    amountClass: 'text-violet-400',
  },
  WARN: {
    leftBorder: '2px solid rgba(245,158,11,0.6)',
    bgClass: 'bg-amber-500/[0.07]',
    dotClass: 'bg-amber-400',
    titleClass: 'text-amber-300',
    amountClass: 'text-amber-400',
  },
  ALERT: {
    leftBorder: '2px solid rgba(244,63,94,0.65)',
    bgClass: 'bg-rose-500/[0.08]',
    dotClass: 'bg-rose-400 animate-pulse',
    titleClass: 'text-rose-300',
    amountClass: 'text-rose-400',
  },
};

export default function InsightsCard({ insights }) {
  const t = useTranslations();
  const items = insights ?? [];

  return (
    <div
      className="bg-[#0e0e1c] border border-white/[0.07] rounded-[18px] overflow-hidden flex flex-col"
      style={{ animation: 'fadeUp 0.5s ease both', animationDelay: '0.18s' }}
    >
      {/* Header */}
      <div className="px-5 py-4 border-b border-white/[0.07] shrink-0">
        <div className="text-[15px] font-bold tracking-[-0.2px] text-white">{t('dashboard.insights')}</div>
        <div className="text-[12px] text-white/35 mt-0.5">{t('dashboard.insightsSubtitle')}</div>
      </div>

      {/* Scrollable list — flex-1 fills the remaining card height set by the grid row */}
      <div className="flex flex-col flex-1 min-h-0 overflow-y-auto dashboard-scroll">
        {items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-10 px-6 gap-3">
            <CheckCircle className="w-9 h-9 text-emerald-400/50" />
            <div className="text-[12px] text-white/40 text-center leading-relaxed">
              {t('dashboard.noInsights')}
            </div>
          </div>
        ) : (
          items.map((insight, idx) => {
            const cfg = SEVERITY_CFG[insight.severity] ?? SEVERITY_CFG.INFO;
            const hasAmount = insight.relatedAmount != null;
            return (
              <div
                key={idx}
                className={`flex items-start gap-3 px-5 py-4 border-b border-white/[0.04] last:border-b-0 ${cfg.bgClass}`}
                style={{ borderLeft: cfg.leftBorder }}
              >
                <span className={`w-1.5 h-1.5 rounded-full mt-[5px] shrink-0 ${cfg.dotClass}`} />
                <div className="flex-1 min-w-0">
                  <div className={`text-[12px] font-semibold leading-snug ${cfg.titleClass}`}>
                    {insight.title}
                  </div>
                  {insight.description && (
                    <div className="text-[11px] text-white/55 mt-1 leading-relaxed">
                      {insight.description}
                    </div>
                  )}
                  {hasAmount && (
                    <div className={`text-[11px] font-semibold mt-1 font-mono ${cfg.amountClass}`}>
                      {formatCurrency(insight.relatedAmount)}
                    </div>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
