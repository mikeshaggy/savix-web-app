'use client';
import React from 'react';
import { TrendingUp, TrendingDown, Wallet, PiggyBank, ShoppingCart, Hash } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { formatCurrency } from '@/utils/helpers';
import AnalyticsMetricCard from './AnalyticsMetricCard';
import { CardLoading } from '@/components/common/Loading';

export default function MonthlyOverviewSection({ data, loading }) {
  const t = useTranslations('analytics');

  if (loading) {
    return (
      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        {Array.from({ length: 6 }).map((_, i) => (
          <CardLoading key={i} />
        ))}
      </div>
    );
  }

  if (!data) return null;

  const cards = [
    {
      key: 'income',
      label: t('income'),
      value: formatCurrency(data.income),
      icon: TrendingUp,
      color: '#4ade80',
    },
    {
      key: 'expenses',
      label: t('expenses'),
      value: formatCurrency(data.expenses),
      icon: TrendingDown,
      color: '#f87171',
    },
    {
      key: 'balance',
      label: t('balance'),
      value: formatCurrency(data.balance),
      icon: Wallet,
      color: '#c084fc',
    },
    {
      key: 'savingsRate',
      label: t('savingsRate'),
      value: (
        <>
          {data.savingsRate}
          <span className="text-[15px] font-light opacity-55 tracking-normal">%</span>
        </>
      ),
      subtext: t('ofIncome'),
      icon: PiggyBank,
      color: '#fbbf24',
    },
    {
      key: 'avgDailySpending',
      label: t('avgDailySpending'),
      value: formatCurrency(data.avgDailySpending),
      subtext: t('daysElapsed', { elapsed: data.daysElapsed, total: data.daysInPeriod }),
      icon: ShoppingCart,
      color: '#60a5fa',
    },
    {
      key: 'transactionCount',
      label: t('transactionCount'),
      value: data.transactionCount,
      subtext: data.startDate && data.endDate ? `${data.startDate} – ${data.endDate}` : null,
      icon: Hash,
      color: '#2dd4bf',
    },
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
      {cards.map((card) => (
        <AnalyticsMetricCard
          key={card.key}
          label={card.label}
          value={card.value}
          subtext={card.subtext}
          icon={card.icon}
          color={card.color}
        />
      ))}
    </div>
  );
}
