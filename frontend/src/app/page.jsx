'use client';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useUser } from '@/contexts/UserContext';
import { PageLoading } from '@/components/common/Loading';
import { useTranslations } from 'next-intl';

export default function HomePage() {
  const router = useRouter();
  const { isLoading, isAuthenticated } = useUser();
  const t = useTranslations();
  
  useEffect(() => {
    if (isLoading) return;
    
    if (isAuthenticated) {
      router.replace('/dashboard');
    } else {
      router.replace('/login');
    }
  }, [isLoading, isAuthenticated, router]);

  return <PageLoading message={t('common.loading')} />;
}
