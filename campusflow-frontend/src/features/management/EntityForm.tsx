import { useToast } from '../../components/ui/toastContext';
import { useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { Entity, Resource, Requests } from '../../types/api';
import { resources } from '../../services/resources';
import { ApiError, errorMessage } from '../../services/apiClient';
import { definitions, defaults } from './definitions';
import { RelationshipPicker } from './RelationshipPicker';
import { Dialog } from '../../components/ui/feedback';
export function EntityForm({
  resource,
  record,
  onClose,
}: {
  resource: Resource;
  record?: Entity;
  onClose: () => void;
}) {
  const definition = definitions[resource];
  const [error, setError] = useState('');
  const notify = useToast();
  const cache = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    watch,
    setValue,
    setError: fieldError,
    formState: { errors },
  } = useForm<Record<string, string>>({
    defaultValues: defaults(resource, record),
    resolver: zodResolver(definition.schema),
  });
  const values = watch();
  const mutation = useMutation({
    gcTime: 0,
    mutationFn: (data: Record<string, string>) =>
      record && resource !== 'accounts'
        ? resources.update(
            resource,
            record.id,
            definition.payload(data) as Requests[Exclude<Resource, 'accounts'>],
          )
        : resources.create(resource, definition.payload(data)),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: [resource] });
      await cache.invalidateQueries({ queryKey: ['faculty-finder'] });
      await cache.invalidateQueries({ queryKey: ['timetable'] });
      if (resource === 'faculty') await cache.invalidateQueries({ queryKey: ['directory'] });
      await cache.invalidateQueries({ queryKey: ['me'] });
      notify(
        record
          ? 'Changes saved.'
          : `${definition.singular[0].toUpperCase() + definition.singular.slice(1)} created.`,
      );
      onClose();
    },
    onError: (problem) => {
      setError(errorMessage(problem));
      if (problem instanceof ApiError)
        Object.entries(problem.fieldErrors).forEach(([key, message]) =>
          fieldError(key, { message }),
        );
    },
  });
  return (
    <Dialog
      title={`${record ? 'Edit' : 'Create'} ${definition.singular}`}
      onClose={onClose}
      busy={mutation.isPending}
    >
      <form
        className="entity-form"
        noValidate
        onSubmit={handleSubmit((data) => {
          setError('');
          mutation.mutate(data);
        })}
      >
        <div className="form-grid">
          {definition.fields
            .filter((field) => !field.when || values[field.when.field] === field.when.value)
            .map((field) => {
              const id = `edit-${field.name}`;
              const errorId = errors[field.name] ? `${id}-error` : undefined;
              return (
                <div
                  className={`field ${field.type === 'textarea' ? 'span-full' : ''}`}
                  key={field.name}
                >
                  <label htmlFor={id}>
                    {field.label}
                    {field.optional && <span className="muted"> · optional</span>}
                  </label>
                  {field.type === 'relation' && field.resource ? (
                    <Controller
                      name={field.name}
                      control={control}
                      render={({ field: input }) => (
                        <RelationshipPicker
                          key={`${field.name}-${field.name === 'batchId' ? values.departmentId : field.name === 'sectionId' ? values.batchId : ''}`}
                          resource={field.resource!}
                          label={field.label}
                          id={id}
                          errorId={errorId}
                          optional={field.optional}
                          value={input.value}
                          params={
                            field.name === 'batchId' && values.departmentId
                              ? { departmentId: values.departmentId }
                              : field.name === 'sectionId' && values.batchId
                                ? { batchId: values.batchId }
                                : {}
                          }
                          disabled={
                            resource === 'students' &&
                            ((field.name === 'batchId' && !values.departmentId) ||
                              (field.name === 'sectionId' && !values.batchId))
                          }
                          onChange={(value) => {
                            input.onChange(value);
                            if (field.name === 'departmentId') {
                              setValue('batchId', '');
                              setValue('sectionId', '');
                            }
                            if (field.name === 'batchId') setValue('sectionId', '');
                          }}
                        />
                      )}
                    />
                  ) : field.type === 'select' ? (
                    <select
                      id={id}
                      {...register(field.name)}
                      aria-describedby={errorId}
                      aria-invalid={!!errorId}
                    >
                      {field.options?.map((option) => (
                        <option value={option.value} key={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  ) : field.type === 'textarea' ? (
                    <textarea
                      id={id}
                      rows={3}
                      {...register(field.name)}
                      aria-describedby={errorId}
                      aria-invalid={!!errorId}
                    />
                  ) : (
                    <input
                      id={id}
                      type={field.type ?? 'text'}
                      {...register(field.name)}
                      readOnly={!!record && field.immutable}
                      aria-describedby={errorId}
                      aria-invalid={!!errorId}
                      autoComplete={field.type === 'password' ? 'new-password' : undefined}
                      min={field.type === 'number' ? 1 : undefined}
                    />
                  )}{' '}
                  {!!record && field.immutable && (
                    <small className="muted">Enrollment numbers cannot be changed.</small>
                  )}
                  {field.type === 'password' && (
                    <small className="muted">
                      12+ characters, at most 72 UTF-8 bytes. Share privately.
                    </small>
                  )}
                  {errors[field.name] && (
                    <small id={errorId} role="alert">
                      {errors[field.name]?.message}
                    </small>
                  )}
                </div>
              );
            })}
        </div>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <footer className="dialog-actions">
          <button
            type="button"
            className="button secondary"
            onClick={onClose}
            disabled={mutation.isPending}
          >
            Cancel
          </button>
          <button className="button primary" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : record ? 'Save changes' : 'Create record'}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
