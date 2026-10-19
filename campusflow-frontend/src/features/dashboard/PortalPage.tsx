import { Link } from 'react-router';
import { ArrowUpRight, DoorOpen, GraduationCap, LifeBuoy, Users } from 'lucide-react';
import { useAuth } from '../auth/authContext';
import { useOwnProfile } from '../profile/useOwnProfile';
import { roleHome } from '../../services/apiClient';
import { ErrorState, LoadingSkeleton, PageHeader } from '../../components/ui/feedback';
export function PortalPage() {
  const { user } = useAuth();
  const query = useOwnProfile();
  const home = roleHome(user!.account.role);
  if (query.isPending) return <LoadingSkeleton label="Loading your campus workspace…" />;
  if (query.isError) return <ErrorState error={query.error} retry={() => void query.refetch()} />;
  const name = (query.data.student ?? query.data.faculty)?.fullName ?? 'there';
  return (
    <>
      <PageHeader
        eyebrow={`${user!.account.role} WORKSPACE`}
        title={`Welcome, ${name}.`}
        description="Your people, your profile, your campus. All a little closer."
      />
      <section className="welcome-banner">
        <div>
          <span className="banner-tag">
            <span />
            YOUR CAMPUS COMPANION
          </span>
          <h2>
            A place to belong.
            <br />A space to grow.
          </h2>
          <p>
            Keep your academic identity close and connect <br />
            with the people who support your journey.
          </p>
          <Link className="button banner-button" to={`${home}/profile`}>
            Explore my profile
            <ArrowUpRight size={17} />
          </Link>
        </div>
        <GraduationCap className="portal-art" aria-hidden="true" />
      </section>
      <div className="portal-links">
        <Link className="panel portal-link" to={`${home}/profile`}>
          <GraduationCap size={28} />
          <h2>My campus profile</h2>
          <p className="muted">Your account and academic information, kept together.</p>
          <span>
            View my profile
            <ArrowUpRight size={17} />
          </span>
        </Link>
        <Link
          className="panel portal-link"
          to={
            user!.account.role === 'FACULTY' ? '/faculty/directory' : '/student/faculty-directory'
          }
        >
          <Users size={28} />
          <h2>Meet your faculty</h2>
          <p className="muted">Explore the permitted faculty directory across campus.</p>
          <span>
            Open directory
            <ArrowUpRight size={17} />
          </span>
        </Link>
      </div>
      <div className="portal-links">
        <Link className="panel portal-link" to={home + '/timetable'}>
          <h2>My timetable</h2>
          <p>Published teaching sessions in campus time.</p>
        </Link>
        <Link className="panel portal-link" to="/app/faculty-finder">
          <h2>Faculty Finder</h2>
          <p>Find faculty and evaluate known schedule availability.</p>
        </Link>
      </div>
      <div className="section-heading">
        <h2>A little further ahead</h2>
        <span>Upcoming · not available yet</span>
      </div>
      <div className="future-grid">
        {[
          {
            icon: DoorOpen,
            title: 'Classroom Availability',
            text: 'Find the right space at the right time.',
          },
          {
            icon: LifeBuoy,
            title: 'Campus Helpdesk',
            text: 'Get support for the everyday questions.',
          },
        ].map((item) => (
          <section className="panel future-card" key={item.title}>
            <item.icon size={23} />
            <span className="upcoming">Upcoming</span>
            <h3>{item.title}</h3>
            <p>{item.text}</p>
          </section>
        ))}
      </div>
    </>
  );
}
