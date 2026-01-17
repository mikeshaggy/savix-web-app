'use client';
import React, { createContext, useContext, useReducer, useEffect, useCallback } from 'react';
import { walletApi, checkBackendHealth, onAuthStateChange } from '@/lib/api';
import { useUser } from './UserContext';

const WalletContext = createContext();

const initialState = {
  wallets: [],
  currentWallet: null,
  loading: false,
  error: null,
  initialized: false,
};

const CURRENT_WALLET_KEY = 'savix_current_wallet_id';

const saveCurrentWalletId = (walletId) => {
  if (typeof window !== 'undefined') {
    if (walletId) {
      localStorage.setItem(CURRENT_WALLET_KEY, walletId.toString());
    } else {
      localStorage.removeItem(CURRENT_WALLET_KEY);
    }
  }
};

const getSavedCurrentWalletId = () => {
  if (typeof window !== 'undefined') {
    const saved = localStorage.getItem(CURRENT_WALLET_KEY);
    return saved ? parseInt(saved, 10) : null;
  }
  return null;
};

const walletReducer = (state, action) => {
  switch (action.type) {
    case 'SET_LOADING':
      return { ...state, loading: action.payload };
    case 'SET_ERROR':
      return { ...state, error: action.payload };
    case 'SET_WALLETS':
      const savedWalletId = getSavedCurrentWalletId();
      let selectedWallet = null;
      
      if (savedWalletId) {
        selectedWallet = action.payload.find(w => w.id === savedWalletId);
      }
      
      if (!selectedWallet && action.payload.length > 0) {
        selectedWallet = action.payload[0];
      }
      
      return { 
        ...state, 
        wallets: action.payload, 
        currentWallet: selectedWallet,
      };
    case 'SET_CURRENT_WALLET':
      saveCurrentWalletId(action.payload?.id);
      return { ...state, currentWallet: action.payload };
    case 'ADD_WALLET':
      const newWalletsArray = [...state.wallets, action.payload];
      const shouldSetAsCurrent = state.wallets.length === 0 && !state.currentWallet;
      
      if (shouldSetAsCurrent) {
        saveCurrentWalletId(action.payload.id);
      }
      
      return { 
        ...state, 
        wallets: newWalletsArray,
        currentWallet: shouldSetAsCurrent ? action.payload : state.currentWallet,
      };
    case 'UPDATE_WALLET':
      return {
        ...state,
        wallets: state.wallets.map(wallet => 
          wallet.id === action.payload.id ? action.payload : wallet
        ),
        currentWallet: state.currentWallet?.id === action.payload.id ? action.payload : state.currentWallet,
      };
    case 'DELETE_WALLET':
      const remainingWallets = state.wallets.filter(w => w.id !== action.payload);
      let newCurrentWallet = state.currentWallet;
      
      if (state.currentWallet?.id === action.payload) {
        newCurrentWallet = remainingWallets.length > 0 ? remainingWallets[0] : null;
        saveCurrentWalletId(newCurrentWallet?.id);
      }
      
      return {
        ...state,
        wallets: remainingWallets,
        currentWallet: newCurrentWallet,
      };
    case 'RESET':
      return {
        ...initialState,
        initialized: false,
      };
    case 'SET_INITIALIZED':
      return { ...state, initialized: action.payload };
    default:
      return state;
  }
};

export const WalletProvider = ({ children }) => {
  const [state, dispatch] = useReducer(walletReducer, initialState);
  const { isAuthenticated, isLoading: userLoading } = useUser();

  const fetchWallets = useCallback(async () => {
    if (!isAuthenticated) {
      dispatch({ type: 'SET_WALLETS', payload: [] });
      dispatch({ type: 'SET_INITIALIZED', payload: true });
      return;
    }

    dispatch({ type: 'SET_LOADING', payload: true });
    dispatch({ type: 'SET_ERROR', payload: null });
    
    try {
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const wallets = await walletApi.getAllWallets();
    
      dispatch({ type: 'SET_WALLETS', payload: wallets || [] });
      dispatch({ type: 'SET_INITIALIZED', payload: true });
    } catch (error) {
      console.error('Failed to fetch wallets:', error);
      
      if (error.message && error.message.includes('not found')) {
        dispatch({ type: 'SET_WALLETS', payload: [] });
        dispatch({ type: 'SET_ERROR', payload: null });
      } else {
        dispatch({ type: 'SET_ERROR', payload: error.message });
        dispatch({ type: 'SET_WALLETS', payload: [] });
      }
      dispatch({ type: 'SET_INITIALIZED', payload: true });
    } finally {
      dispatch({ type: 'SET_LOADING', payload: false });
    }
  }, [isAuthenticated]);

  const createWallet = useCallback(async (walletData) => {
    try {
      dispatch({ type: 'SET_ERROR', payload: null });
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const newWallet = await walletApi.createWallet(walletData);
      dispatch({ type: 'ADD_WALLET', payload: newWallet });
      return newWallet;
    } catch (error) {
      dispatch({ type: 'SET_ERROR', payload: error.message });
      throw error;
    }
  }, []);

  const updateWallet = useCallback(async (id, walletData) => {
    try {
      dispatch({ type: 'SET_ERROR', payload: null });
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const updatedWallet = await walletApi.updateWallet(id, walletData);
      dispatch({ type: 'UPDATE_WALLET', payload: updatedWallet });
      return updatedWallet;
    } catch (error) {
      dispatch({ type: 'SET_ERROR', payload: error.message });
      throw error;
    }
  }, []);

  const updateWalletBalance = useCallback(async (id, newBalance) => {
    try {
      dispatch({ type: 'SET_ERROR', payload: null });
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const updatedWallet = await walletApi.updateWalletBalance(id, newBalance);
      dispatch({ type: 'UPDATE_WALLET', payload: updatedWallet });
      return updatedWallet;
    } catch (error) {
      dispatch({ type: 'SET_ERROR', payload: error.message });
      throw error;
    }
  }, []);

  const deleteWallet = useCallback(async (id) => {
    try {
      dispatch({ type: 'SET_ERROR', payload: null });
      const backendAvailable = await checkBackendHealth();
      
      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      await walletApi.deleteWallet(id);
      dispatch({ type: 'DELETE_WALLET', payload: id });
    } catch (error) {
      dispatch({ type: 'SET_ERROR', payload: error.message });
      throw error;
    }
  }, []);

  const setCurrentWallet = useCallback((wallet) => {
    dispatch({ type: 'SET_CURRENT_WALLET', payload: wallet });
  }, []);

  const resetWallets = useCallback(() => {
    dispatch({ type: 'RESET' });
  }, []);

  useEffect(() => {
    if (userLoading) {
      return;
    }

    if (isAuthenticated) {
      fetchWallets();
    } else {
      resetWallets();
    }
  }, [isAuthenticated, userLoading, fetchWallets, resetWallets]);

  useEffect(() => {
    const unsubscribe = onAuthStateChange((authenticated) => {
      if (!authenticated) {
        resetWallets();
      }
    });

    return unsubscribe;
  }, [resetWallets]);

  const value = {
    ...state,
    fetchWallets,
    createWallet,
    updateWallet,
    updateWalletBalance,
    deleteWallet,
    setCurrentWallet,
    resetWallets,
  };

  return (
    <WalletContext.Provider value={value}>
      {children}
    </WalletContext.Provider>
  );
};

export const useWallets = () => {
  const context = useContext(WalletContext);
  if (!context) {
    throw new Error('useWallets must be used within a WalletProvider');
  }
  return context;
};

