import React, { useState, useRef, useEffect } from "react";
import { Plus, Receipt, ArrowLeftRight } from "lucide-react";
import { useTranslations } from "next-intl";

export default function NewActionDropdown({
  onNewTransaction,
  onNewTransfer,
  variant = "sidebar",
}) {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef(null);
  const t = useTranslations();

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleSelect = (action) => {
    setIsOpen(false);
    action();
  };

  const isInline = variant === "inline";

  return (
    <div className="relative" ref={dropdownRef}>
      {/* Main Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={
          isInline
            ? "flex items-center gap-2 px-[18px] py-[10px] rounded-[10px] text-[14px] font-bold cursor-pointer border-none text-white transition-all hover:-translate-y-px"
            : "w-full bg-gradient-to-br from-[#7c3aed] to-[#a855f7] rounded-[13px] py-[13px] flex items-center justify-center gap-2 text-[14px] font-bold text-white cursor-pointer border-none shadow-[0_4px_20px_rgba(124,58,237,0.3)] transition-all hover:-translate-y-px hover:shadow-[0_8px_32px_rgba(124,58,237,0.3)]"
        }
        style={
          isInline
            ? {
                background: "linear-gradient(135deg, #7c3aed, #a855f7)",
                boxShadow: "0 4px 20px rgba(124,58,237,0.3)",
              }
            : undefined
        }
        onMouseEnter={
          isInline
            ? (e) => {
                e.currentTarget.style.boxShadow =
                  "0 8px 32px rgba(124,58,237,0.3)";
              }
            : undefined
        }
        onMouseLeave={
          isInline
            ? (e) => {
                e.currentTarget.style.boxShadow =
                  "0 4px 20px rgba(124,58,237,0.3)";
              }
            : undefined
        }
      >
        <Plus className="w-[14px] h-[14px]" strokeWidth={2.5} />
        {t("newAction.button")}
      </button>

      {/* Dropdown Menu */}
      {isOpen && (
        <div
          className={`absolute bg-[#131325] border border-white/[0.055] rounded-xl shadow-[0_16px_48px_rgba(0,0,0,0.5)] z-50 overflow-hidden w-[240px] ${
            isInline
              ? "top-full right-0 mt-2"
              : "bottom-full left-0 right-0 mb-2 w-auto"
          }`}
          style={{
            animation: "fadeUp 0.15s cubic-bezier(0.4,0,0.2,1) both",
          }}
        >
          <div className="p-1.5">
            <button
              onClick={() => handleSelect(onNewTransaction)}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-[9px] hover:bg-white/[0.04] text-white/50 hover:text-white transition-all cursor-pointer text-left"
            >
              <div className="w-8 h-8 rounded-lg bg-purple-500/15 border border-purple-500/25 flex items-center justify-center">
                <Receipt className="w-4 h-4 text-purple-300" />
              </div>
              <div>
                <div className="text-[13px] font-semibold">
                  {t("newAction.transaction")}
                </div>
                <div className="text-[11px] text-white/25">
                  {t("newAction.transactionDesc")}
                </div>
              </div>
            </button>

            <button
              onClick={() => handleSelect(onNewTransfer)}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-[9px] hover:bg-white/[0.04] text-white/50 hover:text-white transition-all cursor-pointer text-left"
            >
              <div className="w-8 h-8 rounded-lg bg-blue-500/15 border border-blue-500/25 flex items-center justify-center">
                <ArrowLeftRight className="w-4 h-4 text-blue-300" />
              </div>
              <div>
                <div className="text-[13px] font-semibold">
                  {t("newAction.transfer")}
                </div>
                <div className="text-[11px] text-white/25">
                  {t("newAction.transferDesc")}
                </div>
              </div>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
