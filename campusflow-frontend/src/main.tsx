import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './app/App';
import { AuthProvider } from './features/auth/context';
import { ToastProvider } from './components/ui/feedback';
import { ApiError } from './services/apiClient';
import './styles/index.css';
const cache = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30000,
      retry: (count, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 1,
      refetchOnWindowFocus: false,
    },
    mutations: { retry: false, gcTime: 0 },
  },
});
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={cache}>
      <AuthProvider>
        <ToastProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
