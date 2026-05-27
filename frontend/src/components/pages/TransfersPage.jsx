'use client';
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Plus,
  ArrowRight,
  ArrowLeftRight,
  Wallet,
  Trash2,
  Loader2,
  AlertCircle,
} from 'lucide-react';
import { useWallets } from '@/contexts/WalletContext';
import { useAppContext } from '@/contexts/AppContext';
import TransferModal from '@/components/modals/TransferModal';
import { useTranslations } from 'next-intl';
import { useLanguage } from '@/i18n';
import { formatCurrency, formatDate } from '@/utils/helpers';
import { transferApi } from '@/lib/api';

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatMonthLabel(yearMonth, lang) {
  const [year, month] = yearMonth.split('-').map(Number);
  return new Date(year, month - 1, 1).toLocaleDateString(
    lang === 'pl' ? 'pl-PL' : 'en-US',
    { month: 'long', year: 'numeric' }
  );
}

// ─── Stat card ────────────────────────────────────────────────────────────────

function StatCard({ label, value, sub, icon: Icon, iconColor }) {
  return (
    <div className="bg-[#131325] border border-white/[0.055] rounded-2xl px-4 py-3.5 flex items-start gap-3">
      {Icon && (
        <div
          className="w-8 h-8 rounded-[10px] flex items-center justify-center shrink-0 mt-0.5"
          style={{ background: `${iconColor}18` }}
        >
          <Icon className="w-4 h-4" style={{ color: iconColor }} />
        </div>
      )}
      <div className="flex-1 min-w-0">
        <div className="text-[10px] font-bold tracking-[0.12em] uppercase text-white/25 mb-0.5">
          {label}
        </div>
        <div className="font-mono text-[18px] font-bold text-white tracking-[-0.5px] truncate leading-tight">
          {value}
        </div>
        {sub && (
          <div className="text-[11px] text-white/30 mt-0.5">{sub}</div>
        )}
      </div>
    </div>
  );
}

// ─── Skeleton loading rows ────────────────────────────────────────────────────

function SkeletonRows({ count = 5 }) {
  return (
    <div className="bg-[#131325] border border-white/[0.055] rounded-2xl overflow-hidden">
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="flex items-center gap-3 px-4 py-3 border-b border-white/[0.04] last:border-b-0 animate-pulse"
        >
          <div className="w-8 h-8 rounded-full bg-white/[0.04] shrink-0" />
          <div className="flex-1">
            <div className="h-3.5 w-48 bg-white/[0.04] rounded mb-1.5" />
            <div className="h-2.5 w-28 bg-white/[0.03] rounded" />
          </div>
          <div className="h-4 w-20 bg-white/[0.04] rounded" />
          <div className="w-7 h-7 bg-white/[0.02] rounded-lg shrink-0" />
        </div>
      ))}
    </div>
  );
}

// ─── Transfer row ─────────────────────────────────────────────────────────────

function TransferCard({ transfer, lang, onDelete }) {
  const t = useTranslations();
  const initial = transfer.fromWalletName?.charAt(0)?.toUpperCase() ?? '?';

  return (
    <div className="group flex items-center gap-3 px-4 py-3 hover:bg-white/[0.03] transition-colors">
      {/* Wallet-initial avatar */}
      <div className="w-8 h-8 rounded-full bg-purple-500/[0.12] border border-purple-500/[0.18] flex items-center justify-center text-[11px] font-bold text-purple-300/70 shrink-0 select-none">
        {initial}
      </div>

      {/* Wallets + metadata */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5 leading-none">
          <span className="text-[13px] font-semibold text-white/85 truncate">
            {transfer.fromWalletName}
          </span>
          <ArrowRight className="w-3 h-3 text-white/25 shrink-0" />
          <span className="text-[13px] font-semibold text-white/85 truncate">
            {transfer.toWalletName}
          </span>
        </div>
        <div className="flex items-center gap-1.5 mt-0.5">
          <span className="text-[11px] text-white/30">
            {formatDate(transfer.transferDate, lang)}
          </span>
          {transfer.notes && (
            <>
              <span className="text-white/[0.12]">·</span>
              <span className="text-[11px] text-white/25 truncate max-w-[140px]">
                {transfer.notes}
              </span>
            </>
          )}
        </div>
      </div>

      {/* Amount */}
      <div className="font-mono text-[13px] font-bold text-white/80 shrink-0 tabular-nums">
        {formatCurrency(transfer.amount, lang)}
      </div>

      {/* Delete — visible on hover */}
      <button
        onClick={() => onDelete(transfer)}
        className="opacity-0 group-hover:opacity-100 w-7 h-7 rounded-[8px] bg-red-500/[0.08] border border-red-500/[0.18] flex items-center justify-center text-red-400/70 hover:text-red-400 hover:bg-red-500/[0.15] transition-all shrink-0"
        title={t('transfersPage.deleteTooltip')}
      >
        <Trash2 className="w-3 h-3" />
      </button>
    </div>
  );
}

// ─── Quick transfer form ──────────────────────────────────────────────────────

function QuickTransferForm({ wallets, onSubmit }) {
  const t = useTranslations();
  const { lang } = useLanguage();

  const [form, setForm] = useState({
    fromWalletId: '',
    toWalletId: '',
    amount: '',
    notes: '',
  });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  const destinationWallets = useMemo(
    () => wallets.filter((w) => w.id !== parseInt(form.fromWalletId)),
    [wallets, form.fromWalletId]
  );

  const fromWallet = wallets.find((w) => w.id === parseInt(form.fromWalletId));
  const toWallet = wallets.find((w) => w.id === parseInt(form.toWalletId));
  const parsedAmount = parseFloat(form.amount) || 0;

  const handleChange = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: null }));
    if (success) setSuccess(false);
  };

  // Clear destination if it matches the newly chosen source
  useEffect(() => {
    if (
      form.fromWalletId &&
      form.toWalletId &&
      form.fromWalletId === form.toWalletId
    ) {
      setForm((prev) => ({ ...prev, toWalletId: '' }));
    }
  }, [form.fromWalletId, form.toWalletId]);

  const validate = () => {
    const errs = {};
    if (!form.fromWalletId)
      errs.fromWalletId = t('transfer.errors.fromWalletRequired');
    if (!form.toWalletId)
      errs.toWalletId = t('transfer.errors.toWalletRequired');
    if (
      form.fromWalletId &&
      form.toWalletId &&
      form.fromWalletId === form.toWalletId
    )
      errs.toWalletId = t('transfer.errors.sameWallet');
    if (!form.amount || parsedAmount <= 0)
      errs.amount = t('errors.amountRequired');
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setSubmitting(true);
    try {
      await onSubmit({
        fromWalletId: parseInt(form.fromWalletId),
        toWalletId: parseInt(form.toWalletId),
        amount: parsedAmount,
        transferDate: new Date().toISOString().split('T')[0],
        notes: form.notes?.trim() || undefined,
      });
      setForm({ fromWalletId: '', toWalletId: '', amount: '', notes: '' });
      setErrors({});
      setSuccess(true);
      setTimeout(() => setSuccess(false), 4000);
    } catch (err) {
      setErrors((prev) => ({
        ...prev,
        submit: err.message || t('errors.generic'),
      }));
    } finally {
      setSubmitting(false);
    }
  };

  const inputCls =
    'w-full bg-[#0e0e1c] border rounded-[11px] px-3.5 py-2.5 text-sm text-white outline-none transition-all appearance-none cursor-pointer focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]';

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-[#131325] border border-white/[0.055] rounded-2xl overflow-hidden"
    >
      {/* Card header */}
      <div className="px-5 py-4 border-b border-white/[0.055]">
        <div className="flex items-center gap-2 mb-1.5">
          <div
            className="w-6 h-6 rounded-md flex items-center justify-center shrink-0"
            style={{ background: '#7c3aed18' }}
          >
            <ArrowLeftRight className="w-3.5 h-3.5" style={{ color: '#a78bfa' }} />
          </div>
          <span className="text-[14px] font-bold text-white/85">
            {t('transfersPage.quickTransferHeader')}
          </span>
        </div>
        <p className="text-[11px] text-white/25 leading-relaxed">
          {t('transfersPage.quickTransferHelper')}
        </p>
      </div>

      <div className="p-5 flex flex-col gap-4">
        {/* From wallet */}
        <div>
          <label className="text-[11px] font-bold tracking-[0.12em] uppercase text-white/25 mb-1.5 flex items-center gap-1">
            {t('transfer.fromWallet')}
            <span className="text-purple-300/70">*</span>
          </label>
          <select
            value={form.fromWalletId}
            onChange={(e) => handleChange('fromWalletId', e.target.value)}
            className={`${inputCls} ${
              errors.fromWalletId ? 'border-red-500' : 'border-white/[0.055]'
            }`}
          >
            <option value="">{t('transfer.selectWallet')}</option>
            {wallets.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </select>
          {errors.fromWalletId && (
            <p className="text-red-400 text-xs mt-1">{errors.fromWalletId}</p>
          )}
        </div>

        {/* To wallet */}
        <div>
          <label className="text-[11px] font-bold tracking-[0.12em] uppercase text-white/25 mb-1.5 flex items-center gap-1">
            {t('transfer.toWallet')}
            <span className="text-purple-300/70">*</span>
          </label>
          <select
            value={form.toWalletId}
            onChange={(e) => handleChange('toWalletId', e.target.value)}
            className={`${inputCls} ${
              errors.toWalletId ? 'border-red-500' : 'border-white/[0.055]'
            }`}
          >
            <option value="">{t('transfer.selectWallet')}</option>
            {destinationWallets.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </select>
          {errors.toWalletId && (
            <p className="text-red-400 text-xs mt-1">{errors.toWalletId}</p>
          )}
        </div>

        {/* Amount */}
        <div>
          <label className="text-[11px] font-bold tracking-[0.12em] uppercase text-white/25 mb-1.5 flex items-center gap-1">
            {t('transaction.amount')}
            <span className="text-purple-300/70">*</span>
          </label>
          <div className="relative">
            <span className="absolute left-3.5 top-1/2 -translate-y-1/2 font-mono text-sm font-medium text-white/20 pointer-events-none select-none">
              PLN
            </span>
            <input
              type="number"
              step="0.01"
              min="0"
              value={form.amount}
              onChange={(e) => handleChange('amount', e.target.value)}
              className={`w-full bg-[#0e0e1c] border rounded-[11px] pl-12 pr-3.5 py-2.5 font-mono text-base font-medium text-white placeholder-white/20 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] ${
                errors.amount ? 'border-red-500' : 'border-white/[0.055]'
              }`}
              placeholder="0.00"
            />
          </div>
          {errors.amount && (
            <p className="text-red-400 text-xs mt-1">{errors.amount}</p>
          )}
        </div>

        {/* Live balance preview */}
        {fromWallet && toWallet && parsedAmount > 0 && (
          <div className="bg-[#0e0e1c] border border-white/[0.04] rounded-xl p-3">
            <div className="text-[10px] font-bold tracking-[0.1em] uppercase text-white/20 mb-2">
              {t('transfer.balancePreview')}
            </div>
            <div className="grid grid-cols-[1fr_auto_1fr] gap-2 items-center">
              <div>
                <div className="text-[11px] text-white/30 mb-0.5 truncate">
                  {fromWallet.name}
                </div>
                <div className="font-mono text-[11px] text-white/25 line-through">
                  {formatCurrency(fromWallet.balance ?? 0, lang)}
                </div>
                <div
                  className={`font-mono text-[13px] font-bold ${
                    (fromWallet.balance ?? 0) - parsedAmount < 0
                      ? 'text-red-400'
                      : 'text-white/75'
                  }`}
                >
                  {formatCurrency((fromWallet.balance ?? 0) - parsedAmount, lang)}
                </div>
              </div>
              <ArrowRight className="w-3 h-3 text-purple-400/50" />
              <div className="text-right">
                <div className="text-[11px] text-white/30 mb-0.5 truncate">
                  {toWallet.name}
                </div>
                <div className="font-mono text-[11px] text-white/25 line-through">
                  {formatCurrency(toWallet.balance ?? 0, lang)}
                </div>
                <div className="font-mono text-[13px] font-bold text-green-400">
                  {formatCurrency((toWallet.balance ?? 0) + parsedAmount, lang)}
                </div>
              </div>
            </div>
            {(fromWallet.balance ?? 0) - parsedAmount < 0 && (
              <div className="mt-2 text-[11px] text-red-400/70 bg-red-500/[0.08] border border-red-500/[0.15] rounded-lg px-2.5 py-1.5">
                {t('transfer.negativeBalanceWarning')}
              </div>
            )}
          </div>
        )}

        {/* Note */}
        <div>
          <label className="text-[11px] font-bold tracking-[0.12em] uppercase text-white/25 mb-1.5 block">
            {t('transaction.notes')}
          </label>
          <input
            type="text"
            value={form.notes}
            onChange={(e) => handleChange('notes', e.target.value)}
            className="w-full bg-[#0e0e1c] border border-white/[0.055] rounded-[11px] px-3.5 py-2.5 text-sm text-white placeholder-white/20 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]"
            placeholder={t('transfer.notesPlaceholder')}
          />
        </div>

        {/* Error feedback */}
        {errors.submit && (
          <div className="flex items-start gap-2 p-3 bg-red-500/[0.08] border border-red-500/[0.18] rounded-xl text-red-400 text-[12px]">
            <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
            {errors.submit}
          </div>
        )}

        {/* Success feedback */}
        {success && (
          <div className="p-3 bg-green-500/[0.08] border border-green-500/[0.18] rounded-xl text-green-400 text-[12px] text-center font-semibold">
            {t('transfersPage.quickTransferSuccess')}
          </div>
        )}

        {/* Submit */}
        <button
          type="submit"
          disabled={submitting}
          className="flex items-center justify-center gap-2 w-full py-2.5 rounded-xl text-sm font-bold text-white transition-all disabled:opacity-50 disabled:cursor-not-allowed hover:-translate-y-px active:translate-y-0"
          style={{
            background: 'linear-gradient(135deg, #7c3aed, #a855f7)',
            boxShadow: '0 4px 20px rgba(124,58,237,0.3)',
          }}
        >
          {submitting ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              {t('transfersPage.quickTransferCreating')}
            </>
          ) : (
            <>
              <ArrowRight className="w-4 h-4" />
              {t('transfer.saveTransfer')}
            </>
          )}
        </button>
      </div>
    </form>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function TransfersPage() {
  const t = useTranslations();
  const { lang } = useLanguage();
  const { wallets, loading: walletsLoading } = useWallets();
  const { onCreateTransfer, onDeleteTransfer, walletMutationVersion } =
    useAppContext();

  const [transfers, setTransfers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const fetchTransfers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await transferApi.getAllTransfers();
      setTransfers(data || []);
    } catch (err) {
      setError(err.message || t('errors.generic'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  // Fetch on mount and after any wallet mutation (create/delete/update)
  useEffect(() => {
    fetchTransfers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [walletMutationVersion]);

  // ─── Derived ───────────────────────────────────────────────────────────────

  const now = new Date();

  const thisMonthTransfers = useMemo(
    () =>
      transfers.filter((tr) => {
        const d = new Date(tr.transferDate);
        return (
          d.getMonth() === now.getMonth() &&
          d.getFullYear() === now.getFullYear()
        );
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [transfers]
  );

  const totalThisMonth = useMemo(
    () =>
      thisMonthTransfers.reduce(
        (sum, tr) => sum + parseFloat(tr.amount || 0),
        0
      ),
    [thisMonthTransfers]
  );

  const mostActiveSource = useMemo(() => {
    if (!transfers.length) return null;
    const counts = {};
    transfers.forEach((tr) => {
      counts[tr.fromWalletName] = (counts[tr.fromWalletName] || 0) + 1;
    });
    return Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
  }, [transfers]);

  const mostActiveDest = useMemo(() => {
    if (!transfers.length) return null;
    const counts = {};
    transfers.forEach((tr) => {
      counts[tr.toWalletName] = (counts[tr.toWalletName] || 0) + 1;
    });
    return Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
  }, [transfers]);

  // Sort newest-first, then group by month
  const groupedTransfers = useMemo(() => {
    const groups = {};
    const sorted = [...transfers].sort(
      (a, b) => new Date(b.transferDate) - new Date(a.transferDate)
    );
    sorted.forEach((tr) => {
      const key = String(tr.transferDate).slice(0, 7); // "2025-01"
      if (!groups[key]) groups[key] = [];
      groups[key].push(tr);
    });
    // Return entries sorted newest month first
    return Object.entries(groups).sort((a, b) => b[0].localeCompare(a[0]));
  }, [transfers]);

  // ─── Handlers ──────────────────────────────────────────────────────────────

  const handleCreateTransfer = useCallback(
    async (data) => {
      await onCreateTransfer(data);
      // walletMutationVersion increment will trigger fetchTransfers via effect
    },
    [onCreateTransfer]
  );

  const handleConfirmDelete = useCallback(async () => {
    if (!deleteTarget) return;
    setDeleteLoading(true);
    try {
      await onDeleteTransfer(deleteTarget.id);
      setDeleteTarget(null);
    } catch {
      setDeleteLoading(false);
    }
  }, [deleteTarget, onDeleteTransfer]);

  // ─── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="p-5 sm:p-7 max-w-[1400px]">
      {/* Page header */}
      <div className="flex items-start justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-[-0.5px] text-white">
            {t('transfersPage.title')}
          </h1>
          <p className="mt-1 text-sm text-white/35">
            {t('transfersPage.subtitle')}
          </p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-bold text-white shrink-0 transition-all hover:-translate-y-px active:translate-y-0"
          style={{
            background: 'linear-gradient(135deg, #7c3aed, #a855f7)',
            boxShadow: '0 4px 20px rgba(124,58,237,0.3)',
          }}
        >
          <Plus className="w-4 h-4" />
          {t('transfersPage.newTransfer')}
        </button>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-6">
        <StatCard
          icon={ArrowLeftRight}
          iconColor="#a78bfa"
          label={t('transfersPage.statTransferredThisMonth')}
          value={formatCurrency(totalThisMonth, lang)}
          sub={t('transfersPage.statTransferCount', {
            count: thisMonthTransfers.length,
          })}
        />
        <StatCard
          icon={Wallet}
          iconColor="#fb923c"
          label={t('transfersPage.statMostActiveSource')}
          value={mostActiveSource ?? '—'}
          sub={
            mostActiveSource
              ? t('transfersPage.statTopSendingWallet')
              : t('transfersPage.statNoTransfersYet')
          }
        />
        <StatCard
          icon={Wallet}
          iconColor="#34d399"
          label={t('transfersPage.statMostActiveDest')}
          value={mostActiveDest ?? '—'}
          sub={
            mostActiveDest
              ? t('transfersPage.statTopReceivingWallet')
              : t('transfersPage.statNoTransfersYet')
          }
        />
      </div>

      {/* Main 2-column layout */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-6 items-start">
        {/* ── Left: Transfer history ── */}
        <div>
          {/* Section header: title + subtitle left, count badge right */}
          <div className="flex items-start justify-between gap-4 mb-3">
            <div>
              <p className="text-[13px] font-semibold text-white/65 leading-tight">
                {t('transfersPage.historyLabel')}
              </p>
              <p className="text-[11px] text-white/25 mt-0.5">
                {loading
                  ? ' '
                  : t('transfersPage.historyMovements', { count: transfers.length })}
              </p>
            </div>
            {!loading && !error && transfers.length > 0 && (
              <span className="text-[11px] font-semibold text-white/20 bg-white/[0.04] border border-white/[0.05] rounded-full px-2.5 py-0.5 tabular-nums shrink-0 mt-0.5">
                {t('transfersPage.historyTotal', { count: transfers.length })}
              </span>
            )}
          </div>

          {/* Loading state — skeleton rows */}
          {loading ? (
            <SkeletonRows count={5} />
          ) : error ? (
            /* Error state */
            <div className="flex flex-col items-center justify-center py-16 gap-3 text-center">
              <AlertCircle className="w-7 h-7 text-red-400/40" />
              <p className="text-[13px] text-white/30">{error}</p>
              <button
                onClick={fetchTransfers}
                className="px-4 py-2 text-[13px] font-semibold text-purple-300 border border-purple-500/25 rounded-xl bg-purple-500/[0.08] hover:bg-purple-500/[0.15] transition-all"
              >
                {t('common.retry')}
              </button>
            </div>
          ) : groupedTransfers.length === 0 ? (
            /* Empty state */
            <div className="flex flex-col items-center justify-center py-16 gap-3 text-center">
              <div className="w-12 h-12 rounded-2xl bg-purple-500/[0.08] border border-purple-500/[0.15] flex items-center justify-center">
                <ArrowLeftRight className="w-5 h-5 text-purple-400/40" />
              </div>
              <div>
                <p className="text-[14px] font-semibold text-white/30">
                  {t('transfersPage.emptyTitle')}
                </p>
                <p className="text-[12px] text-white/20 mt-1 max-w-[240px] leading-relaxed">
                  {t('transfersPage.emptyDesc')}
                </p>
              </div>
            </div>
          ) : (
            /* Grouped history */
            <div className="flex flex-col gap-4">
              {groupedTransfers.map(([monthKey, monthItems]) => (
                <div key={monthKey}>
                  {/* Month label */}
                  <div className="text-[10px] font-bold tracking-[0.12em] uppercase text-white/20 px-1 mb-1.5">
                    {formatMonthLabel(monthKey, lang)}
                  </div>
                  {/* Rows grouped in a card */}
                  <div className="bg-[#131325] border border-white/[0.055] rounded-2xl overflow-hidden divide-y divide-white/[0.04]">
                    {monthItems.map((tr) => (
                      <TransferCard
                        key={tr.id}
                        transfer={tr}
                        lang={lang}
                        onDelete={setDeleteTarget}
                      />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* ── Right: Quick transfer form (sticky on desktop) ── */}
        <div className="lg:sticky lg:top-4">
          {walletsLoading ? (
            <div className="bg-[#131325] border border-white/[0.055] rounded-2xl p-8 flex items-center justify-center text-white/20">
              <Loader2 className="w-5 h-5 animate-spin" />
            </div>
          ) : wallets.length < 2 ? (
            <div className="bg-[#131325] border border-white/[0.055] rounded-2xl p-8 flex flex-col items-center gap-3 text-center">
              <Wallet className="w-8 h-8 text-white/15" />
              <p className="text-[13px] text-white/30 leading-relaxed">
                {t('transfersPage.needMoreWallets')}
              </p>
            </div>
          ) : (
            <QuickTransferForm
              wallets={wallets}
              onSubmit={handleCreateTransfer}
            />
          )}
        </div>
      </div>

      {/* Full-featured transfer modal (for "+ New Transfer" button) */}
      <TransferModal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        onSave={async (data) => {
          await handleCreateTransfer(data);
          setShowModal(false);
        }}
      />

      {/* Delete confirmation overlay */}
      {deleteTarget && (
        <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-4">
          <div
            className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl w-full max-w-[400px] overflow-hidden"
            style={{
              boxShadow: '0 32px 80px rgba(0,0,0,0.6)',
              animation: 'fadeUp 0.25s cubic-bezier(0.4,0,0.2,1) both',
            }}
          >
            <div className="p-6">
              <div className="w-10 h-10 rounded-full bg-red-500/[0.1] border border-red-500/[0.2] flex items-center justify-center mx-auto mb-4">
                <Trash2 className="w-5 h-5 text-red-400" />
              </div>
              <h3 className="text-center text-[16px] font-bold text-white mb-1">
                {t('transfersPage.deleteTitle')}
              </h3>
              <p className="text-center text-[13px] text-white/35 mb-5 leading-relaxed">
                {t('transfersPage.deleteBody', {
                  amount: formatCurrency(deleteTarget.amount, lang),
                  from: deleteTarget.fromWalletName,
                  to: deleteTarget.toWalletName,
                })}
              </p>
              <div className="flex gap-2.5">
                <button
                  onClick={() => setDeleteTarget(null)}
                  disabled={deleteLoading}
                  className="flex-1 py-2.5 bg-[#131325] border border-white/[0.055] rounded-xl text-sm font-semibold text-white/45 hover:text-white/75 hover:border-white/[0.1] transition-all"
                >
                  {t('common.cancel')}
                </button>
                <button
                  onClick={handleConfirmDelete}
                  disabled={deleteLoading}
                  className="flex-1 py-2.5 flex items-center justify-center gap-2 bg-red-500/[0.12] border border-red-500/[0.25] rounded-xl text-sm font-bold text-red-400 hover:bg-red-500/[0.2] transition-all disabled:opacity-50"
                >
                  {deleteLoading ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    t('transfersPage.deleteButton')
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
