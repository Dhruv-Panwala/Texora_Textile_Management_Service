import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Download, FileText, Minus, Pencil, Plus, Trash } from 'lucide-react';
import { format } from 'date-fns';
import { api } from '../services/api';
import { confirmDelete, downloadCsv } from '../utils/csv';
import { Customer, PageResult, Sale } from '../types';

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
  takaEntries: [{ takaNo: '', meters: '' }] as TakaDraft[],
};

function Sales() {
  const [error, setError] = useState('');
  const [sales, setSales] = useState<Sale[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [isAddingNewSale, setIsAddingNewSale] = useState(false);
  const [newSale, setNewSale] = useState(emptySale);
  const [editingSaleId, setEditingSaleId] = useState<number | null>(null);
  const [editingSale, setEditingSale] = useState(emptySale);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [downloading, setDownloading] = useState('');
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);

  const load = useCallback(async () => {
    try {
      const [salesPage, customersPage] = await Promise.all([
        api.get<PageResult<Sale>>(`/api/sales?page=${page}&size=25`),
        api.get<PageResult<Customer>>('/api/customers?page=0&size=100'),
      ]);
      setSales(salesPage.content);
      setTotalPages(salesPage.totalPages);
      setCustomers(customersPage.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load sales');
    }
  }, [page]);
  useEffect(() => { load(); }, [load]);

  const totalMeters = useMemo(() => newSale.takaEntries.reduce((sum, t) => sum + Number(t.meters || 0), 0), [newSale.takaEntries]);
  const totalAmount = totalMeters * Number(newSale.rate || 0);
  const editTotalMeters = useMemo(() => editingSale.takaEntries.reduce((sum, t) => sum + Number(t.meters || 0), 0), [editingSale.takaEntries]);
  const editTotalAmount = editTotalMeters * Number(editingSale.rate || 0);

  const salePayload = (sale: typeof emptySale) => ({
    saleDate: sale.saleDate || null,
    customer: { id: Number(sale.customerId) },
    brokerName: sale.brokerName,
    quality: sale.quality,
    challanNo: sale.challanNo ? Number(sale.challanNo) : null,
    billNo: sale.billNo ? Number(sale.billNo) : null,
    balanceChallanColumnsByMeters: sale.balanceChallanColumnsByMeters,
    rate: Number(sale.rate),
    takaEntries: sale.takaEntries.map((t) => ({ takaNo: Number(t.takaNo), meters: Number(t.meters) })),
  });

  const handleNewSale = async (e: React.FormEvent) => {
    e.preventDefault();
    if (savingRef.current) {
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError('');
    try {
      await api.post<Sale>('/api/sales', salePayload(newSale));
      setIsAddingNewSale(false);
      setNewSale(emptySale);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save sale');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const startEdit = (sale: Sale) => {
    setEditingSaleId(sale.id);
    setEditingSale({
      saleDate: sale.saleDate,
      customerId: String(sale.customer?.id || ''),
      brokerName: sale.brokerName || '',
      quality: sale.quality || '',
      challanNo: sale.challanNo ? String(sale.challanNo) : '',
      billNo: sale.billNo ? String(sale.billNo) : '',
      balanceChallanColumnsByMeters: sale.balanceChallanColumnsByMeters || false,
      rate: String(sale.rate || ''),
      takaEntries: sale.takaEntries?.length
        ? sale.takaEntries.map((taka) => ({ takaNo: String(taka.takaNo || ''), meters: String(taka.meters || '') }))
        : [{ takaNo: '', meters: '' }],
    });
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
      await api.put<Sale>(`/api/sales/${editingSaleId}`, salePayload(editingSale));
      setEditingSaleId(null);
      setEditingSale(emptySale);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update sale');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const remove = async (sale: Sale) => {
    if (!confirmDelete(`sale challan ${sale.challanNo}`)) {
      return;
    }
    setError('');
    try {
      await api.delete(`/api/sales/${sale.id}`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete sale');
    }
  };

  const addTakaField = (sale: typeof emptySale, setSale: React.Dispatch<React.SetStateAction<typeof emptySale>>) => {
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

  const download = async (sale: Sale, type: 'challan' | 'bill') => {
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
    </>
  );

  const challanLabel = (sale: Sale) => {
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
          <button onClick={() => setIsAddingNewSale(true)} className="btn-primary">
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

      <div className="table-surface">
        <div className="border-b border-stone-200/70 px-6 py-5">
          <h2 className="text-xl font-bold text-stone-900">Sales List</h2>
          <p className="mt-1 text-sm text-stone-500">Responsive table view for all recorded sales and generated documents.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr>{['Date', 'Customer', 'Challan', 'Bill', 'Quality', 'Meters', 'Amount', 'Payment', 'Created', 'Actions'].map(h => <th key={h}>{h}</th>)}</tr></thead>
            <tbody>
              {sales.map((sale) => (
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
