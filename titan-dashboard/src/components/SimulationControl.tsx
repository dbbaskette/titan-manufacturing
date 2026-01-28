// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Simulation Control Panel
// Control sensor data generation and inject anomalies for demos
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect } from 'react';
import {
  Zap,
  Play,
  Pause,
  RotateCcw,
  AlertTriangle,
  Activity,
  Settings2,
  RefreshCw,
  CheckCircle,
  XCircle,
  ChevronUp,
  ChevronDown,
} from 'lucide-react';
import { sortEquipment } from '../utils/equipmentUtils';

// Generator API base URL (direct to generator service)
const GENERATOR_API = 'http://localhost:8090/api/generator';

interface EquipmentState {
  equipmentId: string;
  facilityId: string;
  pattern: string;
  cycles: number;
  vibration: number;
  temperature: number;
  rpm: number;
  power: number;
  pressure: number;
  torque: number;
}

interface GeneratorStatus {
  enabled: boolean;
  equipmentCount: number;
  patterns: string[];
}

const PATTERNS = [
  { value: 'NORMAL', label: 'Normal', color: 'healthy', description: 'Stable baseline operation' },
  { value: 'BEARING_DEGRADATION', label: 'Bearing Degradation', color: 'critical', description: 'Exponential vibration increase' },
  { value: 'MOTOR_BURNOUT', label: 'Motor Burnout', color: 'critical', description: 'Rapid temperature spike' },
  { value: 'SPINDLE_WEAR', label: 'Spindle Wear', color: 'warning', description: 'Gradual RPM loss' },
  { value: 'COOLANT_FAILURE', label: 'Coolant Failure', color: 'warning', description: 'Temperature rise, pressure drop' },
  { value: 'ELECTRICAL_FAULT', label: 'Electrical Fault', color: 'warning', description: 'Erratic power readings' },
];

export function SimulationControl() {
  const [status, setStatus] = useState<GeneratorStatus | null>(null);
  const [equipment, setEquipment] = useState<EquipmentState[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedFacility, setSelectedFacility] = useState<string>('all');
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  // Fetch generator status and equipment
  const fetchData = async () => {
    try {
      const [statusRes, equipmentRes] = await Promise.all([
        fetch(`${GENERATOR_API}/status`),
        fetch(`${GENERATOR_API}/equipment`),
      ]);

      if (!statusRes.ok || !equipmentRes.ok) {
        throw new Error('Generator service not available');
      }

      const statusData = await statusRes.json();
      const equipmentData = await equipmentRes.json();

      setStatus(statusData);
      setEquipment(sortEquipment(equipmentData));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to connect to generator');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 5000); // Refresh every 5s
    return () => clearInterval(interval);
  }, []);

  // Set equipment pattern
  const setPattern = async (equipmentId: string, pattern: string) => {
    setActionInProgress(equipmentId);
    try {
      const res = await fetch(`${GENERATOR_API}/equipment/${equipmentId}/pattern?pattern=${pattern}`, {
        method: 'POST',
      });
      if (res.ok) {
        await fetchData();
      }
    } catch (err) {
      console.error('Failed to set pattern:', err);
    } finally {
      setActionInProgress(null);
    }
  };

  // Reset equipment to normal
  const resetEquipment = async (equipmentId: string) => {
    setActionInProgress(equipmentId);
    try {
      const res = await fetch(`${GENERATOR_API}/equipment/${equipmentId}/reset`, {
        method: 'POST',
      });
      if (res.ok) {
        // Flush ML predictions so stale data doesn't linger
        fetch('http://localhost:8082/ml/predictions/reset', { method: 'POST' }).catch(() => {});
        await fetchData();
      }
    } catch (err) {
      console.error('Failed to reset equipment:', err);
    } finally {
      setActionInProgress(null);
    }
  };

  // Reset all equipment
  const resetAll = async () => {
    setActionInProgress('all');
    try {
      const res = await fetch(`${GENERATOR_API}/reset-all`, {
        method: 'POST',
      });
      if (res.ok) {
        // Also flush ML predictions and sensor windows so stale data doesn't linger
        fetch('http://localhost:8082/ml/predictions/reset', { method: 'POST' }).catch(() => {});
        await fetchData();
      }
    } catch (err) {
      console.error('Failed to reset all:', err);
    } finally {
      setActionInProgress(null);
    }
  };

  // Trigger Phoenix Incident
  const triggerPhoenixIncident = async () => {
    setActionInProgress('phoenix');
    try {
      const res = await fetch(`${GENERATOR_API}/scenarios/phoenix-incident`, {
        method: 'POST',
      });
      if (res.ok) {
        await fetchData();
      }
    } catch (err) {
      console.error('Failed to trigger Phoenix Incident:', err);
    } finally {
      setActionInProgress(null);
    }
  };

  // Toggle generator enabled/disabled
  const toggleGenerator = async () => {
    if (!status) return;
    setActionInProgress('toggle');
    try {
      const res = await fetch(`${GENERATOR_API}/enabled?enabled=${!status.enabled}`, {
        method: 'POST',
      });
      if (res.ok) {
        await fetchData();
      }
    } catch (err) {
      console.error('Failed to toggle generator:', err);
    } finally {
      setActionInProgress(null);
    }
  };

  // Adjust sensor value by delta
  const adjustSensor = async (equipmentId: string, deltas: {
    vibration?: number; temperature?: number; rpm?: number;
    power?: number; pressure?: number; torque?: number;
  }) => {
    try {
      const params = new URLSearchParams();
      for (const [key, val] of Object.entries(deltas)) {
        if (val !== undefined) params.set(key, String(val));
      }
      await fetch(`${GENERATOR_API}/equipment/${equipmentId}/adjust?${params}`, { method: 'POST' });
      await fetchData();
    } catch (err) {
      console.error('Failed to adjust sensor:', err);
    }
  };

  // Get unique facilities
  const facilities = [...new Set(equipment.map((e) => e.facilityId))].sort();

  // Filter equipment by facility
  const filteredEquipment =
    selectedFacility === 'all' ? equipment : equipment.filter((e) => e.facilityId === selectedFacility);

  // Count equipment by pattern
  const patternCounts = equipment.reduce(
    (acc, e) => {
      acc[e.pattern] = (acc[e.pattern] || 0) + 1;
      return acc;
    },
    {} as Record<string, number>
  );

  const getPatternColor = (pattern: string) => {
    const p = PATTERNS.find((p) => p.value === pattern);
    return p?.color || 'slate';
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <RefreshCw className="animate-spin text-ember" size={32} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="panel p-8 text-center">
        <XCircle size={48} className="text-critical mx-auto mb-4" />
        <h3 className="font-display text-xl font-bold text-white mb-2">Generator Unavailable</h3>
        <p className="text-slate mb-4">{error}</p>
        <p className="text-sm text-ash">
          Make sure the sensor-data-generator is running on port 8090.
          <br />
          <code className="text-ember">docker compose --profile generator up -d</code>
        </p>
        <button onClick={fetchData} className="btn-primary mt-4">
          <RefreshCw size={16} />
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6 fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <Zap className="text-ember" />
            Simulation Control
          </h2>
          <p className="text-slate mt-1">Control sensor data generation and inject anomalies</p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={resetAll}
            disabled={actionInProgress === 'all'}
            className="btn-secondary flex items-center gap-2"
          >
            <RotateCcw size={16} className={actionInProgress === 'all' ? 'animate-spin' : ''} />
            Reset All
          </button>
          <button
            onClick={toggleGenerator}
            disabled={actionInProgress === 'toggle'}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all ${
              status?.enabled
                ? 'bg-healthy/20 text-healthy border border-healthy/30 hover:bg-healthy/30'
                : 'bg-critical/20 text-critical border border-critical/30 hover:bg-critical/30'
            }`}
          >
            {status?.enabled ? <Pause size={16} /> : <Play size={16} />}
            {status?.enabled ? 'Running' : 'Stopped'}
          </button>
        </div>
      </div>

      {/* Status Cards */}
      <div className="grid grid-cols-4 gap-4">
        <div className="panel p-4 stagger scale-in stagger-1">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-ember/10 border border-ember/20">
              <Activity size={20} className="text-ember" />
            </div>
            <div>
              <p className="text-2xl font-display font-bold text-white">{status?.equipmentCount || 0}</p>
              <p className="text-xs text-slate">Equipment Active</p>
            </div>
          </div>
        </div>

        <div className="panel p-4 stagger scale-in stagger-2">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-healthy/10 border border-healthy/20">
              <CheckCircle size={20} className="text-healthy" />
            </div>
            <div>
              <p className="text-2xl font-display font-bold text-white">{patternCounts['NORMAL'] || 0}</p>
              <p className="text-xs text-slate">Normal Operation</p>
            </div>
          </div>
        </div>

        <div className="panel p-4 stagger scale-in stagger-3">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-warning/10 border border-warning/20">
              <AlertTriangle size={20} className="text-warning" />
            </div>
            <div>
              <p className="text-2xl font-display font-bold text-white">
                {Object.entries(patternCounts)
                  .filter(([k]) => k !== 'NORMAL')
                  .reduce((sum, [, v]) => sum + v, 0)}
              </p>
              <p className="text-xs text-slate">With Anomalies</p>
            </div>
          </div>
        </div>

        <div className="panel p-4 stagger scale-in stagger-4">
          <button
            onClick={triggerPhoenixIncident}
            disabled={actionInProgress === 'phoenix'}
            className="w-full h-full flex items-center gap-3 hover:bg-critical/10 rounded-lg transition-all"
          >
            <div className="p-2 rounded-lg bg-critical/10 border border-critical/20">
              <Zap size={20} className="text-critical" />
            </div>
            <div className="text-left">
              <p className="text-sm font-display font-bold text-critical">Phoenix Incident</p>
              <p className="text-xs text-slate">Trigger PHX-CNC-007 failure</p>
            </div>
          </button>
        </div>
      </div>

      {/* Facility Filter */}
      <div className="flex items-center gap-2">
        <span className="text-sm text-slate">Facility:</span>
        <div className="flex gap-2">
          <button
            onClick={() => setSelectedFacility('all')}
            className={`px-3 py-1.5 rounded-lg text-xs font-display font-semibold transition-all ${
              selectedFacility === 'all'
                ? 'bg-ember text-white'
                : 'bg-steel text-slate hover:bg-iron hover:text-white'
            }`}
          >
            All
          </button>
          {facilities.map((f) => (
            <button
              key={f}
              onClick={() => setSelectedFacility(f)}
              className={`px-3 py-1.5 rounded-lg text-xs font-display font-semibold transition-all ${
                selectedFacility === f
                  ? 'bg-ember text-white'
                  : 'bg-steel text-slate hover:bg-iron hover:text-white'
              }`}
            >
              {f}
            </button>
          ))}
        </div>
      </div>

      {/* Equipment Grid */}
      <div className="panel">
        <div className="panel-header">
          <Settings2 size={16} />
          Equipment Patterns
          <span className="ml-auto text-xs font-normal text-slate">
            {filteredEquipment.length} equipment
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-iron">
                <th className="text-left p-3 text-xs text-slate uppercase tracking-wider">Equipment</th>
                <th className="text-left p-3 text-xs text-slate uppercase tracking-wider">Facility</th>
                <th className="text-left p-3 text-xs text-slate uppercase tracking-wider">Pattern</th>
                <th className="text-center p-3 text-xs text-slate uppercase tracking-wider">Cycles</th>
                <th className="text-center p-3 text-xs text-slate uppercase tracking-wider">Vibration</th>
                <th className="text-center p-3 text-xs text-slate uppercase tracking-wider">Temp</th>
                <th className="text-center p-3 text-xs text-slate uppercase tracking-wider">RPM</th>
                <th className="text-center p-3 text-xs text-slate uppercase tracking-wider">Power</th>
                <th className="text-center p-3 text-xs text-slate uppercase tracking-wider">Pressure</th>
                <th className="text-center p-3 text-xs text-slate uppercase tracking-wider">Torque</th>
                <th className="text-right p-3 text-xs text-slate uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredEquipment.map((eq, index) => (
                <tr
                  key={eq.equipmentId}
                  className={`border-b border-iron/50 hover:bg-steel/50 transition-colors stagger fade-in stagger-${Math.min(index + 1, 10)}`}
                >
                  <td className="p-3">
                    <span className="font-mono text-sm text-white">{eq.equipmentId}</span>
                  </td>
                  <td className="p-3">
                    <span className="text-sm text-slate">{eq.facilityId}</span>
                  </td>
                  <td className="p-3">
                    <select
                      value={eq.pattern}
                      onChange={(e) => setPattern(eq.equipmentId, e.target.value)}
                      disabled={actionInProgress === eq.equipmentId}
                      className={`bg-steel border rounded px-2 py-1 text-sm font-medium ${
                        eq.pattern === 'NORMAL'
                          ? 'border-healthy/30 text-healthy'
                          : `border-${getPatternColor(eq.pattern)}/30 text-${getPatternColor(eq.pattern)}`
                      }`}
                    >
                      {PATTERNS.map((p) => (
                        <option key={p.value} value={p.value}>
                          {p.label}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="p-3 text-center">
                    <span className="font-mono text-sm text-ash">{eq.cycles}</span>
                  </td>
                  {/* Vibration */}
                  <td className="p-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button onClick={() => adjustSensor(eq.equipmentId, { vibration: -0.5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronDown size={14} />
                      </button>
                      <span className={`font-mono text-sm min-w-[50px] ${
                        eq.vibration >= 5.0 ? 'text-critical' : eq.vibration >= 3.5 ? 'text-warning' : 'text-healthy'
                      }`}>{eq.vibration.toFixed(2)}</span>
                      <button onClick={() => adjustSensor(eq.equipmentId, { vibration: 0.5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronUp size={14} />
                      </button>
                    </div>
                  </td>
                  {/* Temperature */}
                  <td className="p-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button onClick={() => adjustSensor(eq.equipmentId, { temperature: -5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronDown size={14} />
                      </button>
                      <span className={`font-mono text-sm min-w-[45px] ${
                        eq.temperature >= 85 ? 'text-critical' : eq.temperature >= 70 ? 'text-warning' : 'text-healthy'
                      }`}>{eq.temperature.toFixed(1)}°</span>
                      <button onClick={() => adjustSensor(eq.equipmentId, { temperature: 5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronUp size={14} />
                      </button>
                    </div>
                  </td>
                  {/* RPM */}
                  <td className="p-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button onClick={() => adjustSensor(eq.equipmentId, { rpm: -500 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronDown size={14} />
                      </button>
                      <span className={`font-mono text-sm min-w-[50px] ${
                        eq.rpm < 7500 ? 'text-warning' : 'text-healthy'
                      }`}>{(eq.rpm / 1000).toFixed(1)}k</span>
                      <button onClick={() => adjustSensor(eq.equipmentId, { rpm: 500 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronUp size={14} />
                      </button>
                    </div>
                  </td>
                  {/* Power */}
                  <td className="p-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button onClick={() => adjustSensor(eq.equipmentId, { power: -5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronDown size={14} />
                      </button>
                      <span className={`font-mono text-sm min-w-[45px] ${
                        eq.power >= 40 ? 'text-critical' : eq.power >= 25 ? 'text-warning' : 'text-healthy'
                      }`}>{eq.power.toFixed(1)}</span>
                      <button onClick={() => adjustSensor(eq.equipmentId, { power: 5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronUp size={14} />
                      </button>
                    </div>
                  </td>
                  {/* Pressure */}
                  <td className="p-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button onClick={() => adjustSensor(eq.equipmentId, { pressure: -0.5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronDown size={14} />
                      </button>
                      <span className={`font-mono text-sm min-w-[40px] ${
                        eq.pressure < 4.5 ? 'text-warning' : 'text-healthy'
                      }`}>{eq.pressure.toFixed(1)}</span>
                      <button onClick={() => adjustSensor(eq.equipmentId, { pressure: 0.5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronUp size={14} />
                      </button>
                    </div>
                  </td>
                  {/* Torque */}
                  <td className="p-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button onClick={() => adjustSensor(eq.equipmentId, { torque: -5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronDown size={14} />
                      </button>
                      <span className={`font-mono text-sm min-w-[40px] ${
                        eq.torque >= 55 ? 'text-warning' : 'text-healthy'
                      }`}>{eq.torque.toFixed(1)}</span>
                      <button onClick={() => adjustSensor(eq.equipmentId, { torque: 5 })}
                        className="p-0.5 rounded hover:bg-iron text-slate hover:text-white transition-colors">
                        <ChevronUp size={14} />
                      </button>
                    </div>
                  </td>
                  <td className="p-3 text-right">
                    <button
                      onClick={() => resetEquipment(eq.equipmentId)}
                      disabled={eq.pattern === 'NORMAL' || actionInProgress === eq.equipmentId}
                      className="px-3 py-1 bg-steel text-slate hover:text-white hover:bg-iron rounded text-xs transition-all disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                      <RotateCcw
                        size={12}
                        className={`inline mr-1 ${actionInProgress === eq.equipmentId ? 'animate-spin' : ''}`}
                      />
                      Reset
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Pattern Legend */}
      <div className="panel p-4">
        <h4 className="text-xs text-ash uppercase tracking-wider mb-3">Degradation Patterns</h4>
        <div className="grid grid-cols-3 gap-4">
          {PATTERNS.map((p) => (
            <div key={p.value} className="flex items-start gap-3">
              <div
                className={`w-3 h-3 rounded-full mt-1 ${
                  p.color === 'healthy'
                    ? 'bg-healthy'
                    : p.color === 'warning'
                      ? 'bg-warning'
                      : p.color === 'critical'
                        ? 'bg-critical'
                        : 'bg-slate'
                }`}
              />
              <div>
                <p className="text-sm font-medium text-white">{p.label}</p>
                <p className="text-xs text-slate">{p.description}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
