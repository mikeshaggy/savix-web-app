'use client';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useUser } from '@/contexts/UserContext';
import { PageLoading } from '@/components/common/Loading';

export default function HomePage() {
  const router = useRouter();
  const { isLoading, isAuthenticated } = useUser();
  
  useEffect(() => {
    // Wait for auth check to complete
    if (isLoading) return;
    
    if (isAuthenticated) {
      // Authenticated users go to dashboard
      router.replace('/dashboard');
    } else {
      // Unauthenticated users go to login
      router.replace('/login');
    }
  }, [isLoading, isAuthenticated, router]);

  // Show loading while checking auth
  return <PageLoading message="Loading..." />;
}
