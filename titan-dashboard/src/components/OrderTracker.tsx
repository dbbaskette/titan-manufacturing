// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Order Fulfillment Tracker
// ═══════════════════════════════════════════════════════════════════════════

import { useState } from 'react';
import {
  Package,
  Search,
  Clock,
  CheckCircle,
  Truck,
  ArrowUpRight,
} from 'lucide-react';

interface Order {
  id: string;
  customer: string;
  status: 'pending' | 'validated' | 'processing' | 'shipped' | 'delivered';
  priority: 'standard' | 'expedite' | 'critical';
  items: number;
  total: number;
  orderDate: string;
  expectedDelivery: string;
  shipments?: Shipment[];
}

interface Shipment {
  id: string;
  carrier: string;
  status: 'preparing' | 'in_transit' | 'delivered';
  tracking: string;
  origin: string;
  destination: string;
}

const MOCK_ORDERS: Order[] = [
  {
    id: 'TM-2024-45892',
    customer: 'Boeing Commercial',
    status: 'processing',
    priority: 'critical',
    items: 500,
    total: 1225000,
    orderDate: '2024-01-20',
    expectedDelivery: '2024-01-27',
    shipments: [
      {
        id: 'SHIP-2024-001',
        carrier: 'FedEx Express',
        status: 'preparing',
        tracking: 'FX789456123',
        origin: 'Phoenix, AZ',
        destination: 'Seattle, WA',
      },
      {
        id: 'SHIP-2024-002',
        carrier: 'DHL Aviation',
        status: 'preparing',
        tracking: 'DHL456789012',
        origin: 'Munich, DE',
        destination: 'Seattle, WA',
      },
    ],
  },
  {
    id: 'TM-2024-45891',
    customer: 'Airbus Industries',
    status: 'shipped',
    priority: 'expedite',
    items: 250,
    total: 612500,
    orderDate: '2024-01-18',
    expectedDelivery: '2024-01-25',
    shipments: [
      {
        id: 'SHIP-2024-003',
        carrier: 'Maersk Ocean',
        status: 'in_transit',
        tracking: 'MAEU7654321',
        origin: 'Shanghai, CN',
        destination: 'Hamburg, DE',
      },
    ],
  },
  {
    id: 'TM-2024-45890',
    customer: 'Tesla Motors',
    status: 'validated',
    priority: 'standard',
    items: 1000,
    total: 450000,
    orderDate: '2024-01-19',
    expectedDelivery: '2024-02-02',
  },
  {
    id: 'TM-2024-45889',
    customer: 'GE Renewable',
    status: 'delivered',
    priority: 'standard',
    items: 150,
    total: 275000,
    orderDate: '2024-01-10',
    expectedDelivery: '2024-01-17',
  },
  {
    id: 'TM-2024-45888',
    customer: 'Caterpillar Inc.',
    status: 'pending',
    priority: 'standard',
    items: 75,
    total: 125000,
    orderDate: '2024-01-21',
    expectedDelivery: '2024-02-04',
  },
];

const STATUS_CONFIG = {
  pending: { label: 'Pending', color: 'text-slate', bg: 'bg-slate/20', icon: Clock },
  validated: { label: 'Validated', color: 'text-info', bg: 'bg-info/20', icon: CheckCircle },
  processing: { label: 'Processing', color: 'text-warning', bg: 'bg-warning/20', icon: Package },
  shipped: { label: 'Shipped', color: 'text-ember', bg: 'bg-ember/20', icon: Truck },
  delivered: { label: 'Delivered', color: 'text-healthy', bg: 'bg-healthy/20', icon: CheckCircle },
};

const PRIORITY_CONFIG = {
  standard: { label: 'Standard', color: 'text-slate border-slate/30' },
  expedite: { label: 'Expedite', color: 'text-warning border-warning/30' },
  critical: { label: 'Critical', color: 'text-critical border-critical/30' },
};

export function OrderTracker() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(MOCK_ORDERS[0]);
  const [statusFilter, setStatusFilter] = useState<string>('all');

  const filteredOrders = MOCK_ORDERS.filter((order) => {
    const matchesSearch =
      order.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
      order.customer.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesStatus = statusFilter === 'all' || order.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const statusCounts = {
    pending: MOCK_ORDERS.filter((o) => o.status === 'pending').length,
    processing: MOCK_ORDERS.filter((o) => o.status === 'processing' || o.status === 'validated').length,
    shipped: MOCK_ORDERS.filter((o) => o.status === 'shipped').length,
    delivered: MOCK_ORDERS.filter((o) => o.status === 'delivered').length,
  };

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
              const config = STATUS_CONFIG[order.status];
              const priorityConfig = PRIORITY_CONFIG[order.priority];

              return (
                <div
                  key={order.id}
                  onClick={() => setSelectedOrder(order)}
                  className={`p-4 border-b border-iron cursor-pointer transition-all hover:bg-steel ${
                    selectedOrder?.id === order.id ? 'bg-steel border-l-2 border-l-ember' : ''
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-sm text-white">{order.id}</span>
                    <span
                      className={`px-2 py-0.5 rounded text-[10px] uppercase border ${priorityConfig.color}`}
                    >
                      {priorityConfig.label}
                    </span>
                  </div>
                  <p className="text-xs text-slate mb-2">{order.customer}</p>
                  <div className="flex items-center justify-between">
                    <span className={`flex items-center gap-1.5 text-xs ${config.color}`}>
                      <config.icon size={12} />
                      {config.label}
                    </span>
                    <span className="text-xs text-ash font-mono">
                      ${(order.total / 1000).toFixed(0)}K
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Order Details */}
        <div className="col-span-2 space-y-4">
          {selectedOrder ? (
            <>
              {/* Header Card */}
              <div className="panel p-6">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-3 mb-2">
                      <h3 className="font-display text-xl font-bold text-white">
                        {selectedOrder.id}
                      </h3>
                      <span
                        className={`px-2 py-0.5 rounded text-xs uppercase border ${
                          PRIORITY_CONFIG[selectedOrder.priority].color
                        }`}
                      >
                        {selectedOrder.priority}
                      </span>
                    </div>
                    <p className="text-slate">{selectedOrder.customer}</p>
                  </div>
                  <StatusBadge status={selectedOrder.status} />
                </div>

                {/* Order Timeline */}
                <div className="mt-6">
                  <OrderTimeline status={selectedOrder.status} />
                </div>
              </div>

              {/* Order Info */}
              <div className="grid grid-cols-4 gap-4">
                <InfoCard label="Items" value={selectedOrder.items.toString()} />
                <InfoCard
                  label="Total Value"
                  value={`$${(selectedOrder.total / 1000).toFixed(0)}K`}
                />
                <InfoCard label="Order Date" value={selectedOrder.orderDate} />
                <InfoCard label="Expected Delivery" value={selectedOrder.expectedDelivery} />
              </div>

              {/* Shipments */}
              {selectedOrder.shipments && selectedOrder.shipments.length > 0 && (
                <div className="panel">
                  <div className="panel-header">
                    <Truck size={16} />
                    Shipments
                  </div>
                  <div className="divide-y divide-iron">
                    {selectedOrder.shipments.map((shipment) => (
                      <div key={shipment.id} className="p-4">
                        <div className="flex items-center justify-between mb-3">
                          <div className="flex items-center gap-3">
                            <span className="font-mono text-sm text-white">{shipment.id}</span>
                            <span className="px-2 py-0.5 bg-steel rounded text-xs text-slate">
                              {shipment.carrier}
                            </span>
                          </div>
                          <ShipmentStatus status={shipment.status} />
                        </div>
                        <div className="flex items-center gap-4 text-sm">
                          <div className="flex items-center gap-2">
                            <div className="w-2 h-2 rounded-full bg-ember" />
                            <span className="text-slate">{shipment.origin}</span>
                          </div>
                          <ArrowUpRight size={14} className="text-iron" />
                          <div className="flex items-center gap-2">
                            <div className="w-2 h-2 rounded-full bg-healthy" />
                            <span className="text-slate">{shipment.destination}</span>
                          </div>
                        </div>
                        <div className="mt-2 text-xs text-ash font-mono">
                          Tracking: {shipment.tracking}
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
  const config = STATUS_CONFIG[status as keyof typeof STATUS_CONFIG];
  const Icon = config.icon;

  return (
    <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg ${config.bg}`}>
      <Icon size={14} className={config.color} />
      <span className={`text-sm font-medium ${config.color}`}>{config.label}</span>
    </div>
  );
}

function OrderTimeline({ status }: { status: string }) {
  const steps = ['pending', 'validated', 'processing', 'shipped', 'delivered'];
  const currentIndex = steps.indexOf(status);

  return (
    <div className="flex items-center justify-between">
      {steps.map((step, i) => {
        const config = STATUS_CONFIG[step as keyof typeof STATUS_CONFIG];
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

function ShipmentStatus({ status }: { status: string }) {
  const config: Record<string, { label: string; color: string }> = {
    preparing: { label: 'Preparing', color: 'text-warning bg-warning/20' },
    in_transit: { label: 'In Transit', color: 'text-ember bg-ember/20' },
    delivered: { label: 'Delivered', color: 'text-healthy bg-healthy/20' },
  };

  const c = config[status];
  return <span className={`px-2 py-1 rounded text-xs ${c.color}`}>{c.label}</span>;
}

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="panel p-4">
      <p className="text-xs text-ash uppercase tracking-wider mb-1">{label}</p>
      <p className="font-display text-lg font-bold text-white">{value}</p>
    </div>
  );
}
