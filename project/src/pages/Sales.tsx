import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Download, FileText, Minus, Pencil, Plus, Trash } from 'lucide-react';
import { format } from 'date-fns';
import { api } from '../services/api';
import { confirmDelete, downloadCsv } from '../utils/csv';
import { Customer, PageResult, Sale, SaleListItem, SavedTakaEntry } from '../types';
import { SkeletonRows } from '../components/ui/LoadingSkeleton';
import { SpokenTaka, TakaVoiceInput } from '../components/TakaVoiceInput';
import { MAX_TAKA_ENTRIES, validateTakaEntries } from '../utils/takaRules';

interface TakaDraft {
  takaNo: string;
  meters: string;
}

const emptySale = {
  saleDate: '',
  customerId: '',
  brokerName: '',
  quality: '',
  challanNo: '',
  billNo: '',
  balanceChallanColumnsByMeters: false,
  rate: '',
  version: undefined as number | undefined,
  takaEntries: [{ takaNo: '', meters: '' }] as TakaDraft[],
};

function Sales() {
  const [error, setError] = useState('');
  const [sales, setSales] = useState<SaleListItem[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [savedTakas, setSavedTakas] = useState<SavedTakaEntry[]>([]);
  const [savedTakasLoading, setSavedTakasLoading] = useState(true);
  const [savedTakasPage, setSavedTakasPage] = useState(0);
  const [savedTakasTotalPages, setSavedTakasTotalPages] = useState(0);
  const [savedTakasTotalElements, setSavedTakasTotalElements] = useState(0);
  const [isAddingNewSale, setIsAddingNewSale] = useState(false);
  const [newSale, setNewSale] = useState(emptySale);
  const [editingSaleId, setEditingSaleId] = useState<number | null>(null);
  const [editingSale, setEditingSale] = useState(emptySale);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [downloading, setDownloading] = useState('');
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const savingRef = useRef(false);
  const customersLoadedRef = useRef(false);

  const loadSales = useCallback(async () => {
    setLoading(true);
    try {
      const salesPage = await api.get<PageResult<SaleListItem>>(`/api/sales?page=${page}&size=25`);
      setSales(salesPage.content);
      setTotalPages(salesPage.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load sales');
    } finally {
      setLoading(false);
    }
  }, [page]);

  const loadCustomers = useCallback(async () => {
    if (customersLoadedRef.current) {
      return;
    }
    try {
      const customersPage = await api.get<PageResult<Customer>>('/api/customers?page=0&size=100');
      setCustomers(customersPage.content);
      customersLoadedRef.current = true;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load customers');
    }
  }, []);

  const loadSavedTakas = useCallback(async (requestedPage: number) => {
    setSavedTakasLoading(true);
    try {
      const result = await api.getSavedTakaEntries(requestedPage, 100);
      setSavedTakas(result.content);
      setSavedTakasPage(result.page);
      setSavedTakasTotalPages(result.totalPages);
      setSavedTakasTotalElements(result.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load saved taka entries');
    } finally {
      setSavedTakasLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSales();
  }, [loadSales]);

  useEffect(() => {
    void loadSavedTakas(savedTakasPage);
  }, [loadSavedTakas, savedTakasPage]);

  const saveTakas = useCallback(async (entries: SpokenTaka[]) => {
    try {
      const saved = await api.createSavedTakaEntries(entries);
      setSavedTakasTotalElements((current) => current + saved.length);
      setSavedTakasTotalPages((current) => Math.max(current, Math.ceil((savedTakasTotalElements + saved.length) / 100)));
      if (savedTakasPage === 0) {
        setSavedTakas((current) => [...current, ...saved]
          .sort((left, right) => left.takaNo - right.takaNo || left.id - right.id)
          .slice(0, 100));
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not save taka entries';
      setError(message);
      throw new Error(message);
    }
  }, [savedTakasPage, savedTakasTotalElements]);

  const removeSavedTaka = useCallback(async (id: number) => {
    try {
      await api.deleteSavedTakaEntry(id);
      setSavedTakas((current) => current.filter((entry) => entry.id !== id));
      const nextTotal = Math.max(0, savedTakasTotalElements - 1);
      setSavedTakasTotalElements(nextTotal);
      setSavedTakasTotalPages(Math.ceil(nextTotal / 100));
      if (savedTakasPage > 0 && savedTakas.length <= 1) {
        setSavedTakasPage((current) => Math.max(0, current - 1));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove taka entry');
    }
  }, [savedTakas.length, savedTakasPage, savedTakasTotalElements]);

  const totalMeters = useMemo(() => newSale.takaEntries.reduce((sum, t) => sum + Number(t.meters || 0), 0), [newSale.takaEntries]);
  const totalAmount = totalMeters * Number(newSale.rate || 0);
  const editTotalMeters = useMemo(() => editingSale.takaEntries.reduce((sum, t) => sum + Number(t.meters || 0), 0), [editingSale.takaEntries]);
  const editTotalAmount = editTotalMeters * Number(editingSale.rate || 0);

  const salePayload = (sale: typeof emptySale) => {
    const takaEntries = sale.takaEntries.map((t) => ({ takaNo: Number(t.takaNo), meters: Number(t.meters) }));
    const validationError = validateTakaEntries(takaEntries);
    if (validationError) {
      throw new Error(validationError);
    }
    return {
      saleDate: sale.saleDate || null,
      customer: { id: Number(sale.customerId) },
      brokerName: sale.brokerName,
      quality: sale.quality,
      challanNo: sale.challanNo ? Number(sale.challanNo) : null,
      billNo: sale.billNo ? Number(sale.billNo) : null,
      balanceChallanColumnsByMeters: sale.balanceChallanColumnsByMeters,
      rate: Number(sale.rate),
      version: sale.version,
      takaEntries,
    };
  };

  const appendVoiceEntries = (
    sale: typeof emptySale,
    setSale: React.Dispatch<React.SetStateAction<typeof emptySale>>,
    entries: SpokenTaka[],
  ) => {
    const hasOnlyBlankRow = sale.takaEntries.length === 1
      && !sale.takaEntries[0].takaNo.trim()
      && !sale.takaEntries[0].meters.trim();
    const existing = hasOnlyBlankRow ? [] : sale.takaEntries.map((entry) => ({ takaNo: Number(entry.takaNo), meters: Number(entry.meters) }));
    const validationError = validateTakaEntries([...existing, ...entries]);
    if (validationError) {
      setError(validationError);
      throw new Error(validationError);
    }
    setSale({
      ...sale,
      takaEntries: (hasOnlyBlankRow ? [] : sale.takaEntries).concat(entries.map((entry) => ({
        takaNo: String(entry.takaNo),
        meters: String(entry.meters),
      }))),
    });
  };

  const handleNewSale = async (e: React.FormEvent) => {
    e.preventDefault();
    if (savingRef.current) {
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError('');
    try {
      const createdSale = await api.post<Sale>('/api/sales', salePayload(newSale));
      setSales((current) => page === 0
        ? [createdSale, ...current.filter((sale) => sale.id !== createdSale.id)].slice(0, 25)
        : current);
      setIsAddingNewSale(false);
      setNewSale(emptySale);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save sale');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const startEdit = async (sale: SaleListItem) => {
    try {
      const [fullSale] = await Promise.all([
        api.get<Sale>(`/api/sales/${sale.id}`),
        loadCustomers(),
      ]);
      setEditingSaleId(fullSale.id);
      setEditingSale({
        saleDate: fullSale.saleDate,
        customerId: String(fullSale.customer?.id || ''),
        brokerName: fullSale.brokerName || '',
        quality: fullSale.quality || '',
        challanNo: fullSale.challanNo ? String(fullSale.challanNo) : '',
        billNo: fullSale.billNo ? String(fullSale.billNo) : '',
        balanceChallanColumnsByMeters: fullSale.balanceChallanColumnsByMeters || false,
        rate: String(fullSale.rate || ''),
        version: fullSale.version,
        takaEntries: fullSale.takaEntries?.length
          ? fullSale.takaEntries.map((taka) => ({ takaNo: String(taka.takaNo || ''), meters: String(taka.meters || '') }))
          : [{ takaNo: '', meters: '' }],
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load sale details');
    }
  };

  const handleEditSale = async (e: React.FormEvent) => {
    e.preventDefault();
    if (editingSaleId == null || savingRef.current) {
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError('');
    try {
      const updatedSale = await api.put<Sale>(`/api/sales/${editingSaleId}`, salePayload(editingSale));
      setSales((current) => current.map((sale) => sale.id === updatedSale.id ? updatedSale : sale));
      setEditingSaleId(null);
      setEditingSale(emptySale);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update sale');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const remove = async (sale: SaleListItem) => {
    if (!confirmDelete(`sale challan ${sale.challanNo}`)) {
      return;
    }
    setError('');
    try {
      await api.delete(`/api/sales/${sale.id}`);
      setSales((current) => current.filter((currentSale) => currentSale.id !== sale.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete sale');
    }
  };

  const addTakaField = (sale: typeof emptySale, setSale: React.Dispatch<React.SetStateAction<typeof emptySale>>) => {
    if (sale.takaEntries.length >= MAX_TAKA_ENTRIES) {
      setError(`You can add at most ${MAX_TAKA_ENTRIES} taka entries.`);
      return;
    }
    setSale({ ...sale, takaEntries: [...sale.takaEntries, { takaNo: '', meters: '' }] });
  };

  const updateTakaField = (
    sale: typeof emptySale,
    setSale: React.Dispatch<React.SetStateAction<typeof emptySale>>,
    index: number,
    field: 'takaNo' | 'meters',
    value: string,
  ) => {
    setSale({
      ...sale,
      takaEntries: sale.takaEntries.map((t, i) => i === index ? { ...t, [field]: value } : t),
    });
  };

  const handleSaleKeyDown = (
    e: React.KeyboardEvent<HTMLFormElement>,
    sale: typeof emptySale,
    setSale: React.Dispatch<React.SetStateAction<typeof emptySale>>,
  ) => {
    if (e.key !== 'Enter') {
      return;
    }
    e.preventDefault();
    addTakaField(sale, setSale);
  };

  const download = async (sale: SaleListItem, type: 'challan' | 'bill') => {
    const datePart = format(new Date(sale.saleDate), 'dd-MM-yy');
    const numberPart = type === 'challan' ? sale.challanNo : (sale.billNo || sale.challanNo);
    const downloadKey = `${sale.id}-${type}`;
    setError('');
    setDownloading(downloadKey);
    try {
      await api.download(`/api/sales/${sale.id}/${type}.pdf`, `${type}-${datePart}-${numberPart}.pdf`);
    } catch (err) {
      const reason = err instanceof Error ? err.message : '';
      setError(reason ? `Could not download the ${type}: ${reason}` : `Could not generate the ${type}. Please try again.`);
    } finally {
      setDownloading('');
    }
  };

  const exportCsv = () => downloadCsv('sales.csv', sales, [
    { header: 'Date', value: (sale) => sale.saleDate },
    { header: 'Customer', value: (sale) => sale.customer?.name },
    { header: 'Challan No', value: (sale) => sale.challanNo },
    { header: 'Challan Count', value: (sale) => sale.challanCount || 1 },
    { header: 'Bill No', value: (sale) => sale.billNo },
    { header: 'Financial Year', value: (sale) => sale.financialYear },
    { header: 'Quality', value: (sale) => sale.quality },
    { header: 'Broker', value: (sale) => sale.brokerName },
    { header: 'Total Meters', value: (sale) => sale.totalMeters },
    { header: 'Rate', value: (sale) => sale.rate },
    { header: 'Amount', value: (sale) => sale.amount },
    { header: 'Due Date', value: (sale) => sale.dueDate },
    { header: 'Payment Date', value: (sale) => sale.paymentDate },
    { header: 'Payment Mode', value: (sale) => sale.paymentMode },
    { header: 'Status', value: (sale) => sale.status },
    { header: 'Created At', value: (sale) => sale.createdAt },
  ]);

  const saleForm = (
    sale: typeof emptySale,
    setSale: React.Dispatch<React.SetStateAction<typeof emptySale>>,
    meters: number,
    amount: number,
  ) => (
    <>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <input type="date" className="border rounded-md px-3 py-2" value={sale.saleDate} onChange={(e) => setSale({ ...sale, saleDate: e.target.value })} required />
        <select className="border rounded-md px-3 py-2" value={sale.customerId} onChange={(e) => {
          const customer = customers.find(c => c.id === Number(e.target.value));
          setSale({ ...sale, customerId: e.target.value, brokerName: customer?.brokerName || '' });
        }} required>
          <option value="">Select Customer</option>
          {customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name}</option>)}
        </select>
        <input className="border rounded-md px-3 py-2" placeholder="Broker" value={sale.brokerName} onChange={(e) => setSale({ ...sale, brokerName: e.target.value })} />
        <input required className="border rounded-md px-3 py-2" placeholder="Quality, e.g. ARTSILK CLOTH" value={sale.quality} onChange={(e) => setSale({ ...sale, quality: e.target.value })} />
        <input type="number" min="1" className="border rounded-md px-3 py-2" placeholder="Challan no (blank for next)" value={sale.challanNo} onChange={(e) => setSale({ ...sale, challanNo: e.target.value })} />
        <input type="number" min="1" className="border rounded-md px-3 py-2" placeholder="Bill no (blank for next)" value={sale.billNo} onChange={(e) => setSale({ ...sale, billNo: e.target.value })} />
        <input type="number" step="0.01" min="0.01" className="border rounded-md px-3 py-2" placeholder="Rate per meter" value={sale.rate} onChange={(e) => setSale({ ...sale, rate: e.target.value })} required />
        <div className="text-sm text-gray-700 flex items-center">Total: {meters.toFixed(2)} m / Rs {amount.toFixed(2)}</div>
      </div>

      <label className="flex items-start gap-3 rounded-lg border border-stone-200 bg-stone-50 px-4 py-3 text-sm text-stone-700">
        <input type="checkbox" className="mt-1 h-4 w-4" checked={sale.balanceChallanColumnsByMeters} onChange={(e) => setSale({ ...sale, balanceChallanColumnsByMeters: e.target.checked })} />
        <span><span className="font-semibold text-stone-900">Balance challan columns near 1,200 meters</span><span className="mt-1 block text-stone-500">Off keeps 12 takas in each column. On starts the next column when the current column is closest to 1,200 meters, leaving the remaining rows blank.</span></span>
      </label>

      <div className="space-y-2">
        <div className="font-semibold text-gray-700">Taka Entries</div>
        <TakaVoiceInput
          onEntriesConfirmed={(entries) => appendVoiceEntries(sale, setSale, entries)}
          onSaveToLibrary={saveTakas}
        />
        {sale.takaEntries.map((taka, index) => (
          <div key={index} className="grid grid-cols-[120px_1fr_40px] gap-2">
            <input type="number" min="1" className="border rounded-md px-3 py-2" placeholder="Taka no" value={taka.takaNo} onChange={(e) => updateTakaField(sale, setSale, index, 'takaNo', e.target.value)} required />
            <input type="number" step="0.01" min="0.01" className="border rounded-md px-3 py-2" placeholder="Meters" value={taka.meters} onChange={(e) => updateTakaField(sale, setSale, index, 'meters', e.target.value)} required />
            <button type="button" className="bg-red-500 text-white rounded-md flex justify-center items-center disabled:opacity-40" disabled={sale.takaEntries.length === 1} onClick={() => setSale({ ...sale, takaEntries: sale.takaEntries.filter((_, i) => i !== index) })}>
              <Minus className="w-4 h-4" />
            </button>
          </div>
        ))}
        <button type="button" onClick={() => addTakaField(sale, setSale)} className="bg-green-600 text-white px-3 py-2 rounded-md flex items-center gap-1">
          <Plus className="w-4 h-4" /> Add Taka
        </button>
      </div>

      <SavedTakaPicker
        takas={savedTakas}
        loading={savedTakasLoading}
        page={savedTakasPage}
        totalPages={savedTakasTotalPages}
        onPageChange={setSavedTakasPage}
        sale={sale}
        setSale={setSale}
        onDelete={removeSavedTaka}
      />
    </>
  );

  const challanLabel = (sale: SaleListItem) => {
    const count = sale.challanCount || 1;
    if (count <= 1) {
      return String(sale.challanNo);
    }
    return `${sale.challanNo}-${sale.challanNo + count - 1}`;
  };

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <h1 className="page-title">Sales</h1>
          <p className="page-subtitle">Create bills and challans, manage taka entries, and download sale documents from one place.</p>
        </div>
        <div className="action-row">
          <button onClick={exportCsv} className="btn-secondary">
            <Download className="w-5 h-5" /> Export CSV
          </button>
          <button onClick={() => { void loadCustomers(); setIsAddingNewSale(true); }} className="btn-primary">
            <Plus className="w-5 h-5" /> New Sale
          </button>
        </div>
      </div>

      {error && <div className="mb-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}

      {isAddingNewSale && (
        <form onSubmit={handleNewSale} onKeyDown={(e) => handleSaleKeyDown(e, newSale, setNewSale)} className="form-surface space-y-4">
          {saleForm(newSale, setNewSale, totalMeters, totalAmount)}
          <div className="flex gap-2">
            <button disabled={saving} className="btn-primary disabled:opacity-60">{saving ? 'Saving...' : 'Save Sale'}</button>
            <button type="button" disabled={saving} className="btn-secondary disabled:opacity-60" onClick={() => setIsAddingNewSale(false)}>Cancel</button>
          </div>
        </form>
      )}

      {editingSaleId !== null && (
        <form onSubmit={handleEditSale} onKeyDown={(e) => handleSaleKeyDown(e, editingSale, setEditingSale)} className="form-surface space-y-4">
          {saleForm(editingSale, setEditingSale, editTotalMeters, editTotalAmount)}
          <div className="flex gap-2">
            <button disabled={saving} className="btn-primary disabled:opacity-60">{saving ? 'Saving...' : 'Update Sale'}</button>
            <button type="button" disabled={saving} className="btn-secondary disabled:opacity-60" onClick={() => setEditingSaleId(null)}>Cancel</button>
          </div>
        </form>
      )}

      <div className="table-surface" aria-busy={loading}>
        <div className="border-b border-stone-200/70 px-6 py-5">
          <h2 className="text-xl font-bold text-stone-900">Sales List</h2>
          <p className="mt-1 text-sm text-stone-500">Responsive table view for all recorded sales and generated documents.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr>{['Date', 'Customer', 'Challan', 'Bill', 'Quality', 'Meters', 'Amount', 'Payment', 'Created', 'Actions'].map(h => <th key={h}>{h}</th>)}</tr></thead>
            <tbody>
              {loading && sales.length === 0 ? <SkeletonRows columns={10} /> : sales.map((sale) => (
                <tr key={sale.id}>
                  <td>{format(new Date(sale.saleDate), 'dd MMM yyyy')}</td>
                  <td>{sale.customer?.name}</td>
                  <td>{challanLabel(sale)}</td>
                  <td>{sale.billNo}</td>
                  <td>{sale.quality}</td>
                  <td>{sale.totalMeters?.toFixed(2)}</td>
                  <td>Rs {sale.amount?.toLocaleString()}</td>
                  <td>{sale.paymentDate ? `${format(new Date(sale.paymentDate), 'dd MMM yyyy')} / ${sale.paymentMode}` : '-'}</td>
                  <td>{sale.createdAt ? format(new Date(sale.createdAt), 'dd MMM yyyy') : '-'}</td>
                  <td className="!pr-4 sm:!pr-6">
                    <div className="flex flex-wrap gap-2">
                    <button title="Edit sale" onClick={() => startEdit(sale)} className="text-blue-600 hover:text-blue-800"><Pencil className="w-5 h-5" /></button>
                    <button title="Download challan" disabled={downloading === `${sale.id}-challan`} onClick={() => void download(sale, 'challan')} className="text-blue-700 hover:text-blue-900 disabled:opacity-40"><Download className="w-5 h-5" /></button>
                    <button title="Download bill" disabled={downloading === `${sale.id}-bill`} onClick={() => void download(sale, 'bill')} className="text-green-700 hover:text-green-900 disabled:opacity-40"><FileText className="w-5 h-5" /></button>
                    <button title="Delete sale" onClick={() => remove(sale)} className="text-red-600 hover:text-red-800"><Trash className="w-5 h-5" /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {totalPages > 1 && <div className="mt-5 flex items-center justify-between text-sm"><button className="btn-secondary" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Previous</button><span>Page {page + 1} of {totalPages}</span><button className="btn-secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((current) => current + 1)}>Next</button></div>}
      </div>
    </div>
  );
}

export default Sales;

type SaleDraft = typeof emptySale;

function SavedTakaPicker({
  takas,
  loading,
  page,
  totalPages,
  onPageChange,
  sale,
  setSale,
  onDelete,
}: {
  takas: SavedTakaEntry[];
  loading: boolean;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  sale: SaleDraft;
  setSale: React.Dispatch<React.SetStateAction<SaleDraft>>;
  onDelete: (id: number) => Promise<void>;
}) {
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [validationError, setValidationError] = useState('');

  useEffect(() => {
    setSelectedIds([]);
    setValidationError('');
  }, [page]);

  const addSelected = () => {
    const existing = new Set(sale.takaEntries.map((entry) => entry.takaNo));
    const selected = takas.filter((entry) => selectedIds.includes(entry.id))
      .filter((entry) => !existing.has(String(entry.takaNo)));
    if (selected.length === 0) {
      return;
    }
    const combined = sale.takaEntries
      .filter((entry) => entry.takaNo.trim() || entry.meters.trim())
      .map((entry) => ({ takaNo: Number(entry.takaNo), meters: Number(entry.meters) }))
      .concat(selected.map((entry) => ({ takaNo: entry.takaNo, meters: entry.meters })));
    const error = validateTakaEntries(combined);
    if (error) {
      setValidationError(error);
      return;
    }
    setValidationError('');
    const hasOnlyBlankRow = sale.takaEntries.length === 1
      && !sale.takaEntries[0].takaNo.trim()
      && !sale.takaEntries[0].meters.trim();
    setSale((current) => ({
      ...current,
      takaEntries: (hasOnlyBlankRow ? [] : current.takaEntries).concat(selected.map((entry) => ({
        takaNo: String(entry.takaNo),
        meters: String(entry.meters),
      }))),
    }));
    setSelectedIds([]);
  };

  return (
    <div className="space-y-3 rounded-2xl border border-stone-200 bg-white/60 p-4">
      <div>
        <div className="font-semibold text-stone-900">Saved taka library</div>
        <p className="mt-1 text-sm text-stone-500">Capture a taka by voice once, then select it when preparing a challan or bill.</p>
      </div>
      {loading && takas.length === 0 ? <p className="text-sm text-stone-500">Loading saved takas...</p> : takas.length === 0 ? (
        <p className="text-sm text-stone-500">No saved takas yet.</p>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {takas.map((entry) => (
              <label key={entry.id} className="flex items-center gap-3 rounded-xl border border-stone-200 bg-white/70 px-3 py-2 text-sm text-stone-700">
                <input
                  type="checkbox"
                  className="!h-4 !w-4"
                  checked={selectedIds.includes(entry.id)}
                  onChange={() => setSelectedIds((current) => current.includes(entry.id)
                    ? current.filter((id) => id !== entry.id)
                    : [...current, entry.id])}
                />
                <span className="flex-1">Taka {entry.takaNo} · {entry.meters} m</span>
                <button type="button" className="text-red-600 hover:text-red-800" title="Remove saved taka" onClick={() => void onDelete(entry.id)}>
                  <Trash className="h-4 w-4" />
                </button>
              </label>
            ))}
          </div>
          <button type="button" className="btn-secondary" disabled={selectedIds.length === 0} onClick={addSelected}>
            Add selected takas to this sale
          </button>
          {totalPages > 1 && <div className="flex items-center justify-between text-sm text-stone-600">
            <button type="button" className="btn-secondary" disabled={page === 0 || loading} onClick={() => onPageChange(page - 1)}>Previous</button>
            <span>Page {page + 1} of {totalPages}</span>
            <button type="button" className="btn-secondary" disabled={page + 1 >= totalPages || loading} onClick={() => onPageChange(page + 1)}>Next</button>
          </div>}
          {validationError && <p className="text-xs text-red-700" role="alert">{validationError}</p>}
        </>
      )}
    </div>
  );
}
