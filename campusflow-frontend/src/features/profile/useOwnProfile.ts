import { useQuery } from '@tanstack/react-query';
import { api } from '../../services/apiClient';
import type { OwnProfile } from '../../types/api';
export function useOwnProfile() {
  return useQuery({
    queryKey: ['me'],
    queryFn: ({ signal }) => api.request<OwnProfile>('/api/v1/auth/me', { signal }),
  });
}
