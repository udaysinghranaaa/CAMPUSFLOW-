import { useQueries } from '@tanstack/react-query';
import { Link } from 'react-router';
import {
  ArrowDownRight,
  ArrowUpRight,
  BookOpen,
  Building2,
  GraduationCap,
  Layers,
  ShieldCheck,
  Users,
} from 'lucide-react';
import type { Resource } from '../../types/api';
import { resources } from '../../services/resources';
import { useAuth } from '../auth/authContext';
import { PageHeader } from '../../components/ui/feedback';
const cards = [
  { key: 'departments', label: 'Departments', icon: Building2, color: 'blue' },
  { key: 'faculty', label: 'Faculty members', icon: Users, color: 'teal' },
  { key: 'students', label: 'Students', icon: GraduationCap, color: 'purple' },
  { key: 'batches', label: 'Academic batches', icon: BookOpen, color: 'amber' },
  { key: 'sections', label: 'Sections', icon: Layers, color: 'teal' },
  { key: 'accounts', label: 'Accounts', icon: ShieldCheck, color: 'blue' },
] satisfies { key: Resource; label: string; icon: typeof Users; color: string }[];
export function DashboardPage() {
  const { user } = useAuth();
  const queries = useQueries({
    queries: cards.map((card) => ({
      queryKey: [card.key, 'summary'],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        resources.list(card.key, { page: 0, size: 1 }, signal),
    })),
  });
  return (
    <>
      <PageHeader
        eyebrow="YOUR CAMPUS AT A GLANCE"
        title="A clearer view. A better day."
        description="Welcome back. Here’s what’s connected across your campus."
      />
      <section className="welcome-banner">
        <div>
          <span className="banner-tag">
            <span />
            ADMIN WORKSPACE
          </span>
          <h2>
            Make room for
            <br />
            what matters.
          </h2>
          <p>
            Bring your people and academic operations together. <br />
            One organized campus. More possibilities.
          </p>
          <Link className="button banner-button" to="/admin/students">
            Explore students
            <ArrowUpRight size={17} />
          </Link>
        </div>
        <div className="banner-art" aria-hidden="true">
          <div className="art-card art-back">
            <Building2 size={32} />
            <span>Connected departments</span>
          </div>
          <div className="art-card art-front">
            <GraduationCap size={40} />
            <span>People at the center</span>
            <div className="art-lines">
              <i />
              <i />
              <i />
            </div>
          </div>
          <span className="art-spark">✦</span>
          <div className="art-orbit" />
        </div>
      </section>
      <div className="section-heading">
        <h2>Your campus, in numbers</h2>
        <span>Live totals from your campus API</span>
      </div>
      <section className="stats-grid" aria-label="Campus totals">
        {cards.map((card, index) => {
          const query = queries[index];
          return (
            <Link className="stat-card" key={card.key} to={`/admin/${card.key}`}>
              <div className="stat-top">
                <span className={`stat-icon ${card.color}`}>
                  <card.icon size={22} />
                </span>
                <ArrowUpRight size={17} />
              </div>
              <strong>
                {query.isPending ? (
                  <span className="count-skeleton" />
                ) : query.isError ? (
                  '—'
                ) : (
                  query.data.totalElements.toLocaleString()
                )}
              </strong>
              <span>{card.label}</span>
              <small>
                {query.isError
                  ? 'Unavailable · open to retry'
                  : query.isPending
                    ? 'Loading total…'
                    : 'View all records'}
              </small>
            </Link>
          );
        })}
      </section>
      <div className="dashboard-bottom">
        <section className="panel">
          <div className="section-heading">
            <h2>Start with your people</h2>
            <Users size={20} />
          </div>
          <p className="muted">Good campus experiences begin with the right connections.</p>
          <div className="shortcut-list">
            <Link to="/admin/faculty">
              <span className="shortcut-icon">
                <Users size={20} />
              </span>
              <span>
                <strong>Manage faculty</strong>
                <small>Profiles, departments, and account links</small>
              </span>
              <ArrowUpRight size={18} />
            </Link>
            <Link to="/admin/students">
              <span className="shortcut-icon">
                <GraduationCap size={20} />
              </span>
              <span>
                <strong>Manage students</strong>
                <small>Enrollment, cohorts, and academic status</small>
              </span>
              <ArrowUpRight size={18} />
            </Link>
            <Link to="/admin/accounts">
              <span className="shortcut-icon">
                <ShieldCheck size={20} />
              </span>
              <span>
                <strong>Manage access</strong>
                <small>Provision the right role for each person</small>
              </span>
              <ArrowUpRight size={18} />
            </Link>
          </div>
        </section>
        <section className="panel workspace-card">
          <span className="eyebrow">CONNECTED BY DESIGN</span>
          <h2>
            A place for every
            <br />
            part of your campus.
          </h2>
          <div className="connection-flow">
            <span>Department</span>
            <ArrowDownRight size={18} />
            <span>Batch</span>
            <ArrowDownRight size={18} />
            <span>Section &amp; student</span>
          </div>
          <p className="muted">
            Academic relationships keep records consistent. Dependencies are checked before changes
            are saved.
          </p>
          <p className="footnote">Signed in as {user!.account.email}</p>
        </section>
      </div>
    </>
  );
}
