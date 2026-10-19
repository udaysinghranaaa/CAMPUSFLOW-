import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import { changePasswordSchema } from './schemas';
import { useAuth } from './authContext';
import { errorMessage } from '../../services/apiClient';
import { PageHeader } from '../../components/ui/feedback';
export function PasswordPage() {
  const { client } = useAuth();
  const [error, setError] = useState('');
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof changePasswordSchema>>({
    resolver: zodResolver(changePasswordSchema),
  });
  return (
    <>
      <PageHeader
        eyebrow="ACCOUNT SECURITY"
        title="Change password"
        description="A fresh password. A safer workspace."
      />
      <section className="panel narrow">
        <p className="muted">
          Use at least 12 characters and at most 72 UTF-8 bytes. After changing your password, sign
          in again. Your other sessions will also end.
        </p>
        <form
          noValidate
          onSubmit={handleSubmit(async (values) => {
            setError('');
            try {
              await client.changePassword(values.currentPassword, values.newPassword);
            } catch (problem) {
              setError(errorMessage(problem));
            }
          })}
        >
          {(['currentPassword', 'newPassword', 'confirmPassword'] as const).map((name, index) => (
            <div className="field" key={name}>
              <label htmlFor={name}>
                {['Current password', 'New password', 'Confirm new password'][index]}
              </label>
              <input
                id={name}
                type="password"
                autoComplete={index === 0 ? 'current-password' : 'new-password'}
                aria-invalid={!!errors[name]}
                aria-describedby={errors[name] ? `${name}-error` : undefined}
                {...register(name)}
              />
              {errors[name] && (
                <small id={`${name}-error`} role="alert">
                  {errors[name]?.message}
                </small>
              )}
            </div>
          ))}
          {error && (
            <p role="alert" className="form-error">
              {error}
            </p>
          )}
          <button className="button primary" disabled={isSubmitting}>
            {isSubmitting ? 'Updating…' : 'Update password'}
          </button>
        </form>
      </section>
    </>
  );
}
