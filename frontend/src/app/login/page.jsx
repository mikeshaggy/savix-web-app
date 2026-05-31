'use client';
import { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import Image from 'next/image';
import { Loader2, Mail, Lock, AlertCircle, Clock } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { authApi, ApiError } from '@/lib/api';
import { useUser } from '@/contexts/UserContext';

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { loadUser } = useUser();
  const t = useTranslations('auth');
  const [formData, setFormData] = useState({
    email: '',
    password: '',
  });
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState(false);

  useEffect(() => {
    if (searchParams.get('expired') === 'true') {
      setSessionExpiredMessage(true);
      window.history.replaceState({}, '', '/login');
    }
  }, [searchParams]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
    setError(null);
    setSessionExpiredMessage(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsLoading(true);
    setError(null);

    try {
      await authApi.login({
        email: formData.email,
        password: formData.password,
      });
      
      await loadUser();
      
      router.replace('/dashboard');
    } catch (err) {
      console.error('Login failed:', err);
      
      if (err instanceof ApiError) {
        if (err.isUnauthorized) {
          setError(t('invalidCredentials'));
        } else {
          setError(err.message || t('loginFailed'));
        }
      } else {
        setError(t('serverError'));
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-dvh bg-[#06060f] flex items-center justify-center px-4">
      <div className="w-full max-w-md" style={{ animation: 'fadeUp 0.35s cubic-bezier(0.4,0,0.2,1) both' }}>
        {/* Logo/Brand */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-18 h-18 rounded-3xl mb-4">
            <Image src="/logo.png" alt="Savix" width={192} height={192} className="rounded-3xl" />
          </div>
          <h1 className="text-2xl font-bold text-white">{t('welcomeBack')}</h1>
          <p className="text-white/45 mt-2">{t('signInToAccount')}</p>
        </div>

        {/* Login Form */}
        <div className="bg-[#0e0e1c] border border-white/[0.055] rounded-xl p-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Session Expired Alert */}
            {sessionExpiredMessage && (
              <div className="flex items-center gap-3 p-3 bg-amber-500/10 border border-amber-500/20 rounded-xl text-amber-400">
                <Clock className="w-5 h-5 shrink-0" />
                <p className="text-sm">{t('sessionExpired')}</p>
              </div>
            )}

            {/* Error Alert */}
            {error && (
              <div className="flex items-center gap-3 p-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400">
                <AlertCircle className="w-5 h-5 shrink-0" />
                <p className="text-sm">{error}</p>
              </div>
            )}

            {/* Email Field */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-white/70 mb-2">
                {t('email')}
              </label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-white/25" />
                <input
                  type="email"
                  id="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  placeholder={t('enterEmail')}
                  required
                  autoComplete="email"
                  className="w-full bg-[#131325] border border-white/[0.055] rounded-[11px] pl-10 pr-4 py-3 text-base text-white placeholder:text-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e]"
                />
              </div>
            </div>

            {/* Password Field */}
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-white/70 mb-2">
                {t('password')}
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-white/25" />
                <input
                  type="password"
                  id="password"
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  placeholder={t('enterPassword')}
                  required
                  autoComplete="current-password"
                  className="w-full bg-[#131325] border border-white/[0.055] rounded-[11px] pl-10 pr-4 py-3 text-base text-white placeholder:text-white/25 outline-none transition-all focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e]"
                />
              </div>
            </div>

            {/* Forgot Password Link */}
            <div className="text-right">
              <Link
                href="/forgot-password"
                className="text-sm text-violet-400 hover:text-violet-300 transition-colors"
              >
                {t('forgotPassword')}
              </Link>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={isLoading}
              className="w-full bg-gradient-to-br from-[#7c3aed] to-[#a855f7] shadow-[0_4px_20px_rgba(124,58,237,0.3)] hover:shadow-[0_8px_32px_rgba(124,58,237,0.3)] hover:-translate-y-px active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold py-3 px-4 rounded-xl transition-all flex items-center justify-center gap-2"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  {t('common.loading', { ns: 'common' })}
                </>
              ) : (
                t('login')
              )}
            </button>
          </form>

          {/* Register Link */}
          <div className="mt-6 pt-6 border-t border-white/[0.055] text-center">
            <p className="text-white/45">
              {t('dontHaveAccount')}{' '}
              <Link
                href="/register"
                className="text-violet-400 hover:text-violet-300 font-medium transition-colors"
              >
                {t('signUp')}
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={
      <div className="min-h-dvh bg-[#06060f] flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-violet-500" />
      </div>
    }>
      <LoginForm />
    </Suspense>
  );
}
