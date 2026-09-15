import React, { useCallback, useEffect, useState } from 'react';
import { Download, Pencil, Plus, Search, Trash } from 'lucide-react';
import { api } from '../services/api';
import { confirmDelete, downloadCsv } from '../utils/csv';
import { Customer, PageResult } from '../types';
import { SkeletonRows } from '../components/ui/LoadingSkeleton';

const emptyCustomer = { name: '', contact: '', address: '', gstNo: '', brokerName: '', deliveryAddress: '' };
const gstPattern = '[A-Za-z0-9]{15}';

function Customers() {
  const [error, setError] = useState('');
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [isAddingCustomer, setIsAddingCustomer] = useState(false);
  const [newCustomer, setNewCustomer] = useState(emptyCustomer);
  const [editingCustomerId, setEditingCustomerId] = useState<number | null>(null);
  const [editingCustomerVersion, setEditingCustomerVersion] = useState<number | undefined>();
  const [editingCustomer, setEditingCustomer] = useState(emptyCustomer);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await api.get<PageResult<Customer>>(`/api/customers?page=${page}&size=25`);
      setCustomers(result.content);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load customers');
    } finally {
      setLoading(false);
    }
  }, [page]);
  useEffect(() => { load(); }, [load]);

  const customerPayload = (customer: typeof emptyCustomer, version?: number) => ({
    ...customer,
    contact: customer.contact.trim(),
    gstNo: customer.gstNo.trim().toUpperCase(),
    ...(version === undefined ? {} : { version }),
  });

  const handleAddCustomer = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      const createdCustomer = await api.post<Customer>('/api/customers', customerPayload(newCustomer));
      setCustomers((current) => page === 0
        ? [...current, createdCustomer].sort((left, right) => left.name.localeCompare(right.name)).slice(0, 25)
        : current);
      setNewCustomer(emptyCustomer);
      setIsAddingCustomer(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save customer');
    }
  };

  const startEdit = (customer: Customer) => {
    setEditingCustomerId(customer.id);
    setEditingCustomerVersion(customer.version);
    setEditingCustomer({
      name: customer.name || '',
      contact: customer.contact || '',
      address: customer.address || '',
      gstNo: customer.gstNo || '',
      brokerName: customer.brokerName || '',
      deliveryAddress: customer.deliveryAddress || '',
    });
  };

  const cancelEdit = () => {
    setEditingCustomerId(null);
    setEditingCustomerVersion(undefined);
    setEditingCustomer(emptyCustomer);
  };

  const handleEditCustomer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (editingCustomerId == null) {
      return;
    }
    setError('');
    try {
      const updatedCustomer = await api.put<Customer>(`/api/customers/${editingCustomerId}`, customerPayload(editingCustomer, editingCustomerVersion));
      setCustomers((current) => current.map((customer) => customer.id === updatedCustomer.id ? updatedCustomer : customer));
      cancelEdit();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update customer');
    }
  };

  const remove = async (customer: Customer) => {
    if (!confirmDelete(`customer "${customer.name}"`)) {
      return;
    }
    setError('');
    try {
      await api.delete(`/api/customers/${customer.id}`);
      setCustomers((current) => current.filter((currentCustomer) => currentCustomer.id !== customer.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete customer');
    }
  };

  const exportCsv = () => downloadCsv('customers.csv', filteredCustomers, [
    { header: 'Name', value: (customer) => customer.name },
    { header: 'Contact', value: (customer) => customer.contact },
    { header: 'GST', value: (customer) => customer.gstNo },
    { header: 'Broker', value: (customer) => customer.brokerName },
    { header: 'Address', value: (customer) => customer.address },
    { header: 'Delivery Address', value: (customer) => customer.deliveryAddress },
    { header: 'Created At', value: (customer) => customer.createdAt },
  ]);

  const filteredCustomers = customers.filter(customer =>
    customer.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    customer.contact?.includes(searchTerm)
  );

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <h1 className="page-title">Customers</h1>
          <p className="page-subtitle">Keep customer records, GST details, and delivery notes easy to access from any screen size.</p>
        </div>
        <div className="action-row">
          <button onClick={exportCsv} className="btn-secondary">
            <Download className="w-5 h-5" /> Export CSV
          </button>
          <button onClick={() => setIsAddingCustomer(true)} className="btn-primary">
            <Plus className="w-5 h-5" /> Add Customer
          </button>
        </div>
      </div>

      {error && <div className="mb-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}

      {isAddingCustomer && (
        <form onSubmit={handleAddCustomer} className="form-surface space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <input required className="border rounded-md px-3 py-2" placeholder="Name" value={newCustomer.name} onChange={(e) => setNewCustomer({ ...newCustomer, name: e.target.value })} />
            <input required inputMode="numeric" pattern="[0-9]+" className="border rounded-md px-3 py-2" placeholder="Contact number" value={newCustomer.contact} onChange={(e) => setNewCustomer({ ...newCustomer, contact: e.target.value.replace(/\D/g, '') })} />
            <input required className="border rounded-md px-3 py-2 uppercase" placeholder="15 digit GST No" maxLength={15} pattern={gstPattern} title="GST No must be exactly 15 letters or numbers" value={newCustomer.gstNo} onChange={(e) => setNewCustomer({ ...newCustomer, gstNo: e.target.value.replace(/[^a-z0-9]/gi, '').toUpperCase() })} />
            <input className="border rounded-md px-3 py-2" placeholder="Broker" value={newCustomer.brokerName} onChange={(e) => setNewCustomer({ ...newCustomer, brokerName: e.target.value })} />
            <textarea required className="border rounded-md px-3 py-2 md:col-span-2" placeholder="Address" value={newCustomer.address} onChange={(e) => setNewCustomer({ ...newCustomer, address: e.target.value })} />
            <textarea className="border rounded-md px-3 py-2 md:col-span-2" placeholder="Delivery Address" value={newCustomer.deliveryAddress} onChange={(e) => setNewCustomer({ ...newCustomer, deliveryAddress: e.target.value })} />
          </div>
          <div className="flex gap-2">
            <button className="btn-primary">Save</button>
            <button type="button" onClick={() => setIsAddingCustomer(false)} className="btn-secondary">Cancel</button>
          </div>
        </form>
      )}

      {editingCustomerId !== null && (
        <form onSubmit={handleEditCustomer} className="form-surface space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <input required className="border rounded-md px-3 py-2" placeholder="Name" value={editingCustomer.name} onChange={(e) => setEditingCustomer({ ...editingCustomer, name: e.target.value })} />
            <input required inputMode="numeric" pattern="[0-9]+" className="border rounded-md px-3 py-2" placeholder="Contact number" value={editingCustomer.contact} onChange={(e) => setEditingCustomer({ ...editingCustomer, contact: e.target.value.replace(/\D/g, '') })} />
            <input required className="border rounded-md px-3 py-2 uppercase" placeholder="15 digit GST No" maxLength={15} pattern={gstPattern} title="GST No must be exactly 15 letters or numbers" value={editingCustomer.gstNo} onChange={(e) => setEditingCustomer({ ...editingCustomer, gstNo: e.target.value.replace(/[^a-z0-9]/gi, '').toUpperCase() })} />
            <input className="border rounded-md px-3 py-2" placeholder="Broker" value={editingCustomer.brokerName} onChange={(e) => setEditingCustomer({ ...editingCustomer, brokerName: e.target.value })} />
            <textarea required className="border rounded-md px-3 py-2 md:col-span-2" placeholder="Address" value={editingCustomer.address} onChange={(e) => setEditingCustomer({ ...editingCustomer, address: e.target.value })} />
            <textarea className="border rounded-md px-3 py-2 md:col-span-2" placeholder="Delivery Address" value={editingCustomer.deliveryAddress} onChange={(e) => setEditingCustomer({ ...editingCustomer, deliveryAddress: e.target.value })} />
          </div>
          <div className="flex gap-2">
            <button className="btn-primary">Update</button>
            <button type="button" onClick={cancelEdit} className="btn-secondary">Cancel</button>
          </div>
        </form>
      )}

      <div className="table-surface p-6" aria-busy={loading}>
        <div className="mb-6 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-5 h-5" />
          <input placeholder="Search customers..." className="w-full !rounded-2xl !pl-10" value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
        </div>
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr>{['Name', 'Contact', 'GST', 'Address', 'Created', 'Actions'].map(h => <th key={h}>{h}</th>)}</tr></thead>
            <tbody>
              {loading && customers.length === 0 ? <SkeletonRows columns={6} /> : filteredCustomers.map((customer) => (
                <tr key={customer.id}>
                  <td>{customer.name}</td>
                  <td>{customer.contact}</td>
                  <td>{customer.gstNo}</td>
                  <td>{customer.address}</td>
                  <td>{customer.createdAt ? formatDate(customer.createdAt) : '-'}</td>
                  <td>
                    <div className="flex gap-3">
                      <button title="Edit customer" onClick={() => startEdit(customer)} className="text-blue-600 hover:text-blue-800"><Pencil className="w-5 h-5" /></button>
                      <button title="Delete customer" onClick={() => remove(customer)} className="text-red-600 hover:text-red-800"><Trash className="w-5 h-5" /></button>
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

export default Customers;

function formatDate(value: string) {
  return new Date(value).toLocaleDateString();
}
