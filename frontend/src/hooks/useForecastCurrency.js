'use client';

import { useCallback } from 'react';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { withTypographicMinus } from '@/lib/forecastV2';

// Forecast v2 panels (Stage 5.7): the locale-bound `formatCurrency` with a typographic minus, so exact amounts
// ("−PLN 140.50") and rounded forecast figures ("≈ −PLN 380") read the same. Legacy panels keep `useFormatCurrency`.
export function useForecastCurrency() {
  const formatCurrency = useFormatCurrency();
  return useCallback((amount) => withTypographicMinus(formatCurrency(amount)), [formatCurrency]);
}

export default useForecastCurrency;
