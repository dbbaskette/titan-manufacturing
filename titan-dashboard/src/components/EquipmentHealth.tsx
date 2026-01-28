// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Equipment Health Dashboard
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect, useCallback } from 'react';
import { Wrench, AlertTriangle, TrendingDown, CheckCircle, Search, Activity } from 'lucide-react';
import { titanApi } from '../api/titanApi';
import type { GeneratorEquipment, MLPrediction } from '../api/titanApi';

// Facility code → display name
const FACILITY_NAMES: Record<string, string> = {
  PHX: 'Phoenix', DET: 'Detroit', ATL: 'Atlanta', DAL: 'Dallas',
  MUC: 'Munich', LYN: 'Lyon', MAN: 'Manchester', SHA: 'Shanghai',
  TYO: 'Tokyo', SEO: 'Seoul', SYD: 'Sydney', MEX: 'Mexico City',
};

// Equipment type from ID prefix pattern (e.g. PHX-CNC-001 → CNC)
function deriveEquipmentType(id: string): string {
  const parts = id.split('-');
  if (parts.length >= 2) {
    const typeCode = parts[1];
    const typeMap: Record<string, string> = {
      CNC: 'CNC Mill', LTH: 'Lathe', GRD: 'Grinder', ASM: 'Assembly Robot',
      WLD: 'Welder', PRS: 'Press', INS: 'Inspector', DRL: 'Drill',
      EDM: 'EDM Machine', PLM: 'Plasma Cutter',
    };
    return typeMap[typeCode] || typeCode;
  }
  return 'Unknown';
}

type EquipmentStatus = 'healthy' | 'degraded' | 'critical' | 'maintenance';

interface EquipmentItem {
  id: string;
  name: string;
  type: string;
  facility: string;
  facilityId: string;
  status: EquipmentStatus;
  healthScore: number;
  failureProbability: number;
  remainingLife: number;
  pattern: string;
  // ML prediction data (if available)
  probableCause?: string;
  riskLevel?: string;
  drivers?: Record<string, number>;
  readingsInWindow?: number;
  // Sensor values
  vibration: number;
  temperature: number;
  rpm: number;
  power: number;
  pressure: number;
  torque: number;
}

function deriveStatus(riskLevel?: string, failureProbability?: number): EquipmentStatus {
  if (!riskLevel) {
    // No ML prediction — use failure probability threshold
    const fp = failureProbability ?? 0;
    if (fp > 50) return 'critical';
    if (fp > 20) return 'degraded';
    return 'healthy';
  }
  switch (riskLevel) {
    case 'CRITICAL': case 'HIGH': return 'critical';
    case 'MEDIUM': return 'degraded';
    default: return 'healthy';
  }
}

function mergeEquipmentData(
  equipment: GeneratorEquipment[],
  predictions: MLPrediction[],
): EquipmentItem[] {
  const predMap = new Map<string, MLPrediction>();
  for (const p of predictions) {
    predMap.set(p.equipmentId, p);
  }

  return equipment.map((eq) => {
    const pred = predMap.get(eq.equipmentId);
    const fp = pred ? pred.failureProbability * 100 : 0;
    const healthScore = Math.round(Math.max(0, Math.min(100, 100 - fp)));
    const status = deriveStatus(pred?.riskLevel, fp);

    // Estimate remaining useful life from failure probability
    let remainingLife: number;
    if (fp > 80) remainingLife = Math.round(24 * (1 - fp / 100));
    else if (fp > 50) remainingLife = Math.round(72 * (1 - fp / 100));
    else if (fp > 20) remainingLife = Math.round(720 * (1 - fp / 100));
    else remainingLife = Math.round(2400 * (1 - fp / 100));

    return {
      id: eq.equipmentId,
      name: deriveEquipmentType(eq.equipmentId),
      type: deriveEquipmentType(eq.equipmentId),
      facility: FACILITY_NAMES[eq.facilityId] || eq.facilityId,
      facilityId: eq.facilityId,
      status,
      healthScore,
      failureProbability: Math.round(fp * 10) / 10,
      remainingLife,
      pattern: eq.pattern,
      probableCause: pred?.probableCause,
      riskLevel: pred?.riskLevel,
      drivers: pred?.drivers,
      readingsInWindow: pred?.readingsInWindow,
      vibration: eq.vibration,
      temperature: eq.temperature,
      rpm: eq.rpm,
      power: eq.power,
      pressure: eq.pressure,
      torque: eq.torque,
    };
  });
}

const STATUS_ORDER: Record<EquipmentStatus, number> = {
  critical: 0, degraded: 1, maintenance: 2, healthy: 3,
};

function getRecommendations(cause?: string): { priority: 'high' | 'medium'; action: string; part: string }[] {
  switch (cause) {
    case 'Bearing Degradation':
      return [
        { priority: 'high', action: 'Schedule emergency bearing replacement', part: 'SKU-BRG-7420 - Precision Ball Bearing' },
        { priority: 'medium', action: 'Reduce spindle speed to 80% capacity', part: 'Temporary measure to extend RUL' },
      ];
    case 'Motor Burnout Risk':
      return [
        { priority: 'high', action: 'Inspect motor windings and brushes', part: 'SKU-MTR-2200 - Drive Motor Assembly' },
        { priority: 'medium', action: 'Check cooling system airflow', part: 'Verify fan and duct integrity' },
      ];
    case 'Electrical Fault':
      return [
        { priority: 'high', action: 'Inspect power supply and wiring', part: 'SKU-PWR-1100 - Power Supply Module' },
        { priority: 'medium', action: 'Check grounding and shielding', part: 'Verify EMI compliance' },
      ];
    case 'Coolant System Failure':
      return [
        { priority: 'high', action: 'Inspect coolant pump and lines', part: 'SKU-CLT-3300 - Coolant Pump Assembly' },
        { priority: 'medium', action: 'Check coolant level and quality', part: 'Replace if contaminated' },
      ];
    case 'Spindle Wear':
      return [
        { priority: 'high', action: 'Schedule spindle bearing replacement', part: 'SKU-SPD-5500 - Spindle Bearing Set' },
        { priority: 'medium', action: 'Reduce feed rate and depth of cut', part: 'Temporary measure to limit wear' },
      ];
    default:
      return [
        { priority: 'medium', action: 'Increase monitoring frequency', part: 'Enable real-time anomaly detection' },
      ];
  }
}

export function EquipmentHealth() {
  const [equipment, setEquipment] = useState<EquipmentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedEquipment, setSelectedEquipment] = useState<EquipmentItem | null>(null);
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const [filterFacility, setFilterFacility] = useState<string>('all');

  const fetchData = useCallback(async () => {
    try {
      const [eqList, predData] = await Promise.all([
        titanApi.getEquipmentList(),
        titanApi.getMlPredictions(),
      ]);
      const merged = mergeEquipmentData(eqList, predData.predictions || []);
      setEquipment(merged);

      // Update selected equipment with fresh data
      setSelectedEquipment((prev) => {
        if (!prev) return merged.length > 0 ? merged[0] : null;
        return merged.find((e) => e.id === prev.id) || prev;
      });
    } catch (err) {
      console.error('Failed to fetch equipment data:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 5000);
    return () => clearInterval(interval);
  }, [fetchData]);

  // Derive facilities from data
  const facilities = [...new Set(equipment.map((e) => e.facilityId))].sort();

  // Filter and sort
  const filteredEquipment = equipment
    .filter((eq) => {
      const matchesSearch =
        eq.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
        eq.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        eq.facility.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesStatus = filterStatus === 'all' || eq.status === filterStatus;
      const matchesFacility = filterFacility === 'all' || eq.facilityId === filterFacility;
      return matchesSearch && matchesStatus && matchesFacility;
    })
    .sort((a, b) => {
      const statusDiff = STATUS_ORDER[a.status] - STATUS_ORDER[b.status];
      if (statusDiff !== 0) return statusDiff;
      return b.failureProbability - a.failureProbability;
    });

  const statusCounts = {
    healthy: equipment.filter((e) => e.status === 'healthy').length,
    degraded: equipment.filter((e) => e.status === 'degraded').length,
    critical: equipment.filter((e) => e.status === 'critical').length,
    maintenance: equipment.filter((e) => e.status === 'maintenance').length,
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-slate">Loading equipment data...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6 fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <Wrench className="text-ember" />
            Equipment Health Monitor
          </h2>
          <p className="text-zinc-400 mt-1">
            {equipment.length} equipment tracked • Predictive maintenance powered by ML
          </p>
        </div>
      </div>

      {/* Status Summary */}
      <div className="grid grid-cols-4 gap-4">
        <div className="stagger scale-in stagger-1">
          <StatusCard label="Healthy" count={statusCounts.healthy} icon={CheckCircle} color="healthy"
            onClick={() => setFilterStatus(filterStatus === 'healthy' ? 'all' : 'healthy')}
            active={filterStatus === 'healthy'} />
        </div>
        <div className="stagger scale-in stagger-2">
          <StatusCard label="Degraded" count={statusCounts.degraded} icon={TrendingDown} color="warning"
            onClick={() => setFilterStatus(filterStatus === 'degraded' ? 'all' : 'degraded')}
            active={filterStatus === 'degraded'} />
        </div>
        <div className="stagger scale-in stagger-3">
          <StatusCard label="Critical" count={statusCounts.critical} icon={AlertTriangle} color="critical"
            onClick={() => setFilterStatus(filterStatus === 'critical' ? 'all' : 'critical')}
            active={filterStatus === 'critical'} />
        </div>
        <div className="stagger scale-in stagger-4">
          <StatusCard label="In Maintenance" count={statusCounts.maintenance} icon={Wrench} color="info"
            onClick={() => setFilterStatus(filterStatus === 'maintenance' ? 'all' : 'maintenance')}
            active={filterStatus === 'maintenance'} />
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

          {/* Search + Facility Filter */}
          <div className="p-3 border-b border-iron space-y-2">
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
            <select
              value={filterFacility}
              onChange={(e) => setFilterFacility(e.target.value)}
              className="input-field text-sm"
            >
              <option value="all">All Facilities</option>
              {facilities.map((f) => (
                <option key={f} value={f}>{FACILITY_NAMES[f] || f}</option>
              ))}
            </select>
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
                <p className="text-xs text-zinc-300">{eq.name}</p>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-xs text-ash">{eq.facility}</span>
                  <HealthGauge value={eq.healthScore} size="small" />
                </div>
              </div>
            ))}
            {filteredEquipment.length === 0 && (
              <div className="p-8 text-center text-slate text-sm">No equipment matches filters</div>
            )}
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
                    <p className="text-zinc-300">{selectedEquipment.name}</p>
                    <p className="text-sm text-ash mt-1">
                      {selectedEquipment.type} • {selectedEquipment.facility} Plant
                    </p>
                    {selectedEquipment.pattern !== 'NORMAL' && (
                      <p className="text-xs text-warning mt-1 font-mono">
                        Pattern: {selectedEquipment.pattern.replace(/_/g, ' ')}
                      </p>
                    )}
                  </div>
                  <HealthGauge value={selectedEquipment.healthScore} size="large" />
                </div>

                {/* Alert Banner */}
                {selectedEquipment.status === 'critical' && (
                  <div className="mt-4 bg-critical/10 border border-critical/30 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                      <AlertTriangle className="text-critical flex-shrink-0" />
                      <div>
                        <p className="font-semibold text-critical">Immediate Attention Required</p>
                        <p className="text-sm text-zinc-300">
                          {selectedEquipment.failureProbability}% failure probability
                          {selectedEquipment.probableCause && ` — ${selectedEquipment.probableCause}`}.
                          {' '}Estimated {formatHours(selectedEquipment.remainingLife)} until failure.
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
                  sublabel={selectedEquipment.probableCause || 'Based on ML prediction model'}
                  status={
                    selectedEquipment.failureProbability > 50 ? 'critical'
                    : selectedEquipment.failureProbability > 20 ? 'warning'
                    : 'healthy'
                  }
                />
                <MetricPanel
                  label="Remaining Useful Life"
                  value={formatHours(selectedEquipment.remainingLife)}
                  sublabel="Estimated time to failure"
                  status={
                    selectedEquipment.remainingLife < 100 ? 'critical'
                    : selectedEquipment.remainingLife < 500 ? 'warning'
                    : 'healthy'
                  }
                />
                <MetricPanel
                  label="ML Readings"
                  value={selectedEquipment.readingsInWindow?.toString() || '—'}
                  sublabel="Readings in scoring window"
                  status="healthy"
                />
              </div>

              {/* Sensor Values */}
              <div className="panel">
                <div className="panel-header">
                  <Activity size={16} />
                  Current Sensor Values
                </div>
                <div className="grid grid-cols-3 gap-4 p-4">
                  <SensorValue label="Vibration" value={selectedEquipment.vibration} unit="mm/s" warn={4.0} crit={5.5} />
                  <SensorValue label="Temperature" value={selectedEquipment.temperature} unit="°C" warn={75} crit={90} />
                  <SensorValue label="RPM" value={selectedEquipment.rpm} unit="rpm" warn={0} crit={0} inverted />
                  <SensorValue label="Power" value={selectedEquipment.power} unit="kW" warn={35} crit={45} />
                  <SensorValue label="Pressure" value={selectedEquipment.pressure} unit="bar" warn={8} crit={9} />
                  <SensorValue label="Torque" value={selectedEquipment.torque} unit="Nm" warn={65} crit={75} />
                </div>
              </div>

              {/* Risk Drivers */}
              {selectedEquipment.drivers && Object.keys(selectedEquipment.drivers).length > 0 && (
                <div className="panel">
                  <div className="panel-header">
                    <TrendingDown size={16} />
                    Risk Drivers
                  </div>
                  <div className="p-4 space-y-3">
                    {(() => {
                      const entries = Object.entries(selectedEquipment.drivers!)
                        .sort(([, a], [, b]) => Math.abs(b) - Math.abs(a))
                        .slice(0, 6);
                      const totalAbs = entries.reduce((sum, [, v]) => sum + Math.abs(v), 0);
                      const maxAbs = entries.length > 0 ? Math.abs(entries[0][1]) : 1;
                      return entries.map(([name, value]) => {
                        const pct = totalAbs > 0 ? (Math.abs(value) / totalAbs) * 100 : 0;
                        const barWidth = maxAbs > 0 ? (Math.abs(value) / maxAbs) * 100 : 0;
                        const label = name.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
                        return (
                          <div key={name}>
                            <div className="flex items-center justify-between mb-1">
                              <span className="text-xs text-zinc-300">{label}</span>
                              <span className={`text-xs font-mono ${value > 0 ? 'text-critical' : 'text-healthy'}`}>
                                {pct.toFixed(0)}%
                              </span>
                            </div>
                            <div className="h-1.5 bg-steel rounded-full overflow-hidden">
                              <div
                                className={`h-full rounded-full ${value > 0 ? 'bg-critical' : 'bg-healthy'}`}
                                style={{ width: `${barWidth}%` }}
                              />
                            </div>
                          </div>
                        );
                      });
                    })()}
                  </div>
                </div>
              )}

              {/* Recommendations */}
              {(selectedEquipment.status === 'critical' || selectedEquipment.status === 'degraded') && (
                <div className="panel">
                  <div className="panel-header">
                    <Wrench size={16} />
                    Recommended Actions
                  </div>
                  <div className="p-4 space-y-3">
                    {getRecommendations(selectedEquipment.probableCause).map((rec, i) => (
                      <ActionItem key={i} priority={rec.priority} action={rec.action} part={rec.part} />
                    ))}
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

function SensorValue({
  label, value, unit, warn, crit, inverted,
}: {
  label: string; value: number; unit: string; warn: number; crit: number; inverted?: boolean;
}) {
  let color = 'text-healthy';
  if (inverted) {
    // For RPM, lower is worse — skip color coding
    color = 'text-white';
  } else if (crit > 0 && value >= crit) {
    color = 'text-critical';
  } else if (warn > 0 && value >= warn) {
    color = 'text-warning';
  }

  return (
    <div className="bg-steel rounded-lg p-3">
      <p className="text-[10px] text-slate uppercase tracking-wider mb-1">{label}</p>
      <p className={`font-mono text-lg font-bold ${color}`}>
        {typeof value === 'number' ? value.toFixed(1) : '—'}
        <span className="text-xs text-ash ml-1">{unit}</span>
      </p>
    </div>
  );
}

function StatusCard({
  label, count, icon: Icon, color, onClick, active,
}: {
  label: string; count: number; icon: React.ElementType;
  color: 'healthy' | 'warning' | 'critical' | 'info'; onClick: () => void; active: boolean;
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
  const styles: Record<string, string> = {
    healthy: 'bg-healthy/20 text-healthy border-healthy/30',
    degraded: 'bg-warning/20 text-warning border-warning/30',
    critical: 'bg-critical/20 text-critical border-critical/30',
    maintenance: 'bg-info/20 text-info border-info/30',
  };

  return (
    <span className={`px-2 py-0.5 rounded text-xs font-mono uppercase border ${styles[status] || ''}`}>
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
          <circle cx={18} cy={18} r={radius} fill="none" stroke={color} strokeWidth={stroke}
            strokeDasharray={circumference} strokeDashoffset={progress} strokeLinecap="round" />
        </svg>
        <span className="text-xs font-mono" style={{ color }}>{value}%</span>
      </div>
    );
  }

  return (
    <div className="relative">
      <svg width={100} height={100} className="-rotate-90">
        <circle cx={50} cy={50} r={radius} fill="none" stroke="#2a2a32" strokeWidth={stroke} />
        <circle cx={50} cy={50} r={radius} fill="none" stroke={color} strokeWidth={stroke}
          strokeDasharray={circumference} strokeDashoffset={progress} strokeLinecap="round"
          style={{ filter: `drop-shadow(0 0 8px ${color})` }} />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="font-display text-2xl font-bold" style={{ color }}>{value}%</span>
        <span className="text-[10px] text-slate uppercase">Health</span>
      </div>
    </div>
  );
}

function MetricPanel({
  label, value, sublabel, status,
}: {
  label: string; value: string; sublabel: string; status: 'healthy' | 'warning' | 'critical';
}) {
  const colors = { healthy: 'text-healthy', warning: 'text-warning', critical: 'text-critical' };
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
      <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${colors[priority]}`}>{priority}</span>
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
