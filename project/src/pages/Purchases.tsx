import React, { useCallback, useEffect, useRef, useState } from 'react';
import { format } from 'date-fns';
import { Download, Pencil, Plus, Trash } from 'lucide-react';
import { api } from '../services/api';
import { confirmDelete, downloadCsv } from '../utils/csv';
import { PageResult, Purchase, PurchaseListItem, Supplier } from '../types';
import { SkeletonRows } from '../components/ui/LoadingSkeleton';

const emptyPurchase = {
  purchaseDate: '',
  supplierId: '',
  materialType: 'BEAM',
  quantity: '',
  rate: '',
  description: '',
  version: undefined as number | undefined,
};

function Purchases() {
  const [error, setError] = useState('');
  const [purchases, setPurchases] = useState<PurchaseListItem[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [isAdding, setIsAdding] = useState(false);
  const [newPurchase, setNewPurchase] = useState(emptyPurchase);
  const [editingPurchaseId, setEditingPurchaseId] = useState<number | null>(null);
  const [editingPurchase, setEditingPurchase] = useState(emptyPurchase);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const savingRef = useRef(false);
  const suppliersLoadedRef = useRef(false);

  const loadPurchases = useCallback(async () => {
    setLoading(true);
    try {
      const purchasePage = await api.get<PageResult<PurchaseListItem>>(`/api/purchases?page=${page}&size=25`);
      setPurchases(purchasePage.content);
      setTotalPages(purchasePage.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load purchases');
    } finally {
      setLoading(false);
    }
  }, [page]);

  const loadSuppliers = useCallback(async () => {
    if (suppliersLoadedRef.current) {
      return;
    }
    try {
      const supplierPage = await api.get<PageResult<Supplier>>('/api/suppliers?page=0&size=100');
      setSuppliers(supplierPage.content);
      suppliersLoadedRef.current = true;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load suppliers');
    }
  }, []);

  useEffect(() => {
    void loadPurchases();
  }, [loadPurchases]);

  const purchasePayload = (purchase: typeof emptyPurchase) => ({
    purchaseDate: purchase.purchaseDate || null,
    supplier: { id: Number(purchase.supplierId) },
    materialType: purchase.materialType,
    quantity: Number(purchase.quantity),
    rate: Number(purchase.rate),
    description: purchase.description,
    version: purchase.version,
  });

  const handleNewPurchase = async (e: React.FormEvent) => {
    e.preventDefault();
    if (savingRef.current) {
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError('');
    try {
      const createdPurchase = await api.post<Purchase>('/api/purchases', purchasePayload(newPurchase));
      setPurchases((current) => page === 0
        ? [createdPurchase, ...current.filter((purchase) => purchase.id !== createdPurchase.id)].slice(0, 25)
        : current);
      setIsAdding(false);
      setNewPurchase(emptyPurchase);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save purchase');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const startEdit = (purchase: PurchaseListItem) => {
    void loadSuppliers();
    setEditingPurchaseId(purchase.id);
    setEditingPurchase({
      purchaseDate: purchase.purchaseDate,
      supplierId: String(purchase.supplier?.id || ''),
      materialType: purchase.materialType,
      quantity: String(purchase.quantity || ''),
      rate: String(purchase.rate || ''),
      description: purchase.description || '',
      version: purchase.version,
    });
  };

  const handleEditPurchase = async (e: React.FormEvent) => {
    e.preventDefault();
    if (editingPurchaseId == null || savingRef.current) {
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError('');
    try {
      const updatedPurchase = await api.put<Purchase>(`/api/purchases/${editingPurchaseId}`, purchasePayload(editingPurchase));
      setPurchases((current) => current.map((purchase) => purchase.id === updatedPurchase.id ? updatedPurchase : purchase));
      setEditingPurchaseId(null);
      setEditingPurchase(emptyPurchase);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update purchase');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const remove = async (purchase: PurchaseListItem) => {
    if (!confirmDelete(`purchase from "${purchase.supplier?.name || 'supplier'}"`)) {
      return;
    }
    setError('');
    try {
      await api.delete(`/api/purchases/${purchase.id}`);
      setPurchases((current) => current.filter((currentPurchase) => currentPurchase.id !== purchase.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete purchase');
    }
  };

  const exportCsv = () => downloadCsv('purchases.csv', purchases, [
    { header: 'Date', value: (purchase) => purchase.purchaseDate },
    { header: 'Supplier', value: (purchase) => purchase.supplier?.name },
    { header: 'Material', value: (purchase) => purchase.materialType },
    { header: 'Description', value: (purchase) => purchase.description },
    { header: 'Quantity', value: (purchase) => purchase.quantity },
    { header: 'Rate', value: (purchase) => purchase.rate },
    { header: 'Amount', value: (purchase) => purchase.amount },
    { header: 'Due Date', value: (purchase) => purchase.dueDate },
    { header: 'Payment Date', value: (purchase) => purchase.paymentDate },
    { header: 'Payment Mode', value: (purchase) => purchase.paymentMode },
    { header: 'Status', value: (purchase) => purchase.status },
    { header: 'Created At', value: (purchase) => purchase.createdAt },
  ]);

  const purchaseTotal = Number(newPurchase.quantity || 0) * Number(newPurchase.rate || 0);
  const editPurchaseTotal = Number(editingPurchase.quantity || 0) * Number(editingPurchase.rate || 0);

  const purchaseForm = (
    purchase: typeof emptyPurchase,
    setPurchase: React.Dispatch<React.SetStateAction<typeof emptyPurchase>>,
    total: number,
  ) => (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      <input type="date" className="border rounded-md px-3 py-2" value={purchase.purchaseDate} onChange={(e) => setPurchase({ ...purchase, purchaseDate: e.target.value })} required />
      <select className="border rounded-md px-3 py-2" value={purchase.supplierId} onChange={(e) => setPurchase({ ...purchase, supplierId: e.target.value })} required>
        <option value="">Select Supplier</option>
        {suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
      </select>
      <select className="border rounded-md px-3 py-2" value={purchase.materialType} onChange={(e) => setPurchase({ ...purchase, materialType: e.target.value })} required>
        <option value="BEAM">BEAM</option>
        <option value="YARN">YARN</option>
        <option value="MISCELLANEOUS">Miscellaneous</option>
      </select>
      <input type="number" min="0.01" step="0.01" className="border rounded-md px-3 py-2" placeholder={purchase.materialType === 'YARN' ? 'Yarn quantity, e.g. 120 kg' : purchase.materialType === 'BEAM' ? 'Beam quantity, e.g. 4 beams' : 'Purchase quantity'} value={purchase.quantity} onChange={(e) => setPurchase({ ...purchase, quantity: e.target.value })} required />
      <input type="number" min="0.01" step="0.01" className="border rounded-md px-3 py-2" placeholder={purchase.materialType === 'YARN' ? 'Yarn price per kg' : purchase.materialType === 'BEAM' ? 'Beam price per unit' : 'Purchase price'} value={purchase.rate} onChange={(e) => setPurchase({ ...purchase, rate: e.target.value })} required />
      {purchase.materialType === 'MISCELLANEOUS' && (
        <textarea required className="border rounded-md px-3 py-2 md:col-span-2" placeholder="Description of purchase" value={purchase.description} onChange={(e) => setPurchase({ ...purchase, description: e.target.value })} />
      )}
      <div className="text-sm text-gray-700 flex items-center">Total: Rs {total.toFixed(2)}</div>
    </div>
  );

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <h1 className="page-title">Purchases</h1>
          <p className="page-subtitle">Track material buying, compare supplier costs, and keep records clean on both desktop and mobile.</p>
        </div>
        <div className="action-row">
          <button onClick={exportCsv} className="btn-secondary">
            <Download className="w-5 h-5" /> Export CSV
          </button>
          <button onClick={() => { void loadSuppliers(); setIsAdding(true); }} className="btn-primary">
            <Plus className="w-5 h-5" /> New Purchase
          </button>
        </div>
      </div>

      {error && <div className="mb-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}

      {isAdding && (
        <form onSubmit={handleNewPurchase} className="form-surface space-y-4">
          {purchaseForm(newPurchase, setNewPurchase, purchaseTotal)}
          <div className="flex gap-2">
            <button disabled={saving} className="btn-primary disabled:opacity-60">{saving ? 'Saving...' : 'Save Purchase'}</button>
            <button type="button" disabled={saving} className="btn-secondary disabled:opacity-60" onClick={() => setIsAdding(false)}>Cancel</button>
          </div>
        </form>
      )}

      {editingPurchaseId !== null && (
        <form onSubmit={handleEditPurchase} className="form-surface space-y-4">
          {purchaseForm(editingPurchase, setEditingPurchase, editPurchaseTotal)}
          <div className="flex gap-2">
            <button disabled={saving} className="btn-primary disabled:opacity-60">{saving ? 'Saving...' : 'Update Purchase'}</button>
            <button type="button" disabled={saving} className="btn-secondary disabled:opacity-60" onClick={() => setEditingPurchaseId(null)}>Cancel</button>
          </div>
        </form>
      )}

      <div className="table-surface" aria-busy={loading}>
        <div className="border-b border-stone-200/70 px-6 py-5">
          <h2 className="text-xl font-bold text-stone-900">Purchase List</h2>
          <p className="mt-1 text-sm text-stone-500">Browse all recorded purchases with cleaner spacing and easier scanning.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr>{['Date', 'Supplier', 'Material', 'Description', 'Quantity', 'Amount', 'Payment', 'Status', 'Created', 'Actions'].map(h => <th key={h}>{h}</th>)}</tr></thead>
            <tbody>
              {loading && purchases.length === 0 ? <SkeletonRows columns={10} /> : purchases.map((purchase) => (
                <tr key={purchase.id}>
                  <td>{format(new Date(purchase.purchaseDate), 'dd MMM yyyy')}</td>
                  <td>{purchase.supplier?.name}</td>
                  <td>{purchase.materialType}</td>
                  <td>{purchase.description}</td>
                  <td>{purchase.quantity}</td>
                  <td>Rs {purchase.amount?.toLocaleString()}</td>
                  <td>{purchase.paymentDate ? `${format(new Date(purchase.paymentDate), 'dd MMM yyyy')} / ${purchase.paymentMode}` : '-'}</td>
                  <td>{purchase.status}</td>
                  <td>{purchase.createdAt ? format(new Date(purchase.createdAt), 'dd MMM yyyy') : '-'}</td>
                  <td>
                    <div className="flex gap-3">
                      <button title="Edit purchase" onClick={() => startEdit(purchase)} className="text-blue-600 hover:text-blue-800"><Pencil className="w-5 h-5" /></button>
                      <button title="Delete purchase" onClick={() => remove(purchase)} className="text-red-600 hover:text-red-800"><Trash className="w-5 h-5" /></button>
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

export default Purchases;
