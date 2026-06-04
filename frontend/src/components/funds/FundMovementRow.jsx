'use client';
import React from 'react';
import { ArrowDownLeft, ArrowUpRight } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { formatCurrency, formatDate } from '@/utils/helpers';

/**
 * A single fund movement row.
 *   DEPOSIT    – money added to the fund   → emerald, "+", incoming arrow
 *   WITHDRAWAL – money removed from the fund → rose,   "−", outgoing arrow
 *
 * Only the counterparty (normal) wallet name is shown — never the fund wallet.
 */
export default function FundMovementRow({ movement }) {
  const t = useTranslations('funds');
  const { lang } = useLanguage();

  const isDeposit = movement.type === 'DEPOSIT';
  const Icon = isDeposit ? ArrowDownLeft : ArrowUpRight;
  const amount = Number(movement.amount) || 0;

  return (
    <div className="flex items-center gap-3 px-4 py-3">
      {/* Direction icon */}
      <div
        className={`w-8 h-8 shrink-0 rounded-[10px] border flex items-center justify-center ${
          isDeposit
            ? 'bg-emerald-400/10 border-emerald-400/20 text-emerald-400'
            : 'bg-rose-400/10 border-rose-400/20 text-rose-300'
        }`}
      >
        <Icon className="w-4 h-4" />
      </div>

      {/* Type + counterparty + notes */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-[13px] font-semibold text-white/85">
            {isDeposit ? t('movementDeposit') : t('movementWithdrawal')}
          </span>
          <span className="text-[12px] text-white/35 truncate">
            · {movement.counterpartyWalletName}
          </span>
        </div>
        {movement.notes && (
          <p className="text-[12px] text-white/35 truncate mt-0.5">{movement.notes}</p>
        )}
      </div>

      {/* Amount + date */}
      <div className="text-right shrink-0">
        <p
          className={`text-[14px] font-bold font-mono tracking-[-0.3px] ${
            isDeposit ? 'text-emerald-400' : 'text-rose-300'
          }`}
        >
          {isDeposit ? '+' : '−'}{formatCurrency(amount, lang)}
        </p>
        <p className="text-[11px] text-white/30 mt-0.5">{formatDate(movement.date, lang)}</p>
      </div>
    </div>
  );
}
