const HEALTH_CHECK_TTL = 30000; // 30 seconds

let cachedResult = null;
let lastChecked = null;
let inflightPromise = null;

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || '/api/proxy';

export const checkBackendHealth = async () => {
  if (cachedResult !== null && lastChecked && Date.now() - lastChecked < HEALTH_CHECK_TTL) {
    return cachedResult;
  }

  if (inflightPromise) {
    return inflightPromise;
  }

  inflightPromise = performHealthCheck();
  
  try {
    const result = await inflightPromise;
    cachedResult = result;
    lastChecked = Date.now();
    return result;
  } finally {
    inflightPromise = null;
  }
};

async function performHealthCheck() {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
      },
    });
    
    return response.status < 500;
  } catch (error) {
    console.warn('Backend not accessible:', error.message);
    return false;
  }
}

export const resetBackendHealthCache = () => {
  cachedResult = null;
  lastChecked = null;
  inflightPromise = null;
};

export const markBackendHealthy = () => {
  cachedResult = true;
  lastChecked = Date.now();
};

export const markBackendUnhealthy = () => {
  cachedResult = false;
  lastChecked = Date.now();
};
