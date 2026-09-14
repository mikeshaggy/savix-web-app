'use client';

import { useCallback } from 'react';
import { useLocale } from 'next-intl';
import { formatCurrency as formatCurrencyBase } from '@/utils/helpers';

// Binds formatCurrency to the active UI locale so call sites never have to
// remember to pass `lang` — the class of bug behind the EN/PL formatting
// inconsistency (dashboard/analytics rendering pl-PL grouping regardless of
// the selected language).
export function useFormatCurrency() {
  const locale = useLocale();
  const lang = locale === 'pl' ? 'pl' : 'en';

  return useCallback((amount) => formatCurrencyBase(amount, lang), [lang]);
}

export default useFormatCurrency;
