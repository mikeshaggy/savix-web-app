import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Wallet, Home, PieChart, DollarSign, Settings, Plus, ChevronDown, ChevronRight, List, Tag, BarChart3, Filter } from 'lucide-react';

const navItems = [
    { id: 'dashboard', label: 'Dashboard', icon: Home, href: '/dashboard' },
    { id: 'wallets', label: 'Wallets', icon: Wallet, href: '/wallets' },
    { id: 'analytics', label: 'Analytics', icon: PieChart, href: '/analytics' },
    { 
        id: 'transactions', 
        label: 'Transactions', 
        icon: DollarSign, 
        href: '/transactions',
        hasSubmenu: true,
        submenu: [
            { id: 'all', label: 'All Transactions', icon: List, href: '/transactions' },
            { id: 'categories', label: 'Categories', icon: Tag, href: '/transactions/categories' },
            { id: 'reports', label: 'Reports', icon: BarChart3, href: '/transactions/reports' },
            { id: 'filters', label: 'Saved Filters', icon: Filter, href: '/transactions/filters' }
        ]
    },
    { id: 'settings', label: 'Settings', icon: Settings, href: '/settings' }
];

export default function Sidebar({ currentPath, onNewTransaction }) {
    const pathname = usePathname();
    const [expandedMenus, setExpandedMenus] = useState({});

    const toggleMenu = (menuId) => {
        setExpandedMenus(prev => ({
            ...prev,
            [menuId]: !prev[menuId]
        }));
    };

    React.useEffect(() => {
        if (pathname.startsWith('/transactions')) {
            setExpandedMenus(prev => ({ ...prev, transactions: true }));
        } else {
            setExpandedMenus(prev => ({ ...prev, transactions: false }));
        }
    }, [pathname]);

    const isItemActive = (item) => {
        if (item.hasSubmenu) {
            return item.submenu.some(subItem => pathname === subItem.href);
        }
        return pathname === item.href || (item.href === '/dashboard' && pathname === '/');
    };

    const isSubItemActive = (subItem) => {
        return pathname === subItem.href;
    };
    return (
        <aside className="w-64 bg-gray-900/50 border-r border-gray-800">
            <div className="p-6">
                <Link href="/dashboard" className="flex items-center gap-3 mb-8 hover:opacity-80 transition-opacity">
                    <div className="w-10 h-10 bg-gradient-to-br from-violet-500 to-purple-600 rounded-xl flex items-center justify-center">
                        <Wallet className="w-6 h-6 text-white" />
                    </div>
                    <h1 className="text-xl font-bold">ExpenseTracker</h1>
                </Link>

                <nav className="space-y-1">
                    {navItems.map(item => {
                        const isActive = isItemActive(item);
                        const isExpanded = expandedMenus[item.id];
                        
                        return (
                            <div key={item.id}>
                                {item.hasSubmenu ? (
                                    <Link
                                        href={item.href}
                                        className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all ${
                                            isActive
                                                ? 'bg-violet-500/20 text-violet-400 border border-violet-500/30'
                                                : 'hover:bg-gray-800/50 text-gray-400 hover:text-white'
                                        }`}
                                    >
                                        <item.icon className="w-5 h-5" />
                                        <span className="font-medium flex-1 text-left">{item.label}</span>
                                    </Link>
                                ) : (
                                    <Link
                                        href={item.href}
                                        className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all ${
                                            isActive
                                                ? 'bg-violet-500/20 text-violet-400 border border-violet-500/30'
                                                : 'hover:bg-gray-800/50 text-gray-400 hover:text-white'
                                        }`}
                                    >
                                        <item.icon className="w-5 h-5" />
                                        <span className="font-medium">{item.label}</span>
                                    </Link>
                                )}
                                
                                {/* Submenu */}
                                {item.hasSubmenu && isExpanded && (
                                    <div className="ml-4 mt-1 space-y-1">
                                        {item.submenu.map(subItem => {
                                            const isSubActive = isSubItemActive(subItem);
                                            return (
                                                <Link
                                                    key={subItem.id}
                                                    href={subItem.href}
                                                    className={`w-full flex items-center gap-3 px-4 py-2 rounded-lg transition-all text-sm ${
                                                        isSubActive
                                                            ? 'bg-violet-500/30 text-violet-300 border border-violet-500/40'
                                                            : 'hover:bg-gray-800/30 text-gray-400 hover:text-white'
                                                    }`}
                                                >
                                                    <subItem.icon className="w-4 h-4" />
                                                    <span className="font-medium">{subItem.label}</span>
                                                </Link>
                                            );
                                        })}
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </nav>

                <button 
                    onClick={onNewTransaction}
                    className="w-full mt-8 px-4 py-3 bg-gradient-to-r from-violet-500 to-purple-600 rounded-lg font-medium hover:from-violet-600 hover:to-purple-700 transition-all flex items-center justify-center gap-2"
                >
                    <Plus className="w-5 h-5" />
                    New Transaction
                </button>
            </div>
        </aside>
    );
}
