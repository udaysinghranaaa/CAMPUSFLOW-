import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../features/auth/context';
import { ToastProvider } from '../components/ui/feedback';
import { api } from '../services/apiClient';
export function renderWorkspace(children: ReactNode, path = '/login') {
  const cache = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false, gcTime: 0 },
    },
  });
  return {
    ...render(
      <QueryClientProvider client={cache}>
        <AuthProvider client={api}>
          <ToastProvider>
            <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>,
    ),
    cache,
  };
}
