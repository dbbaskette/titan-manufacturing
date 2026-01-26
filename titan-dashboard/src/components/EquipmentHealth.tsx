// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Equipment Health Dashboard
// ═══════════════════════════════════════════════════════════════════════════

import { useState } from 'react';
import { Wrench, AlertTriangle, TrendingDown, CheckCircle, Search } from 'lucide-react';

interface EquipmentItem {
  id: string;
  name: string;
  type: string;
  facility: string;
  status: 'healthy' | 'degraded' | 'critical' | 'maintenance';
  healthScore: number;
  failureProbability: number;
  remainingLife: number; // hours
  lastMaintenance: string;
  nextMaintenance: string;
}

const MOCK_EQUIPMENT: EquipmentItem[] = [
  {
    id: 'PHX-CNC-007',
    name: 'CNC Vertical Mill',
    type: 'CNC Mill',
    facility: 'Phoenix',
    status: 'critical',
    healthScore: 42,
    failureProbability: 73,
    remainingLife: 48,
    lastMaintenance: '2024-01-15',
    nextMaintenance: 'URGENT',
  },
  {
    id: 'PHX-CNC-003',
    name: 'CNC Horizontal Mill',
    type: 'CNC Mill',
    facility: 'Phoenix',
    status: 'healthy',
    healthScore: 94,
    failureProbability: 3,
    remainingLife: 2400,
    lastMaintenance: '2024-01-10',
    nextMaintenance: '2024-04-10',
  },
  {
    id: 'MUC-LTH-012',
    name: 'Precision Lathe',
    type: 'Lathe',
    facility: 'Munich',
    status: 'healthy',
    healthScore: 88,
    failureProbability: 8,
    remainingLife: 1800,
    lastMaintenance: '2024-01-08',
    nextMaintenance: '2024-03-08',
  },
  {
    id: 'SHA-CNC-001',
    name: 'High-Speed CNC',
    type: 'CNC Mill',
    facility: 'Shanghai',
    status: 'degraded',
    healthScore: 68,
    failureProbability: 25,
    remainingLife: 720,
    lastMaintenance: '2024-01-05',
    nextMaintenance: '2024-02-15',
  },
  {
    id: 'DET-ASM-005',
    name: 'Assembly Robot',
    type: 'Robotics',
    facility: 'Detroit',
    status: 'healthy',
    healthScore: 91,
    failureProbability: 5,
    remainingLife: 2100,
    lastMaintenance: '2024-01-12',
    nextMaintenance: '2024-04-12',
  },
  {
    id: 'MAN-GRD-002',
    name: 'Surface Grinder',
    type: 'Grinder',
    facility: 'Manchester',
    status: 'maintenance',
    healthScore: 75,
    failureProbability: 0,
    remainingLife: 1200,
    lastMaintenance: '2024-01-20',
    nextMaintenance: 'In Progress',
  },
];

export function EquipmentHealth() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedEquipment, setSelectedEquipment] = useState<EquipmentItem | null>(MOCK_EQUIPMENT[0]);
  const [filterStatus, setFilterStatus] = useState<string>('all');

  const filteredEquipment = MOCK_EQUIPMENT.filter((eq) => {
    const matchesSearch =
      eq.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
      eq.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      eq.facility.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesFilter = filterStatus === 'all' || eq.status === filterStatus;
    return matchesSearch && matchesFilter;
  });

  const statusCounts = {
    healthy: MOCK_EQUIPMENT.filter((e) => e.status === 'healthy').length,
    degraded: MOCK_EQUIPMENT.filter((e) => e.status === 'degraded').length,
    critical: MOCK_EQUIPMENT.filter((e) => e.status === 'critical').length,
    maintenance: MOCK_EQUIPMENT.filter((e) => e.status === 'maintenance').length,
  };

  return (
    <div className="space-y-6 fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <Wrench className="text-ember" />
            Equipment Health Monitor
          </h2>
          <p className="text-slate mt-1">Predictive maintenance powered by ML anomaly detection</p>
        </div>
      </div>

      {/* Status Summary */}
      <div className="grid grid-cols-4 gap-4">
        <div className="stagger scale-in stagger-1">
          <StatusCard
            label="Healthy"
            count={statusCounts.healthy}
            icon={CheckCircle}
            color="healthy"
            onClick={() => setFilterStatus(filterStatus === 'healthy' ? 'all' : 'healthy')}
            active={filterStatus === 'healthy'}
          />
        </div>
        <div className="stagger scale-in stagger-2">
          <StatusCard
            label="Degraded"
            count={statusCounts.degraded}
            icon={TrendingDown}
            color="warning"
            onClick={() => setFilterStatus(filterStatus === 'degraded' ? 'all' : 'degraded')}
            active={filterStatus === 'degraded'}
          />
        </div>
        <div className="stagger scale-in stagger-3">
          <StatusCard
            label="Critical"
            count={statusCounts.critical}
            icon={AlertTriangle}
            color="critical"
            onClick={() => setFilterStatus(filterStatus === 'critical' ? 'all' : 'critical')}
            active={filterStatus === 'critical'}
          />
        </div>
        <div className="stagger scale-in stagger-4">
          <StatusCard
            label="In Maintenance"
            count={statusCounts.maintenance}
            icon={Wrench}
            color="info"
            onClick={() => setFilterStatus(filterStatus === 'maintenance' ? 'all' : 'maintenance')}
            active={filterStatus === 'maintenance'}
          />
        </div>
      </div>

      <div className="grid grid-cols-3 gap-6">
        {/* Equipment List */}
        <div className="col-span-1 panel">
          <div className="panel-header justify-between">
            <div className="flex items-center gap-2">
              <Wrench size={16} />
              Equipment List
            </div>
            <span className="text-xs text-slate font-normal">{filteredEquipment.length} items</span>
          </div>

          {/* Search */}
          <div className="p-3 border-b border-iron">
            <div className="relative">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ash" />
              <input
                type="text"
                placeholder="Search equipment..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="input-field pl-10"
              />
            </div>
          </div>

          {/* List */}
          <div className="max-h-[500px] overflow-y-auto">
            {filteredEquipment.map((eq) => (
              <div
                key={eq.id}
                onClick={() => setSelectedEquipment(eq)}
                className={`p-4 border-b border-iron cursor-pointer transition-all hover:bg-steel ${
                  selectedEquipment?.id === eq.id ? 'bg-steel border-l-2 border-l-ember' : ''
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="font-mono text-sm text-white">{eq.id}</span>
                  <StatusBadge status={eq.status} />
                </div>
                <p className="text-xs text-slate">{eq.name}</p>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-xs text-ash">{eq.facility}</span>
                  <HealthGauge value={eq.healthScore} size="small" />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Equipment Details */}
        <div className="col-span-2 space-y-4">
          {selectedEquipment ? (
            <>
              {/* Header Card */}
              <div className="panel p-6">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-3 mb-2">
                      <h3 className="font-display text-xl font-bold text-white">
                        {selectedEquipment.id}
                      </h3>
                      <StatusBadge status={selectedEquipment.status} />
                    </div>
                    <p className="text-slate">{selectedEquipment.name}</p>
                    <p className="text-sm text-ash mt-1">
                      {selectedEquipment.type} • {selectedEquipment.facility} Plant
                    </p>
                  </div>
                  <HealthGauge value={selectedEquipment.healthScore} size="large" />
                </div>

                {/* Alert Banner */}
                {selectedEquipment.status === 'critical' && (
                  <div className="mt-4 bg-critical/10 border border-critical/30 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                      <AlertTriangle className="text-critical" />
                      <div>
                        <p className="font-semibold text-critical">Immediate Attention Required</p>
                        <p className="text-sm text-slate">
                          {selectedEquipment.failureProbability}% failure probability detected.
                          Estimated {selectedEquipment.remainingLife} hours until failure.
                        </p>
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* Metrics Grid */}
              <div className="grid grid-cols-3 gap-4">
                <MetricPanel
                  label="Failure Probability"
                  value={`${selectedEquipment.failureProbability}%`}
                  sublabel="Based on ML prediction model"
                  status={
                    selectedEquipment.failureProbability > 50
                      ? 'critical'
                      : selectedEquipment.failureProbability > 20
                      ? 'warning'
                      : 'healthy'
                  }
                />
                <MetricPanel
                  label="Remaining Useful Life"
                  value={formatHours(selectedEquipment.remainingLife)}
                  sublabel="Estimated time to failure"
                  status={
                    selectedEquipment.remainingLife < 100
                      ? 'critical'
                      : selectedEquipment.remainingLife < 500
                      ? 'warning'
                      : 'healthy'
                  }
                />
                <MetricPanel
                  label="Next Maintenance"
                  value={selectedEquipment.nextMaintenance}
                  sublabel={`Last: ${selectedEquipment.lastMaintenance}`}
                  status={selectedEquipment.nextMaintenance === 'URGENT' ? 'critical' : 'healthy'}
                />
              </div>

              {/* Recommendations */}
              {selectedEquipment.status === 'critical' && (
                <div className="panel">
                  <div className="panel-header">
                    <Wrench size={16} />
                    Recommended Actions
                  </div>
                  <div className="p-4 space-y-3">
                    <ActionItem
                      priority="high"
                      action="Schedule emergency bearing replacement"
                      part="SKU-BRG-7420 - Precision Ball Bearing"
                    />
                    <ActionItem
                      priority="high"
                      action="Order replacement parts from Timken (7-day lead time)"
                      part="Alternative: SKF Industries (14-day lead time)"
                    />
                    <ActionItem
                      priority="medium"
                      action="Reduce spindle speed to 80% capacity"
                      part="Temporary measure to extend RUL"
                    />
                    <ActionItem
                      priority="medium"
                      action="Increase monitoring frequency to 1-minute intervals"
                      part="Enable real-time anomaly detection"
                    />
                  </div>
                  <div className="p-4 border-t border-iron flex gap-3">
                    <button className="btn-primary flex-1">Schedule Maintenance</button>
                    <button className="btn-secondary flex-1">Order Parts</button>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="panel p-12 text-center">
              <Wrench size={48} className="mx-auto text-iron mb-4" />
              <p className="text-slate">Select equipment to view details</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function StatusCard({
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
  color: 'healthy' | 'warning' | 'critical' | 'info';
  onClick: () => void;
  active: boolean;
}) {
  const colors = {
    healthy: 'bg-healthy/10 border-healthy/30 text-healthy',
    warning: 'bg-warning/10 border-warning/30 text-warning',
    critical: 'bg-critical/10 border-critical/30 text-critical',
    info: 'bg-info/10 border-info/30 text-info',
  };

  return (
    <button
      onClick={onClick}
      className={`panel card-interactive p-4 text-left transition-all ${active ? colors[color] : ''}`}
    >
      <div className="flex items-center justify-between">
        <Icon size={20} className={active ? '' : 'text-slate'} />
        <span className="font-display text-2xl font-bold">{count}</span>
      </div>
      <p className="text-sm text-slate mt-2">{label}</p>
    </button>
  );
}

function StatusBadge({ status }: { status: string }) {
  const styles = {
    healthy: 'bg-healthy/20 text-healthy border-healthy/30',
    degraded: 'bg-warning/20 text-warning border-warning/30',
    critical: 'bg-critical/20 text-critical border-critical/30',
    maintenance: 'bg-info/20 text-info border-info/30',
  };

  return (
    <span
      className={`px-2 py-0.5 rounded text-xs font-mono uppercase border ${
        styles[status as keyof typeof styles]
      }`}
    >
      {status}
    </span>
  );
}

function HealthGauge({ value, size }: { value: number; size: 'small' | 'large' }) {
  const radius = size === 'large' ? 40 : 16;
  const stroke = size === 'large' ? 6 : 3;
  const circumference = 2 * Math.PI * radius;
  const progress = ((100 - value) / 100) * circumference;

  const color = value > 70 ? '#10b981' : value > 40 ? '#f59e0b' : '#ef4444';

  if (size === 'small') {
    return (
      <div className="flex items-center gap-1">
        <svg width={36} height={36} className="-rotate-90">
          <circle cx={18} cy={18} r={radius} fill="none" stroke="#2a2a32" strokeWidth={stroke} />
          <circle
            cx={18}
            cy={18}
            r={radius}
            fill="none"
            stroke={color}
            strokeWidth={stroke}
            strokeDasharray={circumference}
            strokeDashoffset={progress}
            strokeLinecap="round"
          />
        </svg>
        <span className="text-xs font-mono" style={{ color }}>
          {value}%
        </span>
      </div>
    );
  }

  return (
    <div className="relative">
      <svg width={100} height={100} className="-rotate-90">
        <circle cx={50} cy={50} r={radius} fill="none" stroke="#2a2a32" strokeWidth={stroke} />
        <circle
          cx={50}
          cy={50}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeDasharray={circumference}
          strokeDashoffset={progress}
          strokeLinecap="round"
          style={{ filter: `drop-shadow(0 0 8px ${color})` }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="font-display text-2xl font-bold" style={{ color }}>
          {value}%
        </span>
        <span className="text-[10px] text-slate uppercase">Health</span>
      </div>
    </div>
  );
}

function MetricPanel({
  label,
  value,
  sublabel,
  status,
}: {
  label: string;
  value: string;
  sublabel: string;
  status: 'healthy' | 'warning' | 'critical';
}) {
  const colors = {
    healthy: 'text-healthy',
    warning: 'text-warning',
    critical: 'text-critical',
  };

  return (
    <div className="panel p-4">
      <p className="text-xs text-slate uppercase tracking-wider mb-2">{label}</p>
      <p className={`font-display text-2xl font-bold ${colors[status]}`}>{value}</p>
      <p className="text-xs text-ash mt-1">{sublabel}</p>
    </div>
  );
}

function ActionItem({ priority, action, part }: { priority: 'high' | 'medium' | 'low'; action: string; part: string }) {
  const colors = {
    high: 'bg-critical text-white',
    medium: 'bg-warning text-black',
    low: 'bg-info text-white',
  };

  return (
    <div className="flex items-start gap-3 p-3 bg-steel rounded-lg">
      <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${colors[priority]}`}>
        {priority}
      </span>
      <div>
        <p className="text-sm text-white">{action}</p>
        <p className="text-xs text-ash mt-1">{part}</p>
      </div>
    </div>
  );
}

function formatHours(hours: number): string {
  if (hours < 24) return `${hours}h`;
  const days = Math.floor(hours / 24);
  return `${days}d ${hours % 24}h`;
}
