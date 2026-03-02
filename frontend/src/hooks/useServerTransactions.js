'use client';
import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useRouter, useSearchParams, usePathname } from 'next/navigation';
import { transactionApi, checkBackendHealth } from '@/lib/api';

const ALLOWED_SIZES = [10, 20, 50, 100];
const DEFAULT_SIZE = 10;
const ALLOWED_SORT_FIELDS = ['transactionDate', 'amount', 'title'];
const DEFAULT_SORT = 'transactionDate,desc';
const DEBOUNCE_MS = 400;

function parseIntSafe(val, fallback) {
  const n = parseInt(val, 10);
  return isNaN(n) ? fallback : n;
}

function sanitizeSize(val) {
  const n = parseIntSafe(val, DEFAULT_SIZE);
  return ALLOWED_SIZES.includes(n) ? n : DEFAULT_SIZE;
}

function sanitizeSort(val) {
  if (!val) return DEFAULT_SORT;
  const [field, dir] = val.split(',');
  if (!ALLOWED_SORT_FIELDS.includes(field)) return DEFAULT_SORT;
  const direction = dir === 'asc' ? 'asc' : 'desc';
  return `${field},${direction}`;
}

function parseArrayParam(val) {
  if (!val) return [];
  if (Array.isArray(val)) return val.filter(Boolean);
  return val.split(',').filter(Boolean);
}

export function useServerTransactions() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const initialState = useMemo(() => {
    const sp = searchParams;
    return {
      page: Math.max(parseIntSafe(sp.get('page'), 0), 0),
      size: sanitizeSize(sp.get('size')),
      sort: sanitizeSort(sp.get('sort')),
      types: sp.getAll('type').filter(Boolean),
      categoryIds: sp.getAll('categoryId').map(Number).filter(n => !isNaN(n)),
      importances: sp.getAll('importance').filter(Boolean),
      startDate: sp.get('startDate') || '',
      endDate: sp.get('endDate') || '',
      q: sp.get('q') || '',
    };
  }, []);

  const [page, setPage] = useState(initialState.page);
  const [size, setSize] = useState(initialState.size);
  const [sort, setSort] = useState(initialState.sort);
  const [types, setTypes] = useState(initialState.types);
  const [categoryIds, setCategoryIds] = useState(initialState.categoryIds);
  const [importances, setImportances] = useState(initialState.importances);
  const [startDate, setStartDate] = useState(initialState.startDate);
  const [endDate, setEndDate] = useState(initialState.endDate);
  const [q, setQ] = useState(initialState.q);

  const [searchInput, setSearchInput] = useState(initialState.q);

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const abortRef = useRef(null);
  const debounceTimerRef = useRef(null);
  const isFirstRender = useRef(true);

  useEffect(() => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }
    debounceTimerRef.current = setTimeout(() => {
      const trimmed = searchInput.trim();
      if (trimmed.length >= 2 || trimmed.length === 0) {
        setQ(trimmed || '');
        setPage(0);
      }
    }, DEBOUNCE_MS);
    return () => clearTimeout(debounceTimerRef.current);
  }, [searchInput]);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    
    const params = new URLSearchParams();
    if (page > 0) params.set('page', String(page));
    if (size !== DEFAULT_SIZE) params.set('size', String(size));
    if (sort !== DEFAULT_SORT) params.set('sort', sort);
    types.forEach(t => params.append('type', t));
    categoryIds.forEach(id => params.append('categoryId', String(id)));
    importances.forEach(imp => params.append('importance', imp));
    if (startDate) params.set('startDate', startDate);
    if (endDate) params.set('endDate', endDate);
    if (q) params.set('q', q);

    const qs = params.toString();
    const newUrl = qs ? `${pathname}?${qs}` : pathname;
    router.replace(newUrl, { scroll: false });
  }, [page, size, sort, types, categoryIds, importances, startDate, endDate, q, pathname, router]);

  const fetchData = useCallback(async () => {
    if (abortRef.current) {
      abortRef.current.abort();
    }
    const controller = new AbortController();
    abortRef.current = controller;

    try {
      setLoading(true);
      setError(null);

      const backendAvailable = await checkBackendHealth();
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      if (controller.signal.aborted) return;

      const response = await transactionApi.getTransactions({
        page,
        size,
        sort,
        type: types.length > 0 ? types : undefined,
        categoryId: categoryIds.length > 0 ? categoryIds : undefined,
        importance: importances.length > 0 ? importances : undefined,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
        q: q || undefined,
      });

      if (controller.signal.aborted) return;

      setData(response);
    } catch (err) {
      if (!controller.signal.aborted) {
        console.error('Failed to fetch transactions:', err);
        setError(err.message || 'Failed to load transactions');
      }
    } finally {
      if (!controller.signal.aborted) {
        setLoading(false);
      }
    }
  }, [page, size, sort, types, categoryIds, importances, startDate, endDate, q]);

  useEffect(() => {
    fetchData();
    return () => {
      if (abortRef.current) {
        abortRef.current.abort();
      }
    };
  }, [fetchData]);

  const updateTypes = useCallback((newTypes) => {
    setTypes(newTypes);
    setPage(0);
  }, []);

  const updateCategoryIds = useCallback((newIds) => {
    setCategoryIds(newIds);
    setPage(0);
  }, []);

  const updateImportances = useCallback((newImps) => {
    setImportances(newImps);
    setPage(0);
  }, []);

  const updateStartDate = useCallback((date) => {
    setStartDate(date);
    setPage(0);
  }, []);

  const updateEndDate = useCallback((date) => {
    setEndDate(date);
    setPage(0);
  }, []);

  const updateSort = useCallback((newSort) => {
    setSort(sanitizeSort(newSort));
    setPage(0);
  }, []);

  const updateSize = useCallback((newSize) => {
    setSize(sanitizeSize(newSize));
    setPage(0);
  }, []);

  const clearSearch = useCallback(() => {
    setSearchInput('');
    setQ('');
    setPage(0);
  }, []);

  const clearAllFilters = useCallback(() => {
    setTypes([]);
    setCategoryIds([]);
    setImportances([]);
    setStartDate('');
    setEndDate('');
    setSearchInput('');
    setQ('');
    setSort(DEFAULT_SORT);
    setSize(DEFAULT_SIZE);
    setPage(0);
  }, []);

  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (types.length > 0) count++;
    if (categoryIds.length > 0) count++;
    if (importances.length > 0) count++;
    if (startDate || endDate) count++;
    if (q) count++;
    return count;
  }, [types, categoryIds, importances, startDate, endDate, q]);

  return {
    // Data
    data,
    items: data?.items || [],
    totalElements: data?.totalElements || 0,
    totalPages: data?.totalPages || 0,
    hasNext: data?.hasNext || false,
    hasPrevious: data?.hasPrevious || false,

    // State
    loading,
    error,

    // Current params
    page,
    size,
    sort,
    types,
    categoryIds,
    importances,
    startDate,
    endDate,
    q,
    searchInput,
    activeFilterCount,

    // Setters
    setPage,
    setSearchInput,
    updateTypes,
    updateCategoryIds,
    updateImportances,
    updateStartDate,
    updateEndDate,
    updateSort,
    updateSize,
    clearSearch,
    clearAllFilters,

    // Actions
    refetch: fetchData,
  };
}

export default useServerTransactions;
