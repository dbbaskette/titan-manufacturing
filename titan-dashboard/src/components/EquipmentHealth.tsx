// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Equipment Health Dashboard
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect, useCallback, useRef } from 'react';
import { Wrench, AlertTriangle, TrendingDown, CheckCircle, Search, Activity, Zap } from 'lucide-react';
import { titanApi } from '../api/titanApi';
import type { GeneratorEquipment, MLPrediction } from '../api/titanApi';

// Facility code → display name
const FACILITY_NAMES: Record<string, string> = {
  PHX: 'Phoenix', DET: 'Detroit', ATL: 'Atlanta', DAL: 'Dallas',
  MUC: 'Munich', LYN: 'Lyon', MAN: 'Manchester', SHA: 'Shanghai',
  TYO: 'Tokyo', SEO: 'Seoul', SYD: 'Sydney', MEX: 'Mexico City',
};

// Threshold constants (same as SensorMonitor)
const THRESHOLDS = {
  vibration: { warning: 3.0, critical: 3.5 },
  temperature: { warning: 70, critical: 85 },
  power: { warning: 50, critical: 55 },
} as const;

interface ThresholdCounts {
  warnings: number;
  criticals: number;
  currentSensors: { vibration: 'normal' | 'warning' | 'critical'; temperature: 'normal' | 'warning' | 'critical'; power: 'normal' | 'warning' | 'critical' };
}

function checkThresholds(eq: GeneratorEquipment): ThresholdCounts['currentSensors'] {
  const vibLevel = eq.vibration >= THRESHOLDS.vibration.critical ? 'critical' as const
    : eq.vibration >= THRESHOLDS.vibration.warning ? 'warning' as const : 'normal' as const;
  const tempLevel = eq.temperature >= THRESHOLDS.temperature.critical ? 'critical' as const
    : eq.temperature >= THRESHOLDS.temperature.warning ? 'warning' as const : 'normal' as const;
  const powerLevel = eq.power >= THRESHOLDS.power.critical ? 'critical' as const
    : eq.power >= THRESHOLDS.power.warning ? 'warning' as const : 'normal' as const;
  return { vibration: vibLevel, temperature: tempLevel, power: powerLevel };
}

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

// ═══════════════════════════════════════════════════════════════════════════
// Pattern-Specific RUL Estimation
// Based on degradation physics and sensor severity
// ═══════════════════════════════════════════════════════════════════════════

interface RULEstimate {
  hours: number;
  confidence: 'high' | 'medium' | 'low';
  basis: string;
}

type SensorType = 'vibration' | 'temperature' | 'power' | 'pressure' | 'torque';

interface DegradationProfile {
  baseHoursAtCritical: number;  // Hours remaining when sensors hit critical
  primarySensor: SensorType;
  secondarySensor?: SensorType;
  degradationRate: number;  // Multiplier for how fast this pattern progresses
  description: string;
}

// Pattern-specific degradation profiles
const DEGRADATION_PROFILES: Record<string, DegradationProfile> = {
  'Bearing Degradation': {
    baseHoursAtCritical: 48,
    primarySensor: 'vibration',
    secondarySensor: 'torque',
    degradationRate: 1.0,  // Gradual, predictable
    description: 'Mechanical wear — gradual progression',
  },
  'Motor burnout': {
    baseHoursAtCritical: 12,
    primarySensor: 'temperature',
    secondarySensor: 'power',
    degradationRate: 2.5,  // Can accelerate rapidly
    description: 'Thermal runaway — accelerating failure',
  },
  'Electrical Fault': {
    baseHoursAtCritical: 8,
    primarySensor: 'power',
    degradationRate: 3.0,  // Unpredictable, can fail suddenly
    description: 'Electrical instability — unpredictable',
  },
  'Coolant System Failure': {
    baseHoursAtCritical: 24,
    primarySensor: 'temperature',
    secondarySensor: 'pressure',
    degradationRate: 1.5,
    description: 'Thermal stress — pressure-dependent',
  },
  'Spindle Wear': {
    baseHoursAtCritical: 72,
    primarySensor: 'vibration',
    secondarySensor: 'torque',
    degradationRate: 0.8,  // Slow, mechanical wear
    description: 'Mechanical friction — slow progression',
  },
};

// Sensor severity thresholds for RUL calculation
const SENSOR_SEVERITY = {
  vibration: { normal: 2.5, warning: 3.5, critical: 5.0, max: 10.0 },
  temperature: { normal: 55, warning: 70, critical: 85, max: 150 },
  power: { normal: 30, warning: 45, critical: 55, max: 100 },
  pressure: { normal: 6.0, warning: 4.5, critical: 3.0, max: 10.0, inverted: true },
  torque: { normal: 55, warning: 65, critical: 75, max: 100 },
};

function calculateSensorSeverity(
  sensor: SensorType,
  value: number
): number {
  const s = SENSOR_SEVERITY[sensor];
  const inverted = 'inverted' in s && s.inverted;

  if (inverted) {
    // For pressure: lower is worse
    if (value >= s.normal) return 0;
    if (value >= s.warning) return 0.3 + 0.3 * (s.normal - value) / (s.normal - s.warning);
    if (value >= s.critical) return 0.6 + 0.3 * (s.warning - value) / (s.warning - s.critical);
    return 0.9 + 0.1 * Math.min(1, (s.critical - value) / s.critical);
  }

  // Normal case: higher is worse
  if (value <= s.normal) return 0;
  if (value <= s.warning) return 0.3 * (value - s.normal) / (s.warning - s.normal);
  if (value <= s.critical) return 0.3 + 0.4 * (value - s.warning) / (s.critical - s.warning);
  return 0.7 + 0.3 * Math.min(1, (value - s.critical) / (s.max - s.critical));
}

function estimateRUL(
  probableCause: string | undefined,
  failureProbability: number,
  sensors: { vibration: number; temperature: number; power: number; pressure: number; torque: number }
): RULEstimate {
  // Default fallback for unknown patterns
  const defaultProfile: DegradationProfile = {
    baseHoursAtCritical: 24,
    primarySensor: 'vibration',
    degradationRate: 1.5,
    description: 'Unknown pattern — conservative estimate',
  };

  // Find matching profile (partial match on cause string)
  let profile: DegradationProfile = defaultProfile;
  let matchedPattern = 'Unknown';
  if (probableCause) {
    for (const [pattern, p] of Object.entries(DEGRADATION_PROFILES)) {
      if (probableCause.toLowerCase().includes(pattern.toLowerCase().split(' ')[0])) {
        profile = p;
        matchedPattern = pattern;
        break;
      }
    }
  }

  // Calculate primary sensor severity (0-1)
  const primarySeverity = calculateSensorSeverity(profile.primarySensor, sensors[profile.primarySensor]);

  // Calculate secondary sensor severity if applicable
  let secondarySeverity = 0;
  if (profile.secondarySensor) {
    secondarySeverity = calculateSensorSeverity(profile.secondarySensor, sensors[profile.secondarySensor]);
  }

  // Combined severity (weighted toward primary)
  const combinedSeverity = primarySeverity * 0.7 + secondarySeverity * 0.3;

  // Base RUL from failure probability (inverse relationship)
  // At 0% probability: ~2000 hours, at 100%: ~0 hours
  const probabilityFactor = 1 - failureProbability / 100;

  // Calculate RUL based on pattern and severity
  let hours: number;
  if (failureProbability >= 90) {
    // Critical: use pattern-specific base hours, scaled by remaining probability
    hours = profile.baseHoursAtCritical * probabilityFactor * 10;
  } else if (failureProbability >= 70) {
    // High risk: 1-7 days
    hours = profile.baseHoursAtCritical * 3 * probabilityFactor;
  } else if (failureProbability >= 40) {
    // Moderate risk: 1-4 weeks
    hours = 168 + (672 - 168) * probabilityFactor; // 168h = 1 week, 672h = 4 weeks
  } else if (failureProbability >= 20) {
    // Low-moderate risk: 1-3 months
    hours = 720 + (2160 - 720) * probabilityFactor; // 720h = 1 month, 2160h = 3 months
  } else {
    // Low risk: 3-12 months
    hours = 2160 + (8760 - 2160) * probabilityFactor;
  }

  // Apply degradation rate (faster patterns = shorter RUL)
  hours = hours / profile.degradationRate;

  // Apply sensor severity adjustment (high severity = shorter RUL)
  hours = hours * (1 - combinedSeverity * 0.5);

  // Ensure minimum of 1 hour
  hours = Math.max(1, Math.round(hours));

  // Determine confidence based on data quality
  let confidence: 'high' | 'medium' | 'low' = 'medium';
  if (matchedPattern !== 'Unknown' && failureProbability > 50) {
    confidence = 'high';
  } else if (matchedPattern === 'Unknown' || failureProbability < 20) {
    confidence = 'low';
  }

  return {
    hours,
    confidence,
    basis: profile.description,
  };
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
  rulConfidence: 'high' | 'medium' | 'low';
  rulBasis: string;
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
  // Threshold tracking
  thresholdWarnings: number;
  thresholdCriticals: number;
  currentThresholds: ThresholdCounts['currentSensors'];
}

function deriveStatus(
  riskLevel?: string,
  failureProbability?: number,
  thresholds?: ThresholdCounts['currentSensors'],
): EquipmentStatus {
  // Start with ML-based status
  let mlStatus: EquipmentStatus = 'healthy';
  if (riskLevel) {
    switch (riskLevel) {
      case 'CRITICAL': case 'HIGH': mlStatus = 'critical'; break;
      case 'MEDIUM': mlStatus = 'degraded'; break;
    }
  } else {
    const fp = failureProbability ?? 0;
    if (fp > 50) mlStatus = 'critical';
    else if (fp > 20) mlStatus = 'degraded';
  }

  // Also check live threshold breaches
  let thresholdStatus: EquipmentStatus = 'healthy';
  if (thresholds) {
    const levels = Object.values(thresholds);
    if (levels.includes('critical')) thresholdStatus = 'critical';
    else if (levels.includes('warning')) thresholdStatus = 'degraded';
  }

  // Return the worse of the two
  const order: Record<EquipmentStatus, number> = { healthy: 0, maintenance: 1, degraded: 2, critical: 3 };
  return order[mlStatus] >= order[thresholdStatus] ? mlStatus : thresholdStatus;
}

function mergeEquipmentData(
  equipment: GeneratorEquipment[],
  predictions: MLPrediction[],
  alertCounts: Record<string, { warnings: number; criticals: number }>,
): EquipmentItem[] {
  const predMap = new Map<string, MLPrediction>();
  for (const p of predictions) {
    predMap.set(p.equipmentId, p);
  }

  return equipment.map((eq) => {
    const pred = predMap.get(eq.equipmentId);
    const fp = pred ? pred.failureProbability * 100 : 0;
    const counts = alertCounts[eq.equipmentId] || { warnings: 0, criticals: 0 };
    const currentThresholds = checkThresholds(eq);
    const healthScore = Math.round(Math.max(0, Math.min(100, 100 - fp)));
    const status = deriveStatus(pred?.riskLevel, fp, currentThresholds);

    // Pattern-specific RUL estimation
    const rulEstimate = estimateRUL(
      pred?.probableCause,
      fp,
      { vibration: eq.vibration, temperature: eq.temperature, power: eq.power, pressure: eq.pressure, torque: eq.torque }
    );

    return {
      id: eq.equipmentId,
      name: deriveEquipmentType(eq.equipmentId),
      type: deriveEquipmentType(eq.equipmentId),
      facility: FACILITY_NAMES[eq.facilityId] || eq.facilityId,
      facilityId: eq.facilityId,
      status,
      healthScore,
      failureProbability: Math.round(fp * 10) / 10,
      remainingLife: rulEstimate.hours,
      rulConfidence: rulEstimate.confidence,
      rulBasis: rulEstimate.basis,
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
      thresholdWarnings: counts.warnings,
      thresholdCriticals: counts.criticals,
      currentThresholds,
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
  const alertCountsRef = useRef<Record<string, { warnings: number; criticals: number }>>({});

  const fetchData = useCallback(async () => {
    try {
      const [eqList, predData] = await Promise.all([
        titanApi.getEquipmentList(),
        titanApi.getMlPredictions(),
      ]);

      // Accumulate threshold alerts
      for (const eq of eqList) {
        const levels = checkThresholds(eq);
        if (!alertCountsRef.current[eq.equipmentId]) {
          alertCountsRef.current[eq.equipmentId] = { warnings: 0, criticals: 0 };
        }
        const counts = alertCountsRef.current[eq.equipmentId];
        for (const level of Object.values(levels)) {
          if (level === 'critical') counts.criticals++;
          else if (level === 'warning') counts.warnings++;
        }
      }

      const merged = mergeEquipmentData(eqList, predData.predictions || [], alertCountsRef.current);
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
          <StatusCard label="Threshold Alerts" count={equipment.filter(e => {
            const t = e.currentThresholds;
            return t.vibration !== 'normal' || t.temperature !== 'normal' || t.power !== 'normal';
          }).length} icon={Zap} color="info"
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
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-ash">{eq.facility}</span>
                    {(eq.thresholdWarnings > 0 || eq.thresholdCriticals > 0) && (
                      <div className="flex items-center gap-1">
                        {eq.thresholdCriticals > 0 && (
                          <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-critical/20 text-critical">
                            {eq.thresholdCriticals}C
                          </span>
                        )}
                        {eq.thresholdWarnings > 0 && (
                          <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-warning/20 text-warning">
                            {eq.thresholdWarnings}W
                          </span>
                        )}
                      </div>
                    )}
                  </div>
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
                        </p>
                        <p className="text-sm text-zinc-400 mt-1">
                          <span className="text-critical font-mono">{formatHours(selectedEquipment.remainingLife)}</span> estimated until failure
                          <span className="text-ash"> • {selectedEquipment.rulBasis}</span>
                          <span className={`ml-2 px-1.5 py-0.5 rounded text-[9px] font-mono uppercase ${
                            selectedEquipment.rulConfidence === 'high' ? 'bg-healthy/20 text-healthy'
                            : selectedEquipment.rulConfidence === 'medium' ? 'bg-warning/20 text-warning'
                            : 'bg-slate/20 text-slate'
                          }`}>
                            {selectedEquipment.rulConfidence} confidence
                          </span>
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
                <RULPanel
                  hours={selectedEquipment.remainingLife}
                  confidence={selectedEquipment.rulConfidence}
                  basis={selectedEquipment.rulBasis}
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

              {/* Threshold Alerts */}
              {(selectedEquipment.thresholdWarnings > 0 || selectedEquipment.thresholdCriticals > 0) && (
                <div className="panel">
                  <div className="panel-header">
                    <Zap size={16} />
                    Threshold Alerts
                    <span className="ml-auto text-xs font-normal text-ash">Accumulated since page load</span>
                  </div>
                  <div className="p-4">
                    <div className="flex items-center gap-4 mb-4">
                      <div className="flex items-center gap-2 px-3 py-1.5 bg-critical/10 border border-critical/30 rounded-lg">
                        <span className="font-display text-lg font-bold text-critical">{selectedEquipment.thresholdCriticals}</span>
                        <span className="text-xs text-critical">Critical</span>
                      </div>
                      <div className="flex items-center gap-2 px-3 py-1.5 bg-warning/10 border border-warning/30 rounded-lg">
                        <span className="font-display text-lg font-bold text-warning">{selectedEquipment.thresholdWarnings}</span>
                        <span className="text-xs text-warning">Warning</span>
                      </div>
                    </div>
                    <div className="space-y-2">
                      {(['vibration', 'temperature', 'power'] as const).map((sensor) => {
                        const level = selectedEquipment.currentThresholds[sensor];
                        const value = sensor === 'vibration' ? selectedEquipment.vibration
                          : sensor === 'temperature' ? selectedEquipment.temperature
                          : selectedEquipment.power;
                        const unit = sensor === 'vibration' ? 'mm/s' : sensor === 'temperature' ? '°C' : 'kW';
                        const thresholds = THRESHOLDS[sensor];
                        const color = level === 'critical' ? 'text-critical' : level === 'warning' ? 'text-warning' : 'text-healthy';
                        const bg = level === 'critical' ? 'bg-critical/10' : level === 'warning' ? 'bg-warning/10' : 'bg-steel';
                        return (
                          <div key={sensor} className={`flex items-center justify-between p-2 rounded-lg ${bg}`}>
                            <span className="text-xs text-zinc-300 capitalize">{sensor}</span>
                            <div className="flex items-center gap-3">
                              <span className="text-[10px] text-ash">
                                W:{thresholds.warning} C:{thresholds.critical}
                              </span>
                              <span className={`font-mono text-sm font-bold ${color}`}>
                                {value.toFixed(1)} {unit}
                              </span>
                              <span className={`px-1.5 py-0.5 rounded text-[9px] font-mono font-bold uppercase ${
                                level === 'critical' ? 'bg-critical/20 text-critical'
                                : level === 'warning' ? 'bg-warning/20 text-warning'
                                : 'bg-healthy/20 text-healthy'
                              }`}>
                                {level}
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}

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

function RULPanel({
  hours, confidence, basis,
}: {
  hours: number; confidence: 'high' | 'medium' | 'low'; basis: string;
}) {
  const status = hours < 48 ? 'critical' : hours < 168 ? 'warning' : 'healthy';
  const colors = { healthy: 'text-healthy', warning: 'text-warning', critical: 'text-critical' };
  const confidenceColors = {
    high: 'bg-healthy/20 text-healthy border-healthy/30',
    medium: 'bg-warning/20 text-warning border-warning/30',
    low: 'bg-slate/20 text-slate border-slate/30',
  };

  return (
    <div className="panel p-4">
      <div className="flex items-center justify-between mb-2">
        <p className="text-xs text-slate uppercase tracking-wider">Remaining Useful Life</p>
        <span className={`px-1.5 py-0.5 rounded text-[9px] font-mono uppercase border ${confidenceColors[confidence]}`}>
          {confidence}
        </span>
      </div>
      <p className={`font-display text-2xl font-bold ${colors[status]}`}>{formatHours(hours)}</p>
      <p className="text-xs text-ash mt-1">{basis}</p>
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
