import React, { useState, useRef, useEffect } from 'react';
import { RefreshCw, Bell, User, Wallet, ChevronDown, Check, LogOut, Settings } from 'lucide-react';
import { useWallets } from '@/contexts/WalletContext';
import { useUser } from '@/contexts/UserContext';
import { useRouter } from 'next/navigation';

export default function TopBar({ 
    onRefresh 
}) {
    const { currentWallet, wallets, setCurrentWallet } = useWallets();
    const { user, logout } = useUser();
    const router = useRouter();
    const [isDropdownOpen, setIsDropdownOpen] = useState(false);
    const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
    const [isLoggingOut, setIsLoggingOut] = useState(false);
    const dropdownRef = useRef(null);
    const userMenuRef = useRef(null);

    const handleWalletSwitch = () => {
        router.push('/wallets');
    };

    const handleWalletSelect = (wallet) => {
        setCurrentWallet(wallet);
        setIsDropdownOpen(false);
    };

    const handleLogout = async () => {
        setIsLoggingOut(true);
        try {
            await logout();
            router.replace('/login');
        } catch (error) {
            console.error('Logout failed:', error);
            router.replace('/login');
        } finally {
            setIsLoggingOut(false);
        }
    };

    useEffect(() => {
        const handleClickOutside = (event) => {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
                setIsDropdownOpen(false);
            }
            if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
                setIsUserMenuOpen(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, []);

    return (
        <header className="bg-gray-900/50 border-b border-gray-800 px-8 py-4">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4 flex-1">
                    {/* Current Wallet Display */}
                    <div className="flex items-center gap-3 text-gray-300">
                        <span className="text-sm">Current wallet:</span>
                        <div className="relative" ref={dropdownRef}>
                            <button 
                                onClick={() => setIsDropdownOpen(!isDropdownOpen)}
                                className="flex items-center gap-2 bg-gray-800/50 hover:bg-gray-800 rounded-lg px-3 py-2 transition-colors group"
                                title="Select wallet"
                            >
                                <Wallet className="w-4 h-4 text-violet-500" />
                                <span className="text-sm font-medium text-white">
                                    {currentWallet ? currentWallet.name : 'No wallet selected'}
                                </span>
                                <ChevronDown className={`w-3 h-3 text-gray-400 group-hover:text-gray-300 transition-all ${
                                    isDropdownOpen ? 'rotate-180' : ''
                                }`} />
                            </button>

                            {/* Dropdown Menu */}
                            {isDropdownOpen && wallets.length > 0 && (
                                <div className="absolute top-full left-0 mt-1 w-64 bg-gray-800 border border-gray-700 rounded-lg shadow-lg z-50">
                                    <div className="p-2">
                                        <div className="text-xs font-medium text-gray-400 px-2 py-1 mb-1">
                                            Select Wallet
                                        </div>
                                        {wallets.map((wallet) => (
                                            <button
                                                key={wallet.id}
                                                onClick={() => handleWalletSelect(wallet)}
                                                className={`w-full flex items-center justify-between px-3 py-2 rounded-md transition-colors text-left ${
                                                    currentWallet?.id === wallet.id
                                                        ? 'bg-violet-500/20 text-violet-300'
                                                        : 'hover:bg-gray-700 text-gray-300'
                                                }`}
                                            >
                                                <div className="flex items-center gap-3">
                                                    <Wallet className="w-4 h-4 text-violet-500" />
                                                    <div>
                                                        <div className="font-medium">{wallet.name}</div>
                                                        <div className="text-xs text-gray-400">
                                                            {wallet.balance?.toLocaleString() || '0.00'} PLN
                                                        </div>
                                                    </div>
                                                </div>
                                                {currentWallet?.id === wallet.id && (
                                                    <Check className="w-4 h-4 text-violet-400" />
                                                )}
                                            </button>
                                        ))}
                                        
                                        {/* Manage Wallets Option */}
                                        <div className="border-t border-gray-700 mt-2 pt-2">
                                            <button
                                                onClick={() => {
                                                    handleWalletSwitch();
                                                    setIsDropdownOpen(false);
                                                }}
                                                className="w-full flex items-center gap-2 px-3 py-2 rounded-md hover:bg-gray-700 text-gray-300 text-sm"
                                            >
                                                <Wallet className="w-4 h-4" />
                                                Manage Wallets
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                <div className="flex items-center gap-4 ml-8">
                    <button 
                        onClick={onRefresh}
                        className="p-2 hover:bg-gray-800 rounded-lg transition-colors"
                        title="Refresh data"
                    >
                        <RefreshCw className="w-5 h-5 text-gray-400" />
                    </button>
                    <button className="p-2 hover:bg-gray-800 rounded-lg transition-colors">
                        <Bell className="w-5 h-5 text-gray-400" />
                    </button>
                    
                    {/* User Menu */}
                    <div className="relative" ref={userMenuRef}>
                        <button 
                            onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
                            className="flex items-center gap-2 p-2 hover:bg-gray-800 rounded-lg transition-colors"
                        >
                            <User className="w-5 h-5 text-gray-400" />
                            {user && (
                                <span className="text-sm text-gray-300 hidden sm:inline">
                                    {user.username || user.email}
                                </span>
                            )}
                            <ChevronDown className={`w-3 h-3 text-gray-400 transition-transform ${
                                isUserMenuOpen ? 'rotate-180' : ''
                            }`} />
                        </button>

                        {/* User Dropdown Menu */}
                        {isUserMenuOpen && (
                            <div className="absolute top-full right-0 mt-1 w-48 bg-gray-800 border border-gray-700 rounded-lg shadow-lg z-50">
                                <div className="p-2">
                                    {user && (
                                        <div className="px-3 py-2 border-b border-gray-700 mb-2">
                                            <div className="text-sm font-medium text-white truncate">
                                                {user.username}
                                            </div>
                                            <div className="text-xs text-gray-400 truncate">
                                                {user.email}
                                            </div>
                                        </div>
                                    )}
                                    
                                    <button
                                        onClick={() => {
                                            router.push('/settings');
                                            setIsUserMenuOpen(false);
                                        }}
                                        className="w-full flex items-center gap-2 px-3 py-2 rounded-md hover:bg-gray-700 text-gray-300 text-sm"
                                    >
                                        <Settings className="w-4 h-4" />
                                        Settings
                                    </button>
                                    
                                    <button
                                        onClick={handleLogout}
                                        disabled={isLoggingOut}
                                        className="w-full flex items-center gap-2 px-3 py-2 rounded-md hover:bg-red-500/20 text-red-400 text-sm disabled:opacity-50"
                                    >
                                        <LogOut className="w-4 h-4" />
                                        {isLoggingOut ? 'Signing out...' : 'Sign out'}
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
}
