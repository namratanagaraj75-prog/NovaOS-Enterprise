const configuredApiUrl = String(import.meta.env.VITE_API_URL || '')
  .replace(/^VITE_API_URL=/, '')
  .trim()
  .replace(/\/+$/, '');

/**
 * VITE_API_URL is the backend origin in hosted environments, for example:
 * https://novaos-enterprise.onrender.com
 *
 * All Spring Boot endpoints live below /api. Accepting an existing /api suffix
 * keeps local and hosted configuration tolerant without ever producing /api/api.
 */
export const API_BASE_URL = configuredApiUrl
  ? /\/api$/i.test(configuredApiUrl)
    ? configuredApiUrl
    : `${configuredApiUrl}/api`
  : '/api';

export const API_HEALTH_URL = `${API_BASE_URL}/health`;
