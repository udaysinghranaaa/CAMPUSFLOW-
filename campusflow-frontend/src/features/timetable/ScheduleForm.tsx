import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { Resource } from '../../types/api';
import { Dialog } from '../../components/ui/feedback';
import { useToast } from '../../components/ui/toastContext';
import { ApiError, errorMessage } from '../../services/apiClient';
import { RelationshipPicker } from '../management/RelationshipPicker';
export interface ScheduleField {
  name: string;
  label: string;
  type?: 'date' | 'time' | 'number' | 'text' | 'select' | 'relation' | 'days';
  resource?: Resource;
  params?: Record<string, string>;
  options?: (string | { value: string; label: string })[];
  optional?: boolean;
  disabled?: boolean;
}
export function ScheduleForm({
  title,
  defaults,
  schema,
  fields,
  onSubmit,
  onClose,
  note,
}: {
  title: string;
  defaults: Record<string, string>;
  schema: z.ZodType<Record<string, string>, Record<string, string>>;
  fields: (values: Record<string, string>) => ScheduleField[];
  onSubmit: (values: Record<string, string>) => Promise<unknown>;
  onClose: () => void;
  note?: string;
}) {
  const [error, setError] = useState('');
  const notify = useToast();
  const {
    register,
    control,
    watch,
    setValue,
    setError: fieldError,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Record<string, string>>({ defaultValues: defaults, resolver: zodResolver(schema) });
  const values = watch();
  return (
    <Dialog title={title} onClose={onClose} busy={isSubmitting}>
      {note && <p className="info-banner">{note}</p>}
      <form
        noValidate
        onSubmit={handleSubmit(async (data) => {
          setError('');
          try {
            await onSubmit(data);
            notify('Schedule changes saved.');
            onClose();
          } catch (problem) {
            setError(errorMessage(problem));
            if (problem instanceof ApiError)
              Object.entries(problem.fieldErrors).forEach(([name, message]) =>
                fieldError(name, { message }),
              );
          }
        })}
      >
        <div className="form-grid">
          {fields(values).map((field) => (
            <div className="field" key={field.name}>
              <label htmlFor={`schedule-${field.name}`}>
                {field.label}
                {field.optional ? ' · optional' : ''}
              </label>
              {field.type === 'relation' && field.resource ? (
                <Controller
                  name={field.name}
                  control={control}
                  render={({ field: input }) => (
                    <RelationshipPicker
                      key={JSON.stringify([field.name, field.params])}
                      resource={field.resource!}
                      label={field.label}
                      id={`schedule-${field.name}`}
                      errorId={errors[field.name] ? `schedule-${field.name}-error` : undefined}
                      value={input.value}
                      disabled={field.disabled}
                      optional={field.optional}
                      params={field.params}
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
              ) : field.type === 'days' ? (
                <Controller
                  name={field.name}
                  control={control}
                  render={({ field: input }) => (
                    <div className="weekday-options" role="group" aria-label={field.label}>
                      {[
                        'Monday',
                        'Tuesday',
                        'Wednesday',
                        'Thursday',
                        'Friday',
                        'Saturday',
                        'Sunday',
                      ].map((label, index) => (
                        <label key={label}>
                          <input
                            type="checkbox"
                            checked={(Number(input.value) & (1 << index)) !== 0}
                            onChange={() =>
                              input.onChange(String(Number(input.value) ^ (1 << index)))
                            }
                          />
                          {label}
                        </label>
                      ))}
                    </div>
                  )}
                />
              ) : field.type === 'select' ? (
                <select
                  id={`schedule-${field.name}`}
                  {...register(field.name)}
                  aria-invalid={!!errors[field.name]}
                >
                  {field.options?.map((value) => (
                    <option
                      key={typeof value === 'string' ? value : value.value}
                      value={typeof value === 'string' ? value : value.value}
                    >
                      {typeof value === 'string' ? value : value.label}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  id={`schedule-${field.name}`}
                  type={field.type ?? 'text'}
                  {...register(field.name)}
                  aria-invalid={!!errors[field.name]}
                  aria-describedby={errors[field.name] ? `schedule-${field.name}-error` : undefined}
                />
              )}
              {errors[field.name] && (
                <small role="alert" id={`schedule-${field.name}-error`}>
                  {errors[field.name]?.message}
                </small>
              )}
            </div>
          ))}
        </div>
        {error && (
          <p role="alert" className="form-error">
            {error}
          </p>
        )}
        <footer className="dialog-actions">
          <button
            type="button"
            className="button secondary"
            disabled={isSubmitting}
            onClick={onClose}
          >
            Cancel
          </button>
          <button className="button primary" disabled={isSubmitting}>
            {isSubmitting ? 'Saving…' : 'Save changes'}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
