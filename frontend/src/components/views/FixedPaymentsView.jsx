'use client';
import React, { useState, useMemo } from 'react';
import {
  Plus, RefreshCw, Loader2, AlertCircle, Check, X, ArrowRight,
  Edit3, Trash2, Calendar, Clock, Eye, EyeOff,
} from 'lucide-react';
import { formatCurrency, formatDate } from '@/utils/helpers';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { useFixedPaymentsTile, useFixedPayments } from '@/hooks/useFixedPayments';
import { useCategories } from '@/hooks/useApi';
import { useWallets } from '@/contexts/WalletContext';
import { transactionApi } from '@/lib/api';
import FixedPaymentModal from '@/components/modals/FixedPaymentModal';
import TransactionModal from '@/components/modals/TransactionModal';

const TABS = ['overview', 'upcoming', 'overdue', 'history'];

export default function FixedPaymentsView() {
  const t = useTranslations();
  const { lang } = useLanguage();

  const { currentWallet } = useWallets();
  const walletId = currentWallet?.id;

  const { tileData, loading: tileLoading, refetch: refetchTile } = useFixedPaymentsTile(walletId);
  const {
    fixedPayments, loading: listLoading,
    createFixedPayment, updateFixedPayment, deactivateFixedPayment,
    refetch: refetchList,
  } = useFixedPayments(walletId);
  const { categories } = useCategories();

  const [activeTab, setActiveTab] = useState('overview');
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [editingPayment, setEditingPayment] = useState(null);
  const [deactivatingPayment, setDeactivatingPayment] = useState(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showInactive, setShowInactive] = useState(false);

  const [markPaidOccurrence, setMarkPaidOccurrence] = useState(null);
  const [showTransactionModal, setShowTransactionModal] = useState(false);
  const [transactionPrefill, setTransactionPrefill] = useState(null);

  const [filterCategory, setFilterCategory] = useState('all');

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      await Promise.all([refetchTile(), refetchList()]);
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleCreatePayment = async (data) => {
    await createFixedPayment(data);
    refetchTile();
  };

  const handleUpdatePayment = async (data) => {
    if (!editingPayment) return;
    await updateFixedPayment(editingPayment.id, data);
    refetchTile();
  };

  const handleDeactivate = async () => {
    if (!deactivatingPayment) return;
    try {
      await deactivateFixedPayment(deactivatingPayment.id);
      setDeactivatingPayment(null);
      refetchTile();
    } catch (err) {
      console.error('Failed to deactivate:', err);
    }
  };

  const handleMarkPaidClick = (occurrence) => {
    setMarkPaidOccurrence(occurrence);
    setTransactionPrefill({
      title: occurrence.title,
      amount: occurrence.expectedAmount,
      categoryId: occurrence.categoryId || null,
      walletId: occurrence.walletId || null,
      notes: null,
      occurrenceId: occurrence.occurrenceId,
    });
  };

  const handleOpenTransactionModal = () => {
    setShowTransactionModal(true);
  };

  const handleTransactionSave = async (transactionData) => {
    await transactionApi.createTransaction(transactionData);
    refetchTile();
    refetchList();
  };

  const handleCloseMarkPaid = () => {
    setShowTransactionModal(false);
    setMarkPaidOccurrence(null);
    setTransactionPrefill(null);
  };

  const allOccurrences = useMemo(() => {
    if (!tileData) return [];
    const overdue = (tileData.overdue || []).map(o => ({ ...o, _section: 'overdue' }));
    const upcoming = (tileData.upcoming || []).map(o => ({ ...o, _section: 'upcoming' }));
    const paid = (tileData.paid || []).map(o => ({ ...o, _section: 'paid' }));
    return [...overdue, ...upcoming, ...paid];
  }, [tileData]);

  const filteredOccurrences = useMemo(() => {
    let list = allOccurrences;
    if (filterCategory !== 'all') {
      list = list.filter(o => String(o.categoryId) === filterCategory);
    }
    return list;
  }, [allOccurrences, filterCategory]);

  const tabOccurrences = useMemo(() => {
    if (activeTab === 'upcoming') return filteredOccurrences.filter(o => o.status === 'PENDING');
    if (activeTab === 'overdue') return filteredOccurrences.filter(o => o.status === 'OVERDUE');
    if (activeTab === 'history') return filteredOccurrences.filter(o => o.status === 'PAID' || o.status === 'SKIPPED');
    return filteredOccurrences;
  }, [activeTab, filteredOccurrences]);

  const activeTemplates = useMemo(() => {
    return (fixedPayments || []).filter(fp => !fp.activeTo || new Date(fp.activeTo) >= new Date());
  }, [fixedPayments]);

  const inactiveTemplates = useMemo(() => {
    return (fixedPayments || []).filter(fp => fp.activeTo && new Date(fp.activeTo) < new Date());
  }, [fixedPayments]);

  const displayTemplates = showInactive ? [...activeTemplates, ...inactiveTemplates] : activeTemplates;

  const summary = tileData?.summary;
  const progress = tileData?.progress;
  const riskIndicator = tileData?.riskIndicator;
  const balanceAfterFixed = tileData?.balanceAfterFixed;

  const loading = tileLoading || listLoading;

  const getDaysChipClass = (daysDelta) => {
    if (daysDelta < 0) return 'text-red-400 bg-red-500/10 border border-red-500/20';
    if (daysDelta <= 7) return 'text-amber-400 bg-amber-500/10 border border-amber-500/20';
    return 'text-white/25 bg-white/[0.04] border border-white/[0.06]';
  };

  const getDaysLabel = (daysDelta) => {
    if (daysDelta < 0) return t('fixedPayments.daysLate', { days: Math.abs(daysDelta) });
    if (daysDelta === 0) return t('fixedPayments.dueToday');
    return t('fixedPayments.daysLeft', { days: daysDelta });
  };

  const getCycleLabel = (cycle) => {
    const key = `fixedPayments.${cycle?.toLowerCase() || 'monthly'}`;
    try { return t(key); } catch { return cycle; }
  };

  if (loading && !tileData && fixedPayments.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="w-6 h-6 text-purple-400 animate-spin mb-3" />
        <div className="text-[12px] text-white/25">{t('common.loading')}</div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-[18px]">
      {/* Page header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="text-xl sm:text-[26px] font-bold tracking-[-0.4px]">
            {t('fixedPayments.viewTitle')}
          </div>
          <div className="text-[13px] sm:text-[14px] text-white/22 mt-[3px]">
            {t('fixedPayments.viewSubtitle')}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="w-[34px] h-[34px] rounded-[8px] border border-white/[0.055] bg-transparent flex items-center justify-center cursor-pointer text-[#6b6b8a] transition-all hover:border-white/[0.12] hover:text-[#9898b8] disabled:opacity-50"
            title={t('common.refresh')}
          >
            <RefreshCw className={`w-[14px] h-[14px] ${isRefreshing ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={() => {
              setEditingPayment(null);
              setShowPaymentModal(true);
            }}
            className="bg-gradient-to-br from-[#7c3aed] to-[#a855f7] border-none rounded-[8px] px-4 py-2 text-white text-[13.5px] font-medium cursor-pointer flex items-center gap-[6px] shadow-[0_4px_16px_rgba(124,58,237,0.25)] transition-all hover:opacity-90 hover:-translate-y-[1px]"
          >
            <Plus className="w-[13px] h-[13px]" strokeWidth={2.5} />
            {t('fixedPayments.addPayment')}
          </button>
        </div>
      </div>

      {/* Summary strip — 5 cards */}
      {tileData && (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-px bg-white/[0.06] rounded-[14px] overflow-hidden border border-white/[0.06]">
          {/* Planned */}
          <div className="bg-[#13131f] p-4 relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-[#94a3b8] opacity-80" />
            <div className="text-[9px] tracking-[0.1em] uppercase text-white/25 mb-1.5">{t('fixedPayments.planned')}</div>
            <div className="font-bold text-[18px] tracking-[-0.01em] leading-none text-white">
              {formatCurrency(summary?.plannedAmount ?? 0, lang)}
            </div>
            <div className="text-[10px] text-white/25 mt-1.5">
              {t('fixedPayments.countItems', { count: summary?.plannedCount ?? 0 })}
            </div>
          </div>
          {/* Paid */}
          <div className="bg-[#13131f] p-4 relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-green-400 opacity-80" />
            <div className="text-[9px] tracking-[0.1em] uppercase text-white/25 mb-1.5">{t('fixedPayments.paid')}</div>
            <div className="font-bold text-[18px] tracking-[-0.01em] leading-none text-green-400">
              {formatCurrency(summary?.paidAmount ?? 0, lang)}
            </div>
            <div className="text-[10px] text-white/25 mt-1.5">
              {t('fixedPayments.countItems', { count: summary?.paidCount ?? 0 })}
            </div>
          </div>
          {/* Remaining */}
          <div className="bg-[#13131f] p-4 relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-[#7c6af7] opacity-80" />
            <div className="text-[9px] tracking-[0.1em] uppercase text-white/25 mb-1.5">{t('fixedPayments.remaining')}</div>
            <div className="font-bold text-[18px] tracking-[-0.01em] leading-none text-purple-400">
              {formatCurrency(summary?.remainingAmount ?? 0, lang)}
            </div>
            <div className="text-[10px] text-white/25 mt-1.5">
              {t('fixedPayments.countItems', { count: summary?.remainingCount ?? 0 })}
            </div>
          </div>
          {/* Balance After */}
          <div className="bg-[#13131f] p-4 relative">
            <div className={`absolute top-0 left-0 right-0 h-[2px] opacity-80 ${balanceAfterFixed < 0 ? 'bg-red-400' : 'bg-amber-400'}`} />
            <div className="text-[9px] tracking-[0.1em] uppercase text-white/25 mb-1.5">{t('fixedPayments.balanceAfter')}</div>
            <div className={`font-bold text-[18px] tracking-[-0.01em] leading-none ${balanceAfterFixed < 0 ? 'text-red-400' : 'text-amber-400'}`}>
              {formatCurrency(balanceAfterFixed ?? 0, lang)}
            </div>
            <div className="text-[10px] text-white/25 mt-1.5">{t('fixedPayments.afterAllFixed')}</div>
          </div>
          {/* Progress */}
          <div className="bg-[#13131f] p-4 relative col-span-2 md:col-span-1">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-[#7c3aed] to-[#a855f7] opacity-80" />
            <div className="text-[9px] tracking-[0.1em] uppercase text-white/25 mb-1.5">{t('fixedPayments.progressLabel')}</div>
            <div className="font-bold text-[18px] tracking-[-0.01em] leading-none text-purple-400">
              {Math.round(progress?.paidPct ?? 0)}%
            </div>
            <div className="flex items-center gap-2 mt-2">
              <div className="flex-1 h-[3px] bg-white/[0.06] rounded-full overflow-hidden">
                <div
                  className="h-full rounded-full"
                  style={{ width: `${progress?.paidPct ?? 0}%`, background: 'linear-gradient(90deg, #7c3aed, #a855f7)' }}
                />
              </div>
              <span className="text-[9px] text-white/25">{progress?.paidCount ?? 0}/{progress?.totalCount ?? 0}</span>
            </div>
          </div>
        </div>
      )}

      {/* Risk banner */}
      {riskIndicator?.atRisk && (
        <div className="flex items-center gap-2.5 px-5 py-3 bg-red-500/[0.07] border border-red-500/[0.15] rounded-[14px] text-[13px] text-red-400">
          <div className="w-2 h-2 rounded-full bg-red-400 animate-pulse shrink-0" />
          <span className="font-medium">
            {t('fixedPayments.riskWarning', { amount: formatCurrency(riskIndicator.shortfallAmount, lang) })}
          </span>
        </div>
      )}

      {/* Templates section */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <div className="font-mono text-[11px] uppercase tracking-[1.5px] text-[#6b6b8a]">
            {t('fixedPayments.templates')} ({activeTemplates.length})
          </div>
          {inactiveTemplates.length > 0 && (
            <button
              onClick={() => setShowInactive(!showInactive)}
              className="flex items-center gap-1.5 text-[11px] text-[#6b6b8a] hover:text-white/60 transition-colors cursor-pointer border-none bg-transparent"
            >
              {showInactive ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
              {showInactive ? t('fixedPayments.hideInactive') : t('fixedPayments.showInactive', { count: inactiveTemplates.length })}
            </button>
          )}
        </div>

        <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-[14px]">
          {displayTemplates.map((fp) => {
            const isInactive = fp.activeTo && new Date(fp.activeTo) < new Date();
            const cat = (categories || []).find(c => c.id === fp.categoryId);
            return (
              <div
                key={fp.id}
                className={`group relative bg-[#13131f] border rounded-[14px] p-5 transition-all overflow-hidden ${
                  isInactive
                    ? 'border-white/[0.03] opacity-50'
                    : 'border-white/[0.06] hover:border-white/[0.12] hover:bg-[#1a1a2a] hover:-translate-y-[2px] hover:shadow-[0_8px_32px_rgba(0,0,0,0.3)]'
                }`}
              >
                {/* Top line */}
                <div className={`absolute top-0 left-0 right-0 h-[2px] ${
                  isInactive ? 'bg-white/10' : 'bg-gradient-to-r from-[#7c3aed] to-[#a855f7]'
                }`} />

                {/* Card top */}
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-[38px] h-[38px] bg-[#1a1a2a] border border-white/[0.06] rounded-[10px] flex items-center justify-center text-lg">
                      {cat?.emoji || '🔁'}
                    </div>
                    <div>
                      <div className="text-[14px] font-semibold text-white">{fp.title}</div>
                      <div className="text-[11px] text-[#6b6b8a] flex items-center gap-1.5 mt-0.5">
                        <span>{cat?.name || '—'}</span>
                        <span>·</span>
                        <span>{currentWallet?.name || '—'}</span>
                      </div>
                    </div>
                  </div>

                  {/* Actions */}
                  {!isInactive && (
                    <div className="flex gap-1 md:opacity-0 md:group-hover:opacity-100 transition-opacity duration-150">
                      <button
                        onClick={() => {
                          setEditingPayment(fp);
                          setShowPaymentModal(true);
                        }}
                        className="w-7 h-7 rounded-[7px] bg-white/[0.05] border border-white/[0.06] flex items-center justify-center cursor-pointer text-[#6b6b8a] transition-all hover:bg-white/[0.08] hover:text-white"
                        title={t('fixedPayments.editPayment')}
                      >
                        <Edit3 className="w-3 h-3" />
                      </button>
                      <button
                        onClick={() => setDeactivatingPayment(fp)}
                        className="w-7 h-7 rounded-[7px] bg-white/[0.05] border border-white/[0.06] flex items-center justify-center cursor-pointer text-[#6b6b8a] transition-all hover:bg-[rgba(244,63,94,0.15)] hover:text-[#f43f5e] hover:border-[rgba(244,63,94,0.3)]"
                        title={t('fixedPayments.deactivate')}
                      >
                        <Trash2 className="w-3 h-3" />
                      </button>
                    </div>
                  )}
                </div>

                <div className="h-px bg-white/[0.06] mb-3" />

                {/* Details */}
                <div className="flex items-end justify-between">
                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center gap-2 text-[11px] text-[#6b6b8a]">
                      <Calendar className="w-3 h-3" />
                      <span>{getCycleLabel(fp.cycle)}</span>
                      <span>·</span>
                      <span>{t('fixedPayments.anchorShort')}: {fp.anchorDate}</span>
                    </div>
                    {fp.activeTo && (
                      <div className="flex items-center gap-2 text-[11px] text-[#6b6b8a]">
                        <Clock className="w-3 h-3" />
                        <span>{t('fixedPayments.endsOn')}: {fp.activeTo}</span>
                      </div>
                    )}
                  </div>
                  <div className="text-right">
                    <div className="font-mono text-[20px] font-bold text-purple-400 tracking-[-0.3px]">
                      {formatCurrency(fp.amount, lang)}
                    </div>
                    <div className="text-[10px] text-white/25 mt-0.5">/ {getCycleLabel(fp.cycle)}</div>
                  </div>
                </div>

                {/* Inactive badge */}
                {isInactive && (
                  <div className="absolute top-3 right-3 text-[9px] tracking-[0.08em] uppercase px-2 py-0.5 rounded bg-white/[0.04] text-white/25 border border-white/[0.06]">
                    {t('fixedPayments.inactive')}
                  </div>
                )}
              </div>
            );
          })}

          {/* Add template card */}
          <div
            onClick={() => {
              setEditingPayment(null);
              setShowPaymentModal(true);
            }}
            className="bg-transparent border border-dashed border-white/10 rounded-[14px] p-5 cursor-pointer transition-all flex items-center justify-center gap-[10px] min-h-[130px] text-[#6b6b8a] text-[13.5px] hover:border-[rgba(124,58,237,0.3)] hover:bg-[rgba(124,58,237,0.05)] hover:text-[#a855f7] group/add"
          >
            <div className="w-8 h-8 rounded-[8px] bg-white/[0.05] border border-white/[0.08] flex items-center justify-center transition-all group-hover/add:bg-[rgba(124,58,237,0.2)] group-hover/add:border-[rgba(124,58,237,0.3)]">
              <Plus className="w-[15px] h-[15px]" strokeWidth={2.5} />
            </div>
            {t('fixedPayments.addPayment')}
          </div>
        </div>
      </div>

      {/* Tabs + filter bar */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-1 bg-[#131325] rounded-[10px] p-1 border border-white/[0.055]">
          {TABS.map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 rounded-[8px] text-[12px] font-medium transition-all cursor-pointer border-none ${
                activeTab === tab
                  ? 'bg-[#1a1a2e] text-white border border-white/[0.06] shadow-[0_2px_8px_rgba(0,0,0,0.15)]'
                  : 'bg-transparent text-white/25 hover:text-white/50'
              }`}
            >
              {t(`fixedPayments.tab_${tab}`)}
              {tab === 'overdue' && (tileData?.overdue?.length || 0) > 0 && (
                <span className="ml-1.5 px-1.5 py-0.5 rounded-full text-[9px] bg-red-500/20 text-red-400 border border-red-500/25">
                  {tileData.overdue.length}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Filters */}
        <div className="flex items-center gap-2">
          <select
            value={filterCategory}
            onChange={(e) => setFilterCategory(e.target.value)}
            className="bg-[#131325] border border-white/[0.055] rounded-[8px] px-3 py-2 text-[12px] text-white/50 outline-none appearance-none cursor-pointer hover:border-white/[0.12] transition-all"
          >
            <option value="all">{t('fixedPayments.allCategories')}</option>
            {(categories || []).filter(c => c.type === 'EXPENSE').map(c => (
              <option key={c.id} value={c.id}>{c.emoji} {c.name}</option>
            ))}
          </select>

        </div>
      </div>

      {/* Occurrence list */}
      <div className="bg-[#13131f] border border-white/[0.06] rounded-[14px] overflow-hidden">
        {tabOccurrences.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-white/25">
            <Calendar className="w-8 h-8 mb-3 opacity-40" />
            <div className="text-[13px]">{t('fixedPayments.noOccurrences')}</div>
          </div>
        ) : (
          tabOccurrences.map((occ, idx) => {
            const isOverdue = occ.status === 'OVERDUE';
            const isPaid = occ.status === 'PAID';
            const isSkipped = occ.status === 'SKIPPED';
            const canMarkPaid = occ.status === 'PENDING' || isOverdue;

            return (
              <div
                key={occ.occurrenceId || idx}
                className={`group flex items-center gap-3 px-4 md:px-5 py-3 transition-colors border-b border-white/[0.03] last:border-b-0 ${
                  isOverdue ? 'bg-red-500/[0.03] hover:bg-red-500/[0.06]' : 'hover:bg-white/[0.025]'
                } ${isPaid ? 'opacity-60' : ''}`}
                style={{ animation: `fadeUp 0.3s ease both`, animationDelay: `${0.03 * idx}s` }}
              >
                {/* Overdue pulse */}
                {isOverdue && (
                  <div className="w-[5px] h-[5px] rounded-full bg-red-400 shrink-0 animate-pulse" />
                )}

                {/* Icon */}
                <div className="w-[36px] h-[36px] bg-[#1a1a2a] border border-white/[0.06] rounded-[10px] flex items-center justify-center text-[14px] shrink-0">
                  {occ.categoryEmoji || '🔁'}
                </div>

                {/* Info */}
                <div className="flex-1 min-w-0">
                  <div className="text-[13px] font-medium text-white truncate">{occ.title}</div>
                  <div className="text-[10px] text-white/25 flex items-center gap-2 mt-0.5 flex-wrap">
                    <span>{occ.dueDate}</span>
                    <span className="text-[9px] tracking-[0.05em] uppercase bg-white/[0.04] border border-white/[0.06] px-1.5 py-px rounded">
                      {occ.categoryName}
                    </span>
                    <span className={`text-[9px] px-1.5 py-px rounded ${getDaysChipClass(occ.daysDelta)}`}>
                      {getDaysLabel(occ.daysDelta)}
                    </span>
                  </div>
                </div>

                {/* Mark paid */}
                {canMarkPaid && (
                  <button
                    onClick={() => handleMarkPaidClick(occ)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-[8px] text-[10px] tracking-[0.06em] cursor-pointer border border-green-400/30 bg-green-400/[0.07] text-green-400 md:opacity-0 md:group-hover:opacity-100 transition-all hover:bg-green-400/[0.15] shrink-0"
                  >
                    <Check className="w-3 h-3" />
                    {t('fixedPayments.markAsPaid')}
                  </button>
                )}

                {/* Amount + badge */}
                <div className="text-right shrink-0 flex flex-col items-end gap-1">
                  <div className={`font-bold text-[14px] tracking-[-0.01em] ${
                    isOverdue ? 'text-red-400' : isPaid ? 'text-green-400' : 'text-purple-400'
                  }`}>
                    {formatCurrency(occ.expectedAmount, lang)}
                  </div>
                  <span className={`text-[8px] tracking-[0.08em] uppercase px-1.5 py-px rounded ${
                    isOverdue
                      ? 'bg-red-500/[0.12] text-red-400 border border-red-500/25'
                      : isPaid
                        ? 'bg-green-400/10 text-green-400 border border-green-400/20'
                        : isSkipped
                          ? 'bg-white/[0.04] text-white/25 border border-white/[0.06]'
                          : 'bg-purple-500/[0.12] text-purple-400 border border-purple-500/25'
                  }`}>
                    {occ.status}
                  </span>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Modals */}

      {/* Fixed Payment create/edit modal */}
      {showPaymentModal && (
        <FixedPaymentModal
          isOpen={showPaymentModal}
          onClose={() => {
            setShowPaymentModal(false);
            setEditingPayment(null);
          }}
          onSave={editingPayment ? handleUpdatePayment : handleCreatePayment}
          fixedPayment={editingPayment}
        />
      )}

      {/* Deactivate confirmation */}
      {deactivatingPayment && (
        <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-6">
          <div
            className="bg-[#0e0e1c] border border-white/[0.12] rounded-3xl w-full max-w-[420px] overflow-hidden relative"
            style={{
              boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1)',
              animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both',
            }}
          >
            <div className="absolute top-0 left-[10%] right-[10%] h-px bg-gradient-to-r from-transparent via-purple-400 to-transparent opacity-60" />
            <div className="flex items-center justify-between px-7 pt-6 pb-5 border-b border-white/[0.055]">
              <div className="text-lg font-bold tracking-[-0.3px]">{t('fixedPayments.deactivateTitle')}</div>
              <button
                onClick={() => setDeactivatingPayment(null)}
                className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] transition-all"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
            <div className="px-4 sm:px-7 py-5">
              <div className="flex items-center gap-3 bg-[#131325] border border-white/[0.06] rounded-xl p-4 mb-4">
                <div className="w-10 h-10 bg-[rgba(244,63,94,0.15)] rounded-[10px] flex items-center justify-center">
                  <Trash2 className="w-5 h-5 text-[#f43f5e]" />
                </div>
                <div className="flex-1">
                  <div className="text-[14px] font-semibold">{deactivatingPayment.title}</div>
                  <div className="text-[11px] text-white/25 mt-0.5">
                    {formatCurrency(deactivatingPayment.amount, lang)} / {getCycleLabel(deactivatingPayment.cycle)}
                  </div>
                </div>
              </div>
              <p className="text-[12px] text-white/25 leading-relaxed">
                {t('fixedPayments.deactivateInfo')}
              </p>
            </div>
            <div className="flex items-center justify-end gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
              <button
                onClick={() => setDeactivatingPayment(null)}
                className="px-[22px] py-3 bg-[#131325] border border-white/[0.055] rounded-xl text-base font-semibold text-white/50 cursor-pointer hover:border-white/[0.12] hover:text-white transition-all"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={handleDeactivate}
                className="px-5 py-3 bg-[#f43f5e] hover:bg-[#e11d48] rounded-xl text-base font-bold text-white cursor-pointer transition-all"
              >
                {t('fixedPayments.deactivate')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Mark as paid confirmation */}
      {markPaidOccurrence && !showTransactionModal && (
        <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-3 sm:p-6">
          <div
            className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[420px] overflow-hidden relative"
            style={{
              boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1)',
              animation: 'fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both',
            }}
          >
            <div className="absolute top-0 left-[10%] right-[10%] h-px bg-gradient-to-r from-transparent via-purple-400 to-transparent opacity-60" />
            <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
              <div className="text-lg font-bold tracking-[-0.3px]">{t('fixedPayments.markAsPaid')}</div>
              <button
                onClick={handleCloseMarkPaid}
                className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] transition-all"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
            <div className="px-4 sm:px-7 py-5">
              <div className="flex items-center gap-3 bg-[#131325] border border-white/[0.06] rounded-xl p-4">
                <div className="w-10 h-10 bg-[#1a1a2a] border border-white/[0.06] rounded-[10px] flex items-center justify-center text-lg">
                  {markPaidOccurrence.categoryEmoji || '🔁'}
                </div>
                <div className="flex-1">
                  <div className="text-[14px] font-semibold">{markPaidOccurrence.title}</div>
                  <div className="text-[11px] text-white/25 mt-0.5">
                    {t('fixedPayments.dueOn', { date: markPaidOccurrence.dueDate })}
                  </div>
                </div>
                <div className="text-[16px] font-bold text-purple-400">
                  {formatCurrency(markPaidOccurrence.expectedAmount, lang)}
                </div>
              </div>
              <p className="text-[12px] text-white/25 mt-4 leading-relaxed">
                {t('fixedPayments.markPaidInfo')}
              </p>
            </div>
            <div className="flex items-center justify-end gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
              <button
                onClick={handleCloseMarkPaid}
                className="px-[22px] py-3 bg-[#131325] border border-white/[0.055] rounded-xl text-base font-semibold text-white/50 cursor-pointer hover:border-white/[0.12] hover:text-white transition-all"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={handleOpenTransactionModal}
                className="flex items-center gap-2 px-5 py-3 rounded-xl border-none text-base font-bold text-white cursor-pointer transition-all hover:-translate-y-px"
                style={{
                  background: 'linear-gradient(135deg, #7c3aed, #a855f7)',
                  boxShadow: '0 4px 20px rgba(124,58,237,0.3)',
                }}
              >
                {t('fixedPayments.openTransaction')}
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Transaction modal for mark-as-paid */}
      {showTransactionModal && (
        <TransactionModal
          isOpen={showTransactionModal}
          onClose={handleCloseMarkPaid}
          onSave={handleTransactionSave}
          prefill={transactionPrefill}
          onPrefillSaved={() => { refetchTile(); refetchList(); }}
          categories={categories}
          loading={false}
        />
      )}
    </div>
  );
}
