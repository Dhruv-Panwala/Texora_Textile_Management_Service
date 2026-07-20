import React, { useState } from 'react';
import { ArrowRight, UserPlus } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../services/api';

type Props = {
  onAuthenticated: () => void;
};

function Signup({ onAuthenticated }: Props) {
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '', displayName: '', workspaceName: '', businessName: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.signup(form);
      onAuthenticated();
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create account');
    } finally {
      setLoading(false);
    }
  };

  const update = (field: keyof typeof form, value: string) => setForm({ ...form, [field]: value });

  return (
    <div className="app-shell flex min-h-screen items-center justify-center px-4 py-8 sm:px-6">
      <section className="glass-panel w-full max-w-xl p-6 sm:p-10">
        <div className="mb-8 flex items-center gap-3">
          <div className="rounded-2xl bg-stone-900 p-3 text-white shadow-lg"><UserPlus className="h-5 w-5" /></div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-stone-500">Create workspace</p>
            <h1 className="text-2xl font-extrabold text-stone-900">Start your textile workspace</h1>
          </div>
        </div>

        {error && <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}

        <form onSubmit={submit} className="space-y-4">
          <input required type="text" autoComplete="name" placeholder="Your name" value={form.displayName} onChange={(e) => update('displayName', e.target.value)} />
          <input required type="email" autoComplete="email" placeholder="Email address" value={form.email} onChange={(e) => update('email', e.target.value)} />
          <input required minLength={12} type="password" autoComplete="new-password" placeholder="Password (at least 12 characters)" value={form.password} onChange={(e) => update('password', e.target.value)} />
          <input required type="text" placeholder="Workspace or group name" value={form.workspaceName} onChange={(e) => update('workspaceName', e.target.value)} />
          <input required type="text" placeholder="First business name" value={form.businessName} onChange={(e) => update('businessName', e.target.value)} />

          <button disabled={loading} className="btn-primary w-full">
            <span>{loading ? 'Creating workspace...' : 'Create account'}</span>
            {!loading && <ArrowRight className="h-4 w-4" />}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-stone-600">
          Already have an account? <Link className="font-semibold text-stone-900 underline" to="/login">Sign in</Link>
        </p>
      </section>
    </div>
  );
}

export default Signup;
