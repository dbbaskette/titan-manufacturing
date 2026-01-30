// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Order Fulfillment Tracker
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect, useCallback } from 'react';
import {
  Package,
  Search,
  Clock,
  CheckCircle,
  Truck,
  ArrowUpRight,
  RefreshCw,
  FileText,
  Calendar,
  AlertCircle,
  User,
  CreditCard,
} from 'lucide-react';
import { titanApi, type OrderSummary, type OrderDetails, type OrderCounts } from '../api/titanApi';

const STATUS_CONFIG = {
  pending: { label: 'Pending', color: 'text-slate', bg: 'bg-slate/20', icon: Clock },
  validated: { label: 'Validated', color: 'text-info', bg: 'bg-info/20', icon: CheckCircle },
  processing: { label: 'Processing', color: 'text-warning', bg: 'bg-warning/20', icon: Package },
  in_progress: { label: 'Processing', color: 'text-warning', bg: 'bg-warning/20', icon: Package },
  confirmed: { label: 'Confirmed', color: 'text-info', bg: 'bg-info/20', icon: CheckCircle },
  expedite: { label: 'Expedite', color: 'text-ember', bg: 'bg-ember/20', icon: Truck },
  shipped: { label: 'Shipped', color: 'text-ember', bg: 'bg-ember/20', icon: Truck },
  delivered: { label: 'Delivered', color: 'text-healthy', bg: 'bg-healthy/20', icon: CheckCircle },
};

const PRIORITY_CONFIG = {
  standard: { label: 'Standard', color: 'text-slate border-slate/30' },
  expedite: { label: 'Expedite', color: 'text-warning border-warning/30' },
  critical: { label: 'Critical', color: 'text-critical border-critical/30' },
};

const EVENT_ICONS: Record<string, React.ElementType> = {
  CREATED: FileText,
  VALIDATED: CheckCircle,
  STATUS_CHANGED: RefreshCw,
  FULFILLMENT_INITIATED: Package,
  SHIPPED: Truck,
  DELIVERED: CheckCircle,
};

export function OrderTracker() {
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [counts, setCounts] = useState<OrderCounts | null>(null);
  const [selectedOrderDetails, setSelectedOrderDetails] = useState<OrderDetails | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchOrders = useCallback(async () => {
    try {
      const [ordersData, countsData] = await Promise.all([
        titanApi.getOrders(),
        titanApi.getOrderCounts(),
      ]);
      setOrders(ordersData);
      setCounts(countsData);
      setError(null);

      // Auto-select first order if none selected
      if (ordersData.length > 0 && !selectedOrderDetails) {
        const details = await titanApi.getOrderDetails(ordersData[0].order_id);
        setSelectedOrderDetails(details);
      }
    } catch (e) {
      setError('Failed to fetch orders');
      console.error('Order fetch error:', e);
    } finally {
      setLoading(false);
    }
  }, [selectedOrderDetails]);

  useEffect(() => {
    fetchOrders();
    const interval = setInterval(fetchOrders, 30000); // Refresh every 30s
    return () => clearInterval(interval);
  }, [fetchOrders]);

  const handleSelectOrder = async (orderId: string) => {
    try {
      const details = await titanApi.getOrderDetails(orderId);
      setSelectedOrderDetails(details);
    } catch (e) {
      console.error('Failed to fetch order details:', e);
    }
  };

  const normalizeStatus = (status: string): string => {
    const s = status.toLowerCase();
    if (s === 'in_progress' || s === 'confirmed') return 'processing';
    if (s === 'expedite') return 'processing';
    return s;
  };

  const filteredOrders = orders.filter((order) => {
    const matchesSearch =
      order.order_id.toLowerCase().includes(searchQuery.toLowerCase()) ||
      order.customer_name.toLowerCase().includes(searchQuery.toLowerCase());
    const normalized = normalizeStatus(order.status);
    const matchesStatus = statusFilter === 'all' || normalized === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const statusCounts = counts?.counts || {
    pending: 0,
    validated: 0,
    processing: 0,
    shipped: 0,
    delivered: 0,
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <RefreshCw className="animate-spin text-ember mr-2" size={24} />
        <span className="text-slate">Loading orders...</span>
      </div>
    );
  }

  return (
    <div className="space-y-6 fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <Package className="text-ember" />
            Order Fulfillment Tracker
          </h2>
          <p className="text-slate mt-1">B2B order processing and shipment tracking</p>
        </div>
        {error && (
          <div className="flex items-center gap-2 px-3 py-1.5 bg-critical/20 rounded text-critical text-sm">
            <AlertCircle size={14} />
            {error}
          </div>
        )}
      </div>

      {/* Pipeline Summary */}
      <div className="grid grid-cols-4 gap-4">
        <div className="stagger scale-in stagger-1">
          <PipelineCard
            label="Pending"
            count={statusCounts.pending}
            icon={Clock}
            color="slate"
            onClick={() => setStatusFilter(statusFilter === 'pending' ? 'all' : 'pending')}
            active={statusFilter === 'pending'}
          />
        </div>
        <div className="stagger scale-in stagger-2">
          <PipelineCard
            label="Processing"
            count={statusCounts.processing}
            icon={Package}
            color="warning"
            onClick={() => setStatusFilter(statusFilter === 'processing' ? 'all' : 'processing')}
            active={statusFilter === 'processing'}
          />
        </div>
        <div className="stagger scale-in stagger-3">
          <PipelineCard
            label="Shipped"
            count={statusCounts.shipped}
            icon={Truck}
            color="ember"
            onClick={() => setStatusFilter(statusFilter === 'shipped' ? 'all' : 'shipped')}
            active={statusFilter === 'shipped'}
          />
        </div>
        <div className="stagger scale-in stagger-4">
          <PipelineCard
            label="Delivered"
            count={statusCounts.delivered}
            icon={CheckCircle}
            color="healthy"
            onClick={() => setStatusFilter(statusFilter === 'delivered' ? 'all' : 'delivered')}
            active={statusFilter === 'delivered'}
          />
        </div>
      </div>

      <div className="grid grid-cols-3 gap-6">
        {/* Order List */}
        <div className="col-span-1 panel stagger slide-in-left stagger-5">
          <div className="panel-header justify-between">
            <div className="flex items-center gap-2">
              <Package size={16} />
              Orders
            </div>
            <span className="text-xs text-slate font-normal">{filteredOrders.length} total</span>
          </div>

          {/* Search */}
          <div className="p-3 border-b border-iron">
            <div className="relative">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ash" />
              <input
                type="text"
                placeholder="Search orders..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="input-field pl-10"
              />
            </div>
          </div>

          {/* List */}
          <div className="max-h-[500px] overflow-y-auto">
            {filteredOrders.map((order) => {
              const normalizedStatus = normalizeStatus(order.status);
              const config = STATUS_CONFIG[normalizedStatus as keyof typeof STATUS_CONFIG] || STATUS_CONFIG.pending;
              const priorityConfig = PRIORITY_CONFIG[(order.priority?.toLowerCase() || 'standard') as keyof typeof PRIORITY_CONFIG] || PRIORITY_CONFIG.standard;

              return (
                <div
                  key={order.order_id}
                  onClick={() => handleSelectOrder(order.order_id)}
                  className={`p-4 border-b border-iron cursor-pointer transition-all hover:bg-steel ${
                    selectedOrderDetails?.order_id === order.order_id ? 'bg-steel border-l-2 border-l-ember' : ''
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-sm text-white">{order.order_id}</span>
                    <span
                      className={`px-2 py-0.5 rounded text-[10px] uppercase border ${priorityConfig.color}`}
                    >
                      {priorityConfig.label}
                    </span>
                  </div>
                  <p className="text-xs text-slate mb-2">{order.customer_name}</p>
                  <div className="flex items-center justify-between">
                    <span className={`flex items-center gap-1.5 text-xs ${config.color}`}>
                      <config.icon size={12} />
                      {config.label}
                    </span>
                    <span className="text-xs text-ash font-mono">
                      ${((order.total_amount || 0) / 1000).toFixed(0)}K
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Order Details */}
        <div className="col-span-2 space-y-4">
          {selectedOrderDetails ? (
            <>
              {/* Header Card */}
              <div className="panel p-6">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-3 mb-2">
                      <h3 className="font-display text-xl font-bold text-white">
                        {selectedOrderDetails.order_id}
                      </h3>
                      <span
                        className={`px-2 py-0.5 rounded text-xs uppercase border ${
                          PRIORITY_CONFIG[(selectedOrderDetails.priority?.toLowerCase() || 'standard') as keyof typeof PRIORITY_CONFIG]?.color || PRIORITY_CONFIG.standard.color
                        }`}
                      >
                        {selectedOrderDetails.priority || 'Standard'}
                      </span>
                    </div>
                    <p className="text-slate">{selectedOrderDetails.customer_name}</p>
                    <p className="text-xs text-ash mt-1">{selectedOrderDetails.tier} customer</p>
                  </div>
                  <StatusBadge status={selectedOrderDetails.status} />
                </div>

                {/* Order Timeline */}
                <div className="mt-6">
                  <OrderTimeline status={normalizeStatus(selectedOrderDetails.status)} />
                </div>
              </div>

              {/* Order Info + Contract */}
              <div className="grid grid-cols-2 gap-4">
                <div className="panel p-4">
                  <div className="flex items-center gap-2 text-xs text-ash uppercase tracking-wider mb-3">
                    <Calendar size={12} />
                    Order Details
                  </div>
                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between">
                      <span className="text-slate">Order Date</span>
                      <span className="text-white font-mono">{formatDate(selectedOrderDetails.order_date)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate">Required Date</span>
                      <span className="text-white font-mono">{formatDate(selectedOrderDetails.required_date)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate">Line Items</span>
                      <span className="text-white">{selectedOrderDetails.lines?.length || 0}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate">Total Value</span>
                      <span className="text-white font-mono">${((selectedOrderDetails.total_amount || 0) / 1000).toFixed(0)}K</span>
                    </div>
                  </div>
                </div>

                {/* Customer Contract */}
                <div className="panel p-4">
                  <div className="flex items-center gap-2 text-xs text-ash uppercase tracking-wider mb-3">
                    <CreditCard size={12} />
                    Contract Terms
                  </div>
                  {selectedOrderDetails.contract && (
                    <div className="space-y-2 text-sm">
                      <div className="flex justify-between">
                        <span className="text-slate">Type</span>
                        <span className={`${selectedOrderDetails.contract.contract_type === 'STRATEGIC' ? 'text-ember' : selectedOrderDetails.contract.contract_type === 'PREMIUM' ? 'text-warning' : 'text-white'}`}>
                          {selectedOrderDetails.contract.contract_type}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate">Priority Level</span>
                        <span className="text-white">{selectedOrderDetails.contract.priority_level}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate">Discount</span>
                        <span className="text-healthy">{selectedOrderDetails.contract.discount_percent}%</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate">Payment Terms</span>
                        <span className="text-white">Net {selectedOrderDetails.contract.payment_terms}</span>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Order Lines */}
              {selectedOrderDetails.lines && selectedOrderDetails.lines.length > 0 && (
                <div className="panel">
                  <div className="panel-header">
                    <Package size={16} />
                    Order Lines
                  </div>
                  <div className="divide-y divide-iron">
                    {selectedOrderDetails.lines.map((line) => (
                      <div key={line.line_id} className="p-4 flex items-center justify-between">
                        <div>
                          <div className="text-sm text-white">{line.product_name}</div>
                          <div className="text-xs text-ash font-mono">{line.sku}</div>
                        </div>
                        <div className="text-right">
                          <div className="text-sm text-white">
                            {line.qty_shipped}/{line.quantity} shipped
                          </div>
                          <div className="text-xs text-ash font-mono">
                            ${line.unit_price?.toFixed(2)} × {line.quantity}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Order Events Timeline */}
              {selectedOrderDetails.events && selectedOrderDetails.events.length > 0 && (
                <div className="panel">
                  <div className="panel-header">
                    <Clock size={16} />
                    Event Timeline
                  </div>
                  <div className="p-4">
                    <div className="relative">
                      {/* Timeline line */}
                      <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-iron" />

                      {selectedOrderDetails.events.map((event, idx) => {
                        const EventIcon = EVENT_ICONS[event.event_type] || FileText;
                        return (
                          <div key={event.event_id || idx} className="relative pl-10 pb-4 last:pb-0">
                            {/* Timeline dot */}
                            <div className="absolute left-2.5 w-3 h-3 rounded-full bg-steel border-2 border-ember" />

                            <div className="flex items-start justify-between">
                              <div>
                                <div className="flex items-center gap-2">
                                  <EventIcon size={14} className="text-ember" />
                                  <span className="text-sm text-white font-medium">
                                    {event.event_type.replace(/_/g, ' ')}
                                  </span>
                                </div>
                                {event.notes && (
                                  <p className="text-xs text-slate mt-1">{event.notes}</p>
                                )}
                              </div>
                              <div className="text-xs text-ash font-mono">
                                {formatDateTime(event.event_timestamp)}
                              </div>
                            </div>
                            {event.created_by && (
                              <div className="flex items-center gap-1 text-[10px] text-ash mt-1">
                                <User size={10} />
                                {event.created_by}
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}

              {/* Shipments */}
              {selectedOrderDetails.shipments && selectedOrderDetails.shipments.length > 0 && (
                <div className="panel">
                  <div className="panel-header">
                    <Truck size={16} />
                    Shipments
                  </div>
                  <div className="divide-y divide-iron">
                    {selectedOrderDetails.shipments.map((shipment) => (
                      <div key={shipment.shipment_id} className="p-4">
                        <div className="flex items-center justify-between mb-3">
                          <div className="flex items-center gap-3">
                            <span className="font-mono text-sm text-white">{shipment.shipment_id}</span>
                            <span className="px-2 py-0.5 bg-steel rounded text-xs text-slate">
                              {shipment.carrier_name}
                            </span>
                          </div>
                          <ShipmentStatusBadge status={shipment.status} />
                        </div>
                        <div className="flex items-center gap-4 text-sm">
                          <div className="flex items-center gap-2">
                            <div className="w-2 h-2 rounded-full bg-ember" />
                            <span className="text-slate">{shipment.origin_facility}</span>
                          </div>
                          <ArrowUpRight size={14} className="text-iron" />
                          <div className="flex items-center gap-2">
                            <div className="w-2 h-2 rounded-full bg-healthy" />
                            <span className="text-slate">Destination</span>
                          </div>
                        </div>
                        <div className="mt-2 text-xs text-ash font-mono">
                          Tracking: {shipment.tracking_number || 'Pending'}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="panel p-12 text-center">
              <Package size={48} className="mx-auto text-iron mb-4" />
              <p className="text-slate">Select an order to view details</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-';
  try {
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  } catch {
    return dateStr;
  }
}

function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '-';
  try {
    const date = new Date(dateStr);
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  } catch {
    return dateStr;
  }
}

function PipelineCard({
  label,
  count,
  icon: Icon,
  color,
  onClick,
  active,
}: {
  label: string;
  count: number;
  icon: React.ElementType;
  color: string;
  onClick: () => void;
  active: boolean;
}) {
  const colors: Record<string, string> = {
    slate: 'bg-slate/10 border-slate/30 text-slate',
    warning: 'bg-warning/10 border-warning/30 text-warning',
    ember: 'bg-ember/10 border-ember/30 text-ember',
    healthy: 'bg-healthy/10 border-healthy/30 text-healthy',
  };

  return (
    <button
      onClick={onClick}
      className={`panel card-interactive p-4 text-left transition-all ${active ? colors[color] : ''}`}
    >
      <div className="flex items-center justify-between">
        <Icon size={20} className={active ? '' : 'text-slate'} />
        <span className="font-display text-2xl font-bold glow-text">{count}</span>
      </div>
      <p className="text-sm text-slate mt-2">{label}</p>
    </button>
  );
}

function StatusBadge({ status }: { status: string }) {
  const normalizedStatus = status.toLowerCase();
  const config = STATUS_CONFIG[normalizedStatus as keyof typeof STATUS_CONFIG] || STATUS_CONFIG.pending;
  const Icon = config.icon;

  return (
    <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg ${config.bg}`}>
      <Icon size={14} className={config.color} />
      <span className={`text-sm font-medium ${config.color}`}>{config.label}</span>
    </div>
  );
}

function OrderTimeline({ status }: { status: string }) {
  const steps = ['pending', 'processing', 'shipped', 'delivered'];
  const currentIndex = steps.indexOf(status);

  return (
    <div className="flex items-center justify-between">
      {steps.map((step, i) => {
        const config = STATUS_CONFIG[step as keyof typeof STATUS_CONFIG] || STATUS_CONFIG.pending;
        const Icon = config.icon;
        const isComplete = i <= currentIndex;
        const isCurrent = i === currentIndex;

        return (
          <div key={step} className="flex items-center flex-1">
            <div className="flex flex-col items-center">
              <div
                className={`w-10 h-10 rounded-full flex items-center justify-center ${
                  isComplete ? config.bg : 'bg-steel'
                }`}
              >
                <Icon size={18} className={isComplete ? config.color : 'text-slate'} />
              </div>
              <span
                className={`text-[10px] mt-2 uppercase tracking-wider ${
                  isCurrent ? config.color : 'text-ash'
                }`}
              >
                {config.label}
              </span>
            </div>
            {i < steps.length - 1 && (
              <div
                className={`flex-1 h-0.5 mx-2 ${isComplete ? 'bg-ember' : 'bg-iron'}`}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

function ShipmentStatusBadge({ status }: { status: string }) {
  const config: Record<string, { label: string; color: string }> = {
    preparing: { label: 'Preparing', color: 'text-warning bg-warning/20' },
    pending: { label: 'Pending', color: 'text-slate bg-slate/20' },
    in_transit: { label: 'In Transit', color: 'text-ember bg-ember/20' },
    delivered: { label: 'Delivered', color: 'text-healthy bg-healthy/20' },
  };

  const c = config[status?.toLowerCase()] || config.pending;
  return <span className={`px-2 py-1 rounded text-xs ${c.color}`}>{c.label}</span>;
}
