'use client';
import React, { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';
import { Edit3 } from 'lucide-react';

// ─── helpers ──────────────────────────────────────────────────────────────────

const parseDate = (value) => {
  if (!value) return null;
  const s = String(value).slice(0, 10);
  const [y, m, d] = s.split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d);
};

const shortDate = (value, lang) => {
  const dt = value instanceof Date ? value : parseDate(value);
  if (!dt) return '';
  return new Intl.DateTimeFormat(lang === 'pl' ? 'pl-PL' : 'en-US', {
    month: 'short',
    day: 'numeric',
  }).format(dt);
};

// ─── status ───────────────────────────────────────────────────────────────────

const SOON_DAYS = 7;

const classify = (occ) => {
  if (occ.status === 'PAID')    return 'PAID';
  if (occ.status === 'SKIPPED') return 'SKIPPED';
  if (occ.status === 'OVERDUE' || Number(occ.daysDelta) < 0) return 'OVERDUE';
  if (Number(occ.daysDelta) === 0)            return 'DUE_TODAY';
  if (Number(occ.daysDelta) <= SOON_DAYS)     return 'DUE_SOON';
  return 'UPCOMING';
};

const STATUS_STYLE = {
  PAID:      { color: '#4ade80', bg: 'rgba(74,222,128,0.10)',   border: 'rgba(74,222,128,0.38)',  chipBg: 'rgba(74,222,128,0.06)'  },
  OVERDUE:   { color: '#f87171', bg: 'rgba(248,113,113,0.10)',  border: 'rgba(248,113,113,0.40)', chipBg: 'rgba(248,113,113,0.07)' },
  DUE_TODAY: { color: '#f59e0b', bg: 'rgba(245,158,11,0.10)',   border: 'rgba(245,158,11,0.42)',  chipBg: 'rgba(245,158,11,0.06)'  },
  DUE_SOON:  { color: '#fbbf24', bg: 'rgba(251,191,36,0.08)',   border: 'rgba(251,191,36,0.32)',  chipBg: 'rgba(251,191,36,0.05)'  },
  UPCOMING:  { color: '#a78bfa', bg: 'rgba(167,139,250,0.08)',  border: 'rgba(167,139,250,0.32)', chipBg: 'rgba(167,139,250,0.05)' },
  SKIPPED:   { color: 'rgba(255,255,255,0.28)', bg: 'rgba(255,255,255,0.04)', border: 'rgba(255,255,255,0.12)', chipBg: 'rgba(255,255,255,0.03)' },
};

const BADGE_KEY = {
  PAID:      'badge_paidOnTime',
  OVERDUE:   'badge_overdue',
  DUE_TODAY: 'badge_dueSoon',
  DUE_SOON:  'badge_dueSoon',
  UPCOMING:  'badge_upcoming',
  SKIPPED:   'badge_skipped',
};

// ─── TodayDivider ─────────────────────────────────────────────────────────────

function TodayDivider({ label }) {
  return (
    <div
      aria-hidden="true"
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        alignSelf: 'stretch',
        width: 24,
        flexShrink: 0,
        background: 'rgba(124,58,237,0.07)',
        border: '1px solid rgba(124,58,237,0.22)',
        borderRadius: 8,
      }}
    >
      <span
        style={{
          fontSize: 8,
          fontWeight: 700,
          letterSpacing: '0.12em',
          textTransform: 'uppercase',
          color: 'rgba(167,139,250,0.65)',
          writingMode: 'vertical-lr',
          textOrientation: 'mixed',
          transform: 'rotate(180deg)',
        }}
      >
        {label}
      </span>
    </div>
  );
}

// ─── EventChip ────────────────────────────────────────────────────────────────

/**
 * A compact mini-card for one occurrence.
 * Width is fixed at 168 px so the rail scrolls predictably.
 * Click opens the edit modal for the underlying fixed-payment template.
 */
function EventChip({ occ, status, template, cycleLabel, lang, t, onEdit, isActive }) {
  const [hovered, setHovered] = useState(false);

  const sc       = STATUS_STYLE[status] ?? STATUS_STYLE.UPCOMING;
  const amt      = occ.paidAmount ?? occ.expectedAmount;
  const dateStr  = shortDate(occ.dueDate, lang);
  const badge    = t(BADGE_KEY[status] ?? 'badge_upcoming');
  const canEdit  = !!template;
  const lit      = isActive || hovered;

  return (
    <button
      type="button"
      onClick={canEdit ? onEdit : undefined}
      disabled={!canEdit}
      title={canEdit ? t('events.editHint') : undefined}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      aria-label={`${occ.title} – ${badge} ${dateStr}${canEdit ? ', ' + t('events.editHint') : ''}`}
      style={{
        width: 168,
        minHeight: 92,
        flexShrink: 0,
        background: isActive ? sc.chipBg : hovered ? '#1b1b2d' : '#131320',
        border: `1px solid ${isActive ? sc.border : hovered ? 'rgba(255,255,255,0.13)' : 'rgba(255,255,255,0.07)'}`,
        borderRadius: 12,
        padding: '10px 12px',
        textAlign: 'left',
        display: 'flex',
        flexDirection: 'column',
        gap: 5,
        cursor: canEdit ? 'pointer' : 'default',
        position: 'relative',
        overflow: 'hidden',
        transition: 'border-color 0.15s, background 0.15s, box-shadow 0.15s',
        boxShadow: hovered ? '0 4px 20px rgba(0,0,0,0.35)' : 'none',
      }}
    >
      {/* Status accent — top 2 px bar */}
      <div
        style={{
          position: 'absolute',
          top: 0, left: 0, right: 0,
          height: 2,
          background: sc.color,
          opacity: lit ? 0.90 : 0.55,
          transition: 'opacity 0.15s',
        }}
      />

      {/* Row 1: emoji + payment name */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginTop: 2, minWidth: 0 }}>
        <span style={{ fontSize: 14, lineHeight: 1, flexShrink: 0 }}>
          {occ.categoryEmoji || '🔁'}
        </span>
        <span
          style={{
            fontSize: 12,
            fontWeight: 600,
            color: lit ? 'rgba(255,255,255,0.90)' : 'rgba(255,255,255,0.72)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            flex: 1,
            transition: 'color 0.15s',
          }}
        >
          {occ.title}
        </span>
      </div>

      {/* Row 2: amount (large) + cycle label (right-aligned small) */}
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 4 }}>
        <span
          style={{
            fontSize: 17,
            fontWeight: 700,
            fontFamily: 'monospace',
            color: sc.color,
            letterSpacing: '-0.3px',
            lineHeight: 1,
          }}
        >
          {formatCurrency(amt, lang)}
        </span>
        {cycleLabel && (
          <span style={{ fontSize: 9, color: 'rgba(255,255,255,0.22)', flexShrink: 0 }}>
            {cycleLabel}
          </span>
        )}
      </div>

      {/* Row 3: status badge + due/paid date */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 4,
          marginTop: 'auto',
        }}
      >
        <span
          style={{
            fontSize: 9,
            fontWeight: 600,
            color: sc.color,
            background: sc.bg,
            border: `1px solid ${sc.border}`,
            borderRadius: 5,
            padding: '2px 5px',
            lineHeight: 1.3,
            flexShrink: 0,
          }}
        >
          {badge}
        </span>
        <span style={{ fontSize: 9, color: 'rgba(255,255,255,0.22)', fontFamily: 'monospace', flexShrink: 0 }}>
          {dateStr}
        </span>
      </div>

      {/* Edit hint icon — only while hovered */}
      {canEdit && hovered && (
        <div
          style={{ position: 'absolute', top: 8, right: 8, color: 'rgba(255,255,255,0.32)' }}
          aria-hidden="true"
        >
          <Edit3 size={9} />
        </div>
      )}
    </button>
  );
}

// ─── main component ───────────────────────────────────────────────────────────

/**
 * Horizontal event rail that replaces the old Templates grid.
 *
 * Props:
 *   tileData              — fixed-payments tile API response
 *   fixedPayments         — all template objects (used for edit-modal lookup)
 *   lang                  — locale string
 *   onEditPayment(fp)     — called when the user clicks an event chip
 *   activeFixedPaymentId  — id of the template whose modal is currently open
 */
export default function FixedPaymentEventsStrip({
  tileData,
  fixedPayments,
  lang,
  onEditPayment,
  activeFixedPaymentId,
}) {
  const t = useTranslations('fixedPayments');

  // Build a fast id→template lookup
  const templateMap = useMemo(() => {
    const m = {};
    for (const fp of (fixedPayments || [])) m[fp.id] = fp;
    return m;
  }, [fixedPayments]);

  const getCycleLabel = (cycle) => {
    const map = { WEEKLY: 'weekly', MONTHLY: 'monthly', QUARTERLY: 'quarterly', YEARLY: 'yearly' };
    const k = map[cycle?.toUpperCase?.()];
    if (!k) return cycle ?? null;
    try { return t(k); } catch { return cycle; }
  };

  const { railItems, periodStart, periodEnd, paidCount, totalCount } = useMemo(() => {
    if (!tileData) return {};

    const allOccs = [
      ...(tileData.overdue  || []),
      ...(tileData.upcoming || []),
      ...(tileData.paid     || []),
    ];

    const sorted = [...allOccs].sort((a, b) =>
      String(a.dueDate).localeCompare(String(b.dueDate)),
    );

    // Decide whether TODAY falls within the period
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const pStart = parseDate(tileData.periodStart);
    const pEnd   = parseDate(tileData.periodEnd);
    const todayInPeriod = pStart && pEnd && today >= pStart && today <= pEnd;

    // Find the insertion index for the TODAY divider (after the last occ with dueDate <= today)
    let todayIdx = 0;
    if (todayInPeriod) {
      for (let i = 0; i < sorted.length; i++) {
        const d = parseDate(sorted[i].dueDate);
        if (d && d <= today) todayIdx = i + 1;
      }
    }

    // Build the final items array
    const railItems = [];
    sorted.forEach((occ, i) => {
      if (todayInPeriod && i === todayIdx) {
        railItems.push({ type: 'today', key: '__today__' });
      }
      railItems.push({ type: 'occ', key: String(occ.occurrenceId ?? i), occ });
    });
    // Append today marker when it falls after all occurrences
    if (todayInPeriod && todayIdx === sorted.length) {
      railItems.push({ type: 'today', key: '__today__' });
    }

    return {
      railItems,
      periodStart:  tileData.periodStart,
      periodEnd:    tileData.periodEnd,
      paidCount:    allOccs.filter(o => o.status === 'PAID').length,
      totalCount:   allOccs.length,
    };
  }, [tileData]);

  if (!tileData) return null;

  const LEGEND = [
    { status: 'PAID',      badgeKey: 'badge_paidOnTime' },
    { status: 'DUE_TODAY', badgeKey: 'badge_dueSoon'    },
    { status: 'UPCOMING',  badgeKey: 'badge_upcoming'   },
    { status: 'OVERDUE',   badgeKey: 'badge_overdue'    },
    { status: 'SKIPPED',   badgeKey: 'badge_skipped'    },
  ];

  return (
    <div
      className="bg-[#13131f] border border-white/[0.06] rounded-[14px]"
      style={{ animation: 'fadeUp 0.35s ease both' }}
    >
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between px-5 pt-4 pb-3 border-b border-white/[0.05]">
        <div>
          <div className="text-[10px] font-bold tracking-[0.12em] uppercase text-white/35">
            {t('events.title')}
          </div>
          <div className="text-[10px] text-white/20 mt-0.5">
            {shortDate(periodStart, lang)} – {shortDate(periodEnd, lang)}
          </div>
        </div>
        <div className="text-right">
          <div className="text-[11px] text-white/30 font-mono tabular-nums">
            {t('events.paidOf', { paid: paidCount ?? 0, total: totalCount ?? 0 })}
          </div>
          <div className="text-[9px] text-white/[0.18] mt-0.5">
            {t('events.subtitle')}
          </div>
        </div>
      </div>

      {/* ── Event rail ─────────────────────────────────────────────────── */}
      <div className="px-5 py-4 overflow-x-auto">
        {!railItems || railItems.length === 0 ? (
          <div className="text-[12px] text-white/20 py-4 text-center select-none">
            {t('events.noEvents')}
          </div>
        ) : (
          <div
            style={{
              display: 'flex',
              alignItems: 'stretch',
              gap: 10,
              minWidth: 'max-content',
              paddingBottom: 2,
            }}
          >
            {railItems.map((item) =>
              item.type === 'today' ? (
                <TodayDivider key={item.key} label={t('events.today')} />
              ) : (
                <EventChip
                  key={item.key}
                  occ={item.occ}
                  status={classify(item.occ)}
                  template={templateMap[item.occ.fixedPaymentId] ?? null}
                  cycleLabel={getCycleLabel(templateMap[item.occ.fixedPaymentId]?.cycle)}
                  lang={lang}
                  t={t}
                  onEdit={() => {
                    const fp = templateMap[item.occ.fixedPaymentId];
                    if (fp) onEditPayment(fp);
                  }}
                  isActive={
                    activeFixedPaymentId != null &&
                    item.occ.fixedPaymentId === activeFixedPaymentId
                  }
                />
              ),
            )}
          </div>
        )}
      </div>

      {/* ── Legend ─────────────────────────────────────────────────────── */}
      <div className="flex items-center flex-wrap gap-x-4 gap-y-1.5 px-5 pt-3 pb-4 border-t border-white/[0.04]">
        {LEGEND.map(({ status, badgeKey }) => {
          const sc = STATUS_STYLE[status];
          return (
            <div key={status} className="flex items-center gap-1.5" aria-hidden="true">
              <div
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  background: sc.color,
                  flexShrink: 0,
                }}
              />
              <span style={{ fontSize: 9, color: 'rgba(255,255,255,0.28)', lineHeight: 1 }}>
                {t(badgeKey)}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
