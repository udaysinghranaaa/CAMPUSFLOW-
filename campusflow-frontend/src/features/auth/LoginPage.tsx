import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router';
import { ArrowUpRight, Eye, EyeOff, GraduationCap, ShieldCheck, Workflow } from 'lucide-react';
import type { z } from 'zod';
import { useAuth } from './authContext';
import { loginSchema } from './schemas';
import { errorMessage, roleHome } from '../../services/apiClient';
export function LoginPage() {
  const { client, message } = useAuth();
  const navigate = useNavigate();
  const [visible, setVisible] = useState(false);
  const [error, setError] = useState('');
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema) });
  const submit = handleSubmit(async (data) => {
    setError('');
    try {
      const me = await client.login(data.email, data.password);
      navigate(roleHome(me.account.role), { replace: true });
    } catch (problem) {
      setError(errorMessage(problem));
    }
  });
  return (
    <div className="login-page">
      <section className="login-story">
        <a className="brand" href="/login">
          <span className="brand-icon">
            <GraduationCap />
          </span>
          CampusFlow<span className="brand-dot">.</span>
        </a>
        <div className="story-content">
          <span className="story-tag">
            <span />
            Your campus, connected
          </span>
          <h1>
            Less admin.
            <br />
            More <em>possibility.</em>
          </h1>
          <p>
            One connected workspace for the people, departments, and ideas that move your campus
            forward.
          </p>
          <div className="campus-visual" aria-hidden="true">
            <div className="orbit orbit-one" />
            <div className="orbit orbit-two" />
            <div className="campus-core">
              <GraduationCap size={54} />
            </div>
            <span className="visual-node node-one">
              <Workflow size={20} />
              Academic operations
            </span>
            <span className="visual-node node-two">
              <ShieldCheck size={20} />
              Built around your role
            </span>
            <div className="visual-grid" />
          </div>
        </div>
        <footer>Smart Campus Management &amp; Student Assistance Platform</footer>
      </section>
      <section className="login-form-area">
        <div className="login-form">
          <span className="eyebrow">WELCOME TO YOUR WORKSPACE</span>
          <h2>A good day starts here.</h2>
          <p className="muted">Sign in with your campus account to continue.</p>
          {message && (
            <p className="info-banner" role="status">
              {message}
            </p>
          )}
          <form onSubmit={submit} noValidate>
            <div className="field">
              <label htmlFor="email">Email address</label>
              <input
                id="email"
                type="email"
                autoComplete="username"
                placeholder="you@your-campus.edu"
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? 'email-error' : undefined}
                {...register('email')}
              />
              {errors.email && (
                <small id="email-error" role="alert">
                  {errors.email.message}
                </small>
              )}
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <div className="password-input">
                <input
                  id="password"
                  type={visible ? 'text' : 'password'}
                  autoComplete="current-password"
                  aria-invalid={!!errors.password}
                  aria-describedby={errors.password ? 'password-error' : undefined}
                  {...register('password')}
                />
                <button
                  type="button"
                  className="icon-button"
                  aria-label={visible ? 'Hide password' : 'Show password'}
                  onClick={() => setVisible(!visible)}
                >
                  {visible ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {errors.password && (
                <small id="password-error" role="alert">
                  {errors.password.message}
                </small>
              )}
            </div>
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            <button className="button primary login-submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <span className="spinner" />
                  Signing in…
                </>
              ) : (
                <>
                  Sign in to CampusFlow
                  <ArrowUpRight size={18} />
                </>
              )}
            </button>
          </form>
          <p className="login-help">
            Need an account or a password reset?
            <br />
            <strong>Contact your campus administrator.</strong>
          </p>
          <div className="session-note">
            <ShieldCheck size={18} />
            <p>
              Private by design. For your security, sign in again after reloading or closing this
              tab.
            </p>
          </div>
        </div>
        <footer>CampusFlow · A little more connected.</footer>
      </section>
    </div>
  );
}
