import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiClient } from '../../services/apiClient';
import { ClientContext } from './authContext';
export function AuthProvider({
  children,
  client = api,
}: {
  children: ReactNode;
  client?: ApiClient;
}) {
  const cache = useQueryClient();
  useEffect(() => {
    let identity = client.getSnapshot().user?.account.id;
    const unsubscribe = client.subscribe(() => {
      const state = client.getSnapshot();
      if (state.status === 'anonymous' || identity !== state.user?.account.id) {
        void cache.cancelQueries();
        cache.clear();
      }
      identity = state.user?.account.id;
    });
    client.restore();
    return unsubscribe;
  }, [client, cache]);
  return <ClientContext.Provider value={client}>{children}</ClientContext.Provider>;
}
