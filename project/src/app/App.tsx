import { useCallback, useEffect, useState } from 'react';
import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom';
import { Menu } from 'lucide-react';
import Sidebar from '../components/layout/Sidebar';
import Dashboard from '../pages/Dashboard';
import Purchases from '../pages/Purchases';
import Sales from '../pages/Sales';
import Payments from '../pages/Payments';
import Suppliers from '../pages/Suppliers';
import Customers from '../pages/Customers';
import Company from '../pages/Company';
import Login from '../pages/Login';
import Signup from '../pages/Signup';
import Members from '../pages/Members';
import AcceptInvitation from '../pages/AcceptInvitation';
import ForgotPassword from '../pages/ForgotPassword';
import ResetPassword from '../pages/ResetPassword';
import { AUTH_EXPIRED_EVENT, PERMISSION_DENIED_EVENT, api, clearCompanyId, getCompanyId, setCompanyId } from '../services/api';
import type { CompanyProfile } from '../types';

function App() {
  const publicSignupEnabled = import.meta.env.VITE_PUBLIC_SIGNUP_ENABLED !== 'false';
  const [authenticated, setAuthenticated] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [companies, setCompanies] = useState<CompanyProfile[]>([]);
  const [activeCompanyId, setActiveCompanyId] = useState<string>(getCompanyId() || '');
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [permissionMessage, setPermissionMessage] = useState('');

  useEffect(() => {
    const handleAuthExpired = () => {
      clearCompanyId();
      setCompanies([]);
      setActiveCompanyId('');
      setAuthenticated(false);
    };
    const handlePermissionDenied = () => {
      setPermissionMessage('You do not have permission to perform this action. Your current role is read-only.');
    };
    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    window.addEventListener(PERMISSION_DENIED_EVENT, handlePermissionDenied);
    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
      window.removeEventListener(PERMISSION_DENIED_EVENT, handlePermissionDenied);
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    api.me()
      .then(() => {
        if (!cancelled) {
          setAuthenticated(true);
          setAuthReady(true);
        }
      })
      .catch(() => {
        if (!cancelled) {
          clearCompanyId();
          setCompanies([]);
          setActiveCompanyId('');
          setAuthenticated(false);
          setAuthReady(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!authenticated || !authReady) {
      return;
    }

    let cancelled = false;
    api.getCompanyProfiles().then((loadedCompanies) => {
      if (cancelled) {
        return;
      }
      setCompanies(loadedCompanies);
      const activeId = getCompanyId();
      if (activeId && loadedCompanies.some((company) => String(company.id) === activeId)) {
        setActiveCompanyId(activeId);
        return;
      }
      const fallbackId = loadedCompanies[0] ? String(loadedCompanies[0].id) : '';
      if (fallbackId) {
        setCompanyId(fallbackId);
      } else {
        clearCompanyId();
      }
      setActiveCompanyId(fallbackId);
    });

    return () => {
      cancelled = true;
    };
  }, [authenticated, authReady]);

  const logout = () => {
    void api.logout().catch(() => undefined);
    clearCompanyId();
    setCompanies([]);
    setActiveCompanyId('');
    setAuthenticated(false);
    setMobileNavOpen(false);
  };

  const changeCompany = (companyId: string) => {
    setCompanyId(companyId);
    setActiveCompanyId(companyId);
    setMobileNavOpen(false);
  };

  const createCompany = async (tradeName: string) => {
    const created = await api.createCompanyProfile({ tradeName });
    setCompanies((current) => [...current, created].sort((left, right) => left.tradeName.localeCompare(right.tradeName)));
    setCompanyId(String(created.id));
    setActiveCompanyId(String(created.id));
  };

  const handleAuthenticated = useCallback(() => {
    setAuthenticated(true);
  }, []);

  if (!authReady) {
    return (
      <div className="app-shell flex min-h-screen items-center justify-center px-6 text-center">
        <div className="glass-panel max-w-md px-8 py-10">
          <p className="stat-chip mb-4">Preparing workspace</p>
          <h1 className="page-title text-3xl">Checking session...</h1>
          <p className="page-subtitle">Loading your textile workspace and restoring the last signed-in state.</p>
        </div>
      </div>
    );
  }

  return (
    <Router>
      {!authenticated ? (
        <Routes>
          <Route path="/login" element={<Login onAuthenticated={handleAuthenticated} />} />
          <Route path="/signup" element={publicSignupEnabled ? <Signup onAuthenticated={handleAuthenticated} /> : <Navigate to="/login" replace />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/accept-invitation" element={<AcceptInvitation onAuthenticated={handleAuthenticated} />} />
          <Route path="*" element={<Login onAuthenticated={handleAuthenticated} />} />
        </Routes>
      ) : (
        <div className="app-shell min-h-screen">
          <Sidebar
            companies={companies}
            activeCompanyId={activeCompanyId}
            onCompanyChange={changeCompany}
            onCreateCompany={createCompany}
            onLogout={logout}
            mobileOpen={mobileNavOpen}
            onCloseMobile={() => setMobileNavOpen(false)}
          />
          <div className="lg:pl-[20rem]">
            <header className="sticky top-0 z-30 border-b border-white/50 bg-white/65 backdrop-blur-xl lg:hidden">
              <div className="flex items-center justify-between px-4 py-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.2em] text-stone-500">Textile Management</p>
                  <h1 className="text-lg font-extrabold text-stone-900">Business Workspace</h1>
                </div>
                <button
                  type="button"
                  className="btn-secondary !rounded-full !p-3"
                  onClick={() => setMobileNavOpen(true)}
                  aria-label="Open navigation"
                >
                  <Menu className="h-5 w-5" />
                </button>
              </div>
            </header>

            <main key={activeCompanyId || 'default'} className="mobile-safe px-4 py-4 sm:px-6 sm:py-6 lg:px-8 lg:py-8">
              {permissionMessage && <div role="alert" className="mb-4 flex items-center justify-between gap-4 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-900"><span>{permissionMessage}</span><button type="button" className="text-amber-700 underline" onClick={() => setPermissionMessage('')}>Dismiss</button></div>}
              <Routes>
                <Route path="/" element={<Dashboard />} />
                <Route path="/purchases" element={<Purchases />} />
                <Route path="/sales" element={<Sales />} />
                <Route path="/payments" element={<Payments />} />
                <Route path="/suppliers" element={<Suppliers />} />
                <Route path="/customers" element={<Customers />} />
                <Route path="/company" element={<Company />} />
                <Route path="/members" element={<Members />} />
                <Route path="/accept-invitation" element={<AcceptInvitation onAuthenticated={handleAuthenticated} />} />
                <Route path="/forgot-password" element={<ForgotPassword />} />
                <Route path="/reset-password" element={<ResetPassword />} />
                <Route path="/login" element={<Navigate to="/" replace />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </main>
          </div>
        </div>
      )}
    </Router>
  );
}

export default App;
