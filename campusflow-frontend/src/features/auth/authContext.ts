import { createContext, useContext, useSyncExternalStore } from 'react';
import { api } from '../../services/apiClient';
export const ClientContext = createContext(api);
export function useAuth() {
  const client = useContext(ClientContext);
  return {
    ...useSyncExternalStore(client.subscribe, client.getSnapshot, client.getSnapshot),
    client,
  };
}
