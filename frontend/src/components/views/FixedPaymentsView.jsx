'use client';
import React, { useState, useMemo, useEffect, useRef } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import {
  Plus, RefreshCw, Loader2, AlertCircle, Check, X, ArrowRight,
  Calendar, Trash2, CheckCircle2, AlertTriangle,
} from 'lucide-react';
import { formatCurrency, formatDate } from '@/utils/helpers';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { useFixedPaymentsTile, useFixedPayments } from '@/hooks/useFixedPayments';
import { useCategories } from '@/hooks/useApi';
import { useWallets } from '@/contexts/WalletContext';
import { useAppContext } from '@/contexts/AppContext';
import FixedPaymentModal from '@/components/modals/FixedPaymentModal';
import { CHIP_BASE, CHIP_DEFAULT, PAGE_CTA } from '@/components/common/formControls';
import { Loading } from '@/components/common/Loading';
import TransactionModal from '@/components/modals/TransactionModal';
import FixedPaymentEventsStrip from './FixedPaymentEventsStrip';

const TABS = ['schedule', 'attention', 'paid', 'history'];

const SOON_DAYS = 7;
const ATTENTION_DAYS = 3;

const parseDateOnly = (value) => {
  if (!value) return null;
  const [year, month, day] = String(value).slice(0, 10).split('-').map(Number);
  if (!year || !month || !day) return null;
  return new Date(year, month - 1, day);
};

const diffDays = (from, to) => {
  if (!from || !to) return null;
  const start = new Date(from.getFullYear(), from.getMonth(), from.getDate());
  const end = new Date(to.getFullYear(), to.getMonth(), to.getDate());
  return Math.round((end - start) / 86400000);
};

export default function FixedPaymentsView() {
  const t = useTranslations();
  const { lang } = useLanguage();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const { currentWallet } = useWallets();
  const { onCreateTransaction, walletMutationVersion } = useAppContext();
  const walletId = currentWallet?.id;

  const { tileData, loading: tileLoading, refetch: refetchTile } = useFixedPaymentsTile(walletId);
  const {
    fixedPayments, loading: listLoading,
    createFixedPayment, updateFixedPayment, deactivateFixedPayment,
    refetch: refetchList,
  } = useFixedPayments(walletId);
  const { categories } = useCategories();

  const [activeTab, setActiveTab] = useState('schedule');
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [editingPayment, setEditingPayment] = useState(null);
  const [deactivatingPayment, setDeactivatingPayment] = useState(null);
  const [isDeactivating, setIsDeactivating] = useState(false);
  const [deactivateError, setDeactivateError] = useState('');
  const [actionFeedback, setActionFeedback] = useState(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const [markPaidOccurrence, setMarkPaidOccurrence] = useState(null);
  const [showTransactionModal, setShowTransactionModal] = useState(false);
  const [transactionPrefill, setTransactionPrefill] = useState(null);
  const hasHandledMutationRef = useRef(false);

  const [filterCategory, setFilterCategory] = useState('all');

  useEffect(() => {
    if (searchParams.get('open') !== 'create') return;
    setEditingPayment(null);
    setShowPaymentModal(true);
    router.replace(pathname);
  }, [pathname, router, searchParams]);

  useEffect(() => {
    if (!hasHandledMutationRef.current) {
      hasHandledMutationRef.current = true;
      return;
    }
    if (!walletId) return;

    refetchTile();
    refetchList();
  }, [walletMutationVersion, walletId, refetchTile, refetchList]);

  useEffect(() => {
    if (!actionFeedback) return;
    const timeoutId = window.setTimeout(() => setActionFeedback(null), 3600);
    return () => window.clearTimeout(timeoutId);
  }, [actionFeedback]);

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

  const openDeactivateConfirmation = (payment) => {
    setDeactivateError('');
    setDeactivatingPayment(payment);
  };

  const closeDeactivateConfirmation = () => {
    if (isDeactivating) return;
    setDeactivateError('');
    setDeactivatingPayment(null);
  };

  const handleDeactivate = async () => {
    if (!deactivatingPayment) return;
    try {
      setIsDeactivating(true);
      setDeactivateError('');
      await deactivateFixedPayment(deactivatingPayment.id);
      setDeactivatingPayment(null);
      setShowPaymentModal(false);
      setEditingPayment(null);
      await Promise.all([refetchTile(), refetchList()]);
      setActionFeedback({
        type: 'success',
        message: t('fixedPayments.deactivateSuccess'),
      });
    } catch (err) {
      console.error('Failed to deactivate:', err);
      setDeactivateError(err?.message || t('errors.generic'));
    } finally {
      setIsDeactivating(false);
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
    await onCreateTransaction(transactionData);
    refetchTile();
    refetchList();
  };

  const handleCloseMarkPaid = () => {
    setShowTransactionModal(false);
    setMarkPaidOccurrence(null);
    setTransactionPrefill(null);
  };

  const getDaysLabel = (daysDelta) => {
    if (daysDelta < 0) return t('fixedPayments.daysLate', { days: Math.abs(daysDelta) });
    if (daysDelta === 0) return t('fixedPayments.dueToday');
    return t('fixedPayments.daysLeft', { days: daysDelta });
  };

  const allOccurrences = useMemo(() => {
    if (!tileData) return [];
    const overdue = (tileData.overdue || []).map(o => ({ ...o, _section: 'overdue' }));
    const upcoming = (tileData.upcoming || []).map(o => ({ ...o, _section: 'upcoming' }));
    const paid = (tileData.paid || []).map(o => ({ ...o, _section: 'paid' }));
    return [...overdue, ...upcoming, ...paid];
  }, [tileData]);

  const enrichedOccurrences = useMemo(() => {
    return allOccurrences
      .map((occ) => {
        const isPaid = occ.status === 'PAID';
        const isSkipped = occ.status === 'SKIPPED';
        const dueDate = parseDateOnly(occ.dueDate);
        const paidDate = parseDateOnly(occ.paidAt);
        const paidDelayDays = isPaid ? diffDays(dueDate, paidDate) : null;
        const daysDelta = Number.isFinite(occ.daysDelta) ? occ.daysDelta : 999;
        const isOverdue = !isPaid && occ.status === 'OVERDUE';
        const isDueToday = !isPaid && occ.status === 'PENDING' && daysDelta === 0;
        const isDueSoon = !isPaid && occ.status === 'PENDING' && daysDelta > 0 && daysDelta <= SOON_DAYS;
        const needsAttention = isOverdue || isDueToday || isSkipped
          || (!isPaid && occ.status === 'PENDING' && daysDelta > 0 && daysDelta <= ATTENTION_DAYS);
        const dueMonthKey = dueDate ? `${dueDate.getFullYear()}-${dueDate.getMonth()}` : '';
        const periodStart = parseDateOnly(tileData?.periodStart);
        const currentMonthKey = periodStart ? `${periodStart.getFullYear()}-${periodStart.getMonth()}` : '';

        let badgeKey = 'upcoming';
        let tone = 'purple';
        let timingLabel = getDaysLabel(daysDelta);

        if (isPaid) {
          const lateDays = Math.max(paidDelayDays ?? 0, 0);
          badgeKey = lateDays > 0 ? 'paidLate' : 'paidOnTime';
          tone = lateDays > 0 ? 'amber' : 'green';
          timingLabel = lateDays > 0
            ? t('fixedPayments.paidDaysLate', { days: lateDays })
            : t('fixedPayments.paidOnTime');
        } else if (isSkipped) {
          badgeKey = 'skipped';
          tone = 'slate';
          timingLabel = t('fixedPayments.skipped');
        } else if (isOverdue) {
          badgeKey = 'overdue';
          tone = 'red';
        } else if (isDueToday || isDueSoon) {
          badgeKey = 'dueSoon';
          tone = isDueToday ? 'amber' : 'yellow';
        } else if (occ.status === 'PENDING') {
          badgeKey = dueMonthKey && currentMonthKey && dueMonthKey !== currentMonthKey ? 'upcoming' : 'pending';
          tone = 'purple';
        }

        return {
          ...occ,
          isPaid,
          isSkipped,
          isOverdue,
          isDueToday,
          isDueSoon,
          needsAttention,
          paidDelayDays,
          paidDate: occ.paidAt ? String(occ.paidAt).slice(0, 10) : null,
          displayAmount: occ.paidAmount ?? occ.expectedAmount,
          badgeKey,
          tone,
          timingLabel,
          dueMonthKey,
          currentMonthKey,
        };
      })
      .sort((a, b) => {
        if (a.needsAttention !== b.needsAttention) return a.needsAttention ? -1 : 1;
        if (a.isPaid !== b.isPaid) return a.isPaid ? 1 : -1;
        return String(a.dueDate).localeCompare(String(b.dueDate));
      });
  }, [allOccurrences, tileData?.periodStart, t]);

  const filteredOccurrences = useMemo(() => {
    let list = enrichedOccurrences;
    if (filterCategory !== 'all') {
      list = list.filter(o => String(o.categoryId) === filterCategory);
    }
    return list;
  }, [enrichedOccurrences, filterCategory]);

  const tabOccurrences = useMemo(() => {
    if (activeTab === 'attention') return filteredOccurrences.filter(o => o.needsAttention && !o.isPaid);
    if (activeTab === 'paid') return filteredOccurrences.filter(o => o.isPaid);
    if (activeTab === 'history') return filteredOccurrences.filter(o => o.isPaid || o.isSkipped);
    return filteredOccurrences;
  }, [activeTab, filteredOccurrences]);

  const scheduleGroups = useMemo(() => {
    const groups = [
      {
        key: 'needsAttention',
        title: t('fixedPayments.group_needsAttention'),
        subtitle: t('fixedPayments.group_needsAttentionDesc'),
        tone: 'red',
        items: tabOccurrences.filter(o => o.needsAttention && !o.isPaid),
      },
      {
        key: 'dueSoon',
        title: t('fixedPayments.group_dueSoon'),
        subtitle: t('fixedPayments.group_dueSoonDesc'),
        tone: 'amber',
        items: tabOccurrences.filter(o => !o.needsAttention && !o.isPaid && o.status === 'PENDING' && o.daysDelta <= SOON_DAYS),
      },
      {
        key: 'paidThisCycle',
        title: t('fixedPayments.group_paidThisCycle'),
        subtitle: t('fixedPayments.group_paidThisCycleDesc'),
        tone: 'green',
        items: tabOccurrences.filter(o => o.isPaid),
      },
      {
        key: 'laterThisMonth',
        title: t('fixedPayments.group_laterThisMonth'),
        subtitle: t('fixedPayments.group_laterThisMonthDesc'),
        tone: 'purple',
        items: tabOccurrences.filter(o => !o.isPaid && o.status === 'PENDING' && o.daysDelta > SOON_DAYS && o.dueMonthKey === o.currentMonthKey),
      },
      {
        key: 'nextMonth',
        title: t('fixedPayments.group_nextMonth'),
        subtitle: t('fixedPayments.group_nextMonthDesc'),
        tone: 'slate',
        items: tabOccurrences.filter(o => !o.isPaid && o.status === 'PENDING' && o.daysDelta > SOON_DAYS && o.dueMonthKey !== o.currentMonthKey),
      },
    ];

    if (activeTab === 'schedule') return groups.filter(group => group.items.length > 0);

    return [{
      key: activeTab,
      title: t(`fixedPayments.group_${activeTab}`),
      subtitle: t(`fixedPayments.group_${activeTab}Desc`),
      tone: activeTab === 'attention' ? 'red' : activeTab === 'paid' ? 'green' : 'slate',
      items: tabOccurrences,
    }];
  }, [activeTab, tabOccurrences, t]);

  const summary = tileData?.summary;
  const progress = tileData?.progress;
  const riskIndicator = tileData?.riskIndicator;
  const balanceAfterFixed = tileData?.balanceAfterFixed;

  const loading = tileLoading || listLoading;

  const attentionCount = filteredOccurrences.filter(o => o.needsAttention && !o.isPaid).length;
  const paidCount = filteredOccurrences.filter(o => o.isPaid).length;

  const getStatusToneClass = (tone) => {
    if (tone === 'red') return 'text-red-300 bg-red-500/10 border-red-500/25';
    if (tone === 'amber') return 'text-amber-300 bg-amber-500/10 border-amber-500/25';
    if (tone === 'yellow') return 'text-yellow-200 bg-yellow-500/10 border-yellow-500/25';
    if (tone === 'green') return 'text-green-300 bg-green-500/10 border-green-500/25';
    if (tone === 'slate') return 'text-white/35 bg-white/[0.04] border-white/[0.08]';
    return 'text-purple-300 bg-purple-500/10 border-purple-500/25';
  };

  const getCycleLabel = (cycle) => {
    const key = `fixedPayments.${cycle?.toLowerCase() || 'monthly'}`;
    try { return t(key); } catch { return cycle; }
  };

  if (loading && !tileData && fixedPayments.length === 0) {
    return <Loading message={t('common.loading')} />;
  }

  return (
    <div className="flex flex-col gap-[18px]">
      {/* Page header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="text-xl sm:text-[26px] font-bold tracking-[-0.4px]">
            {t('fixedPayments.viewTitle')}
          </div>
          <div className="text-[13px] sm:text-[14px] text-white/25 mt-[3px]">
            {t('fixedPayments.viewSubtitle')}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="w-[34px] h-[34px] rounded-[8px] border border-white/[0.055] bg-transparent flex items-center justify-center cursor-pointer text-white/35 transition-all hover:border-white/[0.12] hover:text-white/50 disabled:opacity-50"
            title={t('common.refresh')}
            aria-label={t('common.refresh')}
          >
            <RefreshCw className={`w-[14px] h-[14px] ${isRefreshing ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={() => {
              setEditingPayment(null);
              setShowPaymentModal(true);
            }}
            className={PAGE_CTA}
          >
            <Plus className="w-[13px] h-[13px]" strokeWidth={2.5} />
            {t('fixedPayments.addPayment')}
          </button>
        </div>
      </div>

      {actionFeedback && (
        <div className="flex items-center gap-2.5 px-4 py-3 bg-green-500/[0.08] border border-green-500/[0.2] rounded-[12px] text-[13px] text-green-300">
          <Check className="w-4 h-4 shrink-0" />
          <span>{actionFeedback.message}</span>
        </div>
      )}

      {/* Summary strip — 5 cards */}
      {tileData && (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-px bg-white/[0.06] rounded-[14px] overflow-hidden border border-white/[0.06]">
          {/* Planned */}
          <div className="bg-[#0e0e1c] p-4 relative">
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
          <div className="bg-[#0e0e1c] p-4 relative">
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
          <div className="bg-[#0e0e1c] p-4 relative">
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
          <div className="bg-[#0e0e1c] p-4 relative">
            <div className={`absolute top-0 left-0 right-0 h-[2px] opacity-80 ${balanceAfterFixed < 0 ? 'bg-red-400' : 'bg-amber-400'}`} />
            <div className="text-[9px] tracking-[0.1em] uppercase text-white/25 mb-1.5">{t('fixedPayments.balanceAfter')}</div>
            <div className={`font-bold text-[18px] tracking-[-0.01em] leading-none ${balanceAfterFixed < 0 ? 'text-red-400' : 'text-amber-400'}`}>
              {formatCurrency(balanceAfterFixed ?? 0, lang)}
            </div>
            <div className="text-[10px] text-white/25 mt-1.5">{t('fixedPayments.afterAllFixed')}</div>
          </div>
          {/* Progress */}
          <div className="bg-[#0e0e1c] p-4 relative col-span-2 md:col-span-1">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-purple-400 opacity-80" />
            <div className="text-[9px] tracking-[0.1em] uppercase text-white/25 mb-1.5">{t('fixedPayments.progressLabel')}</div>
            <div className="font-bold text-[18px] tracking-[-0.01em] leading-none text-purple-400">
              {Math.round(progress?.paidPct ?? 0)}%
            </div>
            <div className="flex items-center gap-2 mt-2">
              <div className="flex-1 h-[3px] bg-white/[0.06] rounded-full overflow-hidden">
                <div
                  className="h-full rounded-full"
                  style={{ width: `${progress?.paidPct ?? 0}%`, background: '#8b5cf6' }}
                />
              </div>
              <span className="text-[9px] text-white/25">{progress?.paidCount ?? 0}/{progress?.totalCount ?? 0}</span>
            </div>
          </div>
        </div>
      )}

      {/* Payment Events strip */}
      {tileData && (
        <FixedPaymentEventsStrip
          tileData={tileData}
          fixedPayments={fixedPayments}
          lang={lang}
          onEditPayment={(fp) => {
            setEditingPayment(fp);
            setShowPaymentModal(true);
          }}
          activeFixedPaymentId={editingPayment?.id ?? null}
        />
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
              {tab === 'attention' && attentionCount > 0 && (
                <span className="ml-1.5 px-1.5 py-0.5 rounded-full text-[9px] bg-red-500/20 text-red-300 border border-red-500/25">
                  {attentionCount}
                </span>
              )}
              {tab === 'paid' && paidCount > 0 && (
                <span className="ml-1.5 px-1.5 py-0.5 rounded-full text-[9px] bg-green-500/15 text-green-300 border border-green-500/20">
                  {paidCount}
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
            className={`${CHIP_BASE} ${CHIP_DEFAULT}`}
          >
            <option value="all">{t('fixedPayments.allCategories')}</option>
            {(categories || []).filter(c => c.type === 'EXPENSE').map(c => (
              <option key={c.id} value={c.id}>{c.emoji} {c.name}</option>
            ))}
          </select>

        </div>
      </div>

      {/* Occurrence schedule */}
      <div className="bg-[#0e0e1c] border border-white/[0.06] rounded-[14px] overflow-hidden">
        {scheduleGroups.length === 0 || tabOccurrences.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-white/25">
            <Calendar className="w-8 h-8 mb-3 opacity-40" />
            <div className="text-[13px]">{t('fixedPayments.noOccurrences')}</div>
          </div>
        ) : (
          <div className="divide-y divide-white/[0.045]">
            {scheduleGroups.map((group, groupIndex) => (
              <div
                key={group.key}
                className="px-3 py-3 sm:px-4 sm:py-4"
                style={{ animation: `fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both`, animationDelay: `${0.04 * groupIndex}s` }}
              >
                <div className="flex items-start justify-between gap-3 px-1 pb-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className={`w-1.5 h-1.5 rounded-full ${
                        group.tone === 'red' ? 'bg-red-400' : group.tone === 'amber' ? 'bg-amber-300' : group.tone === 'green' ? 'bg-green-300' : group.tone === 'purple' ? 'bg-purple-300' : 'bg-white/25'
                      }`} />
                      <div className="text-[13px] sm:text-[14px] font-semibold text-white">{group.title}</div>
                    </div>
                    <div className="text-[11px] sm:text-[12px] text-white/30 mt-1">{group.subtitle}</div>
                  </div>
                  <div className="text-[11px] text-white/30 bg-white/[0.035] border border-white/[0.06] rounded-full px-2.5 py-1 shrink-0">
                    {t('fixedPayments.countItems', { count: group.items.length })}
                  </div>
                </div>
                <div className="grid gap-2">
                  {group.items.map((occ, idx) => {
                    const canMarkPaid = occ.status === 'PENDING' || occ.isOverdue;

                    return (
                      <div
                        key={occ.occurrenceId || `${group.key}-${idx}`}
                        className={`group/row grid grid-cols-[auto_1fr] sm:grid-cols-[auto_1fr_auto] gap-3 rounded-[12px] border px-3.5 py-3 transition-colors ${
                          occ.isOverdue
                            ? 'bg-red-500/[0.045] border-red-500/15 hover:bg-red-500/[0.07]'
                            : 'bg-[#131325] border-white/[0.055] hover:bg-[#1a1a2e]'
                        }`}
                      >
                        <div className="w-[38px] h-[38px] bg-[#1a1a2e] border border-white/[0.06] rounded-[10px] flex items-center justify-center text-[15px] shrink-0">
                          {occ.categoryEmoji || '🔁'}
                        </div>

                        <div className="min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <div className="text-[13.5px] font-semibold text-white truncate max-w-[220px] sm:max-w-none">
                              {occ.title}
                            </div>
                            <span className={`text-[10px] px-2 py-0.5 rounded-full border ${getStatusToneClass(occ.tone)}`}>
                              {t(`fixedPayments.badge_${occ.badgeKey}`)}
                            </span>
                          </div>
                          <div className="mt-1.5 flex items-center gap-2 flex-wrap text-[11px] sm:text-[12px] text-white/30">
                            <span>{formatDate(occ.dueDate, lang)}</span>
                            <span className="text-white/14">·</span>
                            <span className="inline-flex items-center gap-1">
                              <span>{occ.categoryEmoji}</span>
                              <span>{occ.categoryName}</span>
                            </span>
                            <span className="text-white/14">·</span>
                            <span className={occ.isOverdue ? 'text-red-300' : occ.isPaid ? 'text-green-300' : 'text-white/35'}>
                              {occ.timingLabel}
                            </span>
                            {occ.paidDate && (
                              <>
                                <span className="text-white/14">·</span>
                                <span>{t('fixedPayments.paidOnDate', { date: formatDate(occ.paidDate, lang) })}</span>
                              </>
                            )}
                          </div>
                        </div>

                        <div className="col-span-2 sm:col-span-1 flex sm:flex-col items-center sm:items-end justify-between gap-3 sm:min-w-[150px]">
                          {canMarkPaid ? (
                            <button
                              onClick={() => handleMarkPaidClick(occ)}
                              className="flex items-center gap-1.5 px-3 py-1.5 rounded-[8px] text-[10px] tracking-[0.04em] cursor-pointer border border-green-400/30 bg-green-400/[0.07] text-green-300 transition-all hover:bg-green-400/[0.15] shrink-0"
                            >
                              <Check className="w-3 h-3" />
                              {t('fixedPayments.markAsPaid')}
                            </button>
                          ) : (
                            <span className={`inline-flex items-center gap-1.5 text-[11px] ${occ.isPaid ? 'text-green-300' : 'text-white/25'}`}>
                              {occ.isPaid ? <CheckCircle2 className="w-3.5 h-3.5" /> : <AlertTriangle className="w-3.5 h-3.5" />}
                              {occ.isPaid ? t('fixedPayments.settled') : t('fixedPayments.notActionable')}
                            </span>
                          )}
                          <div className="text-right">
                            <div className={`font-bold text-[15px] sm:text-[16px] tracking-[-0.01em] ${
                              occ.isOverdue ? 'text-red-300' : occ.isPaid ? 'text-green-300' : 'text-purple-300'
                            }`}>
                              {formatCurrency(occ.displayAmount, lang)}
                            </div>
                            {occ.paidAmount && occ.paidAmount !== occ.expectedAmount && (
                              <div className="text-[10px] text-white/25 mt-0.5">
                                {t('fixedPayments.plannedShort')}: {formatCurrency(occ.expectedAmount, lang)}
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
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
          onDeactivateRequest={editingPayment ? openDeactivateConfirmation : null}
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
            <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />
            <div className="flex items-center justify-between px-7 pt-6 pb-5 border-b border-white/[0.055]">
              <div className="text-lg font-bold tracking-[-0.3px]">{t('fixedPayments.deactivateConfirmTitle')}</div>
              <button
                onClick={closeDeactivateConfirmation}
                disabled={isDeactivating}
                aria-label={t('common.close')}
                className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
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
                {t('fixedPayments.deactivateConfirmDescription')}
              </p>
              {deactivateError && (
                <div className="mt-4 flex items-start gap-2 rounded-[10px] border border-red-500/30 bg-red-500/[0.08] px-3 py-2.5 text-[12px] text-red-300">
                  <AlertCircle className="w-3.5 h-3.5 mt-0.5 shrink-0" />
                  <span>{deactivateError}</span>
                </div>
              )}
            </div>
            <div className="flex items-center justify-end gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
              <button
                onClick={closeDeactivateConfirmation}
                disabled={isDeactivating}
                className="px-[22px] py-3 bg-[#131325] border border-white/[0.055] rounded-xl text-base font-semibold text-white/50 cursor-pointer hover:border-white/[0.12] hover:text-white transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={handleDeactivate}
                disabled={isDeactivating}
                className="inline-flex items-center gap-2 px-5 py-3 bg-[#f43f5e] hover:bg-[#e11d48] rounded-xl text-base font-bold text-white cursor-pointer transition-all disabled:opacity-70 disabled:cursor-not-allowed"
              >
                {isDeactivating && <Loader2 className="w-4 h-4 animate-spin" />}
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
            <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />
            <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
              <div className="text-lg font-bold tracking-[-0.3px]">{t('fixedPayments.markAsPaid')}</div>
              <button
                onClick={handleCloseMarkPaid}
                aria-label={t('common.close')}
                className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] transition-all"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
            <div className="px-4 sm:px-7 py-5">
              <div className="flex items-center gap-3 bg-[#131325] border border-white/[0.06] rounded-xl p-4">
                <div className="w-10 h-10 bg-[#1a1a2e] border border-white/[0.06] rounded-[10px] flex items-center justify-center text-lg">
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
                className="flex items-center gap-2 px-5 py-3 rounded-xl border-none text-base font-bold text-white cursor-pointer bg-gradient-to-br from-[#7c3aed] to-[#a855f7] shadow-[0_4px_20px_rgba(124,58,237,0.3)] transition-all hover:-translate-y-px"
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
