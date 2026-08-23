import React, { useCallback, useEffect, useState } from 'react';
import { format, isAfter, parseISO } from 'date-fns';
import { AlertCircle, Download } from 'lucide-react';
import { api } from '../services/api';
import { downloadCsv } from '../utils/csv';
import { PageResult, Payment, PaymentUpdate } from '../types';
import { SkeletonRows } from '../components/ui/LoadingSkeleton';

const emptyPayment: PaymentUpdate = { paymentDate: '', paymentMode: 'CASH', chequeNo: '' };

function Payments() {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [activePayment, setActivePayment] = useState<Payment | null>(null);
  const [paymentDetails, setPaymentDetails] = useState<PaymentUpdate>(emptyPayment);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await api.get<PageResult<Payment>>(`/api/payments?page=${page}&size=25`);
      if (result.content.length === 0 && page > 0) {
        setPage((current) => current - 1);
        return;
      }
      setPayments(result.content);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load payments');
    } finally {
      setLoading(false);
    }
  }, [page]);
  useEffect(() => { void load(); }, [load]);

  const openPaymentForm = (payment: Payment) => {
    setActivePayment(payment);
    setPaymentDetails({ ...emptyPayment, paymentDate: payment.sourceDate });
  };

  const markPaid = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!activePayment) {
      return;
    }
    setSaving(true);
    setError('');
    try {
      await api.patchBody(
        activePayment.type === 'TO_SUPPLIER' ? `/api/purchases/${activePayment.sourceId}/paid` : `/api/sales/${activePayment.sourceId}/paid`,
        paymentDetails,
      );
      setActivePayment(null);
      setPaymentDetails(emptyPayment);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update this payment');
    } finally {
      setSaving(false);
    }
  };

  const exportCsv = async () => {
    setExporting(true);
    setError('');
    try {
      const firstPage = await api.get<PageResult<Payment>>('/api/payments?page=0&size=100');
      const allPayments = [...firstPage.content];
      for (let exportPage = 1; exportPage < firstPage.totalPages; exportPage += 1) {
        const result = await api.get<PageResult<Payment>>(`/api/payments?page=${exportPage}&size=100`);
        allPayments.push(...result.content);
      }
      downloadCsv('payments.csv', allPayments, [
        { header: 'Type', value: (payment) => payment.type },
        { header: 'Party', value: (payment) => payment.entityName },
        { header: 'Material/Quality', value: (payment) => payment.materialOrClothType },
        { header: 'Source Date', value: (payment) => payment.sourceDate },
        { header: 'Due Date', value: (payment) => payment.dueDate },
        { header: 'Amount', value: (payment) => payment.amount },
        { header: 'Payment Date', value: (payment) => payment.paymentDate },
        { header: 'Payment Mode', value: (payment) => payment.paymentMode },
        { header: 'Cheque No', value: (payment) => payment.chequeNo },
        { header: 'Status', value: (payment) => payment.status },
      ]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not export payments');
    } finally {
      setExporting(false);
    }
  };

  const section = (title: string, type: Payment['type'], action: string) => {
    const items = payments.filter((p) => p.type === type);
    return (
      <div className="bg-white rounded-xl shadow-md p-6" aria-busy={loading}>
        <h2 className="text-xl font-semibold text-gray-900 mb-4">{title}</h2>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead><tr>{['Due Date', 'Party', 'Material/Quality', 'Amount', 'Status'].map(h => <th key={h} className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>)}</tr></thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {loading && payments.length === 0 ? <SkeletonRows columns={5} rows={5} /> : items.map((payment) => {
                const overdue = isAfter(new Date(), parseISO(payment.dueDate));
                return (
                  <tr key={`${payment.type}-${payment.sourceId}`} className={overdue ? 'bg-red-50' : ''}>
                    <td className="px-6 py-4 text-sm"><span className="flex items-center gap-2">{overdue && <AlertCircle className="w-4 h-4 text-red-500" />}{format(new Date(payment.dueDate), 'dd MMM yyyy')}</span></td>
                    <td className="px-6 py-4 text-sm">{payment.entityName}</td>
                    <td className="px-6 py-4 text-sm">{payment.materialOrClothType}</td>
                    <td className="px-6 py-4 text-sm">Rs {payment.amount?.toLocaleString()}</td>
                    <td className="px-6 py-4 text-sm"><button onClick={() => openPaymentForm(payment)} className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">{action}</button></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    );
  };

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <h1 className="page-title">Payments</h1>
          <p className="page-subtitle">Follow overdue collections and supplier payouts with a clearer action-focused layout.</p>
        </div>
        <button disabled={exporting} onClick={() => void exportCsv()} className="btn-secondary disabled:opacity-60">
          <Download className="w-5 h-5" /> {exporting ? 'Exporting...' : 'Export CSV'}
        </button>
      </div>
      {error && <div role="alert" className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {loading && payments.length > 0 && <p className="text-sm text-stone-500" aria-live="polite">Refreshing payments...</p>}
      {activePayment && (
        <form onSubmit={markPaid} className="form-surface space-y-4">
          <div className="font-semibold text-gray-900">
            {activePayment.type === 'TO_SUPPLIER' ? 'Mark payment as paid' : 'Mark payment as received'} - {activePayment.entityName}
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <input type="date" required min={activePayment.sourceDate} className="border rounded-md px-3 py-2" value={paymentDetails.paymentDate} onChange={(e) => setPaymentDetails({ ...paymentDetails, paymentDate: e.target.value })} />
            <select className="border rounded-md px-3 py-2" value={paymentDetails.paymentMode} onChange={(e) => setPaymentDetails({ ...paymentDetails, paymentMode: e.target.value as PaymentUpdate['paymentMode'], chequeNo: '' })}>
              <option value="CASH">Cash</option>
              <option value="CHEQUE">Cheque</option>
              <option value="UPI">UPI</option>
            </select>
            {paymentDetails.paymentMode === 'CHEQUE' && (
              <input required className="border rounded-md px-3 py-2" placeholder="Cheque number" value={paymentDetails.chequeNo || ''} onChange={(e) => setPaymentDetails({ ...paymentDetails, chequeNo: e.target.value })} />
            )}
          </div>
          <div className="flex gap-2">
            <button disabled={saving} className="btn-primary disabled:opacity-60">{saving ? 'Saving...' : 'Save Payment'}</button>
            <button type="button" disabled={saving} className="btn-secondary disabled:opacity-60" onClick={() => setActivePayment(null)}>Cancel</button>
          </div>
        </form>
      )}
      {section('Payments to Suppliers', 'TO_SUPPLIER', 'Mark as Paid')}
      {section('Payments from Customers', 'FROM_CUSTOMER', 'Received')}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <button className="btn-secondary" disabled={loading || page === 0} onClick={() => setPage((current) => current - 1)}>
            Previous
          </button>
          <span>Page {page + 1} of {totalPages}</span>
          <button className="btn-secondary" disabled={loading || page + 1 >= totalPages} onClick={() => setPage((current) => current + 1)}>
            Next
          </button>
        </div>
      )}
    </div>
  );
}

export default Payments;
