import { FormEvent, useState } from 'react';
import { ArrowLeft, KeyRound } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../services/api';

function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState(token ? '' : 'This reset link is missing its token.');
  const [loading, setLoading] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setMessage('');
    setError('');
    if (password.length < 12) {
      setError('Password must be at least 12 characters.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    setLoading(true);
    try {
      await api.resetPassword(token, password);
      setMessage('Your password has been changed. You can now sign in.');
      setPassword('');
      setConfirmPassword('');
    } catch {
      setError('This reset link is invalid or expired. Request a new one.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-shell flex min-h-screen items-center justify-center px-4 py-8 sm:px-6">
      <section className="glass-panel w-full max-w-md p-6 sm:p-10">
        <div className="mb-8 flex items-center gap-3">
          <div className="rounded-2xl bg-stone-900 p-3 text-white shadow-lg"><KeyRound className="h-5 w-5" /></div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-stone-500">Account recovery</p>
            <h1 className="text-2xl font-extrabold text-stone-900">Choose a new password</h1>
          </div>
        </div>
        {message && <div className="mb-5 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">{message}</div>}
        {error && <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}
        <form onSubmit={submit} className="space-y-4">
          <input required minLength={12} type="password" autoComplete="new-password" placeholder="New password (12+ characters)" value={password} onChange={(event) => setPassword(event.target.value)} disabled={!token || Boolean(message)} />
          <input required minLength={12} type="password" autoComplete="new-password" placeholder="Confirm new password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} disabled={!token || Boolean(message)} />
          <button disabled={loading || !token || Boolean(message)} className="btn-primary w-full">{loading ? 'Updating password...' : 'Update password'}</button>
        </form>
        <Link className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-stone-900 underline" to="/login"><ArrowLeft className="h-4 w-4" />Back to sign in</Link>
      </section>
    </div>
  );
}

export default ResetPassword;
