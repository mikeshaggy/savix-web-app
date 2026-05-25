import { Suspense } from 'react';
import AnalyticsShell from '@/components/analytics/AnalyticsShell';

/**
 * Shared layout for all /analytics/* subpages.
 * AnalyticsShell is a client component that uses useSearchParams, so it must
 * be wrapped in <Suspense> per the Next.js App Router requirement.
 */
export default function AnalyticsLayout({ children }) {
  return (
    <Suspense
      fallback={
        <div className="space-y-3">
          <div className="h-10 rounded-xl bg-white/[0.03] animate-pulse" />
          <div className="h-10 rounded-xl bg-white/[0.03] animate-pulse" />
        </div>
      }
    >
      <AnalyticsShell>{children}</AnalyticsShell>
    </Suspense>
  );
}
