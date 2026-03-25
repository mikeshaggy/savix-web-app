import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { usePathname } from 'next/navigation';
import { Wallet, Home, PieChart, Repeat, Settings, List, Tag, Filter, CalendarCheck, X, History, ArrowLeftRight, TrendingUp, Activity, Import } from 'lucide-react';
import { useTranslations } from 'next-intl';
import NewActionDropdown from '@/components/common/NewActionDropdown';

const getNavItems = (t) => [
    { id: 'dashboard', label: t('nav.dashboard'), icon: Home, href: '/dashboard' },
    {
        id: 'transactions',
        label: t('nav.transactions'),
        icon: Repeat,
        href: '/transactions',
        hasSubmenu: true,
        submenu: [
            { id: 'all-transactions', label: t('nav.allTransactions'), icon: List, href: '/transactions' },
            { id: 'fixed-payments', label: t('nav.fixedPayments'), icon: CalendarCheck, href: '/transactions/fixed-payments' },
            { id: 'categories', label: t('nav.categories'), icon: Tag, href: '/transactions/categories' },
            { id: 'saved-filters', label: t('nav.savedFilters'), icon: Filter, href: '/transactions/filters' }
        ]
    },
    {
        id: 'wallets',
        label: t('nav.wallets'),
        icon: Wallet,
        href: '/wallets',
        hasSubmenu: true,
        submenu: [
            { id: 'all-wallets', label: t('nav.allWallets'), icon: Wallet, href: '/wallets' },
            { id: 'balance-history', label: t('nav.balanceHistory'), icon: History, href: '/wallets/balance-history' },
            { id: 'transfers', label: t('nav.transfers'), icon: ArrowLeftRight, href: '/wallets/transfers' }
        ]
    },
    {
        id: 'analytics',
        label: t('nav.analytics'),
        icon: PieChart,
        href: '/analytics',
        hasSubmenu: true,
        submenu: [
            { id: 'overview', label: t('nav.overview'), icon: PieChart, href: '/analytics' },
            { id: 'cashflow', label: t('nav.cashflow'), icon: Activity, href: '/analytics/cashflow' },
            { id: 'trends', label: t('nav.trends'), icon: TrendingUp, href: '/analytics/trends' }
        ]
    },
    { id: 'import-export', label: t('nav.importExport'), icon: Import, href: '/import-export' },
    { id: 'settings', label: t('nav.settings'), icon: Settings, href: '/settings' }
];

export default function Sidebar({ currentPath, onNewTransaction, onNewTransfer, isOpen, onClose }) {
    const pathname = usePathname();
    const [expandedMenus, setExpandedMenus] = useState({});
    const t = useTranslations();
    const navItems = getNavItems(t);

    useEffect(() => {
        if (pathname.startsWith('/transactions')) {
            setExpandedMenus(prev => ({ ...prev, transactions: true }));
        } else {
            setExpandedMenus(prev => ({ ...prev, transactions: false }));
        }
        if (pathname.startsWith('/wallets')) {
            setExpandedMenus(prev => ({ ...prev, wallets: true }));
        } else {
            setExpandedMenus(prev => ({ ...prev, wallets: false }));
        }
        if (pathname.startsWith('/analytics')) {
            setExpandedMenus(prev => ({ ...prev, analytics: true }));
        } else {
            setExpandedMenus(prev => ({ ...prev, analytics: false }));
        }
    }, [pathname]);

    useEffect(() => {
        if (isOpen) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = '';
        }
        return () => { document.body.style.overflow = ''; };
    }, [isOpen]);

    const isItemActive = (item) => {
        if (item.hasSubmenu) return pathname.startsWith(item.href);
        return pathname === item.href || (item.href === '/dashboard' && pathname === '/');
    };

    const isSubItemActive = (subItem) => {
        return pathname === subItem.href;
    };

    const handleParentToggle = (menuId) => {
        setExpandedMenus(prev => ({
            ...prev,
            [menuId]: !prev[menuId]
        }));
    };

    return (
        <>
            {/* Backdrop overlay — mobile only */}
            {isOpen && (
                <div
                    className="fixed inset-0 bg-black/60 z-40 lg:hidden"
                    onClick={onClose}
                    aria-hidden="true"
                />
            )}

            {/* Sidebar */}
            <aside
                className={`
                    fixed inset-y-0 left-0 z-50 w-[260px] bg-[#0e0e1c] border-r border-white/[0.055] flex flex-col px-[14px] pt-6 pb-5
                    transition-transform duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]
                    lg:relative lg:inset-y-auto lg:left-auto lg:top-auto lg:bottom-auto lg:self-auto lg:h-screen lg:w-[220px] lg:translate-x-0 lg:flex-shrink-0 lg:z-10
                    ${isOpen ? 'translate-x-0' : '-translate-x-full'}
                `}
            >
                {/* Header: Logo + Close button (mobile) */}
                <div className="flex items-center justify-between mb-8">
                    <Link href="/dashboard" className="flex items-center gap-3 px-1.5 hover:opacity-90 transition-opacity">
                        <Image src="/logo.png" alt="Savix" width={38} height={38} className="rounded-xl" />
                        <span className="text-xl font-bold tracking-[-0.5px]">SAVIX</span>
                    </Link>
                    <button
                        onClick={onClose}
                        className="lg:hidden w-8 h-8 flex items-center justify-center rounded-lg text-white/40 hover:text-white hover:bg-white/[0.06] transition-all"
                        aria-label="Close navigation"
                    >
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {/* Navigation */}
                <nav className="flex flex-col gap-[2px] overflow-y-auto flex-1">
                    {navItems.map(item => {
                        const isActive = isItemActive(item);
                        const isExpanded = expandedMenus[item.id];
                        const itemClassName = `flex items-center gap-2.5 px-3 py-2.5 rounded-[11px] text-[14px] font-medium transition-all select-none w-full text-left ${
                            isActive
                                ? 'text-[#c084fc] bg-[rgba(124,58,237,0.15)] shadow-[inset_0_0_0_1px_rgba(124,58,237,0.25)]'
                                : 'text-white/25 hover:text-white hover:bg-white/[0.04]'
                        }`;

                        return (
                            <div key={item.id}>
                                {item.hasSubmenu ? (
                                    <button
                                        type="button"
                                        onClick={() => handleParentToggle(item.id)}
                                        className={itemClassName}
                                        aria-expanded={!!isExpanded}
                                        aria-controls={`${item.id}-submenu`}
                                    >
                                        <span className="w-[18px] flex-shrink-0 flex items-center justify-center">
                                            <item.icon className="w-4 h-4" />
                                        </span>
                                        <span className="flex-1">{item.label}</span>
                                        <span className={`text-[9px] text-white/25 transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}>▾</span>
                                    </button>
                                ) : (
                                    <Link
                                        href={item.href}
                                        className={itemClassName}
                                    >
                                        <span className="w-[18px] flex-shrink-0 flex items-center justify-center">
                                            <item.icon className="w-4 h-4" />
                                        </span>
                                        <span className="flex-1">{item.label}</span>
                                    </Link>
                                )}

                                {/* Animated submenu */}
                                {item.hasSubmenu && (
                                    <div
                                        id={`${item.id}-submenu`}
                                        className={`overflow-hidden transition-all duration-[250ms] ease-[cubic-bezier(0.4,0,0.2,1)] ${
                                            isExpanded ? 'max-h-[200px]' : 'max-h-0'
                                        }`}
                                    >
                                        {item.submenu.map(subItem => (
                                            <Link
                                                key={subItem.id}
                                                href={subItem.href}
                                                className={`flex items-center gap-2.5 py-2 px-3 pl-10 rounded-[9px] text-[13px] font-medium transition-all ${
                                                    isSubItemActive(subItem)
                                                        ? 'text-[#c084fc] bg-[rgba(124,58,237,0.12)]'
                                                        : 'text-white/25 hover:text-white hover:bg-white/[0.03]'
                                                }`}
                                            >
                                                <span className="w-4 flex-shrink-0 flex items-center justify-center">
                                                    <subItem.icon className="w-3.5 h-3.5" />
                                                </span>
                                                {subItem.label}
                                            </Link>
                                        ))}
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </nav>

                {/* New Action Dropdown */}
                <div className="pt-4">
                    <NewActionDropdown
                        onNewTransaction={onNewTransaction}
                        onNewTransfer={onNewTransfer}
                    />
                </div>
            </aside>
        </>
    );
}
