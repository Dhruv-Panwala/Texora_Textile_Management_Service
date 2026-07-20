import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../services/api';

type Props = { onAuthenticated: () => void };

function AcceptInvitation({ onAuthenticated }: Props) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') || '';
  const [checkingSession, setCheckingSession] = useState(true);
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loginUrl, setLoginUrl] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!token) {
      setError('Invitation token is missing.');
      setCheckingSession(false);
      return;
    }
    let active = true;
    api.acceptInvitationAsExisting(token)
      .then(() => {
        if (active) {
          onAuthenticated();
          navigate('/', { replace: true });
        }
      })
      .catch((err) => {
        if (!active) return;
        const message = err instanceof Error ? err.message : '';
        if (message.includes('401')) {
          setCheckingSession(false);
          return;
        }
        setError(message || 'This invitation is invalid or cannot be accepted.');
        setCheckingSession(false);
      });
    return () => {
      active = false;
    };
  }, [navigate, onAuthenticated, token]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return setError('Invitation token is missing.');
    if (password !== confirmPassword) return setError('Passwords do not match.');
    setSaving(true);
    setError('');
    try {
      await api.acceptInvitation({ token, displayName, password });
      onAuthenticated();
      navigate('/', { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not accept invitation';
      if (message.includes('An account already exists')) {
        const returnTo = `/accept-invitation?token=${encodeURIComponent(token)}`;
        setError('An account already exists for this email. Sign in first, then this invitation will be accepted automatically.');
        setDisplayName('');
        setPassword('');
        setConfirmPassword('');
        setLoginUrl(`/login?returnTo=${encodeURIComponent(returnTo)}`);
      } else {
        setError(message);
      }
    } finally {
      setSaving(false);
    }
  };

  if (checkingSession) {
    return <div className="app-shell flex min-h-screen items-center justify-center px-4"><div className="glass-panel p-8 text-center"><h1 className="page-title text-3xl">Checking invitation...</h1><p className="page-subtitle">If you are already signed in, we will add you to the workspace.</p></div></div>;
  }

  return <div className="app-shell min-h-screen flex items-center justify-center px-4"><form onSubmit={submit} className="form-surface w-full max-w-md space-y-4"><div><h1 className="page-title text-3xl">Join workspace</h1><p className="page-subtitle">Create your account to accept this invitation.</p></div>{error && <div className="rounded border border-red-200 bg-red-50 p-2 text-sm text-red-700">{error}{loginUrl && <><br /><Link className="font-semibold underline" to={loginUrl}>Sign in to continue</Link></>}</div>}<input required className="w-full border rounded-md px-3 py-2" placeholder="Display name" value={displayName} onChange={(event) => setDisplayName(event.target.value)} /><input required minLength={12} type="password" className="w-full border rounded-md px-3 py-2" placeholder="Password (12+ characters)" value={password} onChange={(event) => setPassword(event.target.value)} /><input required minLength={12} type="password" className="w-full border rounded-md px-3 py-2" placeholder="Confirm password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} /><button disabled={saving || !token} className="btn-primary w-full disabled:opacity-50">{saving ? 'Joining...' : 'Accept Invitation'}</button></form></div>;
}

export default AcceptInvitation;
