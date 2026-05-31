'use client';
import React from 'react';

// Surface-1 card primitive: dark background, subtle border, soft radius.
// Use className to override rounded-2xl / padding / overflow as needed.
// Adoption is additive — existing hardcoded cards are not migrated here yet.

export default function AppCard({ children, className = '', ...props }) {
  return (
    <div
      className={`bg-[#0e0e1c] border border-white/[0.06] rounded-2xl ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}
