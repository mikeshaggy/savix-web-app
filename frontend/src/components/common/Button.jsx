'use client';
import React from 'react';

// Two variants, two sizes — covers all modal footers in the app.
//
// variant  primary   — violet gradient, animated shadow, lift on hover
//          secondary — muted surface, border, text fade
//
// size     md (default) — px-6/px-[22px] py-3 rounded-xl text-base
//          sm           — px-5/px-[18px] py-2.5 rounded-[10px] text-[14px]
//                         (CategoryModal uses the smaller footers)
//
// All standard button attributes are forwarded via ...props.
// Children are rendered as-is — loading spinners and icon wrappers
// stay in the calling component to preserve existing behaviour.

const variantStyles = {
  primary:
    'flex items-center gap-2 font-bold text-white ' +
    'bg-gradient-to-br from-[#7c3aed] to-[#a855f7] ' +
    'shadow-[0_4px_20px_rgba(124,58,237,0.3)] ' +
    'hover:shadow-[0_8px_32px_rgba(124,58,237,0.3)] ' +
    'hover:-translate-y-px ' +
    'active:scale-[0.98] ' +
    'border-0 ' +
    'disabled:opacity-50 disabled:cursor-not-allowed',
  secondary:
    'font-semibold text-white/50 ' +
    'bg-[#131325] border border-white/[0.055] ' +
    'hover:border-white/[0.12] hover:text-white ' +
    'active:bg-[#0e0e1c] active:scale-[0.98] ' +
    'disabled:opacity-40 disabled:cursor-not-allowed',
};

const sizeMap = {
  primary: {
    md: 'px-6 py-3 rounded-xl text-base',
    sm: 'px-5 py-2.5 rounded-[10px] text-[14px]',
  },
  secondary: {
    md: 'px-[22px] py-3 rounded-xl text-base',
    sm: 'px-[18px] py-2.5 rounded-[10px] text-[14px]',
  },
};

export default function Button({
  variant = 'primary',
  size = 'md',
  type = 'button',
  disabled = false,
  onClick,
  className = '',
  children,
  ...props
}) {
  return (
    <button
      type={type}
      disabled={disabled}
      onClick={onClick}
      className={`cursor-pointer transition-all ${variantStyles[variant]} ${sizeMap[variant][size]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
