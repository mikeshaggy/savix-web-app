import React, { useState, useRef, useEffect } from 'react';
import { RefreshCw, Bell, Wallet, Check, LogOut, Settings, Menu } from 'lucide-react';
import { useWallets } from '@/contexts/WalletContext';
import { useUser } from '@/contexts/UserContext';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import LanguageSwitcher from '@/components/common/LanguageSwitcher';
import { formatCurrency } from '@/utils/helpers';

export default function TopBar({ 
    onRefresh,
    onMenuToggle
}) {
    const { currentWallet, wallets, setCurrentWallet } = useWallets();
    const { user, logout } = useUser();
    const router = useRouter();
    const t = useTranslations();
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
        <header className="h-[58px] flex items-center px-4 md:px-5 lg:px-7 gap-2 md:gap-3 border-b border-white/[0.055] bg-[rgba(6,6,15,0.8)] backdrop-blur-[24px] sticky top-0 z-40">
            {/* Hamburger — visible below lg */}
            <button
                onClick={onMenuToggle}
                className="lg:hidden w-[34px] h-[34px] flex items-center justify-center rounded-[10px] text-white/50 hover:text-white hover:bg-white/[0.06] transition-all"
                aria-label="Open navigation menu"
            >
                <Menu className="w-5 h-5" />
            </button>

            {/* Wallet Pill */}
            <div className="relative" ref={dropdownRef}>
                <button
                    onClick={() => setIsDropdownOpen(!isDropdownOpen)}
                    className="flex items-center gap-2 bg-[#131325] border border-white/[0.12] rounded-full py-1.5 pl-2 pr-3.5 cursor-pointer transition-all hover:border-[#7c3aed] hover:shadow-[0_0_0_3px_rgba(124,58,237,0.1)]"
                >
                    <div className="w-[22px] h-[22px] bg-gradient-to-br from-[#7c3aed] to-[#a855f7] rounded-full shadow-[0_0_10px_rgba(124,58,237,0.3)]" />
                    <span className="text-[13px] font-medium hidden sm:inline">
                        {currentWallet ? currentWallet.name : t('topbar.noWalletSelected')}
                    </span>
                    <span className="text-white/25 text-[10px]">▾</span>
                </button>

                {/* Wallet Dropdown */}
                {isDropdownOpen && wallets.length > 0 && (
                    <div className="absolute top-full left-0 mt-2 w-64 bg-[#131325] border border-white/[0.055] rounded-xl shadow-[0_16px_48px_rgba(0,0,0,0.5)] z-50 overflow-hidden">
                        <div className="p-2">
                            <div className="text-[10px] font-bold tracking-[0.12em] uppercase text-white/25 px-3 py-1.5 mb-1">
                                {t('topbar.selectWallet')}
                            </div>
                            {wallets.map((wallet) => (
                                <button
                                    key={wallet.id}
                                    onClick={() => handleWalletSelect(wallet)}
                                    className={`w-full flex items-center justify-between px-3 py-2.5 rounded-[9px] transition-all text-left cursor-pointer ${
                                        currentWallet?.id === wallet.id
                                            ? 'bg-[rgba(124,58,237,0.15)] text-[#c084fc]'
                                            : 'hover:bg-white/[0.04] text-white/50 hover:text-white'
                                    }`}
                                >
                                    <div className="flex items-center gap-3">
                                        <div className="w-[22px] h-[22px] bg-gradient-to-br from-[#7c3aed] to-[#a855f7] rounded-full shadow-[0_0_8px_rgba(124,58,237,0.25)]" />
                                        <div>
                                            <div className="text-[13px] font-medium">{wallet.name}</div>
                                            <div className="text-[11px] text-white/25 font-mono">
                                                {formatCurrency(wallet.balance ?? 0)}
                                            </div>
                                        </div>
                                    </div>
                                    {currentWallet?.id === wallet.id && (
                                        <Check className="w-3.5 h-3.5 text-[#c084fc]" />
                                    )}
                                </button>
                            ))}

                            {/* Manage Wallets */}
                            <div className="border-t border-white/[0.055] mt-1.5 pt-1.5">
                                <button
                                    onClick={() => {
                                        handleWalletSwitch();
                                        setIsDropdownOpen(false);
                                    }}
                                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-[9px] hover:bg-white/[0.04] text-white/25 hover:text-white text-[12px] font-medium transition-all cursor-pointer"
                                >
                                    <Wallet className="w-3.5 h-3.5" />
                                    {t('topbar.manageWallets')}
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </div>

            {/* Divider */}
            <div className="w-px h-[18px] bg-white/[0.12] hidden sm:block" />

            {/* Right side */}
            <div className="ml-auto flex items-center gap-2">
                {/* Language Switcher */}
                <LanguageSwitcher />

                {/* Refresh */}
                <button
                    onClick={onRefresh}
                    className="w-[34px] h-[34px] flex items-center justify-center bg-[#131325] border border-white/[0.055] rounded-[10px] cursor-pointer text-white/50 transition-all hover:text-white hover:border-white/[0.12]"
                    title={t('topbar.refreshData')}
                >
                    <RefreshCw className="w-[13px] h-[13px]" />
                </button>

                {/* Notifications */}
                <button
                    className="w-[34px] h-[34px] flex items-center justify-center bg-[#131325] border border-white/[0.055] rounded-[10px] cursor-pointer text-white/50 transition-all hover:text-white hover:border-white/[0.12] relative"
                >
                    <Bell className="w-[13px] h-[13px]" />
                    <div className="absolute top-[5px] right-[5px] w-1.5 h-1.5 rounded-full bg-[#a855f7] shadow-[0_0_6px_#a855f7]" />
                </button>

                {/* User Chip */}
                <div className="relative" ref={userMenuRef}>
                    <button
                        onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
                        className="flex items-center gap-2 bg-[#131325] border border-white/[0.055] rounded-[10px] py-[5px] pr-2 sm:pr-3 pl-[5px] cursor-pointer transition-all hover:border-white/[0.12]"
                    >
                        <div className="w-[26px] h-[26px] bg-gradient-to-br from-[#7c3aed] to-[#a855f7] rounded-lg flex items-center justify-center text-[11px] font-bold shadow-[0_0_10px_rgba(124,58,237,0.3)]">
                            {user?.username?.[0]?.toUpperCase() || 'U'}
                        </div>
                        <div className="hidden sm:block">
                            <div className="text-[12px] font-semibold leading-tight">{user?.username || 'User'}</div>
                        </div>
                        <span className="text-white/25 text-[10px] ml-0.5 hidden sm:inline">▾</span>
                    </button>

                    {/* User Dropdown */}
                    {isUserMenuOpen && (
                        <div className="absolute top-full right-0 mt-2 w-52 bg-[#131325] border border-white/[0.055] rounded-xl shadow-[0_16px_48px_rgba(0,0,0,0.5)] z-50 overflow-hidden">
                            <div className="p-2">
                                {user && (
                                    <div className="px-3 py-2.5 border-b border-white/[0.055] mb-1.5">
                                        <div className="text-[13px] font-semibold text-white truncate">
                                            {user.username}
                                        </div>
                                        <div className="text-[11px] text-white/25 truncate mt-0.5">
                                            {user.email}
                                        </div>
                                    </div>
                                )}

                                <button
                                    onClick={() => {
                                        router.push('/settings');
                                        setIsUserMenuOpen(false);
                                    }}
                                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-[9px] hover:bg-white/[0.04] text-white/50 hover:text-white text-[12px] font-medium transition-all cursor-pointer"
                                >
                                    <Settings className="w-3.5 h-3.5" />
                                    {t('nav.settings')}
                                </button>

                                <button
                                    onClick={handleLogout}
                                    disabled={isLoggingOut}
                                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-[9px] hover:bg-red-500/10 text-red-400/70 hover:text-red-400 text-[12px] font-medium transition-all cursor-pointer disabled:opacity-50"
                                >
                                    <LogOut className="w-3.5 h-3.5" />
                                    {isLoggingOut ? t('auth.signingOut') : t('auth.signOut')}
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
}
