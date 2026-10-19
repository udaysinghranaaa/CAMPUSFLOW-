import { useToast } from '../../components/ui/toastContext';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Eye, Pencil, Plus, Trash2, SlidersHorizontal } from 'lucide-react';
import type { Entity, Resource } from '../../types/api';
import { resources, fieldValue, recordLabel, textValue } from '../../services/resources';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../auth/authContext';
import { definitions } from './definitions';
import { EntityForm } from './EntityForm';
import { RelationshipPicker } from './RelationshipPicker';
import { DataTable } from '../../components/ui/DataTable';
import {
  Dialog,
  EmptyState,
  ErrorState,
  LoadingSkeleton,
  PageHeader,
  Pagination,
  StatusBadge,
} from '../../components/ui/feedback';
interface Intent {
  record: Entity;
  kind: 'delete' | 'status';
  value?: string;
}
function Details({
  resource,
  id,
  onClose,
}: {
  resource: Resource;
  id: number;
  onClose: () => void;
}) {
  const query = useQuery({
    queryKey: [resource, 'detail', id],
    queryFn: ({ signal }) => resources.get(resource, id, signal),
  });
  const keys = [
    ...new Set([
      'id',
      ...definitions[resource].fields.filter((f) => f.type !== 'password').map((f) => f.name),
      ...definitions[resource].columns.map((c) => c.key),
      'createdAt',
      'updatedAt',
    ]),
  ];
  return (
    <Dialog title="Record details" onClose={onClose}>
      {query.isPending ? (
        <LoadingSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : (
        <dl className="details-grid">
          {keys.map((key) => (
            <div key={key}>
              <dt>{key.replace(/([A-Z])/g, ' $1')}</dt>
              <dd>{textValue(query.data, key) || '—'}</dd>
            </div>
          ))}
        </dl>
      )}
    </Dialog>
  );
}
export function ManagerPage({ resource }: { resource: Resource }) {
  const definition = definitions[resource];
  const { user } = useAuth();
  const notify = useToast();
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [filterError, setFilterError] = useState('');
  const [editor, setEditor] = useState<{ record?: Entity } | null>(null);
  const [detail, setDetail] = useState<number | null>(null);
  const [intent, setIntent] = useState<Intent | null>(null);
  const [actionError, setActionError] = useState('');
  const query = useQuery({
    queryKey: [resource, 'list', filters, page],
    queryFn: ({ signal }) => resources.list(resource, { ...filters, page, size: 10 }, signal),
  });
  const mutation = useMutation({
    gcTime: 0,
    mutationFn: async (action: Intent) => {
      if (action.kind === 'delete') {
        if (
          resource === 'students' ||
          resource === 'accounts' ||
          resource === 'subjects' ||
          resource === 'rooms'
        )
          throw new Error('Unsupported delete');
        return resources.remove(resource, action.record.id);
      }
      if (resource === 'faculty' || resource === 'subjects' || resource === 'rooms')
        return resources.status(resource, action.record.id, { active: action.value === 'true' });
      if (resource === 'students')
        return resources.status(resource, action.record.id, { status: action.value! });
      if (resource === 'accounts')
        return resources.status(resource, action.record.id, { enabled: action.value === 'true' });
      throw new Error('Unsupported status change');
    },
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: [resource] });
      await cache.invalidateQueries({ queryKey: ['faculty-finder'] });
      await cache.invalidateQueries({ queryKey: ['timetable'] });
      await cache.invalidateQueries({ queryKey: ['me'] });
      if (resource === 'faculty') await cache.invalidateQueries({ queryKey: ['directory'] });
      notify('Record updated successfully.');
      setIntent(null);
      setActionError('');
      if (query.data?.content.length === 1 && page > 0) setPage(page - 1);
    },
    onError: (problem) => setActionError(errorMessage(problem)),
  });
  const choose = (action: Intent) => {
    setActionError('');
    setIntent(action);
  };
  const setFilter = (key: string, value: string) =>
    setDraft((previous) => ({
      ...previous,
      [key]: value,
      ...(key === 'departmentId'
        ? { batchId: '', sectionId: '' }
        : key === 'batchId'
          ? { sectionId: '' }
          : {}),
    }));
  return (
    <>
      <PageHeader
        eyebrow="CAMPUS ADMINISTRATION"
        title={definition.title}
        description={definition.description}
        action={
          <button className="button primary" onClick={() => setEditor({})}>
            <Plus size={17} />
            {resource === 'accounts' ? 'Provision account' : `Add ${definition.singular}`}
          </button>
        }
      />
      <section className="panel data-panel" aria-busy={query.isFetching}>
        {definition.filters.length > 0 && (
          <form
            className="filter-panel"
            aria-label={`${definition.title} filters`}
            onSubmit={(event) => {
              event.preventDefault();
              if (
                Object.entries(draft).some(
                  ([key, value]) =>
                    value &&
                    (key.endsWith('Id') || key === 'semester') &&
                    !/^[1-9][0-9]*$/.test(value),
                )
              ) {
                setFilterError('Use positive whole numbers for IDs and semester.');
                return;
              }
              setFilterError('');
              setFilters(draft);
              setPage(0);
            }}
          >
            <div className="filter-heading">
              <SlidersHorizontal size={16} />
              <span>Find your records</span>
            </div>
            <div className="filter-fields">
              {definition.filters.map((field) => (
                <div className="field" key={field.name}>
                  <label htmlFor={`filter-${field.name}`}>{field.label}</label>
                  {field.resource ? (
                    <RelationshipPicker
                      key={`${field.name}-${field.name === 'batchId' ? draft.departmentId : field.name === 'sectionId' ? draft.batchId : ''}`}
                      id={`filter-${field.name}`}
                      resource={field.resource}
                      label={field.label}
                      value={draft[field.name] ?? ''}
                      optional
                      onChange={(value) => setFilter(field.name, value)}
                      params={
                        field.name === 'batchId' && draft.departmentId
                          ? { departmentId: draft.departmentId }
                          : field.name === 'sectionId' && draft.batchId
                            ? { batchId: draft.batchId }
                            : {}
                      }
                    />
                  ) : field.type === 'select' ? (
                    <select
                      id={`filter-${field.name}`}
                      value={draft[field.name] ?? ''}
                      onChange={(event) => setFilter(field.name, event.target.value)}
                    >
                      <option value="">All</option>
                      {field.options?.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input
                      id={`filter-${field.name}`}
                      type={field.type === 'number' ? 'number' : 'search'}
                      value={draft[field.name] ?? ''}
                      maxLength={
                        field.name === 'code' ? 20 : field.name === 'enrollmentNumber' ? 40 : 150
                      }
                      placeholder={`Search ${field.label.toLowerCase()}`}
                      onChange={(event) => setFilter(field.name, event.target.value)}
                    />
                  )}
                </div>
              ))}
            </div>
            {filterError && (
              <p role="alert" className="form-error">
                {filterError}
              </p>
            )}
            <div className="filter-actions">
              <button className="button secondary small">Apply filters</button>
              <button
                type="button"
                className="text-button"
                onClick={() => {
                  setDraft({});
                  setFilters({});
                  setFilterError('');
                  setPage(0);
                }}
              >
                Clear filters
              </button>
            </div>
          </form>
        )}
        {query.isPending ? (
          <LoadingSkeleton label={`Loading ${definition.title.toLowerCase()}…`} />
        ) : query.isError ? (
          <ErrorState error={query.error} retry={() => void query.refetch()} />
        ) : (
          <>
            {query.data.content.length === 0 ? (
              <EmptyState
                title="No matching records"
                description="Try a different filter, or add a record to your campus."
              />
            ) : (
              <DataTable<Entity>
                caption={definition.title}
                rows={query.data.content}
                columns={[
                  ...definition.columns.map((column, index) => ({
                    ...column,
                    render: (row: Entity) => {
                      const value = fieldValue(row, column.key);
                      return column.key === 'active' ||
                        column.key === 'status' ||
                        column.key === 'enabled' ? (
                        <StatusBadge
                          value={
                            column.key === 'enabled'
                              ? value
                                ? 'Enabled'
                                : 'Disabled'
                              : (value as string | boolean)
                          }
                        />
                      ) : (
                        <span className={index === 0 ? 'primary-cell' : ''}>
                          {value == null ? '—' : String(value)}
                        </span>
                      );
                    },
                  })),
                  {
                    key: 'actions',
                    label: 'Actions',
                    render: (row) => (
                      <div className="row-actions">
                        <button
                          className="icon-button"
                          aria-label={`View ${recordLabel(row)}`}
                          onClick={() => setDetail(row.id)}
                        >
                          <Eye size={17} />
                        </button>
                        {resource !== 'accounts' && (
                          <button
                            className="icon-button"
                            aria-label={`Edit ${recordLabel(row)}`}
                            onClick={() => setEditor({ record: row })}
                          >
                            <Pencil size={16} />
                          </button>
                        )}
                        {resource !== 'students' &&
                          resource !== 'accounts' &&
                          resource !== 'subjects' &&
                          resource !== 'rooms' && (
                            <button
                              className="icon-button danger-text"
                              aria-label={`Delete ${recordLabel(row)}`}
                              onClick={() => choose({ record: row, kind: 'delete' })}
                            >
                              <Trash2 size={16} />
                            </button>
                          )}
                        {resource === 'students' && (
                          <select
                            aria-label={`Change status for ${recordLabel(row)}`}
                            value=""
                            onChange={(event) => {
                              if (event.target.value)
                                choose({ record: row, kind: 'status', value: event.target.value });
                            }}
                          >
                            <option value="">Change status</option>
                            {['ACTIVE', 'INACTIVE', 'GRADUATED', 'SUSPENDED']
                              .filter((status) => status !== fieldValue(row, 'status'))
                              .map((status) => (
                                <option key={status}>{status}</option>
                              ))}
                          </select>
                        )}
                        {(resource === 'faculty' ||
                          resource === 'accounts' ||
                          resource === 'subjects' ||
                          resource === 'rooms') && (
                          <button
                            className="text-button"
                            disabled={resource === 'accounts' && row.id === user?.account.id}
                            title={
                              resource === 'accounts' && row.id === user?.account.id
                                ? 'You cannot change your own account status'
                                : undefined
                            }
                            onClick={() =>
                              choose({
                                record: row,
                                kind: 'status',
                                value: String(
                                  !fieldValue(row, resource === 'accounts' ? 'enabled' : 'active'),
                                ),
                              })
                            }
                          >
                            {fieldValue(row, resource === 'accounts' ? 'enabled' : 'active')
                              ? 'Deactivate'
                              : 'Activate'}
                          </button>
                        )}
                      </div>
                    ),
                  },
                ]}
              />
            )}
            <Pagination
              page={query.data.page}
              totalPages={query.data.totalPages}
              totalElements={query.data.totalElements}
              onPage={setPage}
            />
          </>
        )}
      </section>
      {resource === 'accounts' && (
        <p className="footnote">
          Accounts link to existing faculty or student profiles. ADMIN accounts are provisioned
          separately by your administrator.
        </p>
      )}
      {editor && (
        <EntityForm resource={resource} record={editor.record} onClose={() => setEditor(null)} />
      )}
      {detail !== null && (
        <Details resource={resource} id={detail} onClose={() => setDetail(null)} />
      )}
      {intent && (
        <Dialog
          title={
            intent.kind === 'delete' ? `Delete ${definition.singular}?` : 'Confirm status change'
          }
          onClose={() => setIntent(null)}
          busy={mutation.isPending}
        >
          <p>
            {intent.kind === 'delete' ? (
              <>
                Permanently remove <strong>{recordLabel(intent.record)}</strong>? Referenced records
                cannot be deleted. Reassign dependencies first.
              </>
            ) : (
              <>
                Change the status of <strong>{recordLabel(intent.record)}</strong> to{' '}
                <strong>
                  {intent.value === 'true'
                    ? 'active'
                    : intent.value === 'false'
                      ? 'inactive'
                      : intent.value?.toLowerCase()}
                </strong>
                ? Account changes may end active sessions.
              </>
            )}
          </p>
          {actionError && (
            <p role="alert" className="form-error">
              {actionError}
            </p>
          )}
          <footer className="dialog-actions">
            <button
              className="button secondary"
              disabled={mutation.isPending}
              onClick={() => setIntent(null)}
            >
              Cancel
            </button>
            <button
              className={`button ${intent.kind === 'delete' ? 'danger' : 'primary'}`}
              disabled={mutation.isPending}
              onClick={() => mutation.mutate(intent)}
            >
              {mutation.isPending
                ? 'Updating…'
                : intent.kind === 'delete'
                  ? 'Delete record'
                  : 'Confirm change'}
            </button>
          </footer>
        </Dialog>
      )}
    </>
  );
}
