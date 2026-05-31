'use client';
import React from 'react';
import Link from 'next/link';
import { usePathname, useSearchParams } from 'next/navigation';
import { LayoutDashboard, TrendingUp, PieChart, GitCompareArrows, CalendarDays } from 'lucide-react';
import { useTranslations } from 'next-intl';

const TABS = [
  { id: 'overview',    href: '/analytics/overview',    icon: LayoutDashboard,    labelKey: 'navOverview' },
  { id: 'forecast',   href: '/analytics/forecast',    icon: TrendingUp,          labelKey: 'navForecast' },
  { id: 'breakdown',  href: '/analytics/breakdown',   icon: PieChart,            labelKey: 'navBreakdown' },
  { id: 'comparison', href: '/analytics/comparison',  icon: GitCompareArrows,    labelKey: 'navComparison' },
  { id: 'daily',      href: '/analytics/daily',       icon: CalendarDays,        labelKey: 'navDaily' },
];

export default function AnalyticsSubNav() {
  const t           = useTranslations('analytics');
  const pathname    = usePathname();
  const searchParams = useSearchParams();

  // Preserve period params when switching tabs
  const qs = searchParams.toString();

  return (
    <nav
      className="flex items-center gap-0.5 bg-[#0e0e1c] border border-white/[0.055] rounded-xl p-1 mb-5 overflow-x-auto"
      aria-label="Analytics sections"
    >
      {TABS.map(({ id, href, icon: Icon, labelKey }) => {
        const isActive = pathname.startsWith(href);
        const fullHref = qs ? `${href}?${qs}` : href;

        return (
          <Link
            key={id}
            href={fullHref}
            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] text-xs font-semibold whitespace-nowrap transition-all flex-shrink-0 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-violet-400/60 ${
              isActive
                ? 'bg-purple-600 text-white shadow-[0_2px_12px_rgba(124,58,237,0.3)]'
                : 'text-white/30 hover:text-white/60 hover:bg-white/[0.04]'
            }`}
          >
            <Icon className="w-3.5 h-3.5 flex-shrink-0" />
            {t(labelKey)}
          </Link>
        );
      })}
    </nav>
  );
}
