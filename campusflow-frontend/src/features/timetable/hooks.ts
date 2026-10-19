import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../services/apiClient';
export interface ScheduleWrite {
  path: string;
  method: 'POST' | 'PUT' | 'PATCH';
  body?: unknown;
}
export function useScheduleMutation() {
  const cache = useQueryClient();
  return useMutation({
    gcTime: 0,
    mutationFn: ({ path, method, body }: ScheduleWrite) => api.request(path, { method, body }),
    onSuccess: async () => {
      await Promise.all(
        ['timetable', 'faculty-finder', 'subjects', 'rooms'].map((key) =>
          cache.invalidateQueries({ queryKey: [key] }),
        ),
      );
    },
  });
}
