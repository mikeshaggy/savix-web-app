'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeftRight,
  CalendarCheck,
  Command,
  CreditCard,
  Home,
  PieChart,
  Plus,
  Repeat,
  Search,
  Tag,
  Wallet,
} from 'lucide-react';
import { useTranslations } from 'next-intl';

const normalize = (value) => value.toLowerCase().trim();

export default function CommandPalette({
  onNewTransaction,
  onNewTransfer,
  onAddFixedPayment,
  onAddCategory,
  onAddWallet,
  onNavigate,
}) {
  const t = useTranslations();
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef(null);
  const selectedItemRef = useRef(null);

  const allCommands = useMemo(
    () => [
      {
        id: 'action-transaction',
        label: t('transaction.addTransaction'),
        icon: Plus,
        group: t('commandPalette.quickActions'),
        run: onNewTransaction,
      },
      {
        id: 'action-transfer',
        label: t('transfer.newTransfer'),
        icon: ArrowLeftRight,
        group: t('commandPalette.quickActions'),
        run: onNewTransfer,
      },
      {
        id: 'action-fixed-payment',
        label: t('fixedPayments.addPayment'),
        icon: CreditCard,
        group: t('commandPalette.quickActions'),
        run: onAddFixedPayment,
      },
      {
        id: 'action-category',
        label: t('category.addCategory'),
        icon: Tag,
        group: t('commandPalette.quickActions'),
        run: onAddCategory,
      },
      {
        id: 'action-wallet',
        label: t('wallet.addWallet'),
        icon: Wallet,
        group: t('commandPalette.quickActions'),
        run: onAddWallet,
      },
      {
        id: 'nav-dashboard',
        label: t('commandPalette.goTo', { target: t('nav.dashboard') }),
        icon: Home,
        group: t('commandPalette.navigation'),
        run: () => onNavigate('/dashboard'),
      },
      {
        id: 'nav-transactions',
        label: t('commandPalette.goTo', { target: t('nav.transactions') }),
        icon: Repeat,
        group: t('commandPalette.navigation'),
        run: () => onNavigate('/transactions'),
      },
      {
        id: 'nav-fixed-payments',
        label: t('commandPalette.goTo', { target: t('nav.fixedPayments') }),
        icon: CalendarCheck,
        group: t('commandPalette.navigation'),
        run: () => onNavigate('/transactions/fixed-payments'),
      },
      {
        id: 'nav-categories',
        label: t('commandPalette.goTo', { target: t('nav.categories') }),
        icon: Tag,
        group: t('commandPalette.navigation'),
        run: () => onNavigate('/transactions/categories'),
      },
      {
        id: 'nav-wallets',
        label: t('commandPalette.goTo', { target: t('nav.wallets') }),
        icon: Wallet,
        group: t('commandPalette.navigation'),
        run: () => onNavigate('/wallets'),
      },
      {
        id: 'nav-analytics',
        label: t('commandPalette.goTo', { target: t('nav.analytics') }),
        icon: PieChart,
        group: t('commandPalette.navigation'),
        run: () => onNavigate('/analytics'),
      },
    ],
    [
      onAddCategory,
      onAddFixedPayment,
      onAddWallet,
      onNavigate,
      onNewTransaction,
      onNewTransfer,
      t,
    ]
  );

  const filteredCommands = useMemo(() => {
    const text = normalize(query);
    if (!text) return allCommands;

    return allCommands.filter((command) => {
      const label = normalize(command.label);
      const group = normalize(command.group);
      return label.includes(text) || group.includes(text);
    });
  }, [allCommands, query]);

  useEffect(() => {
    const onShortcut = (event) => {
      const isK = event.key.toLowerCase() === 'k';
      if (!isK) return;

      if (!event.metaKey && !event.ctrlKey) return;

      event.preventDefault();
      setIsOpen((prev) => !prev);
    };

    document.addEventListener('keydown', onShortcut);
    return () => document.removeEventListener('keydown', onShortcut);
  }, []);

  useEffect(() => {
    if (!isOpen) return;

    setQuery('');
    setSelectedIndex(0);

    const timerId = window.setTimeout(() => {
      inputRef.current?.focus();
    }, 0);

    return () => window.clearTimeout(timerId);
  }, [isOpen]);

  useEffect(() => {
    if (selectedIndex >= filteredCommands.length) {
      setSelectedIndex(0);
    }
  }, [filteredCommands.length, selectedIndex]);

  useEffect(() => {
    if (!isOpen) return;
    selectedItemRef.current?.scrollIntoView({ block: 'nearest' });
  }, [filteredCommands, isOpen, selectedIndex]);

  const closePalette = () => {
    setIsOpen(false);
  };

  const executeCommand = (command) => {
    command.run();
    closePalette();
  };

  const handleInputKeyDown = (event) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (filteredCommands.length === 0) return;
      setSelectedIndex((prev) => (prev + 1) % filteredCommands.length);
      return;
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (filteredCommands.length === 0) return;
      setSelectedIndex((prev) =>
        prev === 0 ? filteredCommands.length - 1 : prev - 1
      );
      return;
    }

    if (event.key === 'Enter') {
      event.preventDefault();
      const selectedCommand = filteredCommands[selectedIndex];
      if (selectedCommand) {
        executeCommand(selectedCommand);
      }
      return;
    }

    if (event.key === 'Escape') {
      event.preventDefault();
      closePalette();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[120] flex items-start justify-center px-4 pt-[10vh]">
      <button
        type="button"
        className="absolute inset-0 bg-black/65 backdrop-blur-[2px]"
        onClick={closePalette}
        aria-label={t('commandPalette.close')}
      />

      <div className="relative w-full max-w-[640px] overflow-hidden rounded-2xl border border-white/[0.08] bg-[#121224] shadow-[0_24px_90px_rgba(0,0,0,0.55)]">
        <div className="flex items-center gap-3 border-b border-white/[0.06] px-4 py-3">
          <Search className="h-4 w-4 text-white/35" />
          <input
            ref={inputRef}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setSelectedIndex(0);
            }}
            onKeyDown={handleInputKeyDown}
            placeholder={t('commandPalette.searchPlaceholder')}
            className="w-full bg-transparent text-[14px] text-white outline-none placeholder:text-white/30"
          />
          <div className="hidden items-center gap-1 rounded-md border border-white/[0.1] bg-white/[0.02] px-2 py-1 text-[10px] text-white/35 md:flex">
            <Command className="h-3 w-3" />
            <span>K</span>
          </div>
        </div>

        <div className="max-h-[420px] overflow-y-auto p-2">
          {filteredCommands.length === 0 ? (
            <div className="px-3 py-10 text-center text-[13px] text-white/35">
              {t('commandPalette.noResults')}
            </div>
          ) : (
            filteredCommands.map((command, index) => {
              const Icon = command.icon;
              const selected = index === selectedIndex;

              return (
                <button
                  key={command.id}
                  type="button"
                  ref={selected ? selectedItemRef : null}
                  onClick={() => executeCommand(command)}
                  onMouseEnter={() => setSelectedIndex(index)}
                  className={`mb-1 flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-all ${
                    selected
                      ? 'bg-[rgba(124,58,237,0.22)] text-white shadow-[inset_0_0_0_1px_rgba(124,58,237,0.35)]'
                      : 'text-white/70 hover:bg-white/[0.04] hover:text-white'
                  }`}
                >
                  <span className="flex h-8 w-8 items-center justify-center rounded-lg border border-white/[0.08] bg-[#17172b]">
                    <Icon className="h-4 w-4" />
                  </span>
                  <span className="flex-1">
                    <span className="block text-[13px] font-medium">{command.label}</span>
                    <span className="block text-[11px] text-white/35">{command.group}</span>
                  </span>
                </button>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
