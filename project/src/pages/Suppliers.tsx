import React, { useEffect, useState } from 'react';
import { Download, Pencil, Plus, Search, Trash } from 'lucide-react';
import { api } from '../services/api';
import { confirmDelete, downloadCsv } from '../utils/csv';
import { PageResult, Supplier } from '../types';

const emptySupplier = { name: '', contact: '', address: '' };

function Suppliers() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [isAddingSupplier, setIsAddingSupplier] = useState(false);
  const [newSupplier, setNewSupplier] = useState(emptySupplier);
  const [editingSupplierId, setEditingSupplierId] = useState<number | null>(null);
  const [editingSupplier, setEditingSupplier] = useState(emptySupplier);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = async () => {
    const result = await api.get<PageResult<Supplier>>(`/api/suppliers?page=${page}&size=25`);
    setSuppliers(result.content);
    setTotalPages(result.totalPages);
  };
  useEffect(() => { load(); }, [page]);

  const handleAddSupplier = async (e: React.FormEvent) => {
    e.preventDefault();
    await api.post<Supplier>('/api/suppliers', { ...newSupplier, contact: newSupplier.contact.trim() });
    setNewSupplier(emptySupplier);
    setIsAddingSupplier(false);
    await load();
  };

  const startEdit = (supplier: Supplier) => {
    setEditingSupplierId(supplier.id);
    setEditingSupplier({
      name: supplier.name || '',
      contact: supplier.contact || '',
      address: supplier.address || '',
    });
  };

  const cancelEdit = () => {
    setEditingSupplierId(null);
    setEditingSupplier(emptySupplier);
  };

  const handleEditSupplier = async (e: React.FormEvent) => {
    e.preventDefault();
    if (editingSupplierId == null) {
      return;
    }
    await api.put<Supplier>(`/api/suppliers/${editingSupplierId}`, { ...editingSupplier, contact: editingSupplier.contact.trim() });
    cancelEdit();
    await load();
  };

  const remove = async (supplier: Supplier) => {
    if (!confirmDelete(`supplier "${supplier.name}"`)) {
      return;
    }
    await api.delete(`/api/suppliers/${supplier.id}`);
    await load();
  };

  const filteredSuppliers = suppliers.filter(supplier =>
    supplier.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    supplier.contact?.includes(searchTerm)
  );

  const exportCsv = () => downloadCsv('suppliers.csv', filteredSuppliers, [
    { header: 'Name', value: (supplier) => supplier.name },
    { header: 'Contact', value: (supplier) => supplier.contact },
    { header: 'Address', value: (supplier) => supplier.address },
    { header: 'Created At', value: (supplier) => supplier.createdAt },
  ]);

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <h1 className="page-title">Suppliers</h1>
          <p className="page-subtitle">Maintain supplier contacts and buying relationships with a more readable mobile-friendly layout.</p>
        </div>
        <div className="action-row">
          <button onClick={exportCsv} className="btn-secondary">
            <Download className="w-5 h-5" /> Export CSV
          </button>
          <button onClick={() => setIsAddingSupplier(true)} className="btn-primary">
            <Plus className="w-5 h-5" /> Add Supplier
          </button>
        </div>
      </div>

      {isAddingSupplier && (
        <form onSubmit={handleAddSupplier} className="form-surface space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <input required className="border rounded-md px-3 py-2" placeholder="Name" value={newSupplier.name} onChange={(e) => setNewSupplier({ ...newSupplier, name: e.target.value })} />
            <input required inputMode="numeric" pattern="[0-9]+" className="border rounded-md px-3 py-2" placeholder="Contact number" value={newSupplier.contact} onChange={(e) => setNewSupplier({ ...newSupplier, contact: e.target.value.replace(/\D/g, '') })} />
            <textarea className="border rounded-md px-3 py-2 md:col-span-2" placeholder="Address" value={newSupplier.address} onChange={(e) => setNewSupplier({ ...newSupplier, address: e.target.value })} />
          </div>
          <div className="flex gap-2">
            <button className="btn-primary">Save</button>
            <button type="button" onClick={() => setIsAddingSupplier(false)} className="btn-secondary">Cancel</button>
          </div>
        </form>
      )}

      {editingSupplierId !== null && (
        <form onSubmit={handleEditSupplier} className="form-surface space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <input required className="border rounded-md px-3 py-2" placeholder="Name" value={editingSupplier.name} onChange={(e) => setEditingSupplier({ ...editingSupplier, name: e.target.value })} />
            <input required inputMode="numeric" pattern="[0-9]+" className="border rounded-md px-3 py-2" placeholder="Contact number" value={editingSupplier.contact} onChange={(e) => setEditingSupplier({ ...editingSupplier, contact: e.target.value.replace(/\D/g, '') })} />
            <textarea className="border rounded-md px-3 py-2 md:col-span-2" placeholder="Address" value={editingSupplier.address} onChange={(e) => setEditingSupplier({ ...editingSupplier, address: e.target.value })} />
          </div>
          <div className="flex gap-2">
            <button className="btn-primary">Update</button>
            <button type="button" onClick={cancelEdit} className="btn-secondary">Cancel</button>
          </div>
        </form>
      )}

      <div className="table-surface p-6">
        <div className="mb-6 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-5 h-5" />
          <input placeholder="Search suppliers..." className="w-full !rounded-2xl !pl-10" value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
        </div>
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr>{['Name', 'Contact', 'Address', 'Created', 'Actions'].map(h => <th key={h}>{h}</th>)}</tr></thead>
            <tbody>
              {filteredSuppliers.map((supplier) => (
                <tr key={supplier.id}>
                  <td>{supplier.name}</td>
                  <td>{supplier.contact}</td>
                  <td>{supplier.address}</td>
                  <td>{supplier.createdAt ? formatDate(supplier.createdAt) : '-'}</td>
                  <td>
                    <div className="flex gap-3">
                      <button title="Edit supplier" onClick={() => startEdit(supplier)} className="text-blue-600 hover:text-blue-800"><Pencil className="w-5 h-5" /></button>
                      <button title="Delete supplier" onClick={() => remove(supplier)} className="text-red-600 hover:text-red-800"><Trash className="w-5 h-5" /></button>
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

export default Suppliers;

function formatDate(value: string) {
  return new Date(value).toLocaleDateString();
}
