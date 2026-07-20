import type { CompanyProfile, InvitationCreated, WorkspaceMember, WorkspaceSummary, WorkspaceRole } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
const COMPANY_KEY = 'textile_company_id';
export const AUTH_EXPIRED_EVENT = 'textile_auth_expired';
export const PERMISSION_DENIED_EVENT = 'textile_permission_denied';
let csrfToken: string | null = null;

export function getCompanyId() {
  return localStorage.getItem(COMPANY_KEY);
}

export function setCompanyId(companyId: string) {
  localStorage.setItem(COMPANY_KEY, companyId);
}

export function clearCompanyId() {
  localStorage.removeItem(COMPANY_KEY);
}

function handleAuthExpired<T>(): Promise<T> {
  csrfToken = null;
  window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
  return Promise.reject(new Error('Your session has expired.'));
}

function handlePermissionDenied<T>(): Promise<T> {
  const message = 'You do not have permission to perform this action. Your current role is read-only.';
  window.dispatchEvent(new CustomEvent(PERMISSION_DENIED_EVENT, { detail: message }));
  return Promise.reject(new Error(message));
}

async function getCsrfToken() {
  if (csrfToken) {
    return csrfToken;
  }
  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, { credentials: 'include' });
  if (!response.ok) {
    throw new Error('Could not establish a secure session.');
  }
  const body = await response.json() as { token: string };
  csrfToken = body.token;
  return csrfToken;
}

async function responseError(response: Response) {
  const text = await response.text();
  if (!text) {
    return `Request failed: ${response.status}`;
  }
  try {
    const body = JSON.parse(text) as { message?: string; error?: string };
    return body.message || body.error || text;
  } catch {
    return text;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const method = (options.method || 'GET').toUpperCase();
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    headers.set('X-CSRF-TOKEN', await getCsrfToken());
  }
  const companyId = getCompanyId();
  if (companyId) {
    headers.set('X-Company-Id', companyId);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers, credentials: 'include' });
  if (!response.ok) {
    if (response.status === 401 && path === '/api/auth/me') {
      return handleAuthExpired<T>();
    }
    if (response.status === 403) {
      return handlePermissionDenied<T>();
    }
    throw new Error(await responseError(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

export const api = {
  me: () => request<{ username: string; displayName?: string }>('/api/auth/me'),
  login: (username: string, password: string) =>
    request<{ username: string }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  signup: (details: { email: string; password: string; displayName: string; workspaceName: string; businessName: string }) =>
    request<{ username: string }>('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify(details),
    }),
  forgotPassword: (email: string) =>
    request<{ message: string }>('/api/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),
  resetPassword: (token: string, newPassword: string) =>
    request<void>('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token, newPassword }),
    }),
  acceptInvitation: (details: { token: string; displayName: string; password: string }) =>
    request<{ username: string }>('/api/auth/invitations/accept', {
      method: 'POST',
      body: JSON.stringify(details),
    }),
  acceptInvitationAsExisting: (token: string) =>
    request<void>('/api/auth/invitations/accept-existing', {
      method: 'POST',
      body: JSON.stringify({ token }),
    }),
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string) => request<T>(path, { method: 'PATCH' }),
  patchBody: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: 'DELETE' }),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
  getWorkspaces: () => request<WorkspaceSummary[]>('/api/workspaces'),
  getWorkspaceMembers: (workspaceId: number) => request<WorkspaceMember[]>(`/api/workspaces/${workspaceId}/members`),
  inviteWorkspaceMember: (workspaceId: number, email: string, role: Exclude<WorkspaceRole, 'OWNER'>) =>
    request<InvitationCreated>(`/api/workspaces/${workspaceId}/invitations`, {
      method: 'POST',
      body: JSON.stringify({ email, role }),
    }),
  updateWorkspaceMemberRole: (workspaceId: number, userId: number, role: Exclude<WorkspaceRole, 'OWNER'>) =>
    request<void>(`/api/workspaces/${workspaceId}/members/${userId}/role`, {
      method: 'PATCH',
      body: JSON.stringify({ role }),
    }),
  removeWorkspaceMember: (workspaceId: number, userId: number) =>
    request<void>(`/api/workspaces/${workspaceId}/members/${userId}`, { method: 'DELETE' }),
  getCompanyProfiles: () => request<CompanyProfile[]>('/api/company/all'),
  createCompanyProfile: (profile: Pick<CompanyProfile, 'tradeName'> & Partial<CompanyProfile>) =>
    request<CompanyProfile>('/api/company', { method: 'POST', body: JSON.stringify(profile) }),
  getCompanyProfile: () => request<CompanyProfile>('/api/company'),
  updateCompanyProfile: (profile: Partial<CompanyProfile>) =>
    request<CompanyProfile>('/api/company', { method: 'PUT', body: JSON.stringify(profile) }),
  uploadCompanyLogo: (file: File) => {
    const body = new FormData();
    body.append('file', file);
    return request<CompanyProfile>('/api/company/logo', { method: 'PUT', body });
  },
  deleteCompanyLogo: () => request<void>('/api/company/logo', { method: 'DELETE' }),
  getCompanyLogo: async () => {
    const companyId = getCompanyId();
    const response = await fetch(`${API_BASE_URL}/api/company/logo`, {
      credentials: 'include',
      headers: {
        ...(companyId ? { 'X-Company-Id': companyId } : {}),
      },
    });
    if (response.status === 404) {
      return null;
    }
    if (!response.ok) {
      if (response.status === 401) {
        return handleAuthExpired<Blob | null>();
      }
      if (response.status === 403) {
        return handlePermissionDenied<Blob | null>();
      }
      throw new Error(await responseError(response));
    }
    return response.blob();
  },
  download: async (path: string, filename: string) => {
    const companyId = getCompanyId();
    const response = await fetch(`${API_BASE_URL}${path}`, {
      credentials: 'include',
      headers: {
        ...(companyId ? { 'X-Company-Id': companyId } : {}),
      },
    });
    if (!response.ok) {
      if (response.status === 401) {
        return handleAuthExpired<void>();
      }
      if (response.status === 403) {
        return handlePermissionDenied<void>();
      }
      throw new Error(await responseError(response));
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  },
};
