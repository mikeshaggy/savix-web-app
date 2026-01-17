'use client';
import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { 
  meApi, 
  authApi,
  onAuthStateChange, 
  getAuthState,
  markAuthenticated,
  markUnauthenticated,
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
  
  const [isAuthenticated, setIsAuthenticated] = useState(() => getAuthState());
  
  const [sessionExpired, setSessionExpired] = useState(false);
  
  const wasAuthenticated = useRef(false);

  const loadUser = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const userData = await meApi.getCurrentUser();
      setUser(userData);
      setIsAuthenticated(true);
      markAuthenticated();
    } catch (err) {
      console.error('Failed to load user:', err);
      
      if (err instanceof ApiError && err.isUnauthorized) {
        setUser(null);
        setIsAuthenticated(false);
      } else {
        setError(err.message || 'Failed to load user');
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

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
    const unsubscribe = onAuthStateChange((authenticated) => {
      setIsAuthenticated(authenticated);
      
      if (!authenticated) {
        const expired = wasAuthenticated.current;
        setUser(null);
        setError(null);
        if (expired) {
          setSessionExpired(true);
        }
        wasAuthenticated.current = false;
      } else if (!user && !isLoading) {
        loadUser();
      }
    });

    return unsubscribe;
  }, [user, isLoading, loadUser]);

  useEffect(() => {
    if (isAuthenticated) {
      wasAuthenticated.current = true;
    }
  }, [isAuthenticated]);

  useEffect(() => {
    loadUser();
  }, [loadUser]);

  const currentUser = user;

  const value = {
    user,
    currentUser,
    
    isLoading,
    isAuthenticated,
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
