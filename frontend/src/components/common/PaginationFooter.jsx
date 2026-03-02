import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslations } from 'next-intl';

const PAGE_SIZES = [10, 20, 50, 100];

export default function PaginationFooter({
  page,
  size,
  totalElements,
  totalPages,
  hasNext,
  hasPrevious,
  onPageChange,
  onSizeChange,
}) {
  const t = useTranslations();

  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  const currentPage = page + 1;

  return (
    <div className="flex items-center justify-between px-5 py-3.5 bg-[#0e0e1c] border border-white/[0.055] rounded-[14px]">
      {/* Left: showing X-Y of Z */}
      <div className="text-[13px] text-white/40 font-medium">
        {totalElements === 0
          ? t('pagination.noResults')
          : `${t('pagination.showing')} ${from}–${to} ${t('pagination.of')} ${totalElements}`}
      </div>

      {/* Center: rows per page */}
      <div className="flex items-center gap-2">
        <span className="text-[13px] text-white/30 font-medium">{t('pagination.rowsPerPage')}:</span>
        <div className="flex items-center gap-1">
          {PAGE_SIZES.map((s) => (
            <button
              key={s}
              onClick={() => onSizeChange(s)}
              className={`px-2.5 py-1 rounded-lg text-[13px] font-semibold transition-all ${
                s === size
                  ? 'bg-violet-500/15 border border-violet-500/35 text-violet-300'
                  : 'text-white/30 hover:text-white/60 hover:bg-white/[0.04] border border-transparent'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Right: page nav */}
      <div className="flex items-center gap-3">
        <span className="text-[13px] text-white/30 font-medium">
          {totalPages > 0
            ? `${t('pagination.page')} ${currentPage} ${t('pagination.of')} ${totalPages}`
            : `${t('pagination.page')} 0 ${t('pagination.of')} 0`}
        </span>
        <div className="flex items-center gap-1">
          <button
            onClick={() => onPageChange(page - 1)}
            disabled={!hasPrevious}
            className="w-8 h-8 rounded-lg flex items-center justify-center border border-white/[0.055] bg-[#131325] text-white/40 transition-all hover:border-white/[0.12] hover:text-white disabled:opacity-25 disabled:cursor-not-allowed disabled:hover:border-white/[0.055] disabled:hover:text-white/40"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <button
            onClick={() => onPageChange(page + 1)}
            disabled={!hasNext}
            className="w-8 h-8 rounded-lg flex items-center justify-center border border-white/[0.055] bg-[#131325] text-white/40 transition-all hover:border-white/[0.12] hover:text-white disabled:opacity-25 disabled:cursor-not-allowed disabled:hover:border-white/[0.055] disabled:hover:text-white/40"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
