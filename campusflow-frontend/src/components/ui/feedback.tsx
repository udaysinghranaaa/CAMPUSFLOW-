import { useLayoutEffect, useRef, useState, useId } from 'react';
import type { ReactNode } from 'react';
import { AlertCircle, CheckCircle2, Inbox, X } from 'lucide-react';
import { errorMessage } from '../../services/apiClient';
import { ToastContext } from './toastContext';
export function LoadingSkeleton({ label = 'Loading your workspace…' }: { label?: string }) {
  return (
    <div role="status" className="loading-state">
      <span className="spinner" />
      {label}
      <div className="skeleton" />
      <div className="skeleton short" />
    </div>
  );
}
export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  return (
    <div role="alert" className="state error-state">
      <AlertCircle size={28} />
      <h3>We couldn’t load this</h3>
      <p>{errorMessage(error)}</p>
      {retry && (
        <button className="button secondary" onClick={retry}>
          Try again
        </button>
      )}
    </div>
  );
}
export function EmptyState({
  title = 'Nothing here yet',
  description = 'Add your first record to get started.',
}: {
  title?: string;
  description?: string;
}) {
  return (
    <div className="state">
      <Inbox size={32} />
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}
export function StatusBadge({ value }: { value: string | boolean }) {
  const active = value === true || value === 'ACTIVE' || value === 'Enabled';
  return (
    <span
      className={`badge ${active ? 'positive' : value === 'SUSPENDED' ? 'negative' : 'neutral'}`}
    >
      <span />
      {typeof value === 'boolean'
        ? value
          ? 'Active'
          : 'Inactive'
        : value.toLowerCase().replaceAll('_', ' ')}
    </span>
  );
}
export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="muted">{description}</p>}
      </div>
      {action}
    </header>
  );
}
export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
}) {
  return (
    <nav className="pagination" aria-label="Pagination">
      <span>
        {totalElements.toLocaleString()} records · Page {totalPages === 0 ? 0 : page + 1} of{' '}
        {totalPages}
      </span>
      <div>
        <button
          className="button secondary small"
          disabled={page === 0}
          onClick={() => onPage(page - 1)}
        >
          Previous
        </button>
        <button
          className="button secondary small"
          disabled={page + 1 >= totalPages}
          onClick={() => onPage(page + 1)}
        >
          Next
        </button>
      </div>
    </nav>
  );
}
export function Dialog({
  title,
  children,
  onClose,
  busy = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useLayoutEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog?.showModal();
    return () => {
      dialog?.close();
      if (previous?.isConnected) previous.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className="dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className="dialog-header">
        <h2 id={titleId}>{title}</h2>
        <button className="icon-button" aria-label="Close dialog" disabled={busy} onClick={onClose}>
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
interface Notice {
  id: number;
  message: string;
  kind: 'success' | 'error';
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [notices, setNotices] = useState<Notice[]>([]);
  const next = useRef(0);
  const notify = (message: string, kind: 'success' | 'error' = 'success') =>
    setNotices((items) => [...items.slice(-3), { id: ++next.current, message, kind }]);
  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className="toasts" aria-label="Notifications">
        {notices.map((notice) => (
          <div
            key={notice.id}
            role={notice.kind === 'error' ? 'alert' : 'status'}
            className={`toast ${notice.kind}`}
          >
            {notice.kind === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
            <span>{notice.message}</span>
            <button
              className="icon-button"
              aria-label="Dismiss notification"
              onClick={() => setNotices((items) => items.filter((item) => item.id !== notice.id))}
            >
              <X size={16} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
