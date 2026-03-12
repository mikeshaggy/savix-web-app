import React, { useMemo } from "react";
import { Download } from "lucide-react";
import TransactionFilters from "../transactions/TransactionFilters";
import TransactionTable from "../transactions/TransactionTable";
import PaginationFooter from "../common/PaginationFooter";
import { useTranslations } from "next-intl";
import { formatCurrency } from "@/utils/helpers";
import { useLanguage } from "@/i18n";
import NewActionDropdown from "@/components/common/NewActionDropdown";

export default function TransactionsView({
  // Server-driven data
  items = [],
  totalElements = 0,
  totalPages = 0,
  hasNext = false,
  hasPrevious = false,
  loading = false,
  error = null,
  categories = [],

  // Server filter state
  page,
  size,
  sort,
  types,
  categoryIds,
  importances,
  startDate,
  endDate,
  searchInput,
  activeFilterCount,

  // Setters
  setPage,
  updateSize,
  updateSort,
  updateTypes,
  updateCategoryIds,
  updateImportances,
  updateStartDate,
  updateEndDate,
  setSearchInput,
  clearSearch,
  clearAllFilters,

  // Actions
  onNewTransaction,
  onNewTransfer,
  onEditTransaction,
  onDeleteTransaction,
  onRetry,
}) {
  const t = useTranslations();
  const { lang } = useLanguage();

  const totalIncome = useMemo(
    () =>
      items
        .filter((tx) => tx.categoryType === "INCOME")
        .reduce((sum, tx) => sum + parseFloat(tx.amount || 0), 0),
    [items],
  );
  const totalExpense = useMemo(
    () =>
      items
        .filter((tx) => tx.categoryType === "EXPENSE")
        .reduce((sum, tx) => sum + parseFloat(tx.amount || 0), 0),
    [items],
  );

  return (
    <div className="flex flex-col gap-[18px]">
      {/* Page header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="text-xl sm:text-[26px] font-bold tracking-[-0.4px]">
            {t("nav.allTransactions")}
          </div>
          <div className="text-[13px] sm:text-[14px] text-white/22 mt-[3px] flex items-center gap-0 flex-wrap">
            <span>
              {totalElements} {t("transaction.transactions")}
            </span>
            <span className="mx-2 text-white/[0.08]">|</span>
            <span className="text-green-400">
              ↑ {formatCurrency(totalIncome, lang)}
            </span>
            <span className="mx-1.5 text-white/[0.08]">·</span>
            <span className="text-red-400">
              ↓ {formatCurrency(totalExpense, lang)}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button className="flex items-center gap-[7px] px-4 py-[10px] rounded-[10px] text-[14px] font-semibold cursor-pointer border border-white/[0.055] bg-[#131325] text-white/50 transition-all hover:border-white/[0.12] hover:text-white">
            <Download className="w-[14px] h-[14px]" />
            {t("common.export")}
          </button>
          <NewActionDropdown
            variant="inline"
            onNewTransaction={onNewTransaction}
            onNewTransfer={onNewTransfer}
          />
        </div>
      </div>

      {/* Filter bar */}
      <TransactionFilters
        types={types}
        updateTypes={updateTypes}
        categoryIds={categoryIds}
        updateCategoryIds={updateCategoryIds}
        importances={importances}
        updateImportances={updateImportances}
        startDate={startDate}
        updateStartDate={updateStartDate}
        endDate={endDate}
        updateEndDate={updateEndDate}
        sort={sort}
        updateSort={updateSort}
        searchInput={searchInput}
        setSearchInput={setSearchInput}
        clearSearch={clearSearch}
        activeFilterCount={activeFilterCount}
        clearAllFilters={clearAllFilters}
        categories={categories}
      />

      {/* Error */}
      {error && (
        <div className="bg-red-500/10 border border-red-500/20 rounded-[14px] px-5 py-4 flex items-center justify-between">
          <span className="text-red-400 text-[14px] font-medium">{error}</span>
          {onRetry && (
            <button
              onClick={onRetry}
              className="text-[13px] font-semibold text-red-400 hover:text-red-300 transition-colors"
            >
              {t("common.retry")}
            </button>
          )}
        </div>
      )}

      {/* Loading skeleton */}
      {loading && !items.length && (
        <div className="bg-[#0e0e1c] border border-white/[0.055] rounded-[18px] overflow-hidden">
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              key={i}
              className="flex items-center px-[22px] py-[14px] border-b border-white/[0.055] last:border-b-0 animate-pulse"
            >
              <div className="w-10 h-10 rounded-[11px] bg-white/[0.04] mr-[14px]" />
              <div className="flex-1">
                <div className="h-4 w-40 bg-white/[0.04] rounded mb-2" />
                <div className="h-3 w-60 bg-white/[0.03] rounded" />
              </div>
              <div className="h-5 w-24 bg-white/[0.04] rounded ml-4" />
            </div>
          ))}
        </div>
      )}

      {/* Loading overlay on page changes */}
      {loading && items.length > 0 && (
        <div className="relative">
          <div className="absolute inset-0 bg-[#06060f]/50 rounded-[18px] z-10 flex items-center justify-center">
            <div className="w-6 h-6 border-2 border-violet-400/30 border-t-violet-400 rounded-full animate-spin" />
          </div>
          <TransactionTable
            filteredTransactions={items}
            categories={categories}
            onEdit={onEditTransaction}
            onDelete={onDeleteTransaction}
          />
        </div>
      )}

      {/* Transaction list */}
      {!loading && (
        <TransactionTable
          filteredTransactions={items}
          categories={categories}
          onEdit={onEditTransaction}
          onDelete={onDeleteTransaction}
        />
      )}

      {/* Pagination footer */}
      {totalElements > 0 && (
        <PaginationFooter
          page={page}
          size={size}
          totalElements={totalElements}
          totalPages={totalPages}
          hasNext={hasNext}
          hasPrevious={hasPrevious}
          onPageChange={setPage}
          onSizeChange={updateSize}
        />
      )}
    </div>
  );
}
