'use client';

import { useMemo } from 'react';
import { useUser } from '@/contexts/UserContext';

// Roadmap feature flags served by GET /api/me (`features`). Every flag defaults to
// false until the user is loaded or when the backend omits the object/key.
export function useFeatures() {
  const { features } = useUser();

  return useMemo(
    () => ({
      payCycleV2: features?.payCycleV2 === true,
      forecastV2: features?.forecastV2 === true,
      dashboardV2: features?.dashboardV2 === true,
      insightsV2: features?.insightsV2 === true,
    }),
    [features]
  );
}

export default useFeatures;
