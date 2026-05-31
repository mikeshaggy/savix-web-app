// Form control class-string constants.
// Exclude border-color — supply it at each usage site to avoid
// class conflicts without tailwind-merge.
// Exclude width (w-full), icon-padding (pl-10, pr-8), and
// element-specific attributes (min-h, max-h, resize-y) — supply at usage site.

// ─── MD modals (TransactionModal, FixedPaymentModal, TransferModal) ───────────

export const INPUT_MD =
  'bg-[#131325] border rounded-[11px] px-3.5 py-3 ' +
  'text-base text-white placeholder:text-white/25 ' +
  'outline-none transition-all ' +
  'focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e]';

export const SELECT_MD =
  'bg-[#131325] border rounded-[11px] px-3.5 py-3 ' +
  'text-base text-white ' +
  'outline-none transition-all appearance-none cursor-pointer ' +
  'focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e]';

export const TEXTAREA_MD =
  'bg-[#131325] border rounded-[11px] px-3.5 py-3 ' +
  'text-base text-white placeholder:text-white/25 ' +
  'outline-none transition-all resize-y leading-relaxed ' +
  'focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e]';

// ─── SM modal (CategoryModal) ─────────────────────────────────────────────────

export const INPUT_SM =
  'bg-[#131325] border rounded-[10px] px-3.5 py-2.5 ' +
  'text-[15px] text-white placeholder:text-white/25 ' +
  'outline-none transition-all ' +
  'focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e]';

// ─── Filter bar (TransactionFilters) ─────────────────────────────────────────
// Icon-padding (pl-10, pr-10 for search; px-3.5 for dates) stays at usage site.

export const FILTER_SEARCH =
  'bg-[#0e0e1c] border rounded-[12px] ' +
  'text-[14px] text-white placeholder:text-white/25 ' +
  'outline-none transition-all ' +
  'focus:border-violet-500/40 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.08)]';

export const FILTER_DATE =
  'bg-[#131325] border rounded-[10px] px-3.5 py-[7px] ' +
  'text-[13px] text-white ' +
  'outline-none transition-all [color-scheme:dark] ' +
  'focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]';

// ─── Compact dropdown (MultiSelectDropdown) ───────────────────────────────────
// pl-8 (search icon padding) stays at usage site.
// Border color included here — this input never has an error state.

export const DROPDOWN_SEARCH =
  'bg-[#0e0e1c] border border-white/[0.07] rounded-lg pr-2.5 py-1.5 ' +
  'text-[12px] text-white placeholder:text-white/30 ' +
  'outline-none transition-all ' +
  'focus:border-violet-500/40 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.08)]';

// ─── Chip / selector pill ─────────────────────────────────────────────────────
// Used by: MultiSelectDropdown trigger button, TransactionFilters date+sort selects,
//          FixedPaymentsView category filter select.
// Border color is INCLUDED in CHIP_DEFAULT because chips never have an error state
// that would require switching to border-red-500.

export const CHIP_BASE =
  'appearance-none outline-none rounded-[10px] px-[13px] py-[7px] ' +
  'text-[13px] font-medium cursor-pointer transition-all whitespace-nowrap ' +
  'focus-visible:ring-1 focus-visible:ring-violet-400/60';

export const CHIP_DEFAULT =
  'bg-[#0e0e1c] border border-white/[0.055] text-white/50 ' +
  'hover:border-white/[0.12] hover:text-white';

export const CHIP_ACTIVE =
  'bg-[rgba(124,58,237,0.14)] border border-[rgba(124,58,237,0.35)] text-purple-300';

// ─── Compact controls inside a surface-2 card (TransfersPage QuickTransferForm) ─
// Used for selects and inputs that sit on a #131325 surface, so bg uses surface-1.
// Border color EXCLUDED — supply exactly one border color at usage site (same
// strategy as modal constants).

export const QUICK_SELECT =
  'bg-[#0e0e1c] border rounded-[11px] px-3.5 py-2.5 ' +
  'text-sm text-white ' +
  'outline-none transition-all appearance-none cursor-pointer ' +
  'focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]';

export const QUICK_INPUT =
  'bg-[#0e0e1c] border rounded-[11px] px-3.5 py-2.5 ' +
  'text-sm text-white placeholder:text-white/20 ' +
  'outline-none transition-all ' +
  'focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]';

// ─── Page-level primary CTA ───────────────────────────────────────────────────
// Used by view-level header/error/empty-state "Add X" buttons in
// WalletManagementView, FixedPaymentsView, CategoryManagementView.
// Intentionally distinct from Button.jsx primary — these views have an older
// design vocabulary. Phase 3 will migrate them to Button.jsx.
// Centering (mx-auto) and disabled states stay at the usage site.

export const PAGE_CTA =
  'bg-gradient-to-br from-[#7c3aed] to-[#a855f7] border-none rounded-[8px] px-4 py-2 ' +
  'text-white text-[13.5px] font-medium cursor-pointer ' +
  'flex items-center gap-[6px] ' +
  'shadow-[0_4px_16px_rgba(124,58,237,0.25)] transition-all ' +
  'hover:opacity-90 hover:-translate-y-[1px]';
