'use client';
import React from 'react';
import { History, ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslations } from 'next-intl';
import ErrorState from '@/components/common/ErrorState';
import EmptyState from '@/components/common/EmptyState';
import FundMovementRow from './FundMovementRow';

// Loading skeleton matching the row layout.
function MovementsSkeleton() {
  return (
    <div className="divide-y divide-white/[0.04] animate-pulse">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex items-center gap-3 px-4 py-3">
          <div className="w-8 h-8 shrink-0 rounded-[10px] bg-white/[0.06]" />
          <div className="flex-1 space-y-1.5">
            <div className="h-3 w-32 bg-white/[0.06] rounded" />
            <div className="h-2.5 w-20 bg-white/[0.05] rounded" />
          </div>
          <div className="h-3.5 w-16 bg-white/[0.06] rounded" />
        </div>
      ))}
    </div>
  );
}

/**
 * Paginated fund movement list.
 *
 * Props:
 *   data     – page response { content, page, size, totalElements, totalPages, hasNext, hasPrevious }
 *   loading  – boolean
 *   error    – error object | null
 *   onRetry  – retry handler for the current page
 *   onPrev   – previous-page handler
 *   onNext   – next-page handler
 */
export default function FundMovementsList({ data, loading, error, onRetry, onPrev, onNext }) {
  const t = useTranslations('funds');

  if (loading) return <MovementsSkeleton />;

  if (error) {
    return (
      <ErrorState
        variant="inline"
        title={t('movementsError')}
        onRetry={onRetry}
        retryLabel={t('retry')}
        className="m-4"
      />
    );
  }

  const content = data?.content ?? [];

  if (content.length === 0) {
    return (
      <EmptyState
        variant="card"
        icon={History}
        title={t('noMovementsYet')}
        description={t('noMovementsDesc')}
      />
    );
  }

  const page = data.page ?? 0;
  const totalPages = data.totalPages ?? 1;

  return (
    <div>
      <div className="divide-y divide-white/[0.04]">
        {content.map((m) => (
          <FundMovementRow key={m.id} movement={m} />
        ))}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between gap-3 px-4 py-3 border-t border-white/[0.055]">
          <button
            type="button"
            onClick={onPrev}
            disabled={!data.hasPrevious}
            className="flex items-center gap-1 text-[12px] px-3 py-1.5 rounded-[8px] border border-white/[0.08] text-white/50 hover:text-white hover:border-white/20 transition-all disabled:opacity-30 disabled:cursor-not-allowed"
          >
            <ChevronLeft className="w-3.5 h-3.5" />
            {t('previous')}
          </button>

          <span className="text-[12px] text-white/35">
            {t('pageInfo', { page: page + 1, total: totalPages })}
          </span>

          <button
            type="button"
            onClick={onNext}
            disabled={!data.hasNext}
            className="flex items-center gap-1 text-[12px] px-3 py-1.5 rounded-[8px] border border-white/[0.08] text-white/50 hover:text-white hover:border-white/20 transition-all disabled:opacity-30 disabled:cursor-not-allowed"
          >
            {t('next')}
            <ChevronRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}
    </div>
  );
}
