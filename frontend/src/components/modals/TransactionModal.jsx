import React, {
  useState,
  useRef,
  useEffect,
  useMemo,
  useCallback,
} from "react";
import { createPortal } from "react-dom";
import { X, Save, Loader2, Plus } from "lucide-react";
import { useCategories } from "@/hooks/useApi";
import { useWallets } from "@/contexts/WalletContext";
import { useUser } from "@/contexts/UserContext";
import { useTranslations } from "next-intl";
import { useLanguage } from "@/i18n";
import { formatCurrency } from "@/utils/helpers";
import CategoryModal from "./CategoryModal";

// Importance options matching backend Importance enum
const IMPORTANCE_OPTIONS = [
  { value: "ESSENTIAL", labelKey: "importance.essential", colorClass: "ess" },
  { value: "HAVE_TO_HAVE", labelKey: "importance.haveToHave", colorClass: "" },
  {
    value: "NICE_TO_HAVE",
    labelKey: "importance.niceToHave",
    colorClass: "nice",
  },
  {
    value: "SHOULDNT_HAVE",
    labelKey: "importance.shouldntHave",
    colorClass: "bad",
  },
  { value: "INVESTMENT", labelKey: "importance.investment", colorClass: "inv" },
];

// Quick amount chips
const QUICK_AMOUNTS = [10, 20, 50, 100];

export default function TransactionModal({
  isOpen,
  onClose,
  onSave,
  categories,
  loading = false,
  transaction = null,
  prefill = null,
  onPrefillSaved = null,
}) {
  const { currentWallet, wallets } = useWallets();
  const { user } = useUser();
  const { createCategory } = useCategories();
  const t = useTranslations();
  const { lang } = useLanguage();

  const isEditing = !!transaction;

  // Form state
  const [formData, setFormData] = useState({
    title: "",
    amount: "",
    transactionDate: new Date().toISOString().split("T")[0],
    walletId: currentWallet?.id || "",
    categoryId: "",
    notes: "",
    importance: "ESSENTIAL",
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [showCategoryModal, setShowCategoryModal] = useState(false);
  const [localCategories, setLocalCategories] = useState(categories || []);

  // Category combobox state
  const [categorySearch, setCategorySearch] = useState("");
  const [showCategoryDropdown, setShowCategoryDropdown] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const categoryInputRef = useRef(null);
  const dropdownRef = useRef(null);
  const dropdownPortalRef = useRef(null);
  const categoryOptionRefs = useRef([]);
  const [dropdownPosition, setDropdownPosition] = useState({
    top: 0,
    left: 0,
    width: 0,
    maxHeight: 340,
    openUp: false,
  });

  // Reset form when transaction changes or modal opens
  useEffect(() => {
    if (transaction) {
      setFormData({
        title: transaction.title || "",
        amount: transaction.amount?.toString() || "",
        transactionDate:
          transaction.transactionDate || new Date().toISOString().split("T")[0],
        walletId:
          transaction.walletId?.toString() ||
          currentWallet?.id?.toString() ||
          "",
        categoryId: transaction.categoryId?.toString() || "",
        notes: transaction.notes || "",
        importance: transaction.importance || "ESSENTIAL",
      });
      // Set category search to show selected category name
      const cat = categories?.find((c) => c.id === transaction.categoryId);
      setCategorySearch(cat?.name || "");
    } else if (prefill) {
      setFormData({
        title: prefill.title || "",
        amount: prefill.amount?.toString() || "",
        transactionDate: new Date().toISOString().split("T")[0],
        walletId:
          prefill.walletId?.toString() ||
          currentWallet?.id?.toString() ||
          "",
        categoryId: prefill.categoryId?.toString() || "",
        notes: prefill.notes || "",
        importance: "ESSENTIAL",
      });
      const cat = categories?.find((c) => c.id === prefill.categoryId);
      setCategorySearch(cat?.name || "");
    } else {
      setFormData({
        title: "",
        amount: "",
        transactionDate: new Date().toISOString().split("T")[0],
        walletId: currentWallet?.id?.toString() || "",
        categoryId: "",
        notes: "",
        importance: "ESSENTIAL",
      });
      setCategorySearch("");
    }
    setErrors({});
    setShowCategoryDropdown(false);
  }, [transaction, prefill, currentWallet?.id, isOpen, categories]);

  useEffect(() => {
    setLocalCategories(categories || []);
  }, [categories]);

  // Selected category object
  const selectedCategory = useMemo(() => {
    return localCategories.find(
      (category) => category.id === parseInt(formData.categoryId),
    );
  }, [localCategories, formData.categoryId]);

  // Filter categories based on search
  const filteredCategories = useMemo(() => {
    if (!categorySearch.trim()) {
      return localCategories.slice(0, 50);
    }

    const query = categorySearch.toLowerCase().trim();

    return localCategories
      .map((cat) => {
        const name = cat.name.toLowerCase();
        let score = 0;
        if (name === query) score += 100;
        if (name.startsWith(query)) score += 40;
        if (name.includes(query)) score += 20;
        if (cat.emoji?.includes(query)) score += 10;
        return { cat, score };
      })
      .filter((x) => x.score > 0)
      .sort((a, b) => b.score - a.score)
      .map((x) => x.cat);
  }, [localCategories, categorySearch]);

  const keyboardNavigableCategories = useMemo(() => {
    const expenseCategories = filteredCategories.filter(
      (c) => c.type === "EXPENSE",
    );
    const incomeCategories = filteredCategories.filter((c) => c.type === "INCOME");
    return [...expenseCategories, ...incomeCategories];
  }, [filteredCategories]);

  // Close dropdown when clicking outside (check both the input area and portal)
  useEffect(() => {
    const handleClickOutside = (e) => {
      const inDropdownArea =
        dropdownRef.current && dropdownRef.current.contains(e.target);
      const inPortal =
        dropdownPortalRef.current &&
        dropdownPortalRef.current.contains(e.target);
      if (!inDropdownArea && !inPortal) {
        setShowCategoryDropdown(false);
        setActiveIndex(-1);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    if (!showCategoryDropdown) {
      setActiveIndex(-1);
      categoryOptionRefs.current = [];
      return;
    }

    if (!keyboardNavigableCategories.length) {
      setActiveIndex(-1);
      return;
    }

    if (activeIndex >= keyboardNavigableCategories.length) {
      setActiveIndex(keyboardNavigableCategories.length - 1);
    }
  }, [
    showCategoryDropdown,
    keyboardNavigableCategories.length,
    activeIndex,
  ]);

  useEffect(() => {
    if (!showCategoryDropdown || activeIndex < 0) return;
    categoryOptionRefs.current[activeIndex]?.scrollIntoView({ block: "nearest" });
  }, [activeIndex, showCategoryDropdown]);

  // Compute dropdown position when it opens
  useEffect(() => {
    if (showCategoryDropdown && categoryInputRef.current) {
      const rect = categoryInputRef.current.getBoundingClientRect();
      const viewportHeight = window.innerHeight;
      const spaceBelow = viewportHeight - rect.bottom - 16; // 16px margin
      const spaceAbove = rect.top - 16;
      const preferredHeight = 340;
      const headerBuffer = 44; // header (~40px) + borders/padding

      // Decide direction: open up if more space above and not enough below
      const openUp = spaceBelow < 200 && spaceAbove > spaceBelow;
      const rawMaxHeight = openUp
        ? Math.min(spaceAbove, preferredHeight)
        : Math.min(spaceBelow, preferredHeight);
      // Subtract buffer for header and borders, clamp minimum
      const maxHeight = Math.max(rawMaxHeight - headerBuffer, 120);

      setDropdownPosition({
        left: rect.left,
        width: rect.width,
        maxHeight,
        openUp,
        top: openUp ? rect.top : rect.bottom + 8,
        bottom: openUp ? viewportHeight - rect.top + 8 : undefined,
      });
    }
  }, [showCategoryDropdown]);

  const handleChange = useCallback(
    (field, value) => {
      setFormData((prev) => ({ ...prev, [field]: value }));
      if (errors[field]) {
        setErrors((prev) => ({ ...prev, [field]: "" }));
      }
    },
    [errors],
  );

  const handleCategorySelect = useCallback(
    (category) => {
      setFormData((prev) => ({
        ...prev,
        categoryId: category.id.toString(),
        // Reset importance for income categories
        importance:
          category.type === "INCOME" ? "" : prev.importance || "ESSENTIAL",
      }));
      setCategorySearch(category.name);
      setShowCategoryDropdown(false);
      setActiveIndex(-1);
      if (errors.categoryId) {
        setErrors((prev) => ({ ...prev, categoryId: "" }));
      }
    },
    [errors],
  );

  const handleCategoryInputKeyDown = useCallback(
    (e) => {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        if (!showCategoryDropdown) {
          setShowCategoryDropdown(true);
          setActiveIndex(keyboardNavigableCategories.length ? 0 : -1);
          return;
        }
        if (!keyboardNavigableCategories.length) return;
        setActiveIndex((prev) => {
          if (prev < 0) return 0;
          return (prev + 1) % keyboardNavigableCategories.length;
        });
        return;
      }

      if (e.key === "ArrowUp") {
        e.preventDefault();
        if (!showCategoryDropdown) {
          setShowCategoryDropdown(true);
          setActiveIndex(keyboardNavigableCategories.length ? 0 : -1);
          return;
        }
        if (!keyboardNavigableCategories.length) return;
        setActiveIndex((prev) => {
          if (prev <= 0) return keyboardNavigableCategories.length - 1;
          return prev - 1;
        });
        return;
      }

      if (e.key === "Enter" && showCategoryDropdown) {
        if (!keyboardNavigableCategories.length) return;
        e.preventDefault();
        const idx = activeIndex >= 0 ? activeIndex : 0;
        handleCategorySelect(keyboardNavigableCategories[idx]);
        return;
      }

      if (e.key === "Escape" && showCategoryDropdown) {
        e.preventDefault();
        setShowCategoryDropdown(false);
        setActiveIndex(-1);
      }
    },
    [
      activeIndex,
      handleCategorySelect,
      keyboardNavigableCategories,
      showCategoryDropdown,
    ],
  );

  const handleQuickAmount = useCallback(
    (add) => {
      const current = parseFloat(formData.amount.replace(",", ".")) || 0;
      const next = (current + add).toFixed(2);
      handleChange("amount", next);
    },
    [formData.amount, handleChange],
  );

  const handleQuickDate = useCallback(
    (type) => {
      const d = new Date();
      if (type === "yesterday") d.setDate(d.getDate() - 1);
      handleChange("transactionDate", d.toISOString().split("T")[0]);
    },
    [handleChange],
  );

  const handleCreateCategory = async (categoryData) => {
    try {
      const newCategory = await createCategory(categoryData);
      setLocalCategories((prev) => [...prev, newCategory]);
      handleCategorySelect(newCategory);
      setShowCategoryModal(false);
      return newCategory;
    } catch (error) {
      console.error("Failed to create category:", error);
      throw error;
    }
  };

  // Validation
  const validateForm = () => {
    const newErrors = {};

    if (!formData.title.trim()) {
      newErrors.title = "errors.titleRequired";
    } else if (formData.title.trim().length > 50) {
      newErrors.title = "errors.titleTooLong";
    }

    const amount = parseFloat(formData.amount);
    if (!formData.amount || isNaN(amount) || amount < 0.01) {
      newErrors.amount = "errors.amountRequired";
    }

    if (!formData.walletId) {
      newErrors.walletId = "errors.walletRequired";
    }

    if (!formData.categoryId) {
      newErrors.categoryId = "errors.categoryRequired";
    }

    if (!formData.transactionDate) {
      newErrors.transactionDate = "errors.dateRequired";
    }

    if (selectedCategory?.type === "EXPENSE" && !formData.importance) {
      newErrors.importance = "errors.importanceRequired";
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    try {
      setSubmitting(true);

      const transactionData = {
        title: formData.title.trim(),
        amount: parseFloat(formData.amount),
        transactionDate: formData.transactionDate,
        walletId: parseInt(formData.walletId),
        categoryId: parseInt(formData.categoryId),
        notes: formData.notes?.trim() || undefined,
        importance:
          selectedCategory?.type === "INCOME" ? undefined : formData.importance,
        ...(prefill?.occurrenceId ? { occurrenceId: prefill.occurrenceId } : {}),
      };

      await onSave(transactionData, isEditing ? transaction.id : null);

      if (prefill && onPrefillSaved) {
        onPrefillSaved(prefill.occurrenceId);
      }

      if (!isEditing) {
        setFormData({
          title: "",
          amount: "",
          transactionDate: new Date().toISOString().split("T")[0],
          walletId: currentWallet?.id?.toString() || "",
          categoryId: "",
          notes: "",
          importance: "ESSENTIAL",
        });
        setCategorySearch("");
      }
      setErrors({});
      onClose();
    } catch (error) {
      console.error("Failed to save transaction:", error);
      if (error.message) {
        setErrors((prev) => ({ ...prev, submit: error.message }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-3 sm:p-6">
      <div
        className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[980px] max-h-[90vh] overflow-hidden relative"
        style={{
          boxShadow:
            "0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1), 0 0 80px rgba(124,58,237,0.06)",
          animation: "fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both",
        }}
      >
        {/* Top accent line */}
        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-purple-400/45" />

        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
          <div className="flex items-center gap-2.5 text-lg sm:text-xl font-bold tracking-[-0.3px]">
            {isEditing
              ? t("transaction.editTransaction")
              : t("transaction.addTransaction")}
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] hover:bg-[#1a1a2e] transition-all"
          >
            <X className="w-3 h-3" />
          </button>
        </div>

        {/* Prefill banner */}
        {prefill && !isEditing && (
          <div className="mx-4 sm:mx-7 mt-4 px-4 py-3 rounded-xl bg-purple-500/10 border border-purple-500/20 text-[13px] text-purple-300 flex items-center gap-2">
            <span>📌</span>
            <span>{t('fixedPayments.prefillBanner')}</span>
          </div>
        )}

        {/* Body - Two Column Layout */}
        <form onSubmit={handleSubmit}>
          <div className="grid grid-cols-1 lg:grid-cols-2 max-h-[calc(90vh-150px)] overflow-y-auto">
            {/* LEFT COLUMN */}
            <div className="px-4 sm:px-7 py-5 sm:py-6">
              {/* Title */}
              <div className="mb-5">
                <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                  {t("transaction.title")}{" "}
                  <span className="text-purple-300">*</span>
                </div>
                <input
                  type="text"
                  value={formData.title}
                  onChange={(e) => handleChange("title", e.target.value)}
                  maxLength={50}
                  className={`w-full bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base font-normal text-white placeholder-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] ${
                    errors.title ? "border-red-500" : "border-white/[0.055]"
                  }`}
                  placeholder={t("transaction.titlePlaceholder")}
                />
                {errors.title && (
                  <p className="text-red-400 text-xs mt-1.5">
                    {t(errors.title)}
                  </p>
                )}
              </div>

              {/* Amount */}
              <div className="mb-5">
                <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                  {t("transaction.amount")}{" "}
                  <span className="text-purple-300">*</span>
                </div>
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 font-mono text-base font-medium text-white/25">
                    PLN
                  </span>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={formData.amount}
                    onChange={(e) => handleChange("amount", e.target.value)}
                    className={`w-full bg-[#131325] border rounded-[11px] pl-14 pr-3.5 py-3 font-mono text-xl font-medium tracking-[-0.5px] text-white placeholder-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] ${
                      errors.amount ? "border-red-500" : "border-white/[0.055]"
                    }`}
                    placeholder="0.00"
                  />
                </div>
                <div className="flex gap-1.5 mt-2">
                  {QUICK_AMOUNTS.map((amt) => (
                    <button
                      key={amt}
                      type="button"
                      onClick={() => handleQuickAmount(amt)}
                      className="px-3 py-1.5 bg-[#131325] border border-white/[0.055] rounded-full font-mono text-sm font-semibold text-white/25 cursor-pointer hover:border-purple-500 hover:text-purple-300 hover:bg-purple-500/10 transition-all"
                    >
                      +{amt}
                    </button>
                  ))}
                </div>
                {errors.amount && (
                  <p className="text-red-400 text-xs mt-1.5">
                    {t(errors.amount)}
                  </p>
                )}
              </div>

              {/* Date */}
              <div className="mb-5">
                <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                  {t("transaction.date")}{" "}
                  <span className="text-purple-300">*</span>
                </div>
                <input
                  type="date"
                  value={formData.transactionDate}
                  onChange={(e) =>
                    handleChange("transactionDate", e.target.value)
                  }
                  className={`w-full bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] [color-scheme:dark] ${
                    errors.transactionDate
                      ? "border-red-500"
                      : "border-white/[0.055]"
                  }`}
                />
                <div className="flex gap-1.5 mt-2">
                  <button
                    type="button"
                    onClick={() => handleQuickDate("today")}
                    className={`px-3 py-1.5 rounded-full text-[13px] font-semibold transition-all ${
                      formData.transactionDate ===
                      new Date().toISOString().split("T")[0]
                        ? "bg-purple-500/15 border border-purple-500/40 text-purple-300"
                        : "bg-[#131325] border border-white/[0.055] text-white/25 hover:border-white/[0.12] hover:text-white/50"
                    }`}
                  >
                    {t("transaction.today")}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleQuickDate("yesterday")}
                    className={`px-3 py-1.5 rounded-full text-[13px] font-semibold transition-all ${
                      formData.transactionDate ===
                      (() => {
                        const d = new Date();
                        d.setDate(d.getDate() - 1);
                        return d.toISOString().split("T")[0];
                      })()
                        ? "bg-purple-500/15 border border-purple-500/40 text-purple-300"
                        : "bg-[#131325] border border-white/[0.055] text-white/25 hover:border-white/[0.12] hover:text-white/50"
                    }`}
                  >
                    {t("transaction.yesterday")}
                  </button>
                </div>
                {errors.transactionDate && (
                  <p className="text-red-400 text-xs mt-1.5">
                    {t(errors.transactionDate)}
                  </p>
                )}
              </div>

              {/* Wallet */}
              <div className="mb-5">
                <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                  {t("transaction.wallet")}{" "}
                  <span className="text-purple-300">*</span>
                </div>
                <select
                  value={formData.walletId}
                  onChange={(e) => handleChange("walletId", e.target.value)}
                  className={`w-full bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white outline-none transition-all appearance-none cursor-pointer focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] ${
                    errors.walletId
                      ? "border-red-500"
                      : "border-white/[0.055]"
                  }`}
                >
                  <option value="">{t("transaction.selectWallet")}</option>
                  {wallets.map((wallet) => (
                    <option key={wallet.id} value={wallet.id}>
                      {wallet.name} (
                      {formatCurrency(wallet.balance || 0, lang)})
                    </option>
                  ))}
                </select>
                {errors.walletId && (
                  <p className="text-red-400 text-xs mt-1.5">
                    {t(errors.walletId)}
                  </p>
                )}
              </div>

              {/* Category */}
              <div ref={dropdownRef}>
                <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                  {t("transaction.category")}{" "}
                  <span className="text-purple-300">*</span>
                </div>
                <div className="flex items-center gap-2">
                  {/* Emoji */}
                  <div className="w-10 h-10 shrink-0 rounded-[11px] flex items-center justify-center text-lg border border-white/[0.055] bg-white/[0.02] text-white/80 cursor-pointer">
                    {selectedCategory?.emoji || "🏷️"}
                  </div>

                  {/* Search input */}
                  <input
                    ref={categoryInputRef}
                    type="text"
                    value={categorySearch}
                    onChange={(e) => {
                      setCategorySearch(e.target.value);
                      setShowCategoryDropdown(true);
                      setActiveIndex(0);
                      if (
                        formData.categoryId &&
                        e.target.value !== selectedCategory?.name
                      ) {
                        handleChange("categoryId", "");
                      }
                    }}
                    onFocus={() => {
                      setShowCategoryDropdown(true);
                      setActiveIndex(keyboardNavigableCategories.length ? 0 : -1);
                    }}
                    onKeyDown={handleCategoryInputKeyDown}
                    className={`flex-1 bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white placeholder-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] ${
                      errors.categoryId
                        ? "border-red-500"
                        : "border-white/[0.055]"
                    }`}
                    placeholder={t("transaction.searchCategory")}
                    autoComplete="off"
                  />

                  {/* Type pill */}
                  {(() => {
                    const pillType = selectedCategory?.type;

                    const baseClasses =
                      "shrink-0 text-[11px] font-bold tracking-[0.12em] px-3 py-[6px] rounded-full border whitespace-nowrap text-center transition-all";

                    const variantClasses = !pillType
                      ? // 🔘 neutral state (no category selected)
                        "bg-white/[0.03] border-white/[0.08] text-white/35"
                      : pillType === "INCOME"
                        ? // 🟢 income
                          "bg-green-400/10 border-green-400/35 text-green-400 shadow-[0_0_12px_rgba(74,222,128,0.12)]"
                        : // 🔴 expense
                          "bg-red-400/12 border-red-400/40 text-red-400 shadow-[0_0_12px_rgba(248,113,113,0.15)]";

                    return (
                      <span className={`${baseClasses} ${variantClasses}`}>
                        {pillType || "—"}
                      </span>
                    );
                  })()}

                  {/* Add category button */}
                  <button
                    type="button"
                    onClick={() => setShowCategoryModal(true)}
                    className="w-10 h-10 shrink-0 rounded-[11px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-xl text-white/25 cursor-pointer hover:border-purple-500 hover:text-purple-300 hover:bg-purple-500/10 transition-all"
                    title={t("transaction.addCategory")}
                  >
                    <Plus className="w-4 h-4" />
                  </button>
                </div>

                {/* Dropdown rendered via portal */}
                {showCategoryDropdown &&
                  typeof document !== "undefined" &&
                  createPortal(
                    <div
                      ref={dropdownPortalRef}
                      className="bg-[rgba(18,22,38,0.98)] border border-white/[0.06] rounded-2xl shadow-2xl overflow-hidden backdrop-blur-xl"
                      style={{
                        position: "fixed",
                        left: dropdownPosition.left,
                        width: dropdownPosition.width,
                        zIndex: 9999,
                        ...(dropdownPosition.openUp
                          ? { bottom: dropdownPosition.bottom }
                          : { top: dropdownPosition.top }),
                      }}
                    >
                      <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/[0.04] text-gray-500 text-xs">
                        <span className="text-gray-400">
                          {filteredCategories.length} {t("transaction.items")}
                        </span>
                      </div>
                      <div
                        className="overflow-y-auto pb-3"
                        style={{ maxHeight: dropdownPosition.maxHeight }}
                      >
                        {filteredCategories.length === 0 ? (
                          <div className="flex items-center gap-3 px-4 py-4 text-gray-400">
                            <div className="w-9 h-9 rounded-xl bg-white/[0.02] border border-white/[0.05] flex items-center justify-center text-lg">
                              🤷
                            </div>
                            <div>
                              <div className="font-semibold text-sm text-gray-300">
                                {t("transaction.noMatches")}
                              </div>
                              <div className="text-xs text-gray-500">
                                {t("transaction.tryDifferent")}
                              </div>
                            </div>
                          </div>
                        ) : (
                          (() => {
                            const expenseCategories = filteredCategories.filter(
                              (c) => c.type === "EXPENSE",
                            );
                            const incomeCategories = filteredCategories.filter(
                              (c) => c.type === "INCOME",
                            );
                            let globalIdx = 0;

                            const renderCategory = (cat) => {
                              const idx = globalIdx++;
                              return (
                                <div
                                  key={cat.id}
                                  ref={(node) => {
                                    categoryOptionRefs.current[idx] = node;
                                  }}
                                  onMouseDown={(e) => {
                                    e.preventDefault();
                                    handleCategorySelect(cat);
                                  }}
                                  onMouseEnter={() => setActiveIndex(idx)}
                                  className={`flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors ${
                                    idx === activeIndex
                                      ? "bg-violet-500/10 border-l-2 border-l-violet-400"
                                      : "hover:bg-white/[0.03] border-l-2 border-l-transparent"
                                  }`}
                                >
                                  <div className="w-8 h-8 rounded-lg bg-white/[0.02] border border-white/[0.04] flex items-center justify-center text-base shrink-0">
                                    {cat.emoji || "🏷️"}
                                  </div>
                                  <div className="font-semibold text-sm text-white/90">
                                    {cat.name}
                                  </div>
                                </div>
                              );
                            };

                            return (
                              <>
                                {expenseCategories.length > 0 && (
                                  <>
                                    <div className="px-4 py-2 text-[11px] font-bold uppercase tracking-wider text-red-400/70 bg-red-500/[0.03] border-b border-white/[0.03]">
                                      💸{" "}
                                      {t("transaction.expensesCount", {
                                        count: expenseCategories.length,
                                      })}
                                    </div>
                                    {expenseCategories.map(renderCategory)}
                                  </>
                                )}
                                {incomeCategories.length > 0 && (
                                  <>
                                    <div className="px-4 py-2 text-[11px] font-bold uppercase tracking-wider text-green-400/70 bg-green-500/[0.03] border-y border-white/[0.03]">
                                      💰{" "}
                                      {t("transaction.incomeCount", {
                                        count: incomeCategories.length,
                                      })}
                                    </div>
                                    {incomeCategories.map(renderCategory)}
                                  </>
                                )}
                              </>
                            );
                          })()
                        )}
                      </div>
                    </div>,
                    document.body,
                  )}

                {errors.categoryId && (
                  <p className="text-red-400 text-xs mt-1.5">
                    {t(errors.categoryId)}
                  </p>
                )}
              </div>
            </div>

            {/* RIGHT COLUMN */}
            <div className="px-4 sm:px-7 py-5 sm:py-6 border-t lg:border-t-0 lg:border-l border-white/[0.055]">
              {/* Importance - Only for expense categories */}
              {(!selectedCategory || selectedCategory?.type === "EXPENSE") && (
                <div className="mb-5">
                  <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2">
                    {t("transaction.importance")}{" "}
                    {selectedCategory?.type === "EXPENSE" ? "*" : ""}
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    {IMPORTANCE_OPTIONS.map((option) => {
                      const isSelected = formData.importance === option.value;
                      const isInvestment = option.colorClass === "inv";

                      let activeClasses =
                        "border-violet-500/40 bg-violet-500/15";
                      if (option.colorClass === "ess")
                        activeClasses = "border-green-500/35 bg-green-500/12";
                      if (option.colorClass === "nice")
                        activeClasses = "border-yellow-500/35 bg-yellow-500/12";
                      if (option.colorClass === "inv")
                        activeClasses = "border-blue-400/45 bg-blue-400/15";
                      if (option.colorClass === "bad")
                        activeClasses = "border-red-500/35 bg-red-500/12";

                      return (
                        <button
                          key={option.value}
                          type="button"
                          onClick={() =>
                            handleChange("importance", option.value)
                          }
                          className={`px-3.5 py-3 rounded-[11px] border text-[15px] font-medium transition-all ${
                            isInvestment ? "col-span-full" : ""
                          } ${
                            isSelected
                              ? activeClasses
                              : "border-white/[0.055] bg-[#131325] text-white/25 hover:border-white/[0.12] hover:text-white cursor-pointer"
                          }`}
                        >
                          {t(option.labelKey)}
                        </button>
                      );
                    })}
                  </div>
                  {errors.importance && (
                    <p className="text-red-400 text-xs mt-1.5">
                      {t(errors.importance)}
                    </p>
                  )}
                </div>
              )}

              {/* Notes */}
              <div>
                <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2">
                  {t("transaction.notes")}
                </div>
                <textarea
                  value={formData.notes}
                  onChange={(e) => handleChange("notes", e.target.value)}
                  rows={4}
                  className="w-full bg-[#131325] border border-white/[0.055] rounded-[11px] px-3.5 py-3 text-[15px] text-white placeholder-white/25 outline-none transition-all resize-y min-h-[90px] max-h-[160px] leading-relaxed focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]"
                  placeholder={t("transaction.notesPlaceholder")}
                />
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="flex items-center justify-end gap-2.5 px-4 sm:px-7 py-[18px] border-t border-white/[0.055] bg-[rgba(6,6,15,0.4)]">
            {errors.submit && (
              <p className="text-red-400 text-sm mr-auto">{errors.submit}</p>
            )}
            <button
              type="button"
              onClick={onClose}
              className="px-[22px] py-3 bg-[#131325] border border-white/[0.055] rounded-xl text-base font-semibold text-white/50 cursor-pointer hover:border-white/[0.12] hover:text-white transition-all"
            >
              {t("common.cancel")}
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex items-center gap-2 px-6 py-3 rounded-xl border-none text-base font-bold text-white cursor-pointer transition-all disabled:opacity-50 disabled:cursor-not-allowed hover:-translate-y-px"
              style={{
                background: "linear-gradient(135deg, #7c3aed, #a855f7)",
                boxShadow: "0 4px 20px rgba(124,58,237,0.3)",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow =
                  "0 8px 32px rgba(124,58,237,0.3)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow =
                  "0 4px 20px rgba(124,58,237,0.3)";
              }}
            >
              {submitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  {t("common.loading")}
                </>
              ) : (
                <>
                  <div className="w-[18px] h-[18px] bg-white/20 rounded-[6px] flex items-center justify-center">
                    <Save className="w-2.5 h-2.5" />
                  </div>
                  {t("transaction.saveTransaction")}
                </>
              )}
            </button>
          </div>
        </form>
      </div>

      {/* Category Modal */}
      {showCategoryModal && (
        <CategoryModal
          isOpen={showCategoryModal}
          onClose={() => setShowCategoryModal(false)}
          onSave={handleCreateCategory}
          category={null}
        />
      )}
    </div>
  );
}
