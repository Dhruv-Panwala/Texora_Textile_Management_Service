import { FormEvent, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { Building, Building2, ChevronLeft, LayoutDashboard, LogOut, Plus, Receipt, ShoppingCart, Users, Wallet } from 'lucide-react';
import type { CompanyProfile } from '../../types';

type Props = {
  companies: CompanyProfile[];
  activeCompanyId: string;
  onCompanyChange: (companyId: string) => void;
  onCreateCompany: (tradeName: string) => Promise<void>;
  onLogout: () => void;
  mobileOpen: boolean;
  onCloseMobile: () => void;
};

function Sidebar({ companies, activeCompanyId, onCompanyChange, onCreateCompany, onLogout, mobileOpen, onCloseMobile }: Props) {
  const [addingCompany, setAddingCompany] = useState(false);
  const [tradeName, setTradeName] = useState('');
  const [savingCompany, setSavingCompany] = useState(false);
  const [companyError, setCompanyError] = useState('');
  const activeCompany = companies.find((company) => String(company.id) === activeCompanyId);
  const links = [
    { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/purchases', icon: ShoppingCart, label: 'Purchases' },
    { to: '/sales', icon: Receipt, label: 'Sales' },
    { to: '/payments', icon: Wallet, label: 'Payments' },
    { to: '/suppliers', icon: Building2, label: 'Suppliers' },
    { to: '/customers', icon: Users, label: 'Customers' },
    { to: '/company', icon: Building, label: 'Company' },
    { to: '/members', icon: Users, label: 'Members' },
  ];

  const openAddCompany = () => {
    setCompanyError('');
    setTradeName('');
    setAddingCompany(true);
  };

  const submitCompany = async (event: FormEvent) => {
    event.preventDefault();
    setSavingCompany(true);
    setCompanyError('');
    try {
      await onCreateCompany(tradeName.trim());
      setAddingCompany(false);
      onCloseMobile();
    } catch (error) {
      setCompanyError(error instanceof Error ? error.message : 'Could not add company');
    } finally {
      setSavingCompany(false);
    }
  };

  return (
    <>
      <div
        className={`fixed inset-0 z-40 bg-stone-950/45 backdrop-blur-sm transition-opacity duration-300 lg:hidden ${mobileOpen ? 'opacity-100' : 'pointer-events-none opacity-0'}`}
        onClick={onCloseMobile}
      />
      <aside className={`fixed inset-y-0 left-0 z-50 flex w-[19rem] max-w-[88vw] flex-col border-r border-white/50 bg-[linear-gradient(180deg,rgba(61,45,33,0.96),rgba(96,63,39,0.96))] text-white shadow-2xl transition-transform duration-300 lg:translate-x-0 ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="border-b border-white/10 px-5 pb-5 pt-6">
          <div className="mb-5 flex items-start justify-between gap-3 lg:hidden">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-amber-100/70">Navigation</p>
              <h2 className="text-lg font-extrabold">Textile Manager</h2>
            </div>
            <button type="button" className="btn-ghost !rounded-full !bg-white/10 !p-2 !text-white" onClick={onCloseMobile}>
              <ChevronLeft className="h-5 w-5" />
            </button>
          </div>
          <div className="hidden lg:block">
            <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-amber-100/70">Business Console</p>
            <h1 className="mt-3 font-['Fraunces'] text-3xl font-bold leading-tight text-white">Textile Manager</h1>
            <p className="mt-3 text-sm leading-6 text-amber-50/75">Clean day-to-day control for sales, purchases, parties, challans, and billing.</p>
          </div>
          <div className="mt-5 rounded-[24px] border border-white/10 bg-white/10 p-4 backdrop-blur-md">
            <label className="mb-2 block text-[11px] font-semibold uppercase tracking-[0.18em] text-amber-100/70">Active Company</label>
            <select className="!border-white/10 !bg-white/90 !text-stone-800" value={activeCompanyId} onChange={(event) => onCompanyChange(event.target.value)}>
              {companies.map((company) => <option key={company.id} value={company.id}>{company.tradeName}</option>)}
            </select>
            {activeCompany && <div className="mt-3 rounded-2xl bg-white/10 px-3 py-3 text-sm text-amber-50/90"><div className="font-semibold">{activeCompany.tradeName}</div><div className="mt-1 text-xs text-amber-50/65">All data and documents are scoped to this company.</div></div>}
            <button type="button" className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-white/15 px-3 py-2 text-xs font-semibold text-amber-50 hover:bg-white/20" onClick={openAddCompany}>
              <Plus className="h-4 w-4" /> Add company
            </button>
          </div>
        </div>
        <nav className="flex-1 space-y-2 overflow-y-auto px-4 py-5">
          {links.map(({ to, icon: Icon, label }) => <NavLink key={to} to={to} onClick={onCloseMobile} className={({ isActive }) => `group flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-semibold transition ${isActive ? 'bg-white text-stone-900 shadow-lg' : 'text-amber-50/85 hover:bg-white/10 hover:text-white'}`}><span className="rounded-xl bg-black/10 p-2 transition group-hover:bg-black/15"><Icon className="h-4 w-4" /></span><span>{label}</span></NavLink>)}
        </nav>
        <div className="border-t border-white/10 p-4">
          <button onClick={onLogout} className="flex w-full items-center justify-center gap-3 rounded-2xl bg-white/10 px-4 py-3 text-sm font-semibold text-white hover:bg-white/15"><LogOut className="h-4 w-4" /><span>Logout</span></button>
        </div>
      </aside>
      {addingCompany && <div className="fixed inset-0 z-[60] flex items-center justify-center bg-stone-950/50 px-4" role="dialog" aria-modal="true" aria-labelledby="add-company-title">
        <form className="glass-panel w-full max-w-md space-y-5 p-6 text-stone-900" onSubmit={submitCompany}>
          <div><p className="text-xs font-semibold uppercase tracking-[0.18em] text-stone-500">Business setup</p><h2 id="add-company-title" className="mt-1 text-2xl font-extrabold">Add another company</h2><p className="mt-2 text-sm text-stone-600">Create it under the current workspace. You can add its GST, phone, address, and logo afterward.</p></div>
          {companyError && <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{companyError}</div>}
          <label className="block"><span className="mb-2 block text-sm font-semibold text-stone-700">Company name</span><input required autoFocus maxLength={150} value={tradeName} onChange={(event) => setTradeName(event.target.value)} placeholder="Example: Ritika Creation" /></label>
          <div className="flex justify-end gap-3"><button type="button" className="btn-secondary" onClick={() => setAddingCompany(false)}>Cancel</button><button type="submit" disabled={savingCompany} className="btn-primary disabled:opacity-60">{savingCompany ? 'Adding...' : 'Add company'}</button></div>
        </form>
      </div>}
    </>
  );
}

export default Sidebar;
