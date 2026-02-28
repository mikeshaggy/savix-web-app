import React, { useMemo } from "react";
import { Download } from "lucide-react";
import TransactionFilters from "../transactions/TransactionFilters";
import TransactionTable from "../transactions/TransactionTable";
import { useTranslations } from "next-intl";
import { formatCurrency } from "@/utils/helpers";
import { useLanguage } from "@/i18n";
import NewActionDropdown from "@/components/common/NewActionDropdown";

export default function TransactionsView({
  filteredTransactions = [],
  allTransactions = [],
  categories = [],
  typeFilter,
  setTypeFilter,
  categoryFilter,
  setCategoryFilter,
  importanceFilter,
  setImportanceFilter,
  dateFilter,
  setDateFilter,
  datePreset,
  setDatePreset,
  customFromDate,
  setCustomFromDate,
  customToDate,
  setCustomToDate,
  sortBy,
  setSortBy,
  sortOrder,
  setSortOrder,
  filterStats,
  onNewTransaction,
  onNewTransfer,
  onEditTransaction,
  onDeleteTransaction,
}) {
  const t = useTranslations();
  const { lang } = useLanguage();

  const totalIncome = useMemo(
    () =>
      filteredTransactions
        .filter((tx) => tx.type === "INCOME")
        .reduce((sum, tx) => sum + parseFloat(tx.amount || 0), 0),
    [filteredTransactions],
  );
  const totalExpense = useMemo(
    () =>
      filteredTransactions
        .filter((tx) => tx.type === "EXPENSE")
        .reduce((sum, tx) => sum + parseFloat(tx.amount || 0), 0),
    [filteredTransactions],
  );

  return (
    <div className="flex flex-col gap-[18px]">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <div className="text-[26px] font-bold tracking-[-0.4px]">
            {t("nav.allTransactions")}
          </div>
          <div className="text-[14px] text-white/22 mt-[3px] flex items-center gap-0 flex-wrap">
            <span>
              {filterStats?.filtered || filteredTransactions.length}{" "}
              {t("transaction.transactions")} · {filterStats?.percentage ?? 100}
              % {t("common.total")}
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
        typeFilter={typeFilter}
        setTypeFilter={setTypeFilter}
        categoryFilter={categoryFilter}
        setCategoryFilter={setCategoryFilter}
        importanceFilter={importanceFilter}
        setImportanceFilter={setImportanceFilter}
        dateFilter={dateFilter}
        setDateFilter={setDateFilter}
        datePreset={datePreset}
        setDatePreset={setDatePreset}
        customFromDate={customFromDate}
        setCustomFromDate={setCustomFromDate}
        customToDate={customToDate}
        setCustomToDate={setCustomToDate}
        sortBy={sortBy}
        setSortBy={setSortBy}
        sortOrder={sortOrder}
        setSortOrder={setSortOrder}
        categories={categories}
      />

      {/* Transaction list */}
      <TransactionTable
        filteredTransactions={filteredTransactions}
        categories={categories}
        onEdit={onEditTransaction}
        onDelete={onDeleteTransaction}
      />
    </div>
  );
}
