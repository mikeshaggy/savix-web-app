import { useState, useEffect, useCallback } from 'react';
import { fixedPaymentApi, checkBackendHealth, onAuthStateChange, getAuthState } from '@/lib/api';

export const useFixedPaymentsTile = (walletId) => {
  const [tileData, setTileData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchTileData = useCallback(async () => {
    const isAuthenticated = getAuthState();
    if (!isAuthenticated || !walletId) {
      setTileData(null);
      setLoading(false);
      setError(null);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const backendAvailable = await checkBackendHealth();

      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const data = await fixedPaymentApi.getTileData(walletId);
      setTileData(data);
    } catch (err) {
      console.error('Failed to fetch fixed payments tile data:', err);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [walletId]);

  useEffect(() => {
    fetchTileData();
  }, [fetchTileData]);

  useEffect(() => {
    const unsubscribe = onAuthStateChange((authenticated) => {
      if (!authenticated) {
        setTileData(null);
        setError(null);
      } else {
        fetchTileData();
      }
    });

    return unsubscribe;
  }, [fetchTileData]);

  return { tileData, loading, error, refetch: fetchTileData };
};

export const useFixedPayments = (walletId) => {
  const [fixedPayments, setFixedPayments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchFixedPayments = useCallback(async () => {
    const isAuthenticated = getAuthState();

    if (!isAuthenticated || !walletId) {
      setFixedPayments([]);
      setLoading(false);
      setError(null);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const backendAvailable = await checkBackendHealth();

      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const data = await fixedPaymentApi.getAll(walletId);
      setFixedPayments(data || []);
    } catch (err) {
      console.error('Failed to fetch fixed payments:', err);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [walletId]);

  const createFixedPayment = useCallback(async (data) => {
    try {
      setError(null);

      const backendAvailable = await checkBackendHealth();

      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const newPayment = await fixedPaymentApi.create(data);
      setFixedPayments(prev => [newPayment, ...prev]);
      return newPayment;
    } catch (err) {
      console.error('Failed to create fixed payment:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const updateFixedPayment = useCallback(async (id, data) => {
    try {
      setError(null);

      const backendAvailable = await checkBackendHealth();

      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      const updated = await fixedPaymentApi.update(id, data);
      setFixedPayments(prev => prev.map(fp => fp.id === id ? updated : fp));
      return updated;
    } catch (err) {
      console.error('Failed to update fixed payment:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  const deactivateFixedPayment = useCallback(async (id) => {
    try {
      setError(null);

      const backendAvailable = await checkBackendHealth();

      if (!backendAvailable) {
        throw new Error('Backend not available');
      }

      await fixedPaymentApi.deactivate(id);
      setFixedPayments(prev =>
        prev.map(fp =>
          fp.id === id
            ? { ...fp, activeTo: new Date().toISOString().split('T')[0] }
            : fp
        )
      );
    } catch (err) {
      console.error('Failed to deactivate fixed payment:', err);
      setError(err.message);
      throw err;
    }
  }, []);

  useEffect(() => {
    fetchFixedPayments();
  }, [fetchFixedPayments]);

  useEffect(() => {
    const unsubscribe = onAuthStateChange((authenticated) => {
      if (!authenticated) {
        setFixedPayments([]);
        setError(null);
      } else {
        fetchFixedPayments();
      }
    });

    return unsubscribe;
  }, [fetchFixedPayments]);

  return {
    fixedPayments,
    loading,
    error,
    createFixedPayment,
    updateFixedPayment,
    deactivateFixedPayment,
    refetch: fetchFixedPayments,
  };
};
