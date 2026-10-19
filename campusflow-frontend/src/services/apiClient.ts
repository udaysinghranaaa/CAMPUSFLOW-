import { z } from 'zod';
import type { OwnProfile, Role } from '../types/api';
const tokensSchema = z.object({
  tokenType: z.literal('Bearer'),
  accessToken: z.string().min(1),
  expiresIn: z.number().positive(),
  refreshToken: z.string().regex(/^[A-Za-z0-9_-]{43}$/),
});
type Credentials = z.infer<typeof tokensSchema> & { expiresAt: number };
export interface AuthState {
  status: 'restoring' | 'anonymous' | 'authenticated';
  user: OwnProfile | null;
  message: string | null;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
export function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : 'Something went wrong. Please try again.';
}
const profileSchema = z.object({
  account: z.object({
    id: z.number().positive(),
    email: z.string(),
    role: z.enum(['ADMIN', 'FACULTY', 'STUDENT']),
    enabled: z.boolean(),
    studentId: z.number().nullable(),
    facultyId: z.number().nullable(),
    createdAt: z.string(),
    updatedAt: z.string(),
  }),
  student: z.record(z.string(), z.unknown()).nullable(),
  faculty: z.record(z.string(), z.unknown()).nullable(),
});
interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  authRetry?: boolean;
}
export class ApiClient {
  #credentials: Credentials | null = null;
  #refreshing: Promise<void> | null = null;
  #generation = 0;
  #state: AuthState = { status: 'restoring', user: null, message: null };
  #listeners = new Set<() => void>();
  constructor(
    private baseUrl: string,
    private transport: typeof fetch = (...args) => fetch(...args),
  ) {
    const url = new URL(baseUrl);
    if (
      !['http:', 'https:'].includes(url.protocol) ||
      url.username ||
      url.password ||
      url.search ||
      url.hash
    )
      throw new Error('Invalid public API base URL');
    if (url.protocol !== 'https:' && !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname))
      throw new Error('Remote API requires HTTPS');
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }
  subscribe = (listener: () => void) => {
    this.#listeners.add(listener);
    return () => {
      this.#listeners.delete(listener);
    };
  };
  getSnapshot = () => this.#state;
  #publish(state: AuthState) {
    this.#state = state;
    this.#listeners.forEach((listener) => listener());
  }
  restore = () => {
    if (this.#state.status === 'restoring') this.endSession(null);
  };
  endSession = (message: string | null = null) => {
    this.#generation++;
    this.#credentials = null;
    this.#refreshing = null;
    this.#publish({ status: 'anonymous', user: null, message });
  };
  async #send<T>(path: string, options: RequestOptions = {}, bearer?: string): Promise<T> {
    if (!path.startsWith('/api/')) throw new ApiError(0, 'Invalid API path.');
    const controller = new AbortController();
    const abort = () => controller.abort();
    if (options.signal?.aborted) abort();
    options.signal?.addEventListener('abort', abort, { once: true });
    const timeout = setTimeout(abort, 15000);
    const { body, authRetry: _authRetry, ...init } = options;
    void _authRetry;
    try {
      const headers = new Headers(init.headers);
      headers.set('Accept', 'application/json');
      if (body !== undefined) headers.set('Content-Type', 'application/json');
      if (bearer) headers.set('Authorization', `Bearer ${bearer}`);
      const response = await this.transport(this.baseUrl + path, {
        ...init,
        body: body === undefined ? undefined : JSON.stringify(body),
        headers,
        signal: controller.signal,
        credentials: 'omit',
        cache: 'no-store',
        redirect: 'error',
      });
      if (!response.ok) {
        let payload: unknown;
        try {
          payload = await response.json();
        } catch {
          payload = null;
        }
        const parsed = z
          .object({
            message: z.string().max(500).optional(),
            fieldErrors: z.record(z.string(), z.string()).optional(),
          })
          .safeParse(payload);
        const fallback =
          response.status === 401
            ? 'Sign-in required or invalid credentials.'
            : response.status === 403
              ? 'You do not have permission for this action.'
              : response.status === 404
                ? 'This record could not be found.'
                : response.status === 409
                  ? 'This change conflicts with existing records.'
                  : 'The server could not complete the request.';
        throw new ApiError(
          response.status,
          response.status >= 500
            ? fallback
            : parsed.success
              ? (parsed.data.message ?? fallback)
              : fallback,
          parsed.success ? parsed.data.fieldErrors : {},
        );
      }
      return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
    } catch (error) {
      if (error instanceof ApiError) throw error;
      if (controller.signal.aborted)
        throw new ApiError(
          0,
          options.signal?.aborted ? 'Request cancelled.' : 'Request timed out. Please try again.',
        );
      throw new ApiError(
        0,
        'Cannot reach CampusFlow. Check your connection and that the backend is running.',
      );
    } finally {
      clearTimeout(timeout);
      options.signal?.removeEventListener('abort', abort);
    }
  }
  async #refresh() {
    if (this.#refreshing) return this.#refreshing;
    if (!this.#credentials) throw new ApiError(401, 'Please sign in again.');
    const refreshToken = this.#credentials.refreshToken;
    const generation = this.#generation;
    const pending = (async () => {
      try {
        // Never retry this POST: the server consumes refresh tokens and detects replay.
        const result = tokensSchema.parse(
          await this.#send<unknown>('/api/v1/auth/refresh', {
            method: 'POST',
            body: { refreshToken },
          }),
        );
        if (generation !== this.#generation) throw new ApiError(401, 'Session changed.');
        this.#credentials = { ...result, expiresAt: Date.now() + result.expiresIn * 1000 };
      } catch (error) {
        if (generation === this.#generation)
          this.endSession('Your session ended. Please sign in again.');
        throw error instanceof ApiError ? error : new ApiError(401, 'Please sign in again.');
      }
    })();
    this.#refreshing = pending;
    try {
      await pending;
    } finally {
      if (this.#refreshing === pending) this.#refreshing = null;
    }
  }
  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const generation = this.#generation;
    if (!this.#credentials) throw new ApiError(401, 'Please sign in.');
    if (Date.now() >= this.#credentials.expiresAt - 10000) await this.#refresh();
    const usedToken = this.#credentials?.accessToken;
    try {
      const result = await this.#send<T>(path, options, usedToken);
      if (generation !== this.#generation) throw new ApiError(401, 'Session changed.');
      return result;
    } catch (error) {
      if (
        !(error instanceof ApiError) ||
        error.status !== 401 ||
        options.authRetry === false ||
        generation !== this.#generation
      )
        throw error;
      if (usedToken === this.#credentials?.accessToken) await this.#refresh();
      if (generation !== this.#generation || !this.#credentials)
        throw new ApiError(401, 'Please sign in again.');
      try {
        const result = await this.#send<T>(path, options, this.#credentials.accessToken);
        if (generation !== this.#generation) throw new ApiError(401, 'Session changed.');
        return result;
      } catch (retryError) {
        if (
          retryError instanceof ApiError &&
          retryError.status === 401 &&
          generation === this.#generation
        )
          this.endSession('Your session ended. Please sign in again.');
        throw retryError;
      }
    }
  }
  async login(email: string, password: string) {
    this.endSession(null);
    const generation = this.#generation;
    try {
      const result = tokensSchema.parse(
        await this.#send<unknown>('/api/v1/auth/login', {
          method: 'POST',
          body: { email, password },
        }),
      );
      if (generation !== this.#generation) throw new ApiError(401, 'Sign-in cancelled.');
      this.#credentials = { ...result, expiresAt: Date.now() + result.expiresIn * 1000 };
      const profile = await this.request<OwnProfile>('/api/v1/auth/me');
      profileSchema.parse(profile);
      if (generation !== this.#generation) throw new ApiError(401, 'Sign-in cancelled.');
      this.#publish({ status: 'authenticated', user: profile, message: null });
      return profile;
    } catch (error) {
      if (generation === this.#generation) this.endSession(null);
      throw error instanceof ApiError
        ? error
        : new ApiError(0, 'The server returned an unexpected sign-in response.');
    }
  }
  async logout() {
    try {
      if (
        this.#refreshing ||
        (this.#credentials && Date.now() >= this.#credentials.expiresAt - 10000)
      )
        await this.#refresh();
    } catch {
      this.endSession(null);
      throw new ApiError(0, 'Signed out on this device. Server revocation could not be confirmed.');
    }
    const credentials = this.#credentials;
    this.endSession(null);
    if (credentials)
      await this.#send(
        '/api/v1/auth/logout',
        { method: 'POST', body: { refreshToken: credentials.refreshToken } },
        credentials.accessToken,
      );
  }
  async changePassword(currentPassword: string, newPassword: string) {
    await this.request('/api/v1/auth/change-password', {
      method: 'POST',
      body: { currentPassword, newPassword },
      authRetry: false,
    });
    this.endSession('Password changed. Please sign in again.');
  }
}
export const api = new ApiClient(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080');
export const roleHome = (role: Role) =>
  role === 'ADMIN' ? '/admin' : role === 'FACULTY' ? '/faculty' : '/student';
