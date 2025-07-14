import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Wallet, Home, PieChart, DollarSign, Settings, Plus } from 'lucide-react';

const navItems = [
    { id: 'dashboard', label: 'Dashboard', icon: Home, href: '/dashboard' },
    { id: 'analytics', label: 'Analytics', icon: PieChart, href: '/analytics' },
    { id: 'transactions', label: 'Transactions', icon: DollarSign, href: '/transactions' },
    { id: 'settings', label: 'Settings', icon: Settings, href: '/settings' }
];

export default function Sidebar({ currentPath, onNewTransaction }) {
    const pathname = usePathname();
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
                        const isActive = pathname === item.href || (item.href === '/dashboard' && pathname === '/');
                        return (
                            <Link
                                key={item.id}
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
