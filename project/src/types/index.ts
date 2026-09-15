export interface Customer {
  id: number;
  name: string;
  contact: string;
  address: string;
  gstNo: string;
  brokerName?: string;
  deliveryAddress?: string;
  version?: number;
  createdAt?: string;
}

export interface Supplier {
  id: number;
  name: string;
  contact: string;
  address: string;
  version?: number;
  createdAt?: string;
}

export interface CompanyProfile {
  id: number;
  tradeName: string;
  gstNo?: string;
  phone?: string;
  address?: string;
  defaultBroker?: string;
  defaultQuality?: string;
  logoContentType?: string;
  logoWidth?: number;
  logoHeight?: number;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'STAFF' | 'ACCOUNTANT' | 'VIEWER';

export interface WorkspaceSummary {
  id: number;
  name: string;
  role: WorkspaceRole;
}

export interface WorkspaceMember {
  userId: number;
  username: string;
  email?: string;
  displayName?: string;
  role: WorkspaceRole;
  createdAt?: string;
}

export interface InvitationCreated {
  id: number;
  email: string;
  role: Exclude<WorkspaceRole, 'OWNER'>;
  expiresAt: string;
  token: string;
}

export interface TakaEntry {
  id?: number;
  takaNo: number;
  meters: number;
}

export interface SavedTakaEntry {
  id: number;
  takaNo: number;
  meters: number;
  createdAt?: string;
}

export interface Sale {
  id: number;
  saleDate: string;
  customer: Customer;
  brokerName?: string;
  quality: string;
  challanNo: number;
  challanCount?: number;
  balanceChallanColumnsByMeters?: boolean;
  billNo?: number;
  financialYear: string;
  dueDate: string;
  paymentDate?: string;
  paymentMode?: PaymentMode;
  chequeNo?: string;
  status: 'PENDING' | 'PAID';
  rate: number;
  amount: number;
  totalMeters: number;
  version?: number;
  takaEntries: TakaEntry[];
  createdAt?: string;
}

export type SaleListItem = Omit<Sale, 'takaEntries' | 'customer'> & {
  customer: Pick<Customer, 'id' | 'name'> & { brokerName?: string };
};

export interface Purchase {
  id: number;
  purchaseDate: string;
  supplier: Supplier;
  materialType: 'BEAM' | 'YARN' | 'MISCELLANEOUS';
  quantity: number;
  rate: number;
  amount: number;
  description?: string;
  dueDate: string;
  paymentDate?: string;
  paymentMode?: PaymentMode;
  chequeNo?: string;
  status: 'PENDING' | 'PAID';
  version?: number;
  createdAt?: string;
}

export type PurchaseListItem = Omit<Purchase, 'supplier'> & {
  supplier: Pick<Supplier, 'id' | 'name'>;
};

export interface Payment {
  type: 'TO_SUPPLIER' | 'FROM_CUSTOMER';
  sourceId: number;
  sourceDate: string;
  dueDate: string;
  entityName: string;
  materialOrClothType: string;
  amount: number;
  version: number;
  paymentDate?: string;
  paymentMode?: PaymentMode;
  chequeNo?: string;
  status: 'PENDING' | 'PAID';
}

export type PaymentMode = 'CASH' | 'CHEQUE' | 'UPI';

export interface PaymentUpdate {
  paymentDate: string;
  paymentMode: PaymentMode;
  chequeNo?: string;
  version: number;
}

export interface DashboardSummary {
  period: 'weekly' | 'monthly' | 'yearly';
  periodStart: string;
  periodEnd: string;
  current: DashboardPeriodTotals;
  previous: DashboardPeriodTotals;
  salesChangePercent: number;
  purchasesChangePercent: number;
  outstandingReceivables: number;
  outstandingPayables: number;
  overdueReceivables: number;
  overduePayables: number;
  topQuality: {
    quality: string;
    amount: number;
  };
  trend: Array<{
    label: string;
    sales: number;
    purchases: number;
    net: number;
  }>;
}

export interface DashboardPeriodTotals {
  totalSales: number;
  totalPurchases: number;
  net: number;
  saleCount: number;
  purchaseCount: number;
  totalMeters: number;
  totalPurchaseQuantity: number;
  averageSalesRate: number;
  averageSaleValue: number;
  averagePurchaseValue: number;
}
