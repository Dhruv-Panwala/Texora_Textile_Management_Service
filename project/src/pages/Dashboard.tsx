import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AlertCircle, ArrowUpRight, BarChart3, IndianRupee, RefreshCw, Scale, TrendingUp, Wallet } from 'lucide-react';
import { format } from 'date-fns';
import { api } from '../services/api';
import { DashboardSummary } from '../types';

type DashboardPeriod = 'weekly' | 'monthly' | 'yearly';

const emptySummary: DashboardSummary = {
  period: 'monthly',
  periodStart: '',
  periodEnd: '',
  current: {
    totalSales: 0,
    totalPurchases: 0,
    net: 0,
    saleCount: 0,
    purchaseCount: 0,
    totalMeters: 0,
    totalPurchaseQuantity: 0,
    averageSalesRate: 0,
    averageSaleValue: 0,
    averagePurchaseValue: 0,
  },
  previous: {
    totalSales: 0,
    totalPurchases: 0,
    net: 0,
    saleCount: 0,
    purchaseCount: 0,
    totalMeters: 0,
    totalPurchaseQuantity: 0,
    averageSalesRate: 0,
    averageSaleValue: 0,
    averagePurchaseValue: 0,
  },
  salesChangePercent: 0,
  purchasesChangePercent: 0,
  outstandingReceivables: 0,
  outstandingPayables: 0,
  overdueReceivables: 0,
  overduePayables: 0,
  topQuality: { quality: '-', amount: 0 },
  trend: [],
};

function money(value: number) {
  return `Rs ${Math.round(value || 0).toLocaleString()}`;
}

function number(value: number, suffix = '') {
  return `${Number(value || 0).toLocaleString(undefined, { maximumFractionDigits: 2 })}${suffix}`;
}

function change(value: number) {
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(1)}%`;
}

function Dashboard() {
  const [period, setPeriod] = useState<DashboardPeriod>('monthly');
  const [summary, setSummary] = useState<DashboardSummary>(emptySummary);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const lastLoadedAt = useRef(0);

  const loadSummary = useCallback(async (force = false) => {
    if (!force && Date.now() - lastLoadedAt.current < 30_000) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      setSummary(await api.get<DashboardSummary>(`/api/dashboard?period=${period}`));
      lastLoadedAt.current = Date.now();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load dashboard data');
    } finally {
      setLoading(false);
    }
  }, [period]);

  useEffect(() => {
    void loadSummary(true);
    const refreshWhenFocused = () => { void loadSummary(); };
    window.addEventListener('focus', refreshWhenFocused);
    return () => window.removeEventListener('focus', refreshWhenFocused);
  }, [loadSummary]);

  const maxTrend = useMemo(
    () => Math.max(1, ...summary.trend.flatMap((point) => [point.sales, point.purchases])),
    [summary.trend],
  );

  const periodLabel = summary.periodStart && summary.periodEnd
    ? `${format(new Date(summary.periodStart), 'dd MMM yyyy')} - ${format(new Date(summary.periodEnd), 'dd MMM yyyy')}`
    : '';

  const primaryCards = [
    {
      label: 'Sales',
      value: money(summary.current.totalSales),
      note: `${change(summary.salesChangePercent)} vs previous period`,
      icon: TrendingUp,
      accent: 'from-emerald-500/20 to-emerald-600/5',
      iconColor: 'text-emerald-700',
    },
    {
      label: 'Purchases',
      value: money(summary.current.totalPurchases),
      note: `${change(summary.purchasesChangePercent)} vs previous period`,
      icon: BarChart3,
      accent: 'from-sky-500/20 to-sky-600/5',
      iconColor: 'text-sky-700',
    },
    {
      label: 'Net',
      value: money(summary.current.net),
      note: 'Sales minus purchases',
      icon: IndianRupee,
      accent: summary.current.net >= 0 ? 'from-amber-400/20 to-orange-500/5' : 'from-rose-400/20 to-red-500/5',
      iconColor: summary.current.net >= 0 ? 'text-amber-700' : 'text-rose-700',
    },
    {
      label: 'Receivable',
      value: money(summary.outstandingReceivables),
      note: `${summary.overdueReceivables} overdue customer payments`,
      icon: Wallet,
      accent: 'from-violet-400/20 to-fuchsia-500/5',
      iconColor: 'text-violet-700',
    },
  ];

  const metricCards = [
    { label: 'Avg Sales Rate', value: money(summary.current.averageSalesRate), note: 'Per meter sold' },
    { label: 'Avg Sale Value', value: money(summary.current.averageSaleValue), note: `${summary.current.saleCount} sales` },
    { label: 'Avg Purchase Value', value: money(summary.current.averagePurchaseValue), note: `${summary.current.purchaseCount} purchases` },
    { label: 'Meters Sold', value: number(summary.current.totalMeters, ' m'), note: `Top quality: ${summary.topQuality.quality}` },
    { label: 'Purchase Qty', value: number(summary.current.totalPurchaseQuantity), note: 'Across all purchase types' },
    { label: 'Payable', value: money(summary.outstandingPayables), note: `${summary.overduePayables} overdue supplier payments` },
  ];

  return (
    <div className="page-shell">
      <section className="glass-panel overflow-hidden p-6 sm:p-8">
        <div className="page-header">
          <div>
            <span className="stat-chip">Overview</span>
            <h1 className="page-title mt-4">Business Dashboard</h1>
            <p className="page-subtitle">
              Track current trading momentum, cash follow-up, and the best-performing cloth quality from one place.
            </p>
            {periodLabel && <p className="mt-3 text-sm font-medium text-stone-500">{periodLabel}</p>}
          </div>

          <div className="flex flex-wrap items-center justify-end gap-2">
            <div className="inline-flex rounded-full border border-white/70 bg-white/65 p-1 shadow-sm backdrop-blur">
              {(['weekly', 'monthly', 'yearly'] as DashboardPeriod[]).map((option) => (
                <button
                  key={option}
                  onClick={() => setPeriod(option)}
                  className={`rounded-full px-4 py-2 text-sm font-semibold capitalize sm:px-5 ${
                    period === option
                      ? 'bg-stone-900 text-white shadow-lg'
                      : 'text-stone-600 hover:bg-white'
                  }`}
                >
                  {option}
                </button>
              ))}
            </div>
            <button type="button" className="btn-secondary" onClick={() => void loadSummary(true)} disabled={loading}>
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
            </button>
          </div>
        </div>
        {error && <div role="alert" className="mt-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}
      </section>

      <section className="grid grid-cols-1 gap-4 xl:grid-cols-4">
        {primaryCards.map(({ label, value, note, icon: Icon, accent, iconColor }) => (
          <article key={label} className="card-surface relative overflow-hidden">
            <div className={`absolute inset-x-0 top-0 h-24 bg-gradient-to-r ${accent}`} />
            <div className="relative flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-bold uppercase tracking-[0.14em] text-stone-500">{label}</p>
                <p className="mt-4 text-3xl font-extrabold text-stone-900">{value}</p>
                <p className="mt-2 text-sm leading-6 text-stone-600">{note}</p>
              </div>
              <div className={`rounded-2xl bg-white/85 p-3 shadow-md ${iconColor}`}>
                <Icon className="h-6 w-6" />
              </div>
            </div>
          </article>
        ))}
      </section>

      <section className="grid grid-cols-1 gap-6 xl:grid-cols-[1.6fr_0.9fr]">
        <article className="card-surface">
          <div className="mb-6 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-2xl font-bold text-stone-900">Sales vs Purchases</h2>
              <p className="mt-1 text-sm text-stone-500">Six rolling snapshots for quick trend reading.</p>
            </div>
            <div className="rounded-2xl bg-stone-900 p-3 text-white shadow-lg">
              <Scale className="h-5 w-5" />
            </div>
          </div>

          <div className="space-y-4">
            {summary.trend.map((point) => (
              <div key={point.label} className="grid grid-cols-1 gap-2 rounded-2xl bg-white/55 p-4 sm:grid-cols-[88px_1fr_100px] sm:items-center">
                <div className="text-sm font-semibold text-stone-600">{point.label}</div>
                <div className="space-y-2">
                  <div className="h-3 overflow-hidden rounded-full bg-stone-200/80">
                    <div
                      className="h-3 rounded-full bg-gradient-to-r from-emerald-500 to-emerald-600"
                      style={{ width: `${Math.max(2, (point.sales / maxTrend) * 100)}%` }}
                    />
                  </div>
                  <div className="h-3 overflow-hidden rounded-full bg-stone-200/80">
                    <div
                      className="h-3 rounded-full bg-gradient-to-r from-sky-500 to-sky-600"
                      style={{ width: `${Math.max(2, (point.purchases / maxTrend) * 100)}%` }}
                    />
                  </div>
                </div>
                <div className="text-sm font-bold text-stone-800 sm:text-right">{money(point.net)}</div>
              </div>
            ))}
          </div>

          <div className="mt-5 flex flex-wrap gap-3 text-xs font-semibold uppercase tracking-[0.14em] text-stone-500">
            <span className="inline-flex items-center gap-2"><span className="h-2.5 w-5 rounded-full bg-emerald-500" /> Sales</span>
            <span className="inline-flex items-center gap-2"><span className="h-2.5 w-5 rounded-full bg-sky-500" /> Purchases</span>
          </div>
        </article>

        <article className="card-surface">
          <div className="mb-5 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-2xl font-bold text-stone-900">Owner Watchlist</h2>
              <p className="mt-1 text-sm text-stone-500">A quick pulse check before the day starts.</p>
            </div>
            <ArrowUpRight className="h-5 w-5 text-stone-500" />
          </div>

          <div className="space-y-4">
            <div className="rounded-2xl bg-emerald-50/80 p-4">
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-emerald-700">Top quality</p>
              <p className="mt-2 text-xl font-bold text-stone-900">{summary.topQuality.quality}</p>
              <p className="mt-1 text-sm text-stone-600">{money(summary.topQuality.amount)}</p>
            </div>
            <div className="rounded-2xl bg-amber-50/80 p-4">
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-amber-700">Money to collect</p>
              <p className="mt-2 text-xl font-bold text-stone-900">{money(summary.outstandingReceivables)}</p>
            </div>
            <div className="rounded-2xl bg-rose-50/80 p-4">
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-rose-700">Overdue items</p>
              <p className="mt-2 text-xl font-bold text-stone-900">{summary.overdueReceivables + summary.overduePayables}</p>
            </div>
          </div>
        </article>
      </section>

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {metricCards.map((card) => (
          <article key={card.label} className="card-surface">
            <p className="text-sm font-bold uppercase tracking-[0.14em] text-stone-500">{card.label}</p>
            <p className="mt-3 text-2xl font-extrabold text-stone-900">{card.value}</p>
            <p className="mt-2 text-sm leading-6 text-stone-600">{card.note}</p>
          </article>
        ))}
      </section>

      {(summary.overdueReceivables > 0 || summary.overduePayables > 0) && (
        <div className="glass-panel flex flex-col gap-3 border border-red-100 bg-red-50/70 p-5 sm:flex-row sm:items-center">
          <div className="rounded-2xl bg-red-100 p-3 text-red-600">
            <AlertCircle className="h-5 w-5" />
          </div>
          <p className="text-sm font-medium text-red-800">
            {summary.overdueReceivables} customer payments and {summary.overduePayables} supplier payments need attention.
          </p>
        </div>
      )}
    </div>
  );
}

export default Dashboard;
