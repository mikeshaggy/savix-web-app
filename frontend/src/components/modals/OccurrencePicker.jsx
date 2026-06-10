'use client';
import React, { useEffect, useMemo, useState } from 'react';
import { X, Loader2, Link2, AlertCircle, Search } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { formatCurrency, formatDate } from '@/utils/helpers';
import { sortByMatch } from '@/utils/matchSort';
import { fixedPaymentApi } from '@/lib/api';

/**
 * Transaction-first picker: given a draft transaction (amount/date/category) and
 * a wallet, lists unpaid (PENDING/OVERDUE, unlinked) fixed payment occurrences
 * in that wallet, ranked closest-match first. The inverse of LinkTransactionModal.
 *
 * `onSelect(occurrence)` is called with the chosen FixedOccurrenceRowDto. The
 * caller decides what to do with it (attach occurrenceId on create, or call the
 * link endpoint on edit) and may throw to surface a backend error here.
 */
export default function OccurrencePicker({ walletId, draft, onClose, onSelect }) {
  const t = useTranslations();
  const { lang } = useLanguage();

  const [occurrences, setOccurrences] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [selectingId, setSelectingId] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    fixedPaymentApi
      .getTileData(walletId)
      .then((tile) => {
        if (!active) return;
        const unpaid = [...(tile?.overdue ?? []), ...(tile?.upcoming ?? [])].filter(
          (o) =>
            o.transactionId == null &&
            (o.status === 'PENDING' || o.status === 'OVERDUE'),
        );
        setOccurrences(unpaid);
      })
      .catch((err) => {
        if (!active) return;
        setError(err?.message || t('fixedPayments.linkError'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [walletId, t]);

  const ranked = useMemo(() => {
    const target = {
      amount: draft?.amount,
      date: draft?.transactionDate,
      categoryId: draft?.categoryId,
    };
    const q = query.trim().toLowerCase();

    const filtered = occurrences.filter((o) => {
      if (!q) return true;
      return (
        (o.title || '').toLowerCase().includes(q) ||
        (o.categoryName || '').toLowerCase().includes(q)
      );
    });

    return sortByMatch(filtered, target, (o) => ({
      amount: o.expectedAmount,
      date: o.dueDate,
      categoryId: o.categoryId,
    }));
  }, [occurrences, draft, query]);

  const handleSelect = async (occurrence) => {
    setSelectingId(occurrence.occurrenceId);
    setError('');
    try {
      await onSelect(occurrence);
    } catch (err) {
      setError(err?.message || t('fixedPayments.linkError'));
      setSelectingId(null);
    }
  };

  return (
    <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-[60] p-3 sm:p-6">
      <div
        className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[480px] overflow-hidden relative flex flex-col max-h-[85vh]"
        style={{
          boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1)',
          animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both',
        }}
      >
        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />
        <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
          <div className="text-lg font-bold tracking-[-0.3px]">
            {t('transaction.selectRecurring')}
          </div>
          <button
            onClick={onClose}
            aria-label={t('common.close')}
            className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] transition-all"
          >
            <X className="w-3 h-3" />
          </button>
        </div>

        <div className="px-4 sm:px-7 pt-4">
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-white/25 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('transaction.searchRecurring')}
              className="w-full bg-[#131325] border border-white/[0.06] rounded-[10px] pl-9 pr-3 py-2.5 text-[13px] text-white placeholder:text-white/25 outline-none focus:border-purple-400/40 transition-colors"
            />
          </div>
        </div>

        {error && (
          <div className="mx-4 sm:mx-7 mt-3 flex items-start gap-2 rounded-[10px] border border-red-500/30 bg-red-500/[0.08] px-3 py-2.5 text-[12px] text-red-300">
            <AlertCircle className="w-3.5 h-3.5 mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="flex-1 overflow-y-auto dashboard-scroll px-4 sm:px-7 py-4 min-h-[120px]">
          {loading ? (
            <div className="flex items-center justify-center py-10 text-white/30">
              <Loader2 className="w-5 h-5 animate-spin" />
            </div>
          ) : ranked.length === 0 ? (
            <div className="flex items-center justify-center py-10 text-[12px] text-white/25 text-center">
              {t('transaction.noRecurringCandidates')}
            </div>
          ) : (
            <>
              <div className="text-[10px] tracking-[0.06em] uppercase text-white/25 mb-2">
                {t('transaction.recurringCandidateHint')}
              </div>
              <div className="grid gap-2">
                {ranked.map((o) => (
                  <button
                    key={o.occurrenceId}
                    onClick={() => handleSelect(o)}
                    disabled={selectingId != null}
                    className="group flex items-center gap-3 rounded-[12px] border border-white/[0.055] bg-[#131325] px-3.5 py-3 text-left transition-colors hover:bg-[#1a1a2e] hover:border-purple-400/30 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <div className="w-9 h-9 bg-[#1a1a2e] border border-white/[0.06] rounded-[10px] flex items-center justify-center text-[15px] shrink-0">
                      {o.categoryEmoji || '🔁'}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-[13px] font-semibold text-white truncate">{o.title}</div>
                      <div className="text-[11px] text-white/30 mt-0.5 flex items-center gap-1.5">
                        <span>{formatDate(o.dueDate, lang)}</span>
                        {o.categoryName && (
                          <>
                            <span className="text-white/14">·</span>
                            <span>{o.categoryName}</span>
                          </>
                        )}
                        <span
                          className={`text-[8px] px-1.5 py-px rounded uppercase tracking-[0.05em] ${
                            o.status === 'OVERDUE'
                              ? 'bg-red-500/[0.12] text-red-400 border border-red-500/25'
                              : 'bg-purple-500/[0.12] text-purple-400 border border-purple-500/25'
                          }`}
                        >
                          {o.status}
                        </span>
                      </div>
                    </div>
                    <div className="text-right shrink-0 flex items-center gap-2">
                      <span className="font-mono text-[14px] font-bold text-purple-300">
                        {formatCurrency(o.expectedAmount, lang)}
                      </span>
                      {selectingId === o.occurrenceId ? (
                        <Loader2 className="w-4 h-4 animate-spin text-purple-300" />
                      ) : (
                        <Link2 className="w-4 h-4 text-white/20 group-hover:text-purple-300 transition-colors" />
                      )}
                    </div>
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
