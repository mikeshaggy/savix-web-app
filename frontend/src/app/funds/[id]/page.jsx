'use client';
import React from 'react';
import { useParams } from 'next/navigation';
import FundDetailPage from '@/components/pages/FundDetailPage';

export default function FundDetailRoute() {
  const params = useParams();
  return <FundDetailPage fundId={params.id} />;
}
