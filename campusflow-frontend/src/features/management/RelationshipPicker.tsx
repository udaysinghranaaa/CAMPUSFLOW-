import { useState, useId } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { Resource } from '../../types/api';
import { resources, recordLabel } from '../../services/resources';
import { errorMessage } from '../../services/apiClient';
export function RelationshipPicker({
  resource,
  label,
  value,
  onChange,
  params = {},
  disabled = false,
  optional = false,
  id,
  errorId,
}: {
  resource: Resource;
  label: string;
  value: string;
  onChange: (value: string) => void;
  params?: Record<string, string>;
  disabled?: boolean;
  optional?: boolean;
  id?: string;
  errorId?: string;
}) {
  const [page, setPage] = useState(0);
  const fallbackId = useId();
  const query = useQuery({
    queryKey: [resource, 'choices', params, page],
    queryFn: ({ signal }) => resources.list(resource, { ...params, page, size: 20 }, signal),
    enabled: !disabled,
  });
  const options = query.data?.content ?? [];
  const hasSelected = options.some((item) => String(item.id) === value);
  return (
    <div className="relationship-picker">
      <select
        id={id ?? fallbackId}
        aria-label={label}
        aria-describedby={errorId}
        aria-invalid={!!errorId}
        disabled={disabled || query.isPending}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">
          {disabled ? 'Choose the parent first' : optional ? 'None / all' : 'Select a record'}
        </option>
        {value && !hasSelected && <option value={value}>Selected #{value}</option>}
        {options.map((item) => (
          <option key={item.id} value={item.id}>
            {recordLabel(item)} · #{item.id}
          </option>
        ))}
      </select>
      {query.isError ? (
        <div className="choice-error" role="alert">
          {errorMessage(query.error)}{' '}
          <button type="button" className="text-button" onClick={() => void query.refetch()}>
            Retry
          </button>
        </div>
      ) : (
        !disabled &&
        query.data && (
          <div className="choice-pagination">
            <span>
              {query.data.totalElements} available · {query.data.totalPages === 0 ? 0 : page + 1}/
              {query.data.totalPages}
            </span>
            <button
              type="button"
              aria-label={`Previous ${label} choices`}
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              ‹
            </button>
            <button
              type="button"
              aria-label={`Next ${label} choices`}
              disabled={query.data.last}
              onClick={() => setPage(page + 1)}
            >
              ›
            </button>
          </div>
        )
      )}
    </div>
  );
}
