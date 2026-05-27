'use client';
import React, { useMemo, useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';

// ─── helpers ──────────────────────────────────────────────────────────────────

/** Parse a "YYYY-MM-DD" (or Date) without timezone drift. */
const parseDate = (value) => {
  if (!value) return null;
  const s = String(value).slice(0, 10);
  const [y, m, d] = s.split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d);
};

/** Whole-day difference: how many days is `b` after `a`? */
const daysBetween = (a, b) => {
  const norm = (dt) => new Date(dt.getFullYear(), dt.getMonth(), dt.getDate()).getTime();
  return Math.round((norm(b) - norm(a)) / 86_400_000);
};

/** Format a Date as "Jun 1" / "1 cze" depending on locale. */
const shortDate = (dt, lang) =>
  dt
    ? new Intl.DateTimeFormat(lang === 'pl' ? 'pl-PL' : 'en-US', {
        month: 'short',
        day: 'numeric',
      }).format(dt)
    : '';

// ─── status ───────────────────────────────────────────────────────────────────

const SOON_DAYS = 7;

const classify = (occ) => {
  if (occ.status === 'PAID')    return 'PAID';
  if (occ.status === 'SKIPPED') return 'SKIPPED';
  if (occ.status === 'OVERDUE' || Number(occ.daysDelta) < 0) return 'OVERDUE';
  if (Number(occ.daysDelta) === 0)             return 'DUE_TODAY';
  if (Number(occ.daysDelta) <= SOON_DAYS)      return 'DUE_SOON';
  return 'UPCOMING';
};

const STATUS_PRIORITY = ['OVERDUE', 'DUE_TODAY', 'DUE_SOON', 'UPCOMING', 'SKIPPED', 'PAID'];

const STATUS_STYLE = {
  PAID:      { color: '#4ade80', glow: 'rgba(74,222,128,0.30)',   bg: 'rgba(74,222,128,0.12)',  border: 'rgba(74,222,128,0.45)'  },
  OVERDUE:   { color: '#f87171', glow: 'rgba(248,113,113,0.35)',  bg: 'rgba(248,113,113,0.13)', border: 'rgba(248,113,113,0.50)' },
  DUE_TODAY: { color: '#f59e0b', glow: 'rgba(245,158,11,0.35)',   bg: 'rgba(245,158,11,0.13)',  border: 'rgba(245,158,11,0.50)'  },
  DUE_SOON:  { color: '#fbbf24', glow: 'rgba(251,191,36,0.25)',   bg: 'rgba(251,191,36,0.10)',  border: 'rgba(251,191,36,0.38)'  },
  UPCOMING:  { color: '#a78bfa', glow: 'rgba(167,139,250,0.25)',  bg: 'rgba(167,139,250,0.10)', border: 'rgba(167,139,250,0.38)' },
  SKIPPED:   { color: 'rgba(255,255,255,0.30)', glow: 'none',     bg: 'rgba(255,255,255,0.05)', border: 'rgba(255,255,255,0.18)' },
};

const BADGE_KEY = {
  PAID:      'badge_paidOnTime',
  OVERDUE:   'badge_overdue',
  DUE_TODAY: 'badge_dueSoon',
  DUE_SOON:  'badge_dueSoon',
  UPCOMING:  'badge_upcoming',
  SKIPPED:   'badge_skipped',
};

// ─── row assignment ───────────────────────────────────────────────────────────

/**
 * Greedily assign each group (sorted by pct) to row 0 (close to axis) or
 * row 1 (higher) so chips don't overlap. OVERLAP_PCT is the minimum % gap
 * required between two chips in the same row.
 */
const OVERLAP_PCT = 20;

const assignRows = (sortedGroups) => {
  const rowEdge = [0, 0]; // rightmost pct-edge committed per row
  return sortedGroups.map((g) => {
    for (let r = 0; r <= 1; r++) {
      if (g.pct >= rowEdge[r]) {
        rowEdge[r] = g.pct + OVERLAP_PCT;
        return { ...g, row: r };
      }
    }
    // Both rows busy — overflow into row 0 (minor overlap acceptable)
    rowEdge[0] = g.pct + OVERLAP_PCT;
    return { ...g, row: 0 };
  });
};

// ─── layout constants ─────────────────────────────────────────────────────────

const TRACK_H  = 96;  // total track height (px)
const AXIS_Y   = 62;  // axis line top offset from track top (px)
const CHIP_H   = 22;  // chip pill height (px)

//  Row 0: chip sits 4px above axis  → chipBottom = 58, chipTop = 36
//  Row 1: chip sits 32px above axis → chipBottom = 30, chipTop = 8
const CHIP_TOP  = [36, 8];
const STEM_TOP  = [58, 30];   // = CHIP_TOP[r] + CHIP_H
const STEM_H    = [ 4, 32];   // = AXIS_Y - STEM_TOP[r]

// ─── PortalTooltip ────────────────────────────────────────────────────────────

/**
 * Renders the hover tooltip into document.body (via React portal) so it is
 * never clipped by any ancestor's overflow or z-index context.
 */
function PortalTooltip({ anchorRect, group, lang, t }) {
  if (!anchorRect || typeof document === 'undefined') return null;

  const TOL_W = 210;
  const vw = window.innerWidth;
  const anchorCenterX = anchorRect.left + anchorRect.width / 2;
  const left = Math.max(8, Math.min(anchorCenterX - TOL_W / 2, vw - TOL_W - 8));
  const arrowLeft = Math.max(8, Math.min(anchorCenterX - left - 4, TOL_W - 20));

  return createPortal(
    <div
      style={{
        position: 'fixed',
        top: anchorRect.top - 8,
        left,
        width: TOL_W,
        transform: 'translateY(-100%)',
        zIndex: 9999,
        pointerEvents: 'none',
      }}
    >
      <div
        style={{
          background: '#0a0a18',
          border: '1px solid rgba(255,255,255,0.12)',
          borderRadius: 10,
          padding: 12,
          boxShadow: '0 8px 32px rgba(0,0,0,0.70)',
          position: 'relative',
        }}
      >
        {/* Date header */}
        <div
          style={{
            fontSize: 9,
            fontFamily: 'monospace',
            color: 'rgba(255,255,255,0.30)',
            marginBottom: 8,
          }}
        >
          {shortDate(group.dueDate, lang)}
        </div>

        {/* One row per occurrence */}
        {group.occs.map((occ, i) => {
          const s  = classify(occ);
          const sc = STATUS_STYLE[s] ?? STATUS_STYLE.UPCOMING;
          const amt = occ.paidAmount ?? occ.expectedAmount;
          return (
            <div
              key={occ.occurrenceId ?? i}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 12,
                paddingTop:    i > 0 ? 6 : 0,
                paddingBottom: 6,
                borderTop: i > 0 ? '1px solid rgba(255,255,255,0.06)' : 'none',
              }}
            >
              <div style={{ minWidth: 0, flex: 1 }}>
                <div
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    color: 'rgba(255,255,255,0.85)',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {occ.categoryEmoji ? `${occ.categoryEmoji} ` : ''}{occ.title}
                </div>
                <div style={{ fontSize: 9, marginTop: 2, color: sc.color }}>
                  {t(BADGE_KEY[s] ?? 'badge_upcoming')}
                </div>
              </div>
              <div
                style={{
                  fontSize: 11,
                  fontFamily: 'monospace',
                  fontWeight: 700,
                  flexShrink: 0,
                  color: sc.color,
                }}
              >
                {formatCurrency(amt, lang)}
              </div>
            </div>
          );
        })}

        {/* Arrow */}
        <div
          style={{
            position: 'absolute',
            bottom: -5,
            left: arrowLeft,
            width: 8,
            height: 8,
            background: '#0a0a18',
            borderBottom: '1px solid rgba(255,255,255,0.12)',
            borderRight: '1px solid rgba(255,255,255,0.12)',
            transform: 'rotate(45deg)',
          }}
        />
      </div>
    </div>,
    document.body,
  );
}

// ─── PaymentChip ──────────────────────────────────────────────────────────────

/**
 * Renders a labeled chip pill + thin connector stem for one date-group.
 * Returns a React fragment so both elements sit as direct siblings inside
 * the track container (no wrapper div that could upset overflow).
 */
function PaymentChip({ group, pct, row, lang, t }) {
  const [hovered, setHovered]       = useState(false);
  const [anchorRect, setAnchorRect] = useState(null);
  const chipRef = useRef(null);

  const sc = STATUS_STYLE[group.dominant] ?? STATUS_STYLE.UPCOMING;

  const primaryOcc = group.occs[0];
  const rawName    = primaryOcc.categoryEmoji
    ? `${primaryOcc.categoryEmoji} ${primaryOcc.title}`
    : primaryOcc.title ?? '';
  const displayName = rawName.length > 13 ? rawName.slice(0, 12) + '…' : rawName;
  const extraCount  = group.occs.length - 1;

  const chipTop  = CHIP_TOP[row];
  const stemTop  = STEM_TOP[row];
  const stemH    = STEM_H[row];

  const handleMouseEnter = () => {
    if (chipRef.current) setAnchorRect(chipRef.current.getBoundingClientRect());
    setHovered(true);
  };

  return (
    <>
      {/* Connector stem — thin line from chip bottom to the axis */}
      {stemH > 0 && (
        <div
          style={{
            position: 'absolute',
            left: `${pct}%`,
            top: stemTop,
            width: 1,
            height: stemH,
            background: sc.color,
            opacity: hovered ? 0.65 : 0.28,
            transform: 'translateX(-50%)',
            transition: 'opacity 0.15s ease',
            pointerEvents: 'none',
          }}
        />
      )}

      {/* Chip pill */}
      <div
        ref={chipRef}
        role="button"
        tabIndex={0}
        aria-label={group.occs.map((o) => `${o.title} ${shortDate(group.dueDate, lang)}`).join(', ')}
        style={{
          position: 'absolute',
          left: `${pct}%`,
          top: chipTop,
          height: CHIP_H,
          padding: '0 7px',
          borderRadius: 11,
          border: `1px solid ${hovered ? sc.color : sc.border}`,
          background: hovered ? sc.bg : 'rgba(255,255,255,0.04)',
          display: 'flex',
          alignItems: 'center',
          gap: 4,
          whiteSpace: 'nowrap',
          cursor: 'default',
          transform: 'translateX(-50%)',
          transition: 'border-color 0.15s, background 0.15s, box-shadow 0.15s',
          boxShadow: hovered ? `0 0 10px ${sc.glow}` : 'none',
          zIndex: hovered ? 20 : row === 1 ? 11 : 10,
          userSelect: 'none',
        }}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={() => setHovered(false)}
        onFocus={handleMouseEnter}
        onBlur={() => setHovered(false)}
      >
        {/* Status dot */}
        <div
          style={{
            width: 5,
            height: 5,
            borderRadius: '50%',
            background: sc.color,
            flexShrink: 0,
          }}
        />

        {/* Payment name */}
        <span
          style={{
            fontSize: 10,
            color: hovered ? 'rgba(255,255,255,0.88)' : 'rgba(255,255,255,0.60)',
            fontWeight: 500,
            letterSpacing: '0.01em',
            transition: 'color 0.15s',
          }}
        >
          {displayName}
        </span>

        {/* "+N more" badge for grouped dates */}
        {extraCount > 0 && (
          <span
            style={{
              fontSize: 8,
              fontWeight: 700,
              color: sc.color,
              background: sc.bg,
              border: `1px solid ${sc.border}`,
              borderRadius: 5,
              padding: '1px 3px',
              lineHeight: 1.2,
            }}
          >
            +{extraCount}
          </span>
        )}
      </div>

      {/* Portal tooltip — rendered into document.body, never clipped */}
      {hovered && (
        <PortalTooltip anchorRect={anchorRect} group={group} lang={lang} t={t} />
      )}
    </>
  );
}

// ─── main component ───────────────────────────────────────────────────────────

export default function FixedPaymentsTimeline({ tileData, lang }) {
  const t = useTranslations('fixedPayments');

  const { periodStart, periodEnd, todayPct, groups, paidCount, totalCount } =
    useMemo(() => {
      if (!tileData) return {};

      const pStart = parseDate(tileData.periodStart);
      const pEnd   = parseDate(tileData.periodEnd);
      if (!pStart || !pEnd) return {};

      const totalDays = daysBetween(pStart, pEnd);
      if (totalDays <= 0) return {};

      const pct = (dt) =>
        Math.min(100, Math.max(0, (daysBetween(pStart, dt) / totalDays) * 100));

      const today       = new Date();
      const todayPct    = pct(today);
      const todayVisible =
        daysBetween(pStart, today) >= 0 && daysBetween(today, pEnd) >= 0;

      const allOccs = [
        ...(tileData.overdue  || []),
        ...(tileData.upcoming || []),
        ...(tileData.paid     || []),
      ];

      // Group occurrences by dueDate string
      const byDate = {};
      for (const occ of allOccs) {
        const key = String(occ.dueDate ?? '').slice(0, 10);
        if (!key) continue;
        (byDate[key] ??= []).push(occ);
      }

      const rawGroups = Object.entries(byDate)
        .map(([key, occs]) => {
          const dueDate = parseDate(key);
          if (!dueDate) return null;
          const statuses = occs.map(classify);
          const dominant = STATUS_PRIORITY.find((s) => statuses.includes(s)) ?? 'UPCOMING';
          return { dateStr: key, dueDate, pct: pct(dueDate), occs, dominant };
        })
        .filter(Boolean)
        .sort((a, b) => a.pct - b.pct);

      const groups = assignRows(rawGroups);

      const paidCount  = allOccs.filter((o) => o.status === 'PAID').length;
      const totalCount = allOccs.length;

      return {
        periodStart: pStart,
        periodEnd:   pEnd,
        todayPct:    todayVisible ? todayPct : null,
        groups,
        paidCount,
        totalCount,
      };
    }, [tileData]);

  if (!tileData || !periodStart) return null;

  const LEGEND = [
    { status: 'PAID',      key: 'legend_paid'    },
    { status: 'DUE_TODAY', key: 'legend_dueToday' },
    { status: 'DUE_SOON',  key: 'legend_dueSoon'  },
    { status: 'UPCOMING',  key: 'legend_upcoming' },
    { status: 'OVERDUE',   key: 'legend_overdue'  },
  ];

  return (
    // No overflow-hidden — tooltips need to escape upward freely.
    // border-radius applies to the card's own background without overflow-hidden.
    <div
      className="bg-[#13131f] border border-white/[0.06] rounded-[14px]"
      style={{ animation: 'fadeUp 0.35s ease both' }}
    >
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between px-5 pt-4 pb-3 border-b border-white/[0.05]">
        <div>
          <div className="text-[10px] font-bold tracking-[0.12em] uppercase text-white/35">
            {t('timeline.title')}
          </div>
          <div className="text-[10px] text-white/20 mt-0.5">
            {shortDate(periodStart, lang)} – {shortDate(periodEnd, lang)}
          </div>
        </div>
        <div className="text-[11px] text-white/30 font-mono tabular-nums">
          {t('timeline.paidOf', { paid: paidCount, total: totalCount })}
        </div>
      </div>

      {/* ── Timeline track ─────────────────────────────────────────────── */}
      <div className="px-5 py-4">
        <div
          className="relative select-none"
          style={{ height: TRACK_H }}
          role="img"
          aria-label={t('timeline.title')}
        >
          {/* Base axis line */}
          <div
            style={{
              position: 'absolute',
              left: 0,
              right: 0,
              top: AXIS_Y,
              height: 2,
              borderRadius: 999,
              background: 'rgba(255,255,255,0.07)',
            }}
          />

          {/* Progress fill: start → today */}
          {todayPct !== null && todayPct > 0 && (
            <div
              style={{
                position: 'absolute',
                top: AXIS_Y,
                height: 2,
                left: 0,
                width: `${todayPct}%`,
                borderRadius: 999,
                background:
                  'linear-gradient(to right, rgba(124,58,237,0.55), rgba(124,58,237,0.20))',
              }}
            />
          )}

          {/* Today marker — pill label + gradient vertical rule */}
          {todayPct !== null && (
            <div
              style={{
                position: 'absolute',
                left: `${todayPct}%`,
                top: 0,
                // bottom offset keeps the rule ending ~6 px below axis
                bottom: TRACK_H - AXIS_Y - 6,
                transform: 'translateX(-50%)',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                zIndex: 5,
                pointerEvents: 'none',
              }}
            >
              <div
                style={{
                  fontSize: 8,
                  fontWeight: 700,
                  letterSpacing: '0.08em',
                  textTransform: 'uppercase',
                  color: 'rgba(167,139,250,0.90)',
                  background: 'rgba(124,58,237,0.18)',
                  border: '1px solid rgba(124,58,237,0.38)',
                  borderRadius: 10,
                  padding: '2px 5px',
                  lineHeight: 1.4,
                  whiteSpace: 'nowrap',
                  marginBottom: 2,
                }}
              >
                {t('timeline.today')}
              </div>
              <div
                style={{
                  flex: 1,
                  width: 1.5,
                  background:
                    'linear-gradient(to bottom, rgba(124,58,237,0.65), rgba(124,58,237,0.15))',
                  borderRadius: 1,
                }}
              />
            </div>
          )}

          {/* Period start / end date labels */}
          <div
            style={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              fontSize: 9,
              color: 'rgba(255,255,255,0.20)',
              userSelect: 'none',
            }}
          >
            {shortDate(periodStart, lang)}
          </div>
          <div
            style={{
              position: 'absolute',
              bottom: 0,
              right: 0,
              fontSize: 9,
              color: 'rgba(255,255,255,0.20)',
              userSelect: 'none',
            }}
          >
            {shortDate(periodEnd, lang)}
          </div>

          {/* Empty state */}
          {groups.length === 0 && (
            <div
              style={{
                position: 'absolute',
                top: AXIS_Y - 12,
                left: '50%',
                transform: 'translateX(-50%)',
                fontSize: 11,
                color: 'rgba(255,255,255,0.20)',
                whiteSpace: 'nowrap',
              }}
            >
              {t('timeline.noPayments')}
            </div>
          )}

          {/* Payment chips (rendered as fragments — stem + pill as siblings) */}
          {groups.map((group) => (
            <PaymentChip
              key={group.dateStr}
              group={group}
              pct={group.pct}
              row={group.row}
              lang={lang}
              t={t}
            />
          ))}
        </div>
      </div>

      {/* ── Legend ─────────────────────────────────────────────────────── */}
      <div className="flex items-center flex-wrap gap-x-4 gap-y-1.5 px-5 pb-4">
        {LEGEND.map(({ status, key }) => {
          const sc = STATUS_STYLE[status];
          return (
            <div key={status} className="flex items-center gap-1.5" aria-hidden="true">
              <div
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  background: sc.color,
                  flexShrink: 0,
                }}
              />
              <span style={{ fontSize: 9, color: 'rgba(255,255,255,0.28)', lineHeight: 1 }}>
                {t(`timeline.${key}`)}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
