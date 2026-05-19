'use client';
import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { 
  meApi, 
  authApi,
  onAuthStateChange, 
  getAuthState,
  markAuthenticated,
  markUnauthenticated,
  checkBackendHealth,
  ApiError 
} from '@/lib/api';

const UserContext = createContext();

export const useUser = () => {
  const context = useContext(UserContext);
  if (!context) {
    throw new Error('useUser must be used within a UserProvider');
  }
  return context;
};

export const UserProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [backendAvailable, setBackendAvailable] = useState(null);
  const [backendChecked, setBackendChecked] = useState(false);
  
  const [isAuthenticated, setIsAuthenticated] = useState(() => getAuthState());
  
  const [sessionExpired, setSessionExpired] = useState(false);

  const wasAuthenticated = useRef(false);
  const isLoadingRef = useRef(false);
  const userRef = useRef(null);

  const loadUser = useCallback(async () => {
    if (isLoadingRef.current) return;
    if (backendChecked && !backendAvailable) {
      userRef.current = null;
      setUser(null);
      setIsAuthenticated(false);
      setError('Backend not available');
      setIsLoading(false);
      return;
    }

    isLoadingRef.current = true;
    setIsLoading(true);
    setError(null);

    try {
      const userData = await meApi.getCurrentUser();
      userRef.current = userData;
      setUser(userData);
      setIsAuthenticated(true);
      markAuthenticated();
    } catch (err) {
      console.error('Failed to load user:', err);

      if (err instanceof ApiError && err.isUnauthorized) {
        userRef.current = null;
        setUser(null);
        setIsAuthenticated(false);
      } else {
        setError(err.message || 'Failed to load user');
      }
    } finally {
      isLoadingRef.current = false;
      setIsLoading(false);
    }
  }, [backendAvailable, backendChecked]);

  const updateProfile = useCallback(async (data) => {
    setError(null);
    
    try {
      const updatedUser = await meApi.updateCurrentUser(data);
      setUser(updatedUser);
      return updatedUser;
    } catch (err) {
      console.error('Failed to update profile:', err);
      const errorMessage = err.message || 'Failed to update profile';
      setError(errorMessage);
      throw err;
    }
  }, []);

  const clearUser = useCallback((expired = false) => {
    userRef.current = null;
    setUser(null);
    setIsAuthenticated(false);
    setError(null);
    setSessionExpired(expired);
    wasAuthenticated.current = false;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch (err) {
      console.error('Logout API call failed:', err);
    } finally {
      clearUser(false);
      markUnauthenticated();
    }
  }, [clearUser]);

  const clearSessionExpired = useCallback(() => {
    setSessionExpired(false);
  }, []);

  const reload = useCallback(() => {
    return loadUser();
  }, [loadUser]);

  useEffect(() => {
    let cancelled = false;

    const runHealthCheck = async () => {
      const available = await checkBackendHealth();

      if (cancelled) return;

      setBackendAvailable(available);
      setBackendChecked(true);

      if (!available) {
        userRef.current = null;
        setUser(null);
        setIsAuthenticated(false);
        setError('Backend not available');
        setIsLoading(false);
      }
    };

    runHealthCheck();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const unsubscribe = onAuthStateChange((authenticated) => {
      setIsAuthenticated(authenticated);

      if (!authenticated) {
        const expired = wasAuthenticated.current;
        userRef.current = null;
        setUser(null);
        setError(null);
        if (expired) {
          setSessionExpired(true);
        }
        wasAuthenticated.current = false;
      } else if (backendAvailable && !userRef.current && !isLoadingRef.current) {
        loadUser();
      }
    });

    return unsubscribe;
  }, [backendAvailable, loadUser]);

  useEffect(() => {
    if (isAuthenticated) {
      wasAuthenticated.current = true;
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (backendChecked && backendAvailable) {
      loadUser();
    }
  }, [backendAvailable, backendChecked, loadUser]);

  const currentUser = user;

  const value = {
    user,
    currentUser,
    
    isLoading,
    isAuthenticated,
    backendAvailable,
    backendChecked,
    sessionExpired,
    error,
    
    loadUser,
    reload,
    updateProfile,
    clearUser,
    logout,
    clearSessionExpired,
  };

  return (
    <UserContext.Provider value={value}>
      {children}
    </UserContext.Provider>
  );
};

export default UserContext;
