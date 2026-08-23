import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom';
import { Menu } from 'lucide-react';
import Sidebar from '../components/layout/Sidebar';
import Login from '../pages/Login';
import Signup from '../pages/Signup';
import AcceptInvitation from '../pages/AcceptInvitation';
import ForgotPassword from '../pages/ForgotPassword';
import ResetPassword from '../pages/ResetPassword';
import { RouteLoading } from '../components/ui/LoadingSkeleton';
import { AUTH_EXPIRED_EVENT, PERMISSION_DENIED_EVENT, api, clearCompanyId, clearCompanyProfileCache, getCompanyId, setCompanyId } from '../services/api';
import type { CompanyProfile } from '../types';

const Dashboard = lazy(() => import('../pages/Dashboard'));
const Purchases = lazy(() => import('../pages/Purchases'));
const Sales = lazy(() => import('../pages/Sales'));
const Payments = lazy(() => import('../pages/Payments'));
const Suppliers = lazy(() => import('../pages/Suppliers'));
const Customers = lazy(() => import('../pages/Customers'));
const Company = lazy(() => import('../pages/Company'));
const Members = lazy(() => import('../pages/Members'));

function App() {
  const publicSignupEnabled = import.meta.env.VITE_PUBLIC_SIGNUP_ENABLED !== 'false';
  const [authenticated, setAuthenticated] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [companies, setCompanies] = useState<CompanyProfile[]>([]);
  const [companiesReady, setCompaniesReady] = useState(false);
  const [activeCompanyId, setActiveCompanyId] = useState<string>(getCompanyId() || '');
  const [workspaceReady, setWorkspaceReady] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [permissionMessage, setPermissionMessage] = useState('');
  const [workspaceError, setWorkspaceError] = useState('');

  useEffect(() => {
    const handleAuthExpired = () => {
      clearCompanyId();
      clearCompanyProfileCache();
      setCompanies([]);
      setCompaniesReady(false);
      setActiveCompanyId('');
      setWorkspaceReady(false);
      setAuthenticated(false);
      setWorkspaceError('');
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

  const selectCompany = useCallback((loadedCompanies: CompanyProfile[]) => {
    setCompanies(loadedCompanies);
    const activeId = getCompanyId();
    if (activeId && loadedCompanies.some((company) => String(company.id) === activeId)) {
      setActiveCompanyId(activeId);
      setWorkspaceError('');
      setCompaniesReady(true);
      setWorkspaceReady(true);
      return;
    }
    const fallbackId = loadedCompanies[0] ? String(loadedCompanies[0].id) : '';
    if (fallbackId) {
      setCompanyId(fallbackId);
    } else {
      clearCompanyId();
    }
    setActiveCompanyId(fallbackId);
    setWorkspaceError('');
    setCompaniesReady(true);
    setWorkspaceReady(true);
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([api.me(), api.getCompanyProfiles()]).then(([sessionResult, companiesResult]) => {
      if (cancelled) {
        return;
      }
      if (sessionResult.status === 'rejected') {
        clearCompanyId();
        clearCompanyProfileCache();
        setCompanies([]);
        setCompaniesReady(false);
        setActiveCompanyId('');
        setAuthenticated(false);
        setWorkspaceError('');
        setAuthReady(true);
        return;
      }
      setAuthenticated(true);
      setAuthReady(true);
      if (companiesResult.status === 'fulfilled') {
        selectCompany(companiesResult.value);
      } else {
        setWorkspaceError(companiesResult.reason instanceof Error ? companiesResult.reason.message : 'Could not load your companies. Please refresh the page.');
        setCompaniesReady(true);
        setWorkspaceReady(true);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [selectCompany]);

  useEffect(() => {
    if (!authenticated || !authReady || companiesReady) {
      return;
    }

    let cancelled = false;
    setWorkspaceReady(false);
    api.getCompanyProfiles().then((loadedCompanies) => {
      if (cancelled) {
        return;
      }
      selectCompany(loadedCompanies);
    }).catch((err) => {
      if (!cancelled) {
        setWorkspaceError(err instanceof Error ? err.message : 'Could not load your companies. Please refresh the page.');
        setCompaniesReady(true);
        setWorkspaceReady(true);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [authenticated, authReady, companiesReady, selectCompany]);

  const logout = () => {
    void api.logout().catch(() => undefined);
    clearCompanyId();
    clearCompanyProfileCache();
    setCompanies([]);
    setCompaniesReady(false);
    setActiveCompanyId('');
    setWorkspaceReady(false);
    setAuthenticated(false);
    setWorkspaceError('');
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
    setWorkspaceReady(false);
    setCompaniesReady(false);
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

  if (authenticated && !workspaceReady) {
    return (
      <div className="app-shell flex min-h-screen items-center justify-center px-6 text-center">
        <div className="glass-panel max-w-md px-8 py-10">
          <p className="stat-chip mb-4">Preparing workspace</p>
          <h1 className="page-title text-3xl">Loading your company...</h1>
          <p className="page-subtitle">Selecting the active company before loading company data.</p>
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
              {workspaceError && <div role="alert" className="mb-4 flex items-center justify-between gap-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-800"><span>{workspaceError}</span><button type="button" className="text-red-700 underline" onClick={() => setWorkspaceError('')}>Dismiss</button></div>}
              <Suspense fallback={<RouteLoading />}>
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
              </Suspense>
            </main>
          </div>
        </div>
      )}
    </Router>
  );
}

export default App;
