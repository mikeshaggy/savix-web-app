import React, { useState, useEffect, useCallback, useMemo } from "react";
import { X, Save, Loader2, ArrowRight } from "lucide-react";
import { useWallets } from "@/contexts/WalletContext";
import { useTranslations } from "next-intl";
import { useLanguage } from "@/i18n";
import { formatCurrency } from "@/utils/helpers";

const QUICK_AMOUNTS = [50, 100, 200, 500];

export default function TransferModal({ isOpen, onClose, onSave }) {
  const { currentWallet, wallets } = useWallets();
  const t = useTranslations();
  const { lang } = useLanguage();

  const [formData, setFormData] = useState({
    fromWalletId: "",
    toWalletId: "",
    amount: "",
    transferDate: new Date().toISOString().split("T")[0],
    notes: "",
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setFormData({
        fromWalletId: currentWallet?.id?.toString() || "",
        toWalletId: "",
        amount: "",
        transferDate: new Date().toISOString().split("T")[0],
        notes: "",
      });
      setErrors({});
    }
  }, [isOpen, currentWallet?.id]);

  const handleChange = useCallback(
    (field, value) => {
      setFormData((prev) => ({ ...prev, [field]: value }));
      if (errors[field]) {
        setErrors((prev) => ({ ...prev, [field]: "" }));
      }
    },
    [errors]
  );

  const handleQuickAmount = useCallback(
    (add) => {
      const current = parseFloat(formData.amount.replace(",", ".")) || 0;
      const next = (current + add).toFixed(2);
      handleChange("amount", next);
    },
    [formData.amount, handleChange]
  );

  const handleQuickDate = useCallback(
    (type) => {
      const d = new Date();
      if (type === "yesterday") d.setDate(d.getDate() - 1);
      handleChange("transferDate", d.toISOString().split("T")[0]);
    },
    [handleChange]
  );

  const fromWallet = useMemo(
    () => wallets.find((w) => w.id === parseInt(formData.fromWalletId)),
    [wallets, formData.fromWalletId]
  );

  const toWallet = useMemo(
    () => wallets.find((w) => w.id === parseInt(formData.toWalletId)),
    [wallets, formData.toWalletId]
  );

  const parsedAmount = parseFloat(formData.amount) || 0;

  const fromBalanceAfter = fromWallet
    ? (fromWallet.balance ?? 0) - parsedAmount
    : null;

  const toBalanceAfter = toWallet
    ? (toWallet.balance ?? 0) + parsedAmount
    : null;

  const destinationWallets = useMemo(
    () => wallets.filter((w) => w.id !== parseInt(formData.fromWalletId)),
    [wallets, formData.fromWalletId]
  );

  useEffect(() => {
    if (
      formData.fromWalletId &&
      formData.toWalletId &&
      formData.fromWalletId === formData.toWalletId
    ) {
      setFormData((prev) => ({ ...prev, toWalletId: "" }));
    }
  }, [formData.fromWalletId, formData.toWalletId]);

  const validateForm = () => {
    const newErrors = {};

    if (!formData.fromWalletId) {
      newErrors.fromWalletId = "transfer.errors.fromWalletRequired";
    }

    if (!formData.toWalletId) {
      newErrors.toWalletId = "transfer.errors.toWalletRequired";
    }

    if (formData.fromWalletId === formData.toWalletId) {
      newErrors.toWalletId = "transfer.errors.sameWallet";
    }

    const amount = parseFloat(formData.amount);
    if (!formData.amount || isNaN(amount) || amount < 0.01) {
      newErrors.amount = "errors.amountRequired";
    }

    if (!formData.transferDate) {
      newErrors.transferDate = "errors.dateRequired";
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) return;

    try {
      setSubmitting(true);

      const transferData = {
        fromWalletId: parseInt(formData.fromWalletId),
        toWalletId: parseInt(formData.toWalletId),
        amount: parseFloat(formData.amount),
        transferDate: formData.transferDate,
        notes: formData.notes?.trim() || undefined,
      };

      await onSave(transferData);

      setFormData({
        fromWalletId: currentWallet?.id?.toString() || "",
        toWalletId: "",
        amount: "",
        transferDate: new Date().toISOString().split("T")[0],
        notes: "",
      });
      setErrors({});
      onClose();
    } catch (error) {
      console.error("Failed to save transfer:", error);
      if (error.message) {
        setErrors((prev) => ({ ...prev, submit: error.message }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleSwapWallets = useCallback(() => {
    setFormData((prev) => ({
      ...prev,
      fromWalletId: prev.toWalletId,
      toWalletId: prev.fromWalletId,
    }));
  }, []);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-[rgba(4,4,12,0.85)] backdrop-blur-[8px] flex items-center justify-center z-50 p-3 sm:p-6">
      <div
        className="bg-[#0e0e1c] border border-white/[0.12] rounded-2xl sm:rounded-3xl w-full max-w-[640px] max-h-[90vh] overflow-hidden relative"
        style={{
          boxShadow:
            "0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(124,58,237,0.1), 0 0 80px rgba(124,58,237,0.06)",
          animation: "fadeUp 0.3s cubic-bezier(0.4,0,0.2,1) both",
        }}
      >
        {/* Top glow line */}
        <div className="absolute top-0 left-[10%] right-[10%] h-px bg-gradient-to-r from-transparent via-purple-400 to-transparent opacity-60" />

        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-7 pt-5 sm:pt-6 pb-4 sm:pb-5 border-b border-white/[0.055]">
          <div className="flex items-center gap-2.5 text-lg sm:text-xl font-bold tracking-[-0.3px]">
            {t("transfer.newTransfer")}
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-[10px] bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-white hover:border-white/[0.12] hover:bg-[#1a1a2e] transition-all"
          >
            <X className="w-3 h-3" />
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit}>
            <div className="px-4 sm:px-7 py-5 sm:py-6 max-h-[calc(90vh-150px)] overflow-y-auto">
            {/* Wallet Selection Row */}
            <div className="mb-6">
              <div className="grid grid-cols-[1fr_auto_1fr] gap-3 items-end">
                {/* From Wallet */}
                <div>
                  <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                    {t("transfer.fromWallet")}{" "}
                    <span className="text-purple-300">*</span>
                  </div>
                  <select
                    value={formData.fromWalletId}
                    onChange={(e) =>
                      handleChange("fromWalletId", e.target.value)
                    }
                    className={`w-full bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white outline-none transition-all appearance-none cursor-pointer focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] ${
                      errors.fromWalletId
                        ? "border-red-500"
                        : "border-white/[0.055]"
                    }`}
                  >
                    <option value="">{t("transfer.selectWallet")}</option>
                    {wallets.map((wallet) => (
                      <option key={wallet.id} value={wallet.id}>
                        {wallet.name}
                      </option>
                    ))}
                  </select>
                  {errors.fromWalletId && (
                    <p className="text-red-400 text-xs mt-1.5">
                      {t(errors.fromWalletId)}
                    </p>
                  )}
                </div>

                {/* Swap Button */}
                <button
                  type="button"
                  onClick={handleSwapWallets}
                  className="w-10 h-10 rounded-full bg-[#131325] border border-white/[0.055] flex items-center justify-center text-white/25 hover:text-purple-300 hover:border-purple-500 hover:bg-purple-500/10 transition-all cursor-pointer mb-[2px]"
                  title={t("transfer.swapWallets")}
                >
                  <ArrowRight className="w-4 h-4" />
                </button>

                {/* To Wallet */}
                <div>
                  <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2 flex items-center gap-1.5">
                    {t("transfer.toWallet")}{" "}
                    <span className="text-purple-300">*</span>
                  </div>
                  <select
                    value={formData.toWalletId}
                    onChange={(e) =>
                      handleChange("toWalletId", e.target.value)
                    }
                    className={`w-full bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white outline-none transition-all appearance-none cursor-pointer focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] ${
                      errors.toWalletId
                        ? "border-red-500"
                        : "border-white/[0.055]"
                    }`}
                  >
                    <option value="">{t("transfer.selectWallet")}</option>
                    {destinationWallets.map((wallet) => (
                      <option key={wallet.id} value={wallet.id}>
                        {wallet.name}
                      </option>
                    ))}
                  </select>
                  {errors.toWalletId && (
                    <p className="text-red-400 text-xs mt-1.5">
                      {t(errors.toWalletId)}
                    </p>
                  )}
                </div>
              </div>
            </div>

            {/* Live Balance Preview */}
            {(fromWallet || toWallet) && parsedAmount > 0 && (
              <div className="mb-6 p-4 bg-[#131325] border border-white/[0.055] rounded-2xl">
                <div className="text-[11px] font-bold tracking-[0.12em] uppercase text-white/25 mb-3">
                  {t("transfer.balancePreview")}
                </div>
                <div className="grid grid-cols-[1fr_auto_1fr] gap-3 items-center">
                  {/* From wallet preview */}
                  <div>
                    {fromWallet && (
                      <>
                        <div className="text-[12px] font-medium text-white/50 mb-1">
                          {fromWallet.name}
                        </div>
                        <div className="text-[11px] text-white/25 line-through">
                          {formatCurrency(fromWallet.balance ?? 0, lang)}
                        </div>
                        <div
                          className={`text-[14px] font-bold font-mono ${
                            fromBalanceAfter < 0
                              ? "text-red-400"
                              : "text-white/80"
                          }`}
                        >
                          {formatCurrency(fromBalanceAfter, lang)}
                        </div>
                      </>
                    )}
                  </div>

                  {/* Arrow */}
                  <div className="flex flex-col items-center gap-1">
                    <div className="text-[12px] font-bold font-mono text-purple-300">
                      {formatCurrency(parsedAmount, lang)}
                    </div>
                    <ArrowRight className="w-4 h-4 text-purple-400" />
                  </div>

                  {/* To wallet preview */}
                  <div className="text-right">
                    {toWallet && (
                      <>
                        <div className="text-[12px] font-medium text-white/50 mb-1">
                          {toWallet.name}
                        </div>
                        <div className="text-[11px] text-white/25 line-through">
                          {formatCurrency(toWallet.balance ?? 0, lang)}
                        </div>
                        <div className="text-[14px] font-bold font-mono text-green-400">
                          {formatCurrency(toBalanceAfter, lang)}
                        </div>
                      </>
                    )}
                  </div>
                </div>

                {/* Warning if negative */}
                {fromBalanceAfter !== null && fromBalanceAfter < 0 && (
                  <div className="mt-3 text-[11px] text-red-400/80 bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2">
                    {t("transfer.negativeBalanceWarning")}
                  </div>
                )}
              </div>
            )}

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
                value={formData.transferDate}
                onChange={(e) =>
                  handleChange("transferDate", e.target.value)
                }
                className={`w-full bg-[#131325] border rounded-[11px] px-3.5 py-3 text-base text-white outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] [color-scheme:dark] ${
                  errors.transferDate
                    ? "border-red-500"
                    : "border-white/[0.055]"
                }`}
              />
              <div className="flex gap-1.5 mt-2">
                <button
                  type="button"
                  onClick={() => handleQuickDate("today")}
                  className={`px-3 py-1.5 rounded-full text-[13px] font-semibold transition-all ${
                    formData.transferDate ===
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
                    formData.transferDate ===
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
              {errors.transferDate && (
                <p className="text-red-400 text-xs mt-1.5">
                  {t(errors.transferDate)}
                </p>
              )}
            </div>

            {/* Notes */}
            <div>
              <div className="text-[13px] font-bold tracking-[0.12em] uppercase text-white/25 mb-2">
                {t("transaction.notes")}
              </div>
              <textarea
                value={formData.notes}
                onChange={(e) => handleChange("notes", e.target.value)}
                rows={3}
                className="w-full bg-[#131325] border border-white/[0.055] rounded-[11px] px-3.5 py-3 text-[15px] text-white placeholder-white/25 outline-none transition-all resize-y min-h-[70px] max-h-[120px] leading-relaxed focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]"
                placeholder={t("transfer.notesPlaceholder")}
              />
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
                  {t("transfer.saveTransfer")}
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
