'use client';

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Activity, AlertCircle, ArrowDownRight, ArrowUpRight, Minus, RefreshCw, TrendingDown, TrendingUp, Wallet } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useLanguage } from '@/i18n';
import { useAppContext } from '@/contexts/AppContext';
import { useWallets } from '@/contexts/WalletContext';
import { transactionApi, walletApi as api } from '@/lib/api';
import { formatCurrency, formatDate } from '@/utils/helpers';
import PaginationFooter from '@/components/common/PaginationFooter';
import TransactionModal from '@/components/modals/TransactionModal';

const formatSourceType = (sourceType) => {
    if (!sourceType) return 'UNKNOWN';

    return sourceType
        .toString()
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/(^|\s)\S/g, (char) => char.toUpperCase());
};

const getSignedAmountTextClass = (amount) => {
    if (amount > 0) return 'text-green-400';
    if (amount < 0) return 'text-red-400';
    return 'text-white/70';
};

const getSignedAmountBadgeClass = (amount) => {
    if (amount > 0) return 'bg-green-500/10 border-green-500/25 text-green-300';
    if (amount < 0) return 'bg-red-500/10 border-red-500/25 text-red-300';
    return 'bg-white/[0.04] border-white/[0.08] text-white/70';
};

const getSummaryTrendIcon = (value) => {
    if (value > 0) return TrendingUp;
    if (value < 0) return TrendingDown;
    return Minus;
};

const toNumber = (value, fallback = 0) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
};

const formatAxisCurrency = (value, lang) => {
    const locale = lang === 'pl' ? 'pl-PL' : 'en-US';

    return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: 'PLN',
        currencyDisplay: 'code',
        notation: 'compact',
        maximumFractionDigits: 1,
    }).format(toNumber(value, 0));
};

const formatChartDate = (value, lang) => {
    if (!value) return '';

    const dateObj = new Date(value);
    if (Number.isNaN(dateObj.getTime())) {
        return formatDate(value, lang);
    }

    const locale = lang === 'pl' ? 'pl-PL' : 'en-US';
    return dateObj.toLocaleDateString(locale, {
        month: 'short',
        day: 'numeric',
    });
};

const getDateTickInterval = (points) => {
    if (points <= 8) return 0;
    if (points <= 16) return 1;
    if (points <= 28) return 2;
    return Math.max(3, Math.ceil(points / 7) - 1);
};

const BALANCE_HISTORY_RANGE_OPTIONS = [
    { key: '7D', label: '7D' },
    { key: '30D', label: '30D' },
    { key: '90D', label: '90D' },
    { key: '1Y', label: '1Y' },
    { key: 'ALL', translationKey: 'filters.allTime' },
    { key: 'CUSTOM', translationKey: 'filters.customRange' },
];

const ALLOWED_SIZES = [10, 20, 50, 100];
const DEFAULT_SIZE = 10;
const VALID_RANGES = BALANCE_HISTORY_RANGE_OPTIONS.map((o) => o.key);
const MIN_Y_AXIS_SPAN = 600;
const Y_AXIS_PADDING_RATIO = 0.15;

const formatDateForInput = (value) => {
    const date = value ? new Date(value) : new Date();

    if (Number.isNaN(date.getTime())) {
        return '';
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
};

const buildTrailingDaysRange = (days) => {
    const end = new Date();
    end.setHours(12, 0, 0, 0);

    const start = new Date(end);
    start.setDate(start.getDate() - (days - 1));

    return {
        from: formatDateForInput(start),
        to: formatDateForInput(end),
    };
};

const resolveRangeParams = (selectedRange, customRange) => {
    switch (selectedRange) {
        case '7D':
            return buildTrailingDaysRange(7);
        case '30D':
            return buildTrailingDaysRange(30);
        case '90D':
            return buildTrailingDaysRange(90);
        case '1Y':
            return buildTrailingDaysRange(365);
        case 'ALL':
            return {};
        case 'CUSTOM':
            if (!customRange?.from || !customRange?.to) {
                return null;
            }

            return {
                from: customRange.from,
                to: customRange.to,
            };
        default:
            return {};
    }
};

const formatRangeLabel = (params, lang, t) => {
    if (!params?.from && !params?.to) {
        return t('filters.allTime');
    }

    if (params?.from && params?.to) {
        return `${formatDate(params.from, lang)} - ${formatDate(params.to, lang)}`;
    }

    if (params?.from) {
        return `${t('filters.fromDate')}: ${formatDate(params.from, lang)}`;
    }

    return `${t('filters.toDate')}: ${formatDate(params.to, lang)}`;
};

function BalanceHistoryChart({ chartData, lang, t, rangeLabel, emptyTitle, emptyHint }) {
    const normalized = useMemo(() => {
        if (!Array.isArray(chartData) || chartData.length === 0) {
            return [];
        }

        const sorted = [...chartData]
            .map((point) => ({
                date: point?.date,
                closingBalance: toNumber(point?.closingBalance, 0),
                netChange: point?.netChange == null ? null : toNumber(point?.netChange, 0),
            }))
            .filter((point) => point.date)
            .sort((a, b) => new Date(a.date) - new Date(b.date));

        return sorted;
    }, [chartData]);

    const chartMeta = useMemo(() => {
        if (!normalized.length) {
            return {
                data: [],
                yDomain: [-1, 1],
                xTickInterval: 0,
            };
        }

        const balances = normalized.map((point) => toNumber(point.closingBalance, 0));
        const minBalance = Math.min(...balances);
        const maxBalance = Math.max(...balances);
        const rawSpan = maxBalance - minBalance;
        const effectiveSpan = Math.max(rawSpan, MIN_Y_AXIS_SPAN);
        const padding = effectiveSpan * Y_AXIS_PADDING_RATIO;
        const center = (maxBalance + minBalance) / 2;
        const halfSpan = effectiveSpan / 2;
        const yMin = center - halfSpan - padding;
        const yMax = center + halfSpan + padding;

        return {
            data: normalized,
            yDomain: [yMin, yMax],
            xTickInterval: getDateTickInterval(normalized.length),
        };
    }, [normalized]);

    if (normalized.length === 0) {
        return (
            <div className="h-[268px] rounded-[16px] border border-white/[0.08] bg-[#111120] px-6 flex items-center justify-center">
                <div className="text-center">
                    <div className="font-mono text-[11px] uppercase tracking-[0.14em] text-white/35 mb-1">
                        {t('wallet.balanceOverTime')}
                    </div>
                    <div className="text-[16px] font-semibold text-white/85 mb-1.5">{emptyTitle}</div>
                    <div className="text-[13px] text-white/45">{emptyHint}</div>
                </div>
            </div>
        );
    }

    const customTooltip = ({ active, payload, label }) => {
        if (!active || !payload?.length) return null;

        const point = payload[0]?.payload;
        const value = toNumber(point?.closingBalance, 0);
        const netChange = point?.netChange;
        const hasChange = Number.isFinite(netChange);
        const changeClass = toNumber(netChange, 0) >= 0 ? 'text-emerald-300' : 'text-rose-300';
        const changePrefix = toNumber(netChange, 0) >= 0 ? '+' : '';

        return (
            <div className="min-w-[190px] rounded-[12px] border border-purple-500/20 bg-[#131325]/96 px-3.5 py-2.5 shadow-[0_16px_36px_rgba(0,0,0,0.5)] backdrop-blur-sm">
                <div className="text-[11px] text-white/70 mb-1.5">{formatDate(label, lang)}</div>
                <div className="flex items-start justify-between gap-4">
                    <div className="text-[11px] text-white/55">{t('wallet.balanceAfter')}</div>
                    <div className="font-mono tabular-nums text-[12px] font-semibold text-white">
                        {formatCurrency(value, lang)}
                    </div>
                </div>
                {hasChange && (
                    <div className="flex items-start justify-between gap-4 mt-1.5 border-t border-white/[0.08] pt-1.5">
                        <div className="text-[11px] text-white/55">{t('wallet.netChange')}</div>
                        <div className={`font-mono tabular-nums text-[12px] font-semibold ${changeClass}`}>
                            {changePrefix}{formatCurrency(netChange, lang)}
                        </div>
                    </div>
                )}
            </div>
        );
    };

    return (
        <div className="rounded-[18px] border border-white/[0.08] bg-[#111120] p-4 sm:p-5">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between mb-4">
                <div>
                    <div className="font-mono text-[11px] uppercase tracking-[0.14em] text-[#9a9ac0]">
                        {t('wallet.balanceOverTime')}
                    </div>
                    <div className="text-[12px] text-white/48 mt-1">
                        {t('wallet.latestBalance')}
                    </div>
                </div>
                <div className="text-[11px] text-white/40 font-medium">
                    {rangeLabel}
                </div>
            </div>

            <div className="h-[280px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                    <AreaChart
                        data={chartMeta.data}
                        margin={{ top: 6, right: 12, left: 16, bottom: 14 }}
                    >
                        <defs>
                            <linearGradient id="balanceGradient" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="0%" stopColor="#a855f7" stopOpacity={0.32} />
                                <stop offset="60%" stopColor="#a855f7" stopOpacity={0.1} />
                                <stop offset="100%" stopColor="#a855f7" stopOpacity={0} />
                            </linearGradient>
                        </defs>
                        <CartesianGrid
                            stroke="rgba(255,255,255,0.05)"
                            strokeDasharray="3 6"
                            vertical={false}
                        />
                        <XAxis
                            dataKey="date"
                            axisLine={false}
                            tickLine={false}
                            minTickGap={56}
                            interval={chartMeta.xTickInterval}
                            tickMargin={10}
                            tick={{ fill: 'rgba(255,255,255,0.62)', fontSize: 11 }}
                            tickFormatter={(value) => formatChartDate(value, lang)}
                        />
                        <YAxis
                            axisLine={false}
                            tickLine={false}
                            width={106}
                            domain={chartMeta.yDomain}
                            tickCount={5}
                            tickMargin={10}
                            tick={{ fill: 'rgba(255,255,255,0.65)', fontSize: 11 }}
                            tickFormatter={(value) => formatAxisCurrency(value, lang)}
                        />
                        <Tooltip
                            content={customTooltip}
                            cursor={{ stroke: 'rgba(168,85,247,0.35)', strokeWidth: 1 }}
                            isAnimationActive={false}
                        />
                        <Area
                            type="linear"
                            dataKey="closingBalance"
                            style={{ filter: 'drop-shadow(0 0 6px rgba(139,92,246,0.35))' }}
                            stroke="#a855f7"
                            strokeWidth={2.8}
                            fill="url(#balanceGradient)"
                            dot={(props) => {
                                const { cx, cy, index } = props;
                                if (index !== chartMeta.data.length - 1) return null;

                                return (
                                    <g>
                                        <circle cx={cx} cy={cy} r={7} fill="#a855f7" fillOpacity={0.18} />
                                        <circle cx={cx} cy={cy} r={4} fill="#a855f7" stroke="#13131f" strokeWidth={2} />
                                    </g>
                                );
                            }}
                            activeDot={{ r: 4, stroke: '#a855f7', strokeWidth: 2, fill: '#13131f' }}
                            isAnimationActive={false}
                            connectNulls
                        />
                    </AreaChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

function SummaryCard({ title, value, accentClass, hint, valueClassName = 'text-white' }) {
    return (
        <div className="rounded-[12px] border border-white/[0.06] bg-[#13131f] px-4 py-3.5">
            <div className={`h-[2px] w-7 rounded-full ${accentClass} mb-3`} />
            <div className="text-[10px] tracking-[0.12em] uppercase text-white/35 mb-2">{title}</div>
            <div className={`font-mono tabular-nums text-[19px] font-semibold tracking-[-0.02em] leading-none ${valueClassName}`}>
                {value}
            </div>
            {hint && <div className="text-[10px] text-white/35 mt-2 leading-tight">{hint}</div>}
        </div>
    );
}

function BalanceHistoryRangeSelector({
    selectedRange,
    customRangeDraft,
    customRangeError,
    appliedRangeLabel,
    onRangeSelect,
    onCustomRangeChange,
    onApplyCustomRange,
    t,
}) {
    return (
        <div className="rounded-[18px] border border-white/[0.08] bg-[#111120] p-4 sm:p-5">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <div className="font-mono text-[11px] uppercase tracking-[0.14em] text-[#9a9ac0]">
                        {t('filters.dateRange')}
                    </div>
                    <div className="text-[13px] text-white/50 mt-1">
                        {appliedRangeLabel}
                    </div>
                </div>

                <div className="flex flex-col items-start gap-3 lg:items-end">
                    <div className="flex flex-wrap gap-2">
                        {BALANCE_HISTORY_RANGE_OPTIONS.map((option) => {
                            const isActive = selectedRange === option.key;
                            const label = option.translationKey ? t(option.translationKey) : option.label;

                            return (
                                <button
                                    key={option.key}
                                    type="button"
                                    onClick={() => onRangeSelect(option.key)}
                                    className={`appearance-none rounded-[10px] border px-[13px] py-[7px] text-[13px] font-medium transition-all whitespace-nowrap ${
                                        isActive
                                            ? 'bg-[rgba(124,58,237,0.18)] border-[rgba(124,58,237,0.4)] text-purple-200 shadow-[0_10px_24px_rgba(124,58,237,0.16)]'
                                            : 'bg-[#0e0e1c] border-white/[0.055] text-white/55 hover:border-white/[0.12] hover:text-white'
                                    }`}
                                >
                                    {label}
                                </button>
                            );
                        })}
                    </div>

                    {selectedRange === 'CUSTOM' && (
                        <div className="flex w-full flex-col gap-2 rounded-[12px] border border-white/[0.08] bg-[#111120] p-3 sm:w-auto sm:min-w-[320px]">
                            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                                <input
                                    type="date"
                                    value={customRangeDraft.from}
                                    onChange={(event) => onCustomRangeChange('from', event.target.value)}
                                    className="bg-[#131325] border border-white/[0.055] rounded-[10px] px-3.5 py-[7px] text-[13px] text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] [color-scheme:dark]"
                                />
                                <span className="text-white/22 text-xs hidden sm:inline">→</span>
                                <input
                                    type="date"
                                    value={customRangeDraft.to}
                                    onChange={(event) => onCustomRangeChange('to', event.target.value)}
                                    className="bg-[#131325] border border-white/[0.055] rounded-[10px] px-3.5 py-[7px] text-[13px] text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] [color-scheme:dark]"
                                />
                                <button
                                    type="button"
                                    onClick={onApplyCustomRange}
                                    disabled={Boolean(customRangeError) || !customRangeDraft.from || !customRangeDraft.to}
                                    className="rounded-[10px] bg-[#7c3aed] px-3.5 py-[7px] text-[12px] font-semibold text-white transition-all hover:bg-[#8b5cf6] disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    {t('common.apply')}
                                </button>
                            </div>

                            {customRangeError && (
                                <div className="text-[12px] text-red-300">{customRangeError}</div>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default function BalanceHistoryPage() {
    const t = useTranslations();
    const { lang } = useLanguage();
    const { currentWallet } = useWallets();
    const { categories, onUpdateTransaction } = useAppContext();

    const router = useRouter();
    const pathname = usePathname();
    const searchParams = useSearchParams();

    const initialState = useMemo(() => {
        const sp = searchParams;
        const rangeParam = sp.get('range');
        const range = VALID_RANGES.includes(rangeParam) ? rangeParam : '7D';
        const fromParam = sp.get('from') || '';
        const toParam = sp.get('to') || '';
        const customRangeInit =
            range === 'CUSTOM' && fromParam && toParam
                ? { from: fromParam, to: toParam }
                : null;
        const pageParam = parseInt(sp.get('page'), 10);
        const parsedPage = Number.isFinite(pageParam) && pageParam > 0 ? pageParam : 0;
        const sizeParam = parseInt(sp.get('size'), 10);
        const parsedSize = ALLOWED_SIZES.includes(sizeParam) ? sizeParam : DEFAULT_SIZE;
        return {
            range,
            customRange: customRangeInit,
            customRangeDraft: customRangeInit ?? { from: '', to: '' },
            page: parsedPage,
            size: parsedSize,
        };
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const [historyData, setHistoryData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [selectedRange, setSelectedRange] = useState(initialState.range);
    const [customRange, setCustomRange] = useState(initialState.customRange);
    const [customRangeDraft, setCustomRangeDraft] = useState(initialState.customRangeDraft);
    const [page, setPage] = useState(initialState.page);
    const [size, setSize] = useState(initialState.size);
    const [timelineLoading, setTimelineLoading] = useState(false);
    const [showTransactionModal, setShowTransactionModal] = useState(false);
    const [editingTransaction, setEditingTransaction] = useState(null);
    const [activeTransactionChipId, setActiveTransactionChipId] = useState(null);

    const isFirstRender = useRef(true);
    const previousQueryKeyRef = useRef(null);

    const requestParams = useMemo(
        () => resolveRangeParams(selectedRange, customRange),
        [customRange, selectedRange]
    );

    const customRangeError = useMemo(() => {
        if (!customRangeDraft.from || !customRangeDraft.to) {
            return null;
        }

        if (customRangeDraft.from > customRangeDraft.to) {
            return t('wallet.invalidBalanceHistoryRange');
        }

        return null;
    }, [customRangeDraft.from, customRangeDraft.to, t]);

    const fetchHistory = useCallback(async (walletId, params, pagination, timelineOnly = false) => {
        if (timelineOnly) {
            setTimelineLoading(true);
        } else {
            setLoading(true);
            setError(null);
        }

        try {
            const response = await api.getWalletBalanceHistory(walletId, {
                ...params,
                page: pagination.page,
                size: pagination.size,
            });
            setHistoryData(response ?? null);

            const responsePage = response?.timelinePagination?.currentPage;
            if (Number.isInteger(responsePage) && responsePage !== pagination.page) {
                setPage(responsePage);
            }
        } catch (err) {
            console.error('Failed to load wallet balance history:', err);
            setError(err?.message || t('wallet.balanceHistoryLoadError'));
            if (!timelineOnly) {
                setHistoryData(null);
            }
        } finally {
            if (timelineOnly) {
                setTimelineLoading(false);
            } else {
                setLoading(false);
            }
        }
    }, [t]);

    const requestFrom = requestParams?.from ?? '';
    const requestTo = requestParams?.to ?? '';

    useEffect(() => {
        setPage(0);
    }, [currentWallet?.id, requestFrom, requestTo]);

    useEffect(() => {
        if (!currentWallet?.id) {
            setHistoryData(null);
            setError(null);
            setLoading(false);
            setTimelineLoading(false);
            return;
        }

        if (requestParams === null) {
            return;
        }

        const queryKey = `${currentWallet.id}|${requestFrom}|${requestTo}`;
        const queryChanged = previousQueryKeyRef.current !== queryKey;
        previousQueryKeyRef.current = queryKey;

        const shouldLoadTimelineOnly = !queryChanged;

        fetchHistory(
            currentWallet.id,
            requestParams,
            { page, size },
            shouldLoadTimelineOnly
        );
    }, [
        currentWallet?.id,
        fetchHistory,
        page,
        requestFrom,
        requestParams,
        requestTo,
        size,
    ]);

    useEffect(() => {
        if (isFirstRender.current) {
            isFirstRender.current = false;
            return;
        }

        const params = new URLSearchParams();
        if (selectedRange !== '7D') params.set('range', selectedRange);
        if (selectedRange === 'CUSTOM' && customRange?.from) params.set('from', customRange.from);
        if (selectedRange === 'CUSTOM' && customRange?.to) params.set('to', customRange.to);
        if (page > 0) params.set('page', String(page));
        if (size !== DEFAULT_SIZE) params.set('size', String(size));

        const qs = params.toString();
        const newUrl = qs ? `${pathname}?${qs}` : pathname;
        router.replace(newUrl, { scroll: false });
    }, [selectedRange, customRange, page, size, pathname, router]);

    const handleRangeSelect = useCallback((rangeKey) => {
        if (rangeKey === 'CUSTOM') {
            setSelectedRange('CUSTOM');
            setCustomRangeDraft((currentDraft) => ({
                from: currentDraft.from || customRange?.from || '',
                to: currentDraft.to || customRange?.to || formatDateForInput(new Date()),
            }));
            return;
        }

        setSelectedRange(rangeKey);
    }, [customRange?.from, customRange?.to]);

    const handleCustomRangeChange = useCallback((field, value) => {
        setCustomRangeDraft((currentDraft) => ({
            ...currentDraft,
            [field]: value,
        }));
    }, []);

    const handleApplyCustomRange = useCallback(() => {
        if (customRangeError || !customRangeDraft.from || !customRangeDraft.to) {
            return;
        }

        setCustomRange({
            from: customRangeDraft.from,
            to: customRangeDraft.to,
        });
        setSelectedRange('CUSTOM');
    }, [customRangeDraft.from, customRangeDraft.to, customRangeError]);

    const handlePageChange = useCallback((nextPage) => {
        setPage(Math.max(0, nextPage));
    }, []);

    const handleSizeChange = useCallback((nextSize) => {
        setSize(nextSize);
        setPage(0);
    }, []);

    const handleRetry = useCallback(() => {
        if (!currentWallet?.id || requestParams === null) {
            return;
        }

        fetchHistory(currentWallet.id, requestParams, { page, size }, false);
    }, [currentWallet?.id, fetchHistory, page, requestParams, size]);

    const openTransactionEditModal = useCallback(async (transactionId) => {
        if (transactionId == null || activeTransactionChipId != null) {
            return;
        }

        setActiveTransactionChipId(transactionId);
        try {
            const transaction = await transactionApi.getTransactionById(transactionId);
            if (!transaction) {
                throw new Error(t('wallet.transactionLoadError'));
            }

            setEditingTransaction(transaction);
            setShowTransactionModal(true);
        } catch (err) {
            console.error('Failed to open transaction edit modal from balance history:', err);
            setError(err?.message || t('wallet.transactionLoadError'));
        } finally {
            setActiveTransactionChipId(null);
        }
    }, [activeTransactionChipId, t]);

    const handleCloseTransactionModal = useCallback(() => {
        setShowTransactionModal(false);
        setEditingTransaction(null);
    }, []);

    const handleSaveTransaction = useCallback(async (transactionData, transactionId) => {
        if (!transactionId) {
            return;
        }

        try {
            await onUpdateTransaction(transactionId, transactionData);
            handleCloseTransactionModal();

            if (currentWallet?.id && requestParams !== null) {
                await fetchHistory(currentWallet.id, requestParams, { page, size }, false);
            }
        } catch (err) {
            console.error('Failed to save transaction from balance history:', err);
            throw err;
        }
    }, [currentWallet?.id, fetchHistory, handleCloseTransactionModal, onUpdateTransaction, page, requestParams, size]);

    const timelineGroups = useMemo(() => {
        return Array.isArray(historyData?.timeline) ? historyData.timeline : [];
    }, [historyData?.timeline]);

    const chartData = useMemo(() => {
        return Array.isArray(historyData?.chart) ? historyData.chart : [];
    }, [historyData?.chart]);

    const timelinePagination = useMemo(() => {
        const pagination = historyData?.timelinePagination;
        return {
            currentPage: pagination?.currentPage ?? page,
            pageSize: pagination?.pageSize ?? size,
            totalElements: pagination?.totalElements ?? 0,
            totalPages: pagination?.totalPages ?? 0,
            hasNext: pagination?.hasNext ?? false,
            hasPrevious: pagination?.hasPrevious ?? false,
        };
    }, [historyData?.timelinePagination, page, size]);

    const appliedRangeLabel = useMemo(() => {
        const params = requestParams === null ? customRange : requestParams;
        return formatRangeLabel(params, lang, t);
    }, [customRange, lang, requestParams, t]);

    const displayedCurrentBalance = historyData?.currentBalance ?? currentWallet?.balance ?? 0;
    const latestBalance = historyData?.summary?.latestBalance ?? null;
    const highestBalance = historyData?.summary?.highestBalance ?? null;
    const lowestBalance = historyData?.summary?.lowestBalance ?? null;
    const entriesCount = historyData?.summary?.entriesCount ?? 0;
    const netChange = historyData?.summary?.netChange ?? 0;
    const NetChangeIcon = getSummaryTrendIcon(toNumber(netChange, 0));
    const hasData = entriesCount > 0;
    const isFilteredRange = Boolean(requestParams?.from || requestParams?.to);
    const emptyStateTitle = isFilteredRange ? t('wallet.noBalanceHistoryInRange') : t('wallet.noBalanceHistory');
    const emptyStateHint = isFilteredRange
        ? t('wallet.noBalanceHistoryInRangeHint', { range: appliedRangeLabel })
        : t('wallet.noBalanceHistoryHint');

    const getSummaryValue = useCallback((value) => {
        if (value == null) {
            return t('wallet.noDataShort');
        }

        return formatCurrency(value, lang);
    }, [lang, t]);

    if (!currentWallet) {
        return (
            <div className="flex flex-col gap-[18px]">
                <div>
                    <div className="text-xl sm:text-[26px] font-bold tracking-[-0.4px]">{t('nav.balanceHistory')}</div>
                    <div className="text-[13px] sm:text-[14px] text-white/22 mt-[3px]">
                        {t('wallet.balanceHistorySubtitleNoWallet')}
                    </div>
                </div>

                <div className="bg-[#13131f] border border-white/[0.06] rounded-[14px] px-6 py-10 text-center">
                    <Wallet className="w-14 h-14 text-[#6b6b8a] mx-auto mb-4" />
                    <h2 className="text-lg font-semibold text-white mb-2">{t('wallet.noWalletSelected')}</h2>
                    <p className="text-[14px] text-[#6b6b8a] mb-5">
                        {t('wallet.balanceHistorySelectWalletHint')}
                    </p>
                    <Link
                        href="/wallets"
                        className="inline-flex items-center justify-center bg-gradient-to-br from-[#7c3aed] to-[#a855f7] border-none rounded-[8px] px-4 py-2 text-white text-[13.5px] font-medium cursor-pointer shadow-[0_4px_16px_rgba(124,58,237,0.25)] transition-all hover:opacity-90 hover:-translate-y-[1px]"
                    >
                        {t('wallet.goToWallets')}
                    </Link>
                </div>
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-[18px]">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <div className="text-xl sm:text-[26px] font-bold tracking-[-0.4px]">{t('nav.balanceHistory')}</div>
                    <div className="text-[13px] sm:text-[14px] text-white/22 mt-[3px]">
                        {t('wallet.balanceHistorySubtitleSelected', { walletName: currentWallet.name })}
                    </div>
                </div>
                <div className="rounded-[12px] border border-white/[0.08] bg-[#13131f] px-4 py-3 text-right">
                    <div className="text-[11px] text-white/35 mb-[3px]">{currentWallet.name}</div>
                    <div className="font-mono text-[10px] uppercase tracking-[1.5px] text-[#7d7d9c] mb-1">
                        {t('dashboard.currentBalance')}
                    </div>
                    <div className="font-mono tabular-nums text-[21px] font-semibold text-white tracking-[-0.3px]">
                        {formatCurrency(displayedCurrentBalance, lang)}
                    </div>
                </div>
            </div>

            <BalanceHistoryRangeSelector
                selectedRange={selectedRange}
                customRangeDraft={customRangeDraft}
                customRangeError={customRangeError}
                appliedRangeLabel={appliedRangeLabel}
                onRangeSelect={handleRangeSelect}
                onCustomRangeChange={handleCustomRangeChange}
                onApplyCustomRange={handleApplyCustomRange}
                t={t}
            />

            {!loading && (
                <BalanceHistoryChart
                    chartData={chartData}
                    lang={lang}
                    t={t}
                    rangeLabel={appliedRangeLabel}
                    emptyTitle={emptyStateTitle}
                    emptyHint={emptyStateHint}
                />
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                <SummaryCard
                    title={t('wallet.latestBalance')}
                    value={getSummaryValue(latestBalance)}
                    accentClass="bg-purple-400"
                    valueClassName={latestBalance != null && toNumber(latestBalance, 0) < 0 ? 'text-red-400' : 'text-purple-400'}
                />
                <SummaryCard
                    title={t('wallet.highestBalance')}
                    value={getSummaryValue(highestBalance)}
                    accentClass="bg-green-400"
                    valueClassName={highestBalance != null && toNumber(highestBalance, 0) < 0 ? 'text-red-400' : 'text-green-400'}
                />
                <SummaryCard
                    title={t('wallet.lowestBalance')}
                    value={getSummaryValue(lowestBalance)}
                    accentClass="bg-amber-400"
                    valueClassName={lowestBalance != null && toNumber(lowestBalance, 0) < 0 ? 'text-red-400' : 'text-yellow-400'}
                />
                <SummaryCard
                    title={t('wallet.entries')}
                    value={String(entriesCount)}
                    accentClass="bg-white/35"
                    hint={
                        hasData ? (
                            <span className="inline-flex items-center gap-1">
                                <NetChangeIcon className="w-3 h-3" />
                                {t('wallet.netChange')}: {netChange > 0 ? '+' : ''}{formatCurrency(netChange, lang)}
                            </span>
                        ) : null
                    }
                />
            </div>

            {error && (
                <div className="bg-red-500/[0.10] border border-red-400/25 rounded-[14px] px-5 py-4 flex items-center justify-between gap-4">
                    <div className="flex items-center gap-2.5 min-w-0">
                        <AlertCircle className="w-4 h-4 text-red-300 shrink-0" />
                        <span className="text-red-200 text-[14px] font-medium truncate">{error}</span>
                    </div>
                    <button
                        onClick={handleRetry}
                        className="text-[12px] font-semibold text-red-200 border border-red-300/25 px-3 py-1.5 rounded-[8px] hover:bg-red-400/10 transition-colors shrink-0"
                    >
                        {t('common.retry')}
                    </button>
                </div>
            )}

            {loading && !hasData && (
                <div className="space-y-3">
                    <div className="bg-[#0f0f1d] border border-white/[0.06] rounded-[16px] p-4 sm:p-5 animate-pulse">
                        <div className="h-3 w-36 bg-white/[0.06] rounded mb-4" />
                        <div className="h-[220px] bg-white/[0.04] rounded-[12px]" />
                    </div>
                    <div className="bg-[#0f0f1d] border border-white/[0.06] rounded-[16px] p-4 sm:p-5 animate-pulse">
                        <div className="h-3 w-40 bg-white/[0.06] rounded mb-4" />
                        {Array.from({ length: 4 }).map((_, i) => (
                            <div key={i} className="h-12 bg-white/[0.04] rounded-[10px] mb-2.5 last:mb-0" />
                        ))}
                    </div>
                </div>
            )}

            {!loading && !error && !hasData && (
                <div className="bg-[#13131f] border border-white/[0.08] rounded-[16px] px-6 py-10 text-center">
                    <div className="w-12 h-12 mx-auto rounded-[12px] border border-white/[0.1] bg-white/[0.03] flex items-center justify-center mb-4">
                        <Activity className="w-5 h-5 text-white/45" />
                    </div>
                    <h3 className="text-lg font-semibold text-white mb-2">{emptyStateTitle}</h3>
                    <p className="text-[14px] text-[#8a8aa7] max-w-[420px] mx-auto">
                        {emptyStateHint}
                    </p>
                </div>
            )}

            {hasData && (
                <div className="relative bg-[#0f0f1d] border border-white/[0.06] rounded-[18px] overflow-hidden p-4 sm:p-5">
                    <div className="font-mono text-[11px] uppercase tracking-[1.5px] text-[#8383a1] mb-5">
                        {t('wallet.activityTimeline')}
                    </div>

                    <div className="space-y-7">
                        {timelineGroups.map((group, groupIndex) => (
                            <section
                                key={`${group?.date}-${groupIndex}`}
                                className={`relative ${groupIndex > 0 ? 'pt-7 border-t border-white/[0.06]' : ''}`}
                            >
                                <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-[#19192a] border border-white/[0.08] text-[11px] uppercase tracking-[0.08em] text-white/55 mb-4">
                                    {formatDate(group?.date, lang)}
                                </div>

                                <div className="space-y-2.5">
                                    {(group?.entries || []).map((entry, entryIndex) => {
                                        const amountSigned = toNumber(entry?.amountSigned, 0);
                                        const balanceAfter = toNumber(entry?.balanceAfter, 0);
                                        const sourceLabel = entry?.sourceLabel || formatSourceType(entry?.sourceType);
                                        const transactionId = entry?.transactionId ?? (entry?.sourceType === 'TRANSACTION' ? entry?.sourceId : null);
                                        const walletEntryId = entry?.walletEntryId ?? entry?.id;
                                        const sourceReference = entry?.sourceReference || null;
                                        const hideSourceReference =
                                            sourceReference === `#${transactionId}` || sourceReference === `#${walletEntryId}`;
                                        const isPositive = amountSigned > 0;
                                        const isNegative = amountSigned < 0;

                                        return (
                                            <article
                                                key={entry?.id ?? `${group?.date}-${entryIndex}`}
                                                className="relative rounded-[12px] border border-white/[0.07] bg-[#141422] px-4 py-3.5"
                                            >
                                                <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-3 items-start">
                                                    <div className="min-w-0 flex items-start gap-2.5">
                                                        <div className={`w-8 h-8 shrink-0 rounded-[8px] border flex items-center justify-center ${
                                                                isPositive
                                                                    ? 'bg-green-500/10 border-green-500/20 text-green-400'
                                                                    : isNegative
                                                                        ? 'bg-red-500/10 border-red-500/20 text-red-400'
                                                                        : 'bg-white/[0.05] border-white/[0.08] text-white/40'
                                                            }`}>
                                                            {isPositive ? <ArrowUpRight className="w-4 h-4" /> : isNegative ? <ArrowDownRight className="w-4 h-4" /> : <Minus className="w-4 h-4" />}
                                                        </div>

                                                        <div className="min-w-0">
                                                            <div className="text-[14px] font-medium text-white/95 truncate">{sourceLabel}</div>
                                                            <div className="flex flex-wrap items-center gap-1.5 mt-1.5 text-[10px]">
                                                                {sourceReference && !hideSourceReference && (
                                                                    <span className="truncate text-white/40">{sourceReference}</span>
                                                                )}
                                                                {transactionId != null && (
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => openTransactionEditModal(transactionId)}
                                                                        title={t('wallet.transactionChipTitle', { id: transactionId })}
                                                                        disabled={activeTransactionChipId != null}
                                                                        className="inline-flex items-center rounded-full border border-violet-300/25 bg-violet-400/[0.07] px-2 py-0.5 text-[10px] font-medium text-violet-200 transition-colors hover:bg-violet-400/[0.14] disabled:cursor-wait disabled:opacity-70 cursor-pointer"
                                                                    >
                                                                        {t('wallet.transactionChipLabel', { id: transactionId })}
                                                                    </button>
                                                                )}
                                                                {walletEntryId != null && (
                                                                    <span
                                                                        title={t('wallet.entryChipTitle', { id: walletEntryId })}
                                                                        className="inline-flex items-center rounded-full border border-white/[0.09] bg-white/[0.04] px-2 py-0.5 text-[10px] font-medium text-white/45"
                                                                    >
                                                                        {t('wallet.entryChipLabel', { id: walletEntryId })}
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </div>
                                                    </div>

                                                    <div className="text-right shrink-0 min-w-[132px]">
                                                        <div className={`inline-flex items-center justify-end px-2.5 py-1 rounded-[8px] border font-mono tabular-nums text-[14px] sm:text-[15px] font-semibold tracking-[-0.01em] ${getSignedAmountBadgeClass(amountSigned)}`}>
                                                            <span className={getSignedAmountTextClass(amountSigned)}>
                                                                {amountSigned > 0 ? '+' : ''}
                                                                {formatCurrency(amountSigned, lang)}
                                                            </span>
                                                        </div>
                                                        <div className="text-[11px] text-white/40 mt-1.5 leading-none">
                                                            {t('wallet.balanceAfter')}:
                                                            <span className="font-mono tabular-nums text-white/55 ml-1">{formatCurrency(balanceAfter, lang)}</span>
                                                        </div>
                                                    </div>
                                                </div>
                                            </article>
                                        );
                                    })}
                                </div>
                            </section>
                        ))}
                    </div>

                    {timelinePagination.totalElements > 0 && (
                        <div className="mt-5">
                            <PaginationFooter
                                page={timelinePagination.currentPage}
                                size={timelinePagination.pageSize}
                                totalElements={timelinePagination.totalElements}
                                totalPages={timelinePagination.totalPages}
                                hasNext={timelinePagination.hasNext}
                                hasPrevious={timelinePagination.hasPrevious}
                                onPageChange={handlePageChange}
                                onSizeChange={handleSizeChange}
                            />
                        </div>
                    )}

                    {timelineLoading && (
                        <div className="absolute inset-0 bg-[#06060f]/50 z-10 flex items-center justify-center">
                            <div className="w-6 h-6 border-2 border-violet-400/30 border-t-violet-400 rounded-full animate-spin" />
                        </div>
                    )}
                </div>
            )}

            {loading && hasData && (
                <div className="fixed bottom-6 right-6 bg-[#13131f] border border-white/[0.08] rounded-[10px] px-3 py-2 flex items-center gap-2 shadow-[0_8px_32px_rgba(0,0,0,0.3)]">
                    <RefreshCw className="w-3.5 h-3.5 text-[#a855f7] animate-spin" />
                    <span className="text-[12px] text-white/70">{t('common.loading')}</span>
                </div>
            )}

            {showTransactionModal && (
                <TransactionModal
                    isOpen={showTransactionModal}
                    onClose={handleCloseTransactionModal}
                    onSave={handleSaveTransaction}
                    categories={categories || []}
                    transaction={editingTransaction}
                />
            )}
        </div>
    );
}
