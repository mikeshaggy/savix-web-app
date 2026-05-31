'use client';
import React from 'react';

// Two variants cover the two distinct uppercase label patterns used in the app:
//   card   — dashboard/analytics card headers (10px, tracking 0.12em, white/35)
//   metric — analytics metric cards (10px, tracking 0.14em, white/25)
//
// className is additive (flex, gap, etc.) — do not pass conflicting font-size
// or opacity utilities without tailwind-merge.

const variants = {
  card:   'text-[10px] font-bold tracking-[0.12em] uppercase text-white/35',
  metric: 'text-[10px] font-bold tracking-[0.14em] uppercase text-white/25',
};

export default function SectionLabel({ variant = 'card', className = '', children, ...props }) {
  return (
    <p className={`${variants[variant]} ${className}`} {...props}>
      {children}
    </p>
  );
}
