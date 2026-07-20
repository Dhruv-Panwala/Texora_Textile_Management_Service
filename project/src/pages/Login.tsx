import React, { useState } from 'react';
import { ArrowRight, LockKeyhole, ShieldCheck } from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../services/api';

type Props = {
  onAuthenticated: () => void;
};

function Login({ onAuthenticated }: Props) {
  const publicSignupEnabled = import.meta.env.VITE_PUBLIC_SIGNUP_ENABLED !== 'false';
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.login(username.trim(), password);
      onAuthenticated();
      const returnTo = searchParams.get('returnTo');
      navigate(returnTo && returnTo.startsWith('/') ? returnTo : '/', { replace: true });
    } catch {
      setError('Login failed. Check your username and password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-shell relative flex min-h-screen items-center overflow-hidden px-4 py-8 sm:px-6 lg:px-10">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(189,120,61,0.18),transparent_22%),radial-gradient(circle_at_82%_12%,rgba(74,117,96,0.16),transparent_24%),radial-gradient(circle_at_50%_90%,rgba(189,120,61,0.12),transparent_18%)]" />
      <div className="relative mx-auto grid w-full max-w-6xl gap-8 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="hidden lg:flex lg:flex-col lg:justify-between lg:py-10">
          <div>
            <span className="stat-chip">Textile business operating system</span>
            <h1 className="mt-6 max-w-xl font-['Fraunces'] text-5xl font-bold leading-[1.05] text-stone-900">
              Billing, parties, challans, and payments in one calm workspace.
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-8 text-stone-600">
              Built for daily textile operations with a cleaner, faster workflow that works well on the office laptop and on the go.
            </p>
          </div>

          <div className="grid max-w-2xl grid-cols-3 gap-4">
            {[
              { title: 'Sales & Bills', note: 'Manage challans, invoices, and taka entries' },
              { title: 'Company Switch', note: 'Separate records cleanly by business identity' },
              { title: 'Payment Follow-up', note: 'Track overdue receivables and payables' },
            ].map((item) => (
              <div key={item.title} className="glass-panel p-5">
                <p className="text-sm font-bold uppercase tracking-[0.16em] text-stone-500">{item.title}</p>
                <p className="mt-3 text-sm leading-6 text-stone-700">{item.note}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="glass-panel relative overflow-hidden p-6 sm:p-8 lg:p-10">
          <div className="absolute right-0 top-0 h-32 w-32 rounded-full bg-amber-200/30 blur-3xl" />
          <div className="relative">
            <div className="mb-8 flex items-center gap-3">
              <div className="rounded-2xl bg-stone-900 p-3 text-white shadow-lg">
                <ShieldCheck className="h-5 w-5" />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-stone-500">Secure access</p>
                <h2 className="text-2xl font-extrabold text-stone-900">Sign in to continue</h2>
              </div>
            </div>

            <div className="mb-6 lg:hidden">
              <h1 className="font-['Fraunces'] text-3xl font-bold text-stone-900">Devashish Textile</h1>
              <p className="mt-2 text-sm leading-6 text-stone-600">Open your workspace from phone or laptop with the same clean workflow.</p>
            </div>

            {error && (
              <div className="mb-5 rounded-2xl border border-red-200 bg-red-50/90 px-4 py-3 text-sm font-medium text-red-700">
                {error}
              </div>
            )}

            <form onSubmit={submit} className="space-y-4">
              <label className="block">
                <span className="mb-2 block text-sm font-semibold text-stone-700">Username</span>
                <input
                  autoComplete="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                />
              </label>

              <label className="block">
                <span className="mb-2 block text-sm font-semibold text-stone-700">Password</span>
                <input
                  autoComplete="current-password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </label>

              <button disabled={loading} className="btn-primary w-full">
                <LockKeyhole className="h-4 w-4" />
                <span>{loading ? 'Signing in...' : 'Enter Workspace'}</span>
                {!loading && <ArrowRight className="h-4 w-4" />}
              </button>
            </form>
            <p className="mt-6 text-center text-sm text-stone-600">
              <Link className="font-semibold text-stone-900 underline" to="/forgot-password">Forgot password?</Link>
            </p>
            {publicSignupEnabled && <p className="mt-3 text-center text-sm text-stone-600">
              New here? <Link className="font-semibold text-stone-900 underline" to="/signup">Create an account</Link>
            </p>}
          </div>
        </section>
      </div>
    </div>
  );
}

export default Login;
