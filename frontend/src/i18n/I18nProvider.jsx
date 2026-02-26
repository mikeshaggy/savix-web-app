'use client';

import React, { useState, useEffect } from 'react';
import { NextIntlClientProvider } from 'next-intl';
import { useLanguage } from './LanguageProvider';

import enMessages from '@/messages/en.json';
import plMessages from '@/messages/pl.json';

const messagesMap = {
  en: enMessages,
  pl: plMessages,
};

export function I18nProvider({ children }) {
  const { lang, mounted } = useLanguage();
  const [messages, setMessages] = useState(messagesMap.en);

  useEffect(() => {
    if (mounted) {
      setMessages(messagesMap[lang] || messagesMap.en);
    }
  }, [lang, mounted]);

  const currentLocale = mounted ? lang : 'en';
  const currentMessages = mounted ? (messagesMap[lang] || messagesMap.en) : messagesMap.en;

  return (
    <NextIntlClientProvider
      locale={currentLocale}
      messages={currentMessages}
      timeZone="Europe/Warsaw"
      onError={(error) => {
        if (process.env.NODE_ENV === 'development') {
          console.warn('Translation error:', error.message);
        }
      }}
      getMessageFallback={({ namespace, key }) => {
        return `${namespace}.${key}`;
      }}
    >
      {children}
    </NextIntlClientProvider>
  );
}

export default I18nProvider;
