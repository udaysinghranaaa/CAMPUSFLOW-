import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import {
  BookOpen,
  Building2,
  ChevronRight,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  ShieldCheck,
  Sun,
  UserRound,
  Users,
  X,
  Layers,
  KeyRound,
} from 'lucide-react';
import { useAuth } from '../../features/auth/authContext';
import { roleHome, errorMessage } from '../../services/apiClient';
import { useToast } from '../ui/toastContext';
export function AppShell() {
  const { user, client } = useAuth();
  const role = user!.account.role;
  const home = roleHome(role);
  const location = useLocation();
  const notify = useToast();
  const [menu, setMenu] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [dark, setDark] = useState(() => {
    try {
      return localStorage.getItem('campusflow-theme') === 'dark';
    } catch {
      return false;
    }
  });
  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    try {
      localStorage.setItem('campusflow-theme', dark ? 'dark' : 'light');
    } catch {
      /* Preferences are optional. */
    }
  }, [dark]);
  useEffect(() => {
    if (!menu) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const sidebar = document.getElementById('navigation');
    const focusable = () =>
      Array.from(
        sidebar?.querySelectorAll<HTMLElement>('a[href],button:not([disabled])') ?? [],
      ).filter((element) => element.getClientRects().length > 0);
    focusable()[0]?.focus();
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        setMenu(false);
      }
      if (event.key === 'Tab') {
        const elements = focusable();
        const first = elements[0];
        const last = elements.at(-1);
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first?.focus();
        }
      }
    };
    const resize = () => {
      if (window.innerWidth > 850) setMenu(false);
    };
    document.addEventListener('keydown', keyboard);
    window.addEventListener('resize', resize);
    return () => {
      document.removeEventListener('keydown', keyboard);
      window.removeEventListener('resize', resize);
      previous?.focus();
    };
  }, [menu]);
  useEffect(() => {
    document.getElementById('main')?.focus();
  }, [location.pathname]);
  const links =
    role === 'ADMIN'
      ? [
          { to: '/admin', label: 'Overview', icon: LayoutDashboard },
          { to: '/admin/departments', label: 'Departments', icon: Building2 },
          { to: '/admin/faculty', label: 'Faculty', icon: Users },
          { to: '/admin/students', label: 'Students', icon: GraduationCap },
          { to: '/admin/batches', label: 'Academic batches', icon: BookOpen },
          { to: '/admin/sections', label: 'Sections', icon: Layers },
          { to: '/admin/accounts', label: 'Accounts & access', icon: ShieldCheck },
          { to: '/admin/subjects', label: 'Subjects', icon: BookOpen },
          { to: '/admin/rooms', label: 'Rooms & labs', icon: Building2 },
          { to: '/admin/terms', label: 'Academic terms', icon: Layers },
          { to: '/admin/timetable', label: 'Timetable builder', icon: BookOpen },
          { to: '/app/faculty-finder', label: 'Faculty Finder', icon: Users },
        ]
      : [
          { to: home, label: 'Overview', icon: LayoutDashboard },
          { to: home + '/timetable', label: 'My timetable', icon: BookOpen },
          { to: '/app/faculty-finder', label: 'Faculty Finder', icon: Users },
          { to: `${home}/profile`, label: 'My profile', icon: UserRound },
          {
            to: role === 'FACULTY' ? '/faculty/directory' : '/student/faculty-directory',
            label: 'Faculty directory',
            icon: Users,
          },
        ];
  const current =
    links.find((link) => link.to === location.pathname)?.label ??
    (location.pathname.endsWith('change-password') ? 'Change password' : 'My profile');
  const logout = async () => {
    setLoggingOut(true);
    try {
      await client.logout();
    } catch (problem) {
      notify(errorMessage(problem), 'error');
    } finally {
      setLoggingOut(false);
    }
  };
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      {menu && (
        <button
          className="sidebar-overlay"
          aria-label="Close navigation"
          onClick={() => setMenu(false)}
        />
      )}
      <aside id="navigation" className={`sidebar ${menu ? 'open' : ''}`}>
        <Link className="brand" to={home} onClick={() => setMenu(false)}>
          <span className="brand-icon">
            <GraduationCap />
          </span>
          CampusFlow<span className="brand-dot">.</span>
        </Link>
        <button
          className="icon-button mobile-close"
          aria-label="Close navigation"
          onClick={() => setMenu(false)}
        >
          <X />
        </button>
        <div className="workspace-label">
          YOUR WORKSPACE<span>{role.toLowerCase()} portal</span>
        </div>
        <nav aria-label="Main navigation">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end
              onClick={() => setMenu(false)}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              <link.icon size={19} />
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <span className="mini-mark">✦</span>
            <strong>A more connected campus.</strong>
            <p>Make space for the things that matter.</p>
          </div>
          <Link className="nav-link" to="/app/change-password" onClick={() => setMenu(false)}>
            <KeyRound size={18} />
            Account security
          </Link>
          <button
            className="nav-link logout-button"
            disabled={loggingOut}
            onClick={() => void logout()}
          >
            <LogOut size={18} />
            {loggingOut ? 'Signing out…' : 'Sign out'}
          </button>
        </div>
      </aside>
      <div className="main-shell" inert={menu}>
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="icon-button menu-toggle"
              aria-label="Open navigation"
              aria-expanded={menu}
              aria-controls="navigation"
              onClick={() => setMenu(!menu)}
            >
              <Menu />
            </button>
            <nav aria-label="Breadcrumb" className="breadcrumbs">
              <Link to={home}>Workspace</Link>
              <ChevronRight size={14} />
              <span>{current}</span>
            </nav>
          </div>
          <div className="topbar-actions">
            <button
              className="icon-button"
              onClick={() => setDark(!dark)}
              aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
            >
              {dark ? <Sun size={19} /> : <Moon size={19} />}
            </button>
            <details className="profile-menu">
              <summary aria-label="Open profile menu">
                <span className="avatar">{user!.account.email.slice(0, 2).toUpperCase()}</span>
                <span className="profile-summary">
                  <strong>
                    {user!.faculty?.fullName ?? user!.student?.fullName ?? 'Campus administrator'}
                  </strong>
                  <small>{role.toLowerCase()}</small>
                </span>
              </summary>
              <div className="profile-dropdown">
                <p>{user!.account.email}</p>
                <Link to="/app/profile">My profile</Link>
                <Link to="/app/change-password">Change password</Link>
                <button disabled={loggingOut} onClick={() => void logout()}>
                  Sign out
                </button>
              </div>
            </details>
          </div>
        </header>
        <main id="main" tabIndex={-1} key={location.pathname} className="main-content">
          <Outlet />
        </main>
        <footer className="app-footer">
          <span>CampusFlow</span>
          <span>Smart Campus Management &amp; Student Assistance Platform</span>
        </footer>
      </div>
    </div>
  );
}
