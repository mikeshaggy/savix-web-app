'use client';
import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { List, Tag, BarChart3, Filter } from 'lucide-react';

const transactionNavItems = [
    { id: 'all', label: 'All Transactions', icon: List, href: '/transactions' },
    { id: 'categories', label: 'Categories', icon: Tag, href: '/transactions/categories' },
    { id: 'reports', label: 'Reports', icon: BarChart3, href: '/transactions/reports' },
    { id: 'filters', label: 'Saved Filters', icon: Filter, href: '/transactions/filters' }
];

export default function TransactionsSidebar() {
    const pathname = usePathname();
    
    return (
        <div className="w-64 bg-gray-900/30 border-r border-gray-800">
            <div className="p-4">
                <h2 className="text-lg font-semibold mb-4 text-gray-200">Transactions</h2>
                
                <nav className="space-y-1">
                    {transactionNavItems.map(item => {
                        const isActive = pathname === item.href;
                        return (
                            <Link
                                key={item.id}
                                href={item.href}
                                className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-all text-sm ${
                                    isActive
                                        ? 'bg-violet-500/20 text-violet-400 border border-violet-500/30'
                                        : 'hover:bg-gray-800/50 text-gray-400 hover:text-white'
                                }`}
                            >
                                <item.icon className="w-4 h-4" />
                                <span className="font-medium">{item.label}</span>
                            </Link>
                        );
                    })}
                </nav>
            </div>
        </div>
    );
}
