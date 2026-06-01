'use client';
import React, { useState, useMemo, useCallback, useEffect } from 'react';
import {
  Target, Plus, Loader2, AlertTriangle, CheckCircle2,
  TrendingUp, Wallet, Pencil, Archive, RotateCcw, Trash2,
  Check, ChevronDown, ShieldAlert,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useWallets } from '@/contexts/WalletContext';
import { useBudgetUsage } from '@/hooks/useBudgetUsage';
import { categoryBudgetsApi } from '@/lib/api';
import { formatCurrency } from '@/utils/helpers';
import BudgetProgressBar from '@/components/common/BudgetProgressBar';
import BudgetCreateModal from '@/components/modals/BudgetCreateModal';
import { INPUT_SM } from '@/components/common/formControls';

// ─── Status config ─────────────────────────────────────────────────────────────

const STATUS_CFG = {
  EXCEEDED: {
    labelKey: 'status_EXCEEDED',
    textColor: 'text-rose-400',
    badgeBg: 'bg-rose-500/[0.12]',
    rowBg: 'bg-rose-500/[0.04]',
    leftBar: 'before:bg-rose-500/60',
    pctColor: 'text-rose-400',
  },
  WARNING: {
    labelKey: 'status_WARNING',
    textColor: 'text-amber-400',
    badgeBg: 'bg-amber-500/[0.12]',
    rowBg: '',
    leftBar: 'before:bg-amber-500/40',
    pctColor: 'text-amber-400',
  },
  OK: {
    labelKey: 'status_OK',
    textColor: 'text-emerald-400/80',
    badgeBg: 'bg-emerald-500/[0.08]',
    rowBg: '',
    leftBar: 'before:bg-white/[0.08]',
    pctColor: 'text-emerald-400/70',
  },
};

function StatusBadge({ status, t }) {
  const cfg = STATUS_CFG[status] ?? STATUS_CFG.OK;
  if (status === 'OK') return null; // no badge for calm state — keeps list quiet
  return (
    <span className={`inline-flex items-center px-1.5 py-0.5 rounded-md text-[10px] font-bold tracking-wide ${cfg.textColor} ${cfg.badgeBg}`}>
      {t(`budget.${cfg.labelKey}`)}
    </span>
  );
}

// ─── Summary chip ──────────────────────────────────────────────────────────────

function SummaryChip({ icon: Icon, label, value, color, loading, accent = false }) {
  return (
    <div className={`relative flex flex-col gap-1.5 p-4 rounded-xl overflow-hidden
      ${accent
        ? 'bg-[#0e0e1c] border border-white/[0.09] shadow-[0_0_0_1px_rgba(124,58,237,0.08),0_4px_24px_rgba(124,58,237,0.06)]'
        : 'bg-[#0e0e1c] border border-white/[0.055]'
      }`}
    >
      {/* Subtle glow orb for accent chip */}
      {accent && (
        <div className="absolute -top-4 -right-4 w-16 h-16 rounded-full blur-2xl pointer-events-none"
          style={{ background: `${color}20` }} />
      )}

      <div className="flex items-center gap-2">
        <div
          className="w-6 h-6 rounded-lg flex items-center justify-center flex-shrink-0"
          style={{ background: `${color}1a`, boxShadow: `0 0 0 1px ${color}22` }}
        >
          <Icon className="w-3 h-3" style={{ color }} />
        </div>
        <span className="text-[10px] font-semibold tracking-[0.09em] uppercase text-white/25 truncate">{label}</span>
      </div>

      {loading ? (
        <div className="h-5 w-20 bg-white/[0.06] rounded animate-pulse" />
      ) : (
        <p className="font-mono text-[18px] font-bold leading-none truncate" style={{ color }}>{value}</p>
      )}
    </div>
  );
}

// ─── Budget row (active) ───────────────────────────────────────────────────────

function ActiveBudgetRow({ item, onArchived, onUpdated, compact = false }) {
  const t = useTranslations();
  const [editing, setEditing]       = useState(false);
  const [editForm, setEditForm]     = useState({ amount: '', warningThresholdPercent: '' });
  const [editErrors, setEditErrors] = useState({});
  const [saving, setSaving]         = useState(false);
  const [archiving, setArchiving]   = useState(false);

  const spent     = Number(item.spentAmount  ?? 0);
  const budget    = Number(item.budgetAmount ?? 0);
  const remaining = budget - spent;
  const percent   = Number(item.usagePercent ?? 0);
  const status    = item.status ?? 'OK';
  const cfg       = STATUS_CFG[status] ?? STATUS_CFG.OK;

  const startEdit = () => {
    setEditForm({
      amount: String(item.budgetAmount ?? ''),
      warningThresholdPercent: String(item.warningThresholdPercent ?? '80'),
    });
    setEditErrors({});
    setEditing(true);
  };

  const cancelEdit = () => { setEditing(false); setEditErrors({}); };

  const validateEdit = () => {
    const errs = {};
    const amt = parseFloat(editForm.amount);
    if (!editForm.amount || isNaN(amt) || amt <= 0) errs.amount = t('budget.amountRequired');
    const thr = parseInt(editForm.warningThresholdPercent, 10);
    if (editForm.warningThresholdPercent !== '' && (isNaN(thr) || thr < 1 || thr > 100))
      errs.warningThresholdPercent = t('budget.thresholdRange');
    return errs;
  };

  const saveEdit = async () => {
    const errs = validateEdit();
    if (Object.keys(errs).length) { setEditErrors(errs); return; }
    setSaving(true);
    try {
      await categoryBudgetsApi.update(item.budgetId, {
        amount: parseFloat(editForm.amount),
        warningThresholdPercent: parseInt(editForm.warningThresholdPercent, 10) || 80,
      });
      setEditing(false);
      onUpdated?.();
    } catch (err) {
      setEditErrors({ save: err.message || t('errors.generic') });
    } finally {
      setSaving(false);
    }
  };

  const handleArchive = async () => {
    setArchiving(true);
    try {
      await categoryBudgetsApi.deactivate(item.budgetId);
      onArchived?.();
    } catch {
      setArchiving(false);
    }
  };

  return (
    // Left status bar via before: pseudo — simulated with a wrapper div
    <div className={`group relative flex border-b border-white/[0.045] last:border-b-0
      transition-colors duration-150 hover:bg-white/[0.02] ${cfg.rowBg}`}
    >
      {/* Status indicator bar */}
      <div className={`w-[3px] flex-shrink-0 self-stretch rounded-r-full opacity-70 ${
        status === 'EXCEEDED' ? 'bg-rose-500/70' :
        status === 'WARNING'  ? 'bg-amber-500/50' : 'bg-transparent'
      }`} />

      <div className={`flex-1 px-4 ${compact ? 'py-3' : 'py-3.5'}`}>
        {editing ? (
          /* ── Edit mode ───────────────────────────────────────────────────── */
          <div>
            <div className="flex items-center gap-2 mb-3">
              {item.categoryEmoji && <span className="text-[14px]">{item.categoryEmoji}</span>}
              <span className="text-[13px] font-semibold text-white">{item.categoryName}</span>
            </div>
            <div className="grid grid-cols-[1fr_auto] gap-2 mb-2">
              <div>
                <input
                  type="number" step="0.01" min="0.01"
                  value={editForm.amount}
                  onChange={(e) => setEditForm((p) => ({ ...p, amount: e.target.value }))}
                  placeholder={t('budget.budgetAmount')}
                  className={`${INPUT_SM} w-full border-white/[0.1] ${editErrors.amount ? 'border-rose-500/60' : ''}`}
                />
                {editErrors.amount && <p className="text-rose-400 text-[11px] mt-1">{editErrors.amount}</p>}
              </div>
              <div className="w-20">
                <input
                  type="number" min="1" max="100"
                  value={editForm.warningThresholdPercent}
                  onChange={(e) => setEditForm((p) => ({ ...p, warningThresholdPercent: e.target.value }))}
                  className={`${INPUT_SM} w-full border-white/[0.1] text-center`}
                />
                <p className="text-white/25 text-[10px] text-center mt-0.5">{t('budget.warningAt')}</p>
              </div>
            </div>
            {editErrors.save && <p className="text-rose-400 text-[11px] mb-2">{editErrors.save}</p>}
            <div className="flex items-center gap-3">
              <button onClick={saveEdit} disabled={saving}
                className="flex items-center gap-1 text-[12px] font-semibold text-emerald-400 hover:text-emerald-300 transition-colors disabled:opacity-40">
                {saving ? <Loader2 className="w-3 h-3 animate-spin" /> : <Check className="w-3 h-3" />}
                {t('budget.save')}
              </button>
              <button onClick={cancelEdit} className="text-[12px] text-white/35 hover:text-white/60 transition-colors">
                {t('budget.cancel')}
              </button>
            </div>
          </div>
        ) : (
          /* ── View mode ───────────────────────────────────────────────────── */
          <div>
            {/* Row: name + badge + actions */}
            <div className="flex items-start justify-between gap-2 mb-2">
              <div className="flex items-center gap-1.5 min-w-0 flex-1 flex-wrap">
                {item.categoryEmoji && <span className="text-[14px] shrink-0 leading-none">{item.categoryEmoji}</span>}
                <span className="text-[13px] font-semibold text-white/90 truncate">{item.categoryName}</span>
                <StatusBadge status={status} t={t} />
              </div>

              {/* Actions — faint by default, full on hover; always full on mobile */}
              <div className="flex items-center gap-1 shrink-0 opacity-40 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity duration-150">
                <button
                  onClick={startEdit}
                  title={t('common.edit')}
                  className="w-6 h-6 flex items-center justify-center rounded-lg bg-white/[0.06] hover:bg-white/[0.12] transition-colors text-white/50 hover:text-white/90"
                >
                  <Pencil className="w-3 h-3" />
                </button>
                <button
                  onClick={handleArchive}
                  disabled={archiving}
                  title={t('budget.archiveBudget')}
                  className="w-6 h-6 flex items-center justify-center rounded-lg bg-white/[0.06] hover:bg-amber-500/15 transition-colors text-white/50 hover:text-amber-400 disabled:opacity-30"
                >
                  {archiving ? <Loader2 className="w-3 h-3 animate-spin" /> : <Archive className="w-3 h-3" />}
                </button>
              </div>
            </div>

            {/* Amount row */}
            <div className="flex items-baseline justify-between gap-3 mb-2">
              <span className="text-[12px] font-mono text-white/55">
                {formatCurrency(spent)}
                <span className="text-white/20"> / {formatCurrency(budget)}</span>
              </span>
              <span className={`text-[11px] font-mono font-semibold tabular-nums shrink-0 ${
                remaining < 0 ? 'text-rose-400' : 'text-white/30'
              }`}>
                {remaining >= 0
                  ? `${formatCurrency(remaining)} ${t('budget.remaining')}`
                  : `${formatCurrency(Math.abs(remaining))} ${t('budget.overBy')}`}
              </span>
            </div>

            {/* Progress bar */}
            <BudgetProgressBar percent={percent} status={status} height="h-[3px]" />

            {/* Footer: pct + warn threshold */}
            <div className="flex items-center justify-between mt-1.5">
              <span className={`text-[10px] font-bold tabular-nums ${cfg.pctColor}`}>
                {percent.toFixed(0)}%
                <span className="font-normal opacity-60 ml-0.5">{' '}{t('budget.used')}</span>
              </span>
              <span className="text-[10px] text-white/15 tabular-nums">
                ⚠ {item.warningThresholdPercent}%
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── Archived budget row ───────────────────────────────────────────────────────

function ArchivedBudgetRow({ budget, onRestored, onDeleted }) {
  const t = useTranslations();
  const [restoring, setRestoring]         = useState(false);
  const [deleting, setDeleting]           = useState(false);
  const [restoreError, setRestoreError]   = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const handleRestore = async () => {
    setRestoring(true);
    setRestoreError(null);
    try {
      await categoryBudgetsApi.reactivate(budget.id);
      onRestored?.();
    } catch (err) {
      setRestoreError(err.message?.includes('already exists')
        ? t('budget.cannotRestoreDuplicateActiveBudget')
        : err.message || t('errors.generic'));
      setRestoring(false);
    }
  };

  const handleDelete = async () => {
    if (!confirmDelete) { setConfirmDelete(true); return; }
    setDeleting(true);
    try {
      await categoryBudgetsApi.deletePermanently(budget.id);
      onDeleted?.();
    } catch {
      setDeleting(false);
      setConfirmDelete(false);
    }
  };

  return (
    <div className="group px-5 py-3 border-b border-white/[0.04] last:border-b-0 transition-colors hover:bg-white/[0.01]">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0 flex-1">
          {budget.categoryEmoji && (
            <span className="text-[13px] shrink-0 opacity-30">{budget.categoryEmoji}</span>
          )}
          <span className="text-[12px] text-white/30 truncate">{budget.categoryName}</span>
          <span className="text-[11px] font-mono text-white/15 shrink-0">{formatCurrency(budget.amount)}</span>
        </div>

        <div className="flex items-center gap-1 shrink-0 opacity-100 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity duration-150">
          <button
            onClick={handleRestore}
            disabled={restoring}
            title={t('budget.restoreBudget')}
            className="w-6 h-6 flex items-center justify-center rounded-lg bg-white/[0.04] hover:bg-emerald-500/15 transition-colors text-white/25 hover:text-emerald-400 disabled:opacity-30"
          >
            {restoring ? <Loader2 className="w-3 h-3 animate-spin" /> : <RotateCcw className="w-3 h-3" />}
          </button>
          <button
            onClick={handleDelete}
            disabled={deleting}
            title={confirmDelete ? t('budget.confirmDeleteBudget') : t('budget.deletePermanently')}
            className={`w-6 h-6 flex items-center justify-center rounded-lg transition-colors disabled:opacity-30 ${
              confirmDelete
                ? 'bg-rose-500/20 text-rose-400 hover:bg-rose-500/35'
                : 'bg-white/[0.04] text-white/25 hover:bg-rose-500/12 hover:text-rose-400'
            }`}
          >
            {deleting ? <Loader2 className="w-3 h-3 animate-spin" /> : <Trash2 className="w-3 h-3" />}
          </button>
        </div>
      </div>

      {restoreError && (
        <p className="text-rose-400/80 text-[11px] mt-1.5 pl-0.5">{restoreError}</p>
      )}
      {confirmDelete && !deleting && (
        <p className="text-white/25 text-[11px] mt-1.5 pl-0.5">{t('budget.confirmDeleteBudget')}</p>
      )}
    </div>
  );
}

// ─── Group section header ──────────────────────────────────────────────────────

function GroupHeader({ label, count, color }) {
  return (
    <div className="flex items-center gap-2.5 px-5 pt-4 pb-2">
      <span className="text-[9px] font-bold uppercase tracking-[0.14em]" style={{ color: color ?? 'rgba(255,255,255,0.25)' }}>
        {label}
      </span>
      {count != null && (
        <span className="text-[9px] font-mono px-1.5 py-0.5 rounded-full bg-white/[0.06] text-white/25">
          {count}
        </span>
      )}
      <div className="flex-1 h-px bg-white/[0.05]" />
    </div>
  );
}

// ─── Needs attention banner ────────────────────────────────────────────────────

function NeedsAttentionSection({ exceeded, warning, onArchived, onUpdated }) {
  const t = useTranslations();
  const items = [...exceeded, ...warning];
  if (items.length === 0) return null;

  return (
    <div className="mb-4 rounded-2xl overflow-hidden border border-rose-500/[0.15] bg-[#0e0e1c]"
      style={{ boxShadow: '0 0 0 1px rgba(239,68,68,0.06), 0 4px 24px rgba(239,68,68,0.04)' }}
    >
      {/* Alert header strip */}
      <div className="flex items-center gap-2.5 px-5 py-3 bg-rose-500/[0.06] border-b border-rose-500/[0.10]">
        <div className="w-5 h-5 rounded-md bg-rose-500/15 flex items-center justify-center flex-shrink-0">
          <ShieldAlert className="w-3 h-3 text-rose-400" />
        </div>
        <span className="text-[11px] font-bold text-rose-300/80 tracking-wide">
          {t('budget.needsAttention')}
        </span>
        <span className="ml-auto text-[10px] font-mono text-rose-400/50">{items.length}</span>
      </div>

      {/* Rows */}
      <div>
        {items.map((item) => (
          <ActiveBudgetRow
            key={item.budgetId}
            item={item}
            onArchived={onArchived}
            onUpdated={onUpdated}
            compact
          />
        ))}
      </div>
    </div>
  );
}

// ─── Loading skeleton ──────────────────────────────────────────────────────────

function BudgetsSkeleton() {
  return (
    <div className="animate-pulse space-y-4">
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
        {[...Array(5)].map((_, i) => (
          <div key={i} className="h-[78px] bg-white/[0.04] rounded-xl" />
        ))}
      </div>
      <div className="bg-white/[0.03] rounded-2xl overflow-hidden">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="flex items-center gap-4 px-5 py-4 border-b border-white/[0.04]">
            <div className="w-8 h-8 rounded-lg bg-white/[0.06]" />
            <div className="flex-1 space-y-2">
              <div className="h-3 w-28 bg-white/[0.06] rounded" />
              <div className="h-2 w-40 bg-white/[0.04] rounded" />
              <div className="h-1.5 w-full bg-white/[0.04] rounded-full" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Main page ─────────────────────────────────────────────────────────────────

export default function BudgetsPage() {
  const t = useTranslations();
  const { currentWallet } = useWallets();
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [archivedOpen, setArchivedOpen]       = useState(false);

  const { usage, loading, refetch } = useBudgetUsage(currentWallet?.id);

  const [archived, setArchived]               = useState([]);
  const [archivedLoading, setArchivedLoading] = useState(false);

  const fetchArchived = useCallback(async () => {
    if (!currentWallet?.id) return;
    setArchivedLoading(true);
    try {
      const data = await categoryBudgetsApi.getAll(currentWallet.id, false);
      setArchived(Array.isArray(data) ? data : []);
    } catch {
      setArchived([]);
    } finally {
      setArchivedLoading(false);
    }
  }, [currentWallet?.id]);

  useEffect(() => { fetchArchived(); }, [fetchArchived]);

  const refetchAll = useCallback(() => {
    refetch();
    fetchArchived();
  }, [refetch, fetchArchived]);

  const arr = Array.isArray(usage) ? usage : [];

  const summary = useMemo(() => ({
    totalBudgeted: arr.reduce((s, u) => s + Number(u.budgetAmount ?? 0), 0),
    totalSpent:    arr.reduce((s, u) => s + Number(u.spentAmount   ?? 0), 0),
    remaining:     arr.reduce((s, u) => s + Math.max(Number(u.budgetAmount ?? 0) - Number(u.spentAmount ?? 0), 0), 0),
    overCount:     arr.filter((u) => u.status === 'EXCEEDED').length,
    warnCount:     arr.filter((u) => u.status === 'WARNING').length,
  }), [arr]);

  const exceeded = arr.filter((u) => u.status === 'EXCEEDED');
  const warning  = arr.filter((u) => u.status === 'WARNING');
  const onTrack  = arr.filter((u) => u.status === 'OK');

  const activeCategoryIds = useMemo(() => new Set(arr.map((u) => u.categoryId)), [arr]);

  // ── Loading ──────────────────────────────────────────────────────────────────
  if (loading && arr.length === 0) {
    return <BudgetsSkeleton />;
  }

  // ── Empty state ──────────────────────────────────────────────────────────────
  if (!loading && arr.length === 0 && archived.length === 0) {
    return (
      <div style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}>
        <div className="flex flex-col items-center justify-center py-24 px-6 text-center">
          <div className="w-16 h-16 rounded-2xl bg-[#0e0e1c] border border-white/[0.07] flex items-center justify-center mb-6"
            style={{ boxShadow: '0 0 0 1px rgba(124,58,237,0.12), 0 8px 32px rgba(124,58,237,0.08)' }}
          >
            <Target className="w-7 h-7 text-violet-400/80" />
          </div>
          <p className="text-[11px] font-semibold tracking-[0.12em] uppercase text-white/25 mb-3">
            {t('budget.emptyTitle')}
          </p>
          <p className="text-[14px] text-white/40 max-w-[260px] leading-relaxed mb-8">
            {t('budget.emptyDescription')}
          </p>
          <button
            onClick={() => setCreateModalOpen(true)}
            className="inline-flex items-center gap-2.5 px-5 py-2.5 rounded-xl text-[13px] font-semibold
                       bg-gradient-to-r from-violet-600 to-purple-600 text-white
                       hover:from-violet-500 hover:to-purple-500
                       active:scale-[0.98]
                       transition-all duration-200 ease-[cubic-bezier(0.4,0,0.2,1)]
                       shadow-[0_4px_20px_rgba(124,58,237,0.28)]"
          >
            <Plus className="w-4 h-4" />
            {t('budget.addFirstBudget')}
          </button>
        </div>

        <BudgetCreateModal
          isOpen={createModalOpen}
          onClose={() => setCreateModalOpen(false)}
          walletId={currentWallet?.id}
          activeBudgetCategoryIds={activeCategoryIds}
          onCreated={refetchAll}
        />
      </div>
    );
  }

  return (
    <div style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}>
      {/* ── Summary chips ────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-5">
        <SummaryChip
          icon={Wallet}
          label={t('budget.totalBudgeted')}
          value={formatCurrency(summary.totalBudgeted)}
          color="#a78bfa"
          loading={loading}
          accent
        />
        <SummaryChip
          icon={TrendingUp}
          label={t('budget.totalSpent')}
          value={formatCurrency(summary.totalSpent)}
          color="#60a5fa"
          loading={loading}
        />
        <SummaryChip
          icon={CheckCircle2}
          label={t('budget.remaining')}
          value={formatCurrency(summary.remaining)}
          color="#34d399"
          loading={loading}
        />
        <SummaryChip
          icon={AlertTriangle}
          label={t('budget.overBudgetCount')}
          value={String(summary.overCount)}
          color={summary.overCount > 0 ? '#f87171' : 'rgba(255,255,255,0.2)'}
          loading={loading}
        />
        <SummaryChip
          icon={AlertTriangle}
          label={t('budget.warningCount')}
          value={String(summary.warnCount)}
          color={summary.warnCount > 0 ? '#fbbf24' : 'rgba(255,255,255,0.2)'}
          loading={loading}
        />
      </div>

      {/* ── Needs attention ──────────────────────────────────────────────────── */}
      <NeedsAttentionSection
        exceeded={exceeded}
        warning={warning}
        onArchived={refetchAll}
        onUpdated={refetchAll}
      />

      {/* ── All active budgets ───────────────────────────────────────────────── */}
      <div className="bg-[#0e0e1c] border border-white/[0.055] rounded-2xl overflow-hidden mb-4">
        {/* Card header */}
        <div className="flex items-center justify-between gap-3 px-5 py-4 border-b border-white/[0.05]">
          <div>
            <p className="text-[13px] font-bold text-white leading-tight">{t('budget.manageBudgets')}</p>
            <p className="text-[11px] text-white/30 mt-0.5">{t('budget.manageBudgetsSubtitle')}</p>
          </div>
          <button
            onClick={() => setCreateModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[12px] font-semibold
                       bg-violet-500/[0.12] text-violet-300 border border-violet-500/[0.15]
                       hover:bg-violet-500/[0.22] hover:border-violet-500/[0.25]
                       active:scale-[0.98]
                       transition-all duration-150"
          >
            <Plus className="w-3.5 h-3.5" />
            {t('budget.addBudget')}
          </button>
        </div>

        {arr.length === 0 ? (
          <div className="px-5 py-10 text-center">
            <p className="text-[13px] text-white/25">{t('budget.noBudgets')}</p>
            <p className="text-[12px] text-white/15 mt-1">{t('budget.noBudgetsDescription')}</p>
          </div>
        ) : (
          <>
            {exceeded.length > 0 && (
              <>
                <GroupHeader label={t('budget.status_EXCEEDED')} count={exceeded.length} color="#f87171" />
                {exceeded.map((item) => (
                  <ActiveBudgetRow key={item.budgetId} item={item} onArchived={refetchAll} onUpdated={refetchAll} />
                ))}
              </>
            )}
            {warning.length > 0 && (
              <>
                <GroupHeader label={t('budget.status_WARNING')} count={warning.length} color="#fbbf24" />
                {warning.map((item) => (
                  <ActiveBudgetRow key={item.budgetId} item={item} onArchived={refetchAll} onUpdated={refetchAll} />
                ))}
              </>
            )}
            {onTrack.length > 0 && (
              <>
                <GroupHeader label={t('budget.status_OK')} count={onTrack.length} color="rgba(255,255,255,0.25)" />
                {onTrack.map((item) => (
                  <ActiveBudgetRow key={item.budgetId} item={item} onArchived={refetchAll} onUpdated={refetchAll} />
                ))}
              </>
            )}
          </>
        )}
      </div>

      {/* ── Archived budgets — collapsible ───────────────────────────────────── */}
      {(archived.length > 0 || archivedLoading) && (
        <div className="border border-white/[0.045] rounded-2xl overflow-hidden">
          <button
            onClick={() => setArchivedOpen((v) => !v)}
            className="w-full flex items-center justify-between px-5 py-3.5 text-left
                       hover:bg-white/[0.015] transition-colors duration-150"
          >
            <div className="flex items-center gap-2.5">
              <div className="w-5 h-5 rounded-md bg-white/[0.04] flex items-center justify-center">
                <Archive className="w-3 h-3 text-white/25" />
              </div>
              <span className="text-[12px] font-semibold text-white/30">{t('budget.archivedBudgets')}</span>
              {archived.length > 0 && (
                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded-full bg-white/[0.06] text-white/20">
                  {archived.length}
                </span>
              )}
            </div>
            <ChevronDown className={`w-3.5 h-3.5 text-white/20 transition-transform duration-200 ${archivedOpen ? 'rotate-180' : ''}`} />
          </button>

          <div className={`overflow-hidden transition-all duration-[220ms] ease-[cubic-bezier(0.4,0,0.2,1)] ${archivedOpen ? 'max-h-[640px]' : 'max-h-0'}`}>
            {archivedLoading ? (
              <div className="flex items-center justify-center py-8 border-t border-white/[0.04] text-white/20">
                <Loader2 className="w-4 h-4 animate-spin" />
              </div>
            ) : (
              <div className="border-t border-white/[0.04]">
                {archived.map((b) => (
                  <ArchivedBudgetRow
                    key={b.id}
                    budget={b}
                    onRestored={refetchAll}
                    onDeleted={refetchAll}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      <BudgetCreateModal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        walletId={currentWallet?.id}
        activeBudgetCategoryIds={activeCategoryIds}
        onCreated={refetchAll}
      />
    </div>
  );
}
