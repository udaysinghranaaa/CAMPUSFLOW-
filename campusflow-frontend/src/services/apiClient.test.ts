import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiClient, ApiError } from './apiClient';
import { failure, json, me, noContent, tokens } from '../test/fixtures';
function setup(
  extra?: (path: string, options?: RequestInit) => Response | Promise<Response> | undefined,
  expiresIn = 900,
) {
  const fetcher = vi.fn(async (input: RequestInfo | URL, options?: RequestInit) => {
    const path = String(input);
    const custom = extra?.(path, options);
    if (custom) return await custom;
    if (path.endsWith('/login')) return json(tokens('one', expiresIn));
    if (path.endsWith('/me')) return json(me());
    if (path.endsWith('/refresh')) return json(tokens('two'));
    if (path.endsWith('/logout') || path.endsWith('/change-password')) return noContent();
    return json({ ok: true });
  });
  const client = new ApiClient('http://localhost:8080', fetcher);
  return { client, fetcher };
}
afterEach(() => vi.useRealTimers());
describe('memory-only bearer sessions', () => {
  it('restores an anonymous session after a fresh page load', () => {
    const { client } = setup();
    expect(client.getSnapshot().status).toBe('restoring');
    client.restore();
    expect(client.getSnapshot()).toMatchObject({ status: 'anonymous', user: null });
  });
  it('logs in with real contract property names, then attaches bearer only to protected requests', async () => {
    const { client, fetcher } = setup();
    await client.login('test@example.edu', 'fixture-password');
    expect(client.getSnapshot().user?.account.role).toBe('ADMIN');
    const login = fetcher.mock.calls[0][1]!;
    expect(login.body).toBe(
      JSON.stringify({ email: 'test@example.edu', password: 'fixture-password' }),
    );
    expect(new Headers(login.headers).has('Authorization')).toBe(false);
    expect(new Headers(fetcher.mock.calls[1][1]?.headers).get('Authorization')).toBe(
      'Bearer fixture-access-one',
    );
    expect(login.credentials).toBe('omit');
  });
  it('never writes tokens or credentials into browser storage', async () => {
    const local = vi.spyOn(Storage.prototype, 'setItem');
    const { client } = setup();
    await client.login('test@example.edu', 'fixture-password');
    await client.logout();
    expect(local).not.toHaveBeenCalled();
  });
  it('deduplicates proactive refresh across simultaneous expired-token requests', async () => {
    const clock = vi.spyOn(Date, 'now').mockReturnValue(1000);
    const { client, fetcher } = setup(undefined, 60);
    await client.login('test@example.edu', 'fixture-password');
    clock.mockReturnValue(62000);
    await Promise.all([
      client.request('/api/one'),
      client.request('/api/two'),
      client.request('/api/three'),
    ]);
    expect(fetcher.mock.calls.filter(([url]) => String(url).endsWith('/refresh'))).toHaveLength(1);
    const calls = fetcher.mock.calls.filter(([url]) => /\/api\/(one|two|three)$/.test(String(url)));
    expect(
      calls.every(
        ([, options]) =>
          new Headers(options?.headers).get('Authorization') === 'Bearer fixture-access-two',
      ),
    ).toBe(true);
  });
  it('deduplicates concurrent 401 refresh and retries each rejected request once', async () => {
    const { client, fetcher } = setup((path, options) =>
      path.includes('/api/work') &&
      new Headers(options?.headers).get('Authorization') === 'Bearer fixture-access-one'
        ? failure(401)
        : undefined,
    );
    await client.login('test@example.edu', 'fixture-password');
    await Promise.all([client.request('/api/work1'), client.request('/api/work2')]);
    expect(fetcher.mock.calls.filter(([url]) => String(url).endsWith('/refresh'))).toHaveLength(1);
    expect(fetcher.mock.calls.filter(([url]) => String(url).includes('/api/work'))).toHaveLength(4);
  });
  it('ends the session on rejected refresh without replaying the rotation request', async () => {
    const { client, fetcher } = setup((path) =>
      path.endsWith('/refresh') || path.endsWith('/api/work') ? failure(401) : undefined,
    );
    await client.login('test@example.edu', 'fixture-password');
    await expect(client.request('/api/work')).rejects.toBeInstanceOf(ApiError);
    expect(fetcher.mock.calls.filter(([url]) => String(url).endsWith('/refresh'))).toHaveLength(1);
    expect(client.getSnapshot().status).toBe('anonymous');
  });
  it('does not refresh or sign out on a 403 response', async () => {
    const { client, fetcher } = setup((path) =>
      path.endsWith('/api/forbidden') ? failure(403) : undefined,
    );
    await client.login('test@example.edu', 'fixture-password');
    await expect(client.request('/api/forbidden')).rejects.toMatchObject({ status: 403 });
    expect(client.getSnapshot().status).toBe('authenticated');
    expect(fetcher.mock.calls.filter(([url]) => String(url).endsWith('/refresh'))).toHaveLength(0);
  });
  it('clears authentication after a retry also returns 401', async () => {
    const { client } = setup((path) => (path.endsWith('/api/work') ? failure(401) : undefined));
    await client.login('test@example.edu', 'fixture-password');
    await expect(client.request('/api/work')).rejects.toMatchObject({ status: 401 });
    expect(client.getSnapshot().status).toBe('anonymous');
  });
  it('logs out using the current refresh token and clears the local session', async () => {
    const { client, fetcher } = setup();
    await client.login('test@example.edu', 'fixture-password');
    await client.logout();
    const call = fetcher.mock.calls.find(([url]) => String(url).endsWith('/logout'))!;
    expect(JSON.parse(String(call[1]?.body))).toEqual({ refreshToken: 'a'.repeat(43) });
    expect(client.getSnapshot().user).toBeNull();
    await expect(client.request('/api/work')).rejects.toMatchObject({ status: 401 });
  });
  it('clears local credentials even when server logout fails', async () => {
    const { client } = setup((path) => (path.endsWith('/logout') ? failure(500) : undefined));
    await client.login('test@example.edu', 'fixture-password');
    await expect(client.logout()).rejects.toBeInstanceOf(ApiError);
    expect(client.getSnapshot().status).toBe('anonymous');
  });
  it('rejects late responses from an ended session', async () => {
    let release!: (value: Response) => void;
    const pending = new Promise<Response>((resolve) => {
      release = resolve;
    });
    const { client } = setup((path) => (path.endsWith('/api/work') ? pending : undefined));
    await client.login('test@example.edu', 'fixture-password');
    const request = client.request('/api/work');
    await client.logout();
    release(json({ private: 'not to be cached' }));
    await expect(request).rejects.toMatchObject({ status: 401 });
  });
  it('keeps the session when the submitted current password is wrong', async () => {
    const { client } = setup((path) =>
      path.endsWith('/change-password') ? failure(401) : undefined,
    );
    await client.login('test@example.edu', 'fixture-password');
    await expect(client.changePassword('wrong', 'new-password-long')).rejects.toMatchObject({
      status: 401,
    });
    expect(client.getSnapshot().status).toBe('authenticated');
  });
  it('ends the session after a successful password change', async () => {
    const { client } = setup();
    await client.login('test@example.edu', 'fixture-password');
    await client.changePassword('fixture-password', 'new-password-long');
    expect(client.getSnapshot()).toMatchObject({
      status: 'anonymous',
      message: 'Password changed. Please sign in again.',
    });
  });
  it('does not expose raw server diagnostics on 5xx', async () => {
    const { client } = setup((path) =>
      path.endsWith('/api/work') ? failure(500, 'SQL diagnostic fixture') : undefined,
    );
    await client.login('test@example.edu', 'fixture-password');
    await expect(client.request('/api/work')).rejects.toMatchObject({
      message: 'The server could not complete the request.',
    });
  });
  it('does not accept insecure remote API destinations', () => {
    expect(() => new ApiClient('http://untrusted.example')).toThrow('HTTPS');
  });
  it('clears the session after a network-uncertain refresh and does not retry it', async () => {
    const clock = vi.spyOn(Date, 'now').mockReturnValue(1000);
    const { client, fetcher } = setup((path) => {
      if (path.endsWith('/refresh')) throw new Error('Network interrupted');
      return undefined;
    }, 60);
    await client.login('test@example.edu', 'fixture-password');
    clock.mockReturnValue(62000);
    await expect(client.request('/api/work')).rejects.toMatchObject({ status: 0 });
    expect(client.getSnapshot().status).toBe('anonymous');
    expect(fetcher.mock.calls.filter(([url]) => String(url).endsWith('/refresh'))).toHaveLength(1);
  });
  it('normalizes server field errors without losing their field names', async () => {
    const { client } = setup((path) =>
      path.endsWith('/api/work')
        ? json(
            { message: 'Request validation failed.', fieldErrors: { name: 'must not be blank' } },
            400,
          )
        : undefined,
    );
    await client.login('test@example.edu', 'fixture-password');
    await expect(client.request('/api/work')).rejects.toMatchObject({
      status: 400,
      fieldErrors: { name: 'must not be blank' },
    });
  });
  it('cancels a request through its caller signal without refreshing', async () => {
    const { client } = setup((path, options) =>
      path.endsWith('/api/slow')
        ? new Promise<Response>((_resolve, reject) =>
            options?.signal?.addEventListener('abort', () =>
              reject(new DOMException('Aborted', 'AbortError')),
            ),
          )
        : undefined,
    );
    await client.login('test@example.edu', 'fixture-password');
    const controller = new AbortController();
    const request = client.request('/api/slow', { signal: controller.signal });
    controller.abort();
    await expect(request).rejects.toMatchObject({ status: 0, message: 'Request cancelled.' });
    expect(client.getSnapshot().status).toBe('authenticated');
  });
  it('times out stalled HTTP requests after fifteen seconds', async () => {
    const { client } = setup((path, options) =>
      path.endsWith('/api/slow')
        ? new Promise<Response>((_resolve, reject) =>
            options?.signal?.addEventListener('abort', () =>
              reject(new DOMException('Aborted', 'AbortError')),
            ),
          )
        : undefined,
    );
    await client.login('test@example.edu', 'fixture-password');
    vi.useFakeTimers();
    const assertion = expect(client.request('/api/slow')).rejects.toMatchObject({
      status: 0,
      message: 'Request timed out. Please try again.',
    });
    await vi.advanceTimersByTimeAsync(15000);
    await assertion;
  });
});
