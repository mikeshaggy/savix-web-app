'use client';

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

const STORAGE_KEY = 'savix_lang';
const SUPPORTED_LANGS = ['en', 'pl'];
const DEFAULT_LANG = 'en';

const detectBrowserLanguage = () => {
  if (typeof window === 'undefined') return DEFAULT_LANG;
  
  const browserLang = navigator.language || navigator.userLanguage || '';
  return browserLang.toLowerCase().startsWith('pl') ? 'pl' : DEFAULT_LANG;
};

const getInitialLanguage = () => {
  if (typeof window === 'undefined') return DEFAULT_LANG;
  
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && SUPPORTED_LANGS.includes(stored)) {
      return stored;
    }
  } catch (e) {
    console.warn('Could not access localStorage:', e);
  }
  
  return detectBrowserLanguage();
};

const LanguageContext = createContext(undefined);

export const useLanguage = () => {
  const context = useContext(LanguageContext);
  if (!context) {
    throw new Error('useLanguage must be used within a LanguageProvider');
  }
  return context;
};

export function LanguageProvider({ children }) {
  const [lang, setLangState] = useState(DEFAULT_LANG);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const initialLang = getInitialLanguage();
    setLangState(initialLang);
    setMounted(true);
  }, []);

  const setLang = useCallback((newLang) => {
    if (!SUPPORTED_LANGS.includes(newLang)) {
      console.warn(`Unsupported language: ${newLang}`);
      return;
    }
    
    setLangState(newLang);
    
    try {
      localStorage.setItem(STORAGE_KEY, newLang);
    } catch (e) {
      console.warn('Could not save language to localStorage:', e);
    }
  }, []);

  const value = {
    lang,
    setLang,
    mounted,
  };

  return (
    <LanguageContext.Provider value={value}>
      {children}
    </LanguageContext.Provider>
  );
}

export default LanguageProvider;
