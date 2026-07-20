import { FormEvent, useState } from 'react';
import { ArrowLeft, Mail } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';

function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setMessage('');
    setError('');
    setLoading(true);
    try {
      const response = await api.forgotPassword(email.trim());
      setMessage(response.message);
    } catch {
      setError('We could not process that request right now. Please try again later.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-shell flex min-h-screen items-center justify-center px-4 py-8 sm:px-6">
      <section className="glass-panel w-full max-w-md p-6 sm:p-10">
        <div className="mb-8 flex items-center gap-3">
          <div className="rounded-2xl bg-stone-900 p-3 text-white shadow-lg"><Mail className="h-5 w-5" /></div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-stone-500">Account recovery</p>
            <h1 className="text-2xl font-extrabold text-stone-900">Forgot password?</h1>
          </div>
        </div>
        {message && <div className="mb-5 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">{message}</div>}
        {error && <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}
        <p className="mb-6 text-sm leading-6 text-stone-600">Enter your account email and we will send a one-time reset link.</p>
        <form onSubmit={submit} className="space-y-4">
          <input required type="email" autoComplete="email" placeholder="Email address" value={email} onChange={(event) => setEmail(event.target.value)} />
          <button disabled={loading} className="btn-primary w-full">{loading ? 'Sending link...' : 'Send reset link'}</button>
        </form>
        <Link className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-stone-900 underline" to="/login"><ArrowLeft className="h-4 w-4" />Back to sign in</Link>
      </section>
    </div>
  );
}

export default ForgotPassword;
