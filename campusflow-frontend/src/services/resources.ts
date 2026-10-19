import { api } from './apiClient';
import type { Resource, Entities, Requests, Page } from '../types/api';
export const endpoint = (resource: Resource) =>
  resource === 'accounts' ? '/api/v1/admin/accounts' : `/api/v1/${resource}`;
export function queryString(params: Record<string, string | number | boolean | undefined>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value));
  });
  return '?' + query.toString();
}
export const resources = {
  list: <K extends Resource>(
    resource: K,
    params: Record<string, string | number | boolean | undefined>,
    signal?: AbortSignal,
  ) => api.request<Page<Entities[K]>>(endpoint(resource) + queryString(params), { signal }),
  get: <K extends Resource>(resource: K, id: number, signal?: AbortSignal) =>
    api.request<Entities[K]>(`${endpoint(resource)}/${id}`, { signal }),
  create: <K extends Resource>(resource: K, body: Requests[K]) =>
    api.request<Entities[K]>(endpoint(resource), { method: 'POST', body }),
  update: <K extends Exclude<Resource, 'accounts'>>(resource: K, id: number, body: Requests[K]) =>
    api.request<Entities[K]>(`${endpoint(resource)}/${id}`, { method: 'PUT', body }),
  remove: (
    resource: Exclude<Resource, 'students' | 'accounts' | 'subjects' | 'rooms'>,
    id: number,
  ) => api.request<void>(`${endpoint(resource)}/${id}`, { method: 'DELETE' }),
  status: (
    resource: 'faculty' | 'students' | 'accounts' | 'subjects' | 'rooms',
    id: number,
    body: { active: boolean } | { enabled: boolean } | { status: string },
  ) => api.request(`${endpoint(resource)}/${id}/status`, { method: 'PATCH', body }),
};
export function fieldValue(record: object, key: string): unknown {
  return Reflect.get(record, key);
}
export function textValue(record: object, key: string) {
  const value = fieldValue(record, key);
  return value == null ? '' : String(value);
}
export function recordLabel(record: object) {
  return (
    textValue(record, 'fullName') ||
    textValue(record, 'name') ||
    textValue(record, 'email') ||
    `#${textValue(record, 'id')}`
  );
}
