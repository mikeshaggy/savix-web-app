'use client';
import { useState, useMemo } from 'react';
import { Loader2, AlertCircle, RefreshCw } from 'lucide-react';
import { useDashboard, useTransactions, useCategories } from '../hooks/useApi';
import TransactionModal from './TransactionModal';
import BackendStatusIndicator from './BackendStatusIndicator';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import DashboardView from './DashboardView';
import TransactionsView from './TransactionsView';
import { AnalyticsView, SettingsView } from './PlaceholderViews';

export default function ExpenseTracker() {
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedFilter, setSelectedFilter] = useState('ALL');
    const [activeNav, setActiveNav] = useState('dashboard');
    const [showTransactionModal, setShowTransactionModal] = useState(false);
    
    // Additional filters for transactions tab
    const [categoryFilter, setCategoryFilter] = useState('ALL');
    const [importanceFilter, setImportanceFilter] = useState('ALL');
    const [dateFilter, setDateFilter] = useState('ALL');
    const [sortBy, setSortBy] = useState('date');
    const [sortOrder, setSortOrder] = useState('desc');

    // Use custom hooks for data management
    const { data: dashboardData, loading: dashboardLoading, error: dashboardError, refetch: refetchDashboard, usingMockData: dashboardMockData } = useDashboard();
    const { transactions, loading: transactionsLoading, error: transactionsError, createTransaction, refetch: refetchTransactions, usingMockData: transactionsMockData } = useTransactions();
    const { categories, loading: categoriesLoading, error: categoriesError, usingMockData: categoriesMockData } = useCategories();

    // Use transactions from the hook, fallback to dashboard data
    const allTransactions = transactions.length > 0 ? transactions : dashboardData.transactions;

    // Calculate summary metrics
    const summary = useMemo(() => {
        const income = allTransactions
            .filter(t => t.type === 'INCOME')
            .reduce((sum, t) => sum + parseFloat(t.amount || 0), 0);

        const expenses = allTransactions
            .filter(t => t.type === 'EXPENSE')
            .reduce((sum, t) => sum + parseFloat(t.amount || 0), 0);

        return {
            income,
            expenses,
            balance: income - expenses,
            savingsRate: income > 0 ? ((income - expenses) / income * 100).toFixed(1) : 0
        };
    }, [allTransactions]);

    // Filter transactions
    const filteredTransactions = useMemo(() => {
        let filtered = allTransactions.filter(transaction => {
            const searchableText = `${transaction.title || ''} ${transaction.categoryName || ''}`.toLowerCase();
            const matchesSearch = searchableText.includes(searchTerm.toLowerCase());
            const matchesFilter = selectedFilter === 'ALL' ||
                (selectedFilter === 'INCOME' && transaction.type === 'INCOME') ||
                (selectedFilter === 'EXPENSE' && transaction.type === 'EXPENSE');
            
            // Additional filters for transactions tab
            const matchesCategory = categoryFilter === 'ALL' || 
                transaction.categoryName === categoryFilter;
            const matchesImportance = importanceFilter === 'ALL' || 
                transaction.importance === importanceFilter;
            
            // Date filter
            const transactionDate = new Date(transaction.transactionDate || transaction.date);
            const now = new Date();
            let matchesDate = true;
            
            if (dateFilter === 'THIS_WEEK') {
                const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
                matchesDate = transactionDate >= weekAgo;
            } else if (dateFilter === 'THIS_MONTH') {
                const monthAgo = new Date(now.getFullYear(), now.getMonth(), 1);
                matchesDate = transactionDate >= monthAgo;
            } else if (dateFilter === 'LAST_MONTH') {
                const lastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
                const thisMonth = new Date(now.getFullYear(), now.getMonth(), 1);
                matchesDate = transactionDate >= lastMonth && transactionDate < thisMonth;
            }

            return matchesSearch && matchesFilter && matchesCategory && matchesImportance && matchesDate;
        });
        
        // Sort transactions
        filtered.sort((a, b) => {
            let aValue, bValue;
            
            switch (sortBy) {
                case 'date':
                    aValue = new Date(a.transactionDate || a.date);
                    bValue = new Date(b.transactionDate || b.date);
                    break;
                case 'amount':
                    aValue = parseFloat(a.amount || 0);
                    bValue = parseFloat(b.amount || 0);
                    break;
                case 'title':
                    aValue = (a.title || '').toLowerCase();
                    bValue = (b.title || '').toLowerCase();
                    break;
                case 'category':
                    aValue = (a.categoryName || '').toLowerCase();
                    bValue = (b.categoryName || '').toLowerCase();
                    break;
                default:
                    aValue = new Date(a.transactionDate || a.date);
                    bValue = new Date(b.transactionDate || b.date);
            }
            
            if (sortOrder === 'asc') {
                return aValue > bValue ? 1 : -1;
            } else {
                return aValue < bValue ? 1 : -1;
            }
        });
        
        return filtered;
    }, [allTransactions, searchTerm, selectedFilter, categoryFilter, importanceFilter, dateFilter, sortBy, sortOrder]);

    // Handle transaction creation
    const handleCreateTransaction = async (transactionData) => {
        try {
            await createTransaction(transactionData);
            // Refresh dashboard data to get updated summary
            refetchDashboard();
        } catch (error) {
            console.error('Failed to create transaction:', error);
            throw error;
        }
    };

    // Handle refresh
    const handleRefresh = () => {
        refetchDashboard();
        refetchTransactions();
    };

    // Handle opening transaction modal
    const handleNewTransaction = () => {
        setShowTransactionModal(true);
    };

    // Loading state
    if (dashboardLoading || transactionsLoading || categoriesLoading) {
        return (
            <div className="min-h-screen bg-gray-950 text-white flex items-center justify-center">
                <div className="flex flex-col items-center gap-4">
                    <Loader2 className="w-8 h-8 animate-spin text-violet-500" />
                    <p className="text-gray-400">Loading your financial data...</p>
                </div>
            </div>
        );
    }

    // Error state
    if (dashboardError || transactionsError || categoriesError) {
        return (
            <div className="min-h-screen bg-gray-950 text-white flex items-center justify-center">
                <div className="flex flex-col items-center gap-4 text-center max-w-md">
                    <AlertCircle className="w-12 h-12 text-red-500" />
                    <h2 className="text-xl font-semibold">Unable to Load Data</h2>
                    <p className="text-gray-400">
                        {dashboardError || transactionsError || categoriesError}
                    </p>
                    <button
                        onClick={handleRefresh}
                        className="px-4 py-2 bg-violet-500 hover:bg-violet-600 rounded-lg transition-colors flex items-center gap-2"
                    >
                        <RefreshCw className="w-4 h-4" />
                        Try Again
                    </button>
                    <p className="text-xs text-gray-500">
                        Using demo data for now. Make sure your backend is running on port 8080.
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-950 text-white flex">
            {/* Sidebar */}
            <Sidebar 
                activeNav={activeNav} 
                setActiveNav={setActiveNav}
                onNewTransaction={handleNewTransaction}
            />

            {/* Main Content */}
            <main className="flex-1 flex flex-col">
                {/* Top Bar */}
                <TopBar 
                    searchTerm={searchTerm}
                    setSearchTerm={setSearchTerm}
                    selectedFilter={selectedFilter}
                    setSelectedFilter={setSelectedFilter}
                    onRefresh={handleRefresh}
                />

                {/* Backend Status Indicator */}
                <div className="px-8 py-2">
                    <BackendStatusIndicator 
                        isUsingMockData={dashboardMockData || transactionsMockData || categoriesMockData}
                        onRefresh={handleRefresh}
                    />
                </div>

                {/* Main Content Area */}
                <div className="flex-1 p-8 overflow-y-auto">
                    {/* Dashboard View */}
                    {activeNav === 'dashboard' && (
                        <DashboardView 
                            summary={summary}
                            allTransactions={allTransactions}
                            categories={categories}
                            filteredTransactions={filteredTransactions}
                        />
                    )}

                    {/* Transactions View */}
                    {activeNav === 'transactions' && (
                        <TransactionsView 
                            filteredTransactions={filteredTransactions}
                            allTransactions={allTransactions}
                            categories={categories}
                            categoryFilter={categoryFilter}
                            setCategoryFilter={setCategoryFilter}
                            importanceFilter={importanceFilter}
                            setImportanceFilter={setImportanceFilter}
                            dateFilter={dateFilter}
                            setDateFilter={setDateFilter}
                            sortBy={sortBy}
                            setSortBy={setSortBy}
                            sortOrder={sortOrder}
                            setSortOrder={setSortOrder}
                            searchTerm={searchTerm}
                            selectedFilter={selectedFilter}
                            setSearchTerm={setSearchTerm}
                            setSelectedFilter={setSelectedFilter}
                            onNewTransaction={handleNewTransaction}
                        />
                    )}

                    {/* Analytics View */}
                    {activeNav === 'analytics' && <AnalyticsView />}

                    {/* Settings View */}
                    {activeNav === 'settings' && <SettingsView />}
                </div>
            </main>

            {/* Transaction Modal */}
            <TransactionModal
                isOpen={showTransactionModal}
                onClose={() => setShowTransactionModal(false)}
                onSave={handleCreateTransaction}
                categories={categories}
                loading={categoriesLoading}
            />
        </div>
    );
}
