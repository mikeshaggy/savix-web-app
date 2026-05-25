'use client';
import { useSearchParams, useRouter, usePathname } from 'next/navigation';
import { useCallback, useMemo } from 'react';

/**
 * @typedef {'PAY_CYCLE' | 'LAST_PAY_CYCLE' | 'MONTHLY' | 'CUSTOM'} PeriodType
 *
 * @typedef {Object} AnalyticsPeriod
 * @property {PeriodType}    periodType
 * @property {string}        selectedMonth  - current yyyy-MM value
 * @property {string|null}   startDate      - yyyy-MM-dd, only set for CUSTOM
 * @property {string|null}   endDate        - yyyy-MM-dd, only set for CUSTOM
 * @property {string|null}   resolvedStart  - effective startDate to pass to the API
 * @property {string|null}   resolvedEnd    - effective endDate to pass to the API
 * @property {function(PeriodType): void} setPeriodType
 * @property {function(string): void}     setMonth
 * @property {function(string, string): void} setCustomDates
 */

const VALID = new Set(['PAY_CYCLE', 'LAST_PAY_CYCLE', 'MONTHLY', 'CUSTOM']);
const DEFAULT_TYPE = 'PAY_CYCLE';

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * URL search-param–driven period state for all analytics subpages.
 * Writing any setter calls router.replace so the URL updates without a
 * navigation — all subpages sharing the same params stay in sync.
 *
 * @returns {AnalyticsPeriod}
 */
export function useAnalyticsPeriod() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const rawType = searchParams.get('periodType');
  const periodType = /** @type {PeriodType} */ (rawType && VALID.has(rawType) ? rawType : DEFAULT_TYPE);
  const startDate     = searchParams.get('startDate') || null;
  const endDate       = searchParams.get('endDate')   || null;
  const selectedMonth = searchParams.get('month')     || currentYearMonth();

  // ─── writers ────────────────────────────────────────────────────────────────

  const updateParams = useCallback(
    (updates) => {
      const params = new URLSearchParams(searchParams.toString());
      for (const [k, v] of Object.entries(updates)) {
        if (v === null || v === undefined) params.delete(k);
        else params.set(k, v);
      }
      router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    },
    [searchParams, router, pathname]
  );

  const setPeriodType = useCallback(
    (type) => {
      if (type === 'CUSTOM') return; // CUSTOM is activated only via setCustomDates
      updateParams({ periodType: type, startDate: null, endDate: null });
    },
    [updateParams]
  );

  const setMonth = useCallback(
    (month) => updateParams({ periodType: 'MONTHLY', month, startDate: null, endDate: null }),
    [updateParams]
  );

  const setCustomDates = useCallback(
    (start, end) => updateParams({ periodType: 'CUSTOM', startDate: start, endDate: end }),
    [updateParams]
  );

  // ─── derived ─────────────────────────────────────────────────────────────────

  const resolvedStart = useMemo(() => {
    if (periodType === 'MONTHLY') return `${selectedMonth}-01`;
    if (periodType === 'CUSTOM')  return startDate;
    return null;
  }, [periodType, selectedMonth, startDate]);

  const resolvedEnd = useMemo(() => {
    if (periodType === 'CUSTOM') return endDate;
    return null;
  }, [periodType, endDate]);

  return {
    periodType,
    selectedMonth,
    startDate,
    endDate,
    resolvedStart,
    resolvedEnd,
    setPeriodType,
    setMonth,
    setCustomDates,
  };
}
