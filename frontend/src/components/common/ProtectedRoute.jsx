'use client';
import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useUser } from '@/contexts/UserContext';
import { PageLoading } from '@/components/common/Loading';
import { useTranslations } from 'next-intl';

const PUBLIC_ROUTES = [
  '/login',
  '/register',
  '/forgot-password',
  '/reset-password',
];


const isPublicRoute = (pathname) => {
  return PUBLIC_ROUTES.some(route => pathname === route || pathname.startsWith(`${route}/`));
};

export default function ProtectedRoute({ children }) {
  const router = useRouter();
  const pathname = usePathname();
  const { isLoading, isAuthenticated, sessionExpired, clearSessionExpired } = useUser();
  const t = useTranslations();

  useEffect(() => {
    if (isLoading) return;

    const publicRoute = isPublicRoute(pathname);

    if (!isAuthenticated && !publicRoute) {
      const loginUrl = sessionExpired ? '/login?expired=true' : '/login';
      clearSessionExpired();
      router.replace(loginUrl);
    } else if (isAuthenticated && publicRoute) {
      router.replace('/dashboard');
    }
  }, [isLoading, isAuthenticated, sessionExpired, pathname, router, clearSessionExpired]);

  if (isLoading) {
    return <PageLoading message={t('auth.checkingAuth')} />;
  }

  const publicRoute = isPublicRoute(pathname);

  if (!isAuthenticated && !publicRoute) {
    return <PageLoading message={t('auth.redirectingToLogin2')} />;
  }

  if (isAuthenticated && publicRoute) {
    return <PageLoading message={t('auth.redirectingToDashboard')} />;
  }

  return children;
}

export const useRouteProtection = () => {
  const pathname = usePathname();
  const publicRoute = isPublicRoute(pathname);
  
  return {
    isPublicRoute: publicRoute,
    isProtectedRoute: !publicRoute,
  };
};
