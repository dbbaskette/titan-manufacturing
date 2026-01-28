// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Real-time Sensor Monitor
// Layered Detection: Threshold Alerts + ML Predictions
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  AreaChart,
  Area,
  ReferenceLine,
} from 'recharts';
import { Activity, Thermometer, Zap, RotateCcw, AlertTriangle, Wifi, WifiOff, ChevronRight, MapPin, Brain } from 'lucide-react';
import {
  sortEquipment,
  groupByFacility,
  getFacilityDisplayName,
  fetchGeneratorEquipment,
} from '../utils/equipmentUtils';
import type { GeneratorEquipment } from '../utils/equipmentUtils';
import { titanApi } from '../api/titanApi';
import type { MLPrediction, MLPredictionsData } from '../api/titanApi';

const SENSOR_API_URL = 'http://localhost:8081/api/sensors';

// ── Threshold Constants ─────────────────────────────────────────────────
const THRESHOLDS = {
  vibration: { warning: 3.0, critical: 3.5 },
  temperature: { warning: 70, critical: 85 },
  power: { warning: 50, critical: 55 },
} as const;

type ThresholdLevel = 'normal' | 'warning' | 'critical';

function getThresholdLevel(sensorType: keyof typeof THRESHOLDS, value: number): ThresholdLevel {
  const t = THRESHOLDS[sensorType];
  if (value >= t.critical) return 'critical';
  if (value >= t.warning) return 'warning';
  return 'normal';
}

function worstLevel(a: ThresholdLevel, b: ThresholdLevel): ThresholdLevel {
  const order: Record<ThresholdLevel, number> = { normal: 0, warning: 1, critical: 2 };
  return order[a] >= order[b] ? a : b;
}

interface SensorData {
  ts: number; // epoch ms — used as X-axis domain value
  temperature: number;
  vibration: number;
  power: number;
  spindle_speed: number;
  pressure: number;
  torque: number;
}

/** Format epoch ms → "HH:MM:SS" for axis tick labels and tooltip */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function formatTime(ts: any): string {
  const d = new Date(Number(ts));
  return d.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

/** Max data points to keep (2s interval × 60 = 2 minutes of data) */
const MAX_POINTS = 60;

/** Sliding window duration in ms (2 minutes) */
const CHART_WINDOW_MS = 2 * 60 * 1000;

interface SensorReading {
  equipmentId: string;
  sensorType: string;
  value: number;
  unit: string;
  timestamp: string;
  qualityFlag: string;
}

// Track latest values per equipment for sidebar threshold coloring
const latestValuesCache = new Map<string, { vibration: number; temperature: number }>();

// Cache sensor data per equipment so it persists when navigating away
const sensorDataCache = new Map<string, SensorData[]>();

export function SensorMonitor() {
  // Equipment state
  const [equipmentList, setEquipmentList] = useState<GeneratorEquipment[]>([]);
  const [equipmentLoading, setEquipmentLoading] = useState(true);
  const [expandedFacilities, setExpandedFacilities] = useState<Set<string>>(new Set(['PHX']));
  const [selectedEquipmentId, setSelectedEquipmentId] = useState<string>('');

  // Sensor data state
  const [sensorData, setSensorData] = useState<SensorData[]>([]);
  const [isLive, setIsLive] = useState(true);
  const [connectionStatus, setConnectionStatus] = useState<'connected' | 'connecting' | 'disconnected'>('disconnected');
  const eventSourceRef = useRef<EventSource | null>(null);
  const latestReadingsRef = useRef<Map<string, SensorReading>>(new Map());
  const connectionStatusRef = useRef<'connected' | 'connecting' | 'disconnected'>('disconnected');
  const currentEquipmentRef = useRef<string>('');
  const sensorDataRef = useRef<SensorData[]>([]);

  // ML Predictions state
  const [mlPredictions, setMlPredictions] = useState<MLPredictionsData | null>(null);

  // Derived: sorted and grouped equipment
  const sorted = useMemo(() => sortEquipment(equipmentList), [equipmentList]);
  const grouped = useMemo(() => groupByFacility(sorted), [sorted]);

  // Track whether initial selection has happened
  const hasSelectedRef = useRef(false);

  // Fetch equipment from generator API
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const data = await fetchGeneratorEquipment();
        if (cancelled) return;
        setEquipmentList(data);
        // Sync latestValuesCache from generator so sidebar updates immediately after reset.
        // Also clear stale chart cache for equipment that returned to normal baselines.
        for (const eq of data) {
          const prev = latestValuesCache.get(eq.equipmentId);
          latestValuesCache.set(eq.equipmentId, {
            vibration: eq.vibration,
            temperature: eq.temperature,
          });
          // If values dropped significantly (reset happened), flush chart cache
          if (prev && (prev.vibration - eq.vibration > 1.0 || prev.temperature - eq.temperature > 10)) {
            sensorDataCache.delete(eq.equipmentId);
          }
        }
        // Auto-select only on first load — prefer first Phoenix CNC (matches demo docs)
        if (!hasSelectedRef.current && data.length > 0) {
          hasSelectedRef.current = true;
          const defaultEq = data.find(e => e.equipmentId.startsWith('PHX-CNC')) || data[0];
          if (defaultEq) setSelectedEquipmentId(defaultEq.equipmentId);
        }
      } catch {
        // Generator offline — keep whatever we had
      } finally {
        if (!cancelled) setEquipmentLoading(false);
      }
    };
    load();
    const interval = setInterval(load, 5000);
    return () => { cancelled = true; clearInterval(interval); };
  }, []);

  // Poll ML predictions from GemFire every 30s
  useEffect(() => {
    let cancelled = false;
    const poll = async () => {
      try {
        const data = await titanApi.getMlPredictions();
        if (!cancelled) setMlPredictions(data);
      } catch {
        // ML predictions unavailable
      }
    };
    poll();
    const interval = setInterval(poll, 30000);
    return () => { cancelled = true; clearInterval(interval); };
  }, []);

  // Convert SSE readings to chart data point
  const createDataPointFromReadings = useCallback((readings: Map<string, SensorReading>): SensorData | null => {
    const temp = readings.get('temperature');
    const vib = readings.get('vibration');
    const pwr = readings.get('power') || readings.get('power_draw');
    const rpm = readings.get('spindle_speed');
    const pres = readings.get('pressure');
    const torq = readings.get('torque');

    if (!temp && !vib && !pwr && !rpm && !pres && !torq) return null;

    // Cache latest values for sidebar coloring
    const eqId = temp?.equipmentId || vib?.equipmentId || pwr?.equipmentId || rpm?.equipmentId;
    if (eqId) {
      latestValuesCache.set(eqId, {
        vibration: vib?.value ?? 2.2,
        temperature: temp?.value ?? 68,
      });
    }

    return {
      ts: Date.now(),
      temperature: temp?.value ?? 68,
      vibration: vib?.value ?? 2.2,
      power: pwr?.value ?? 42,
      spindle_speed: rpm?.value ?? 12000,
      pressure: pres?.value ?? 6.0,
      torque: torq?.value ?? 45,
    };
  }, []);

  // Connect to SSE stream
  useEffect(() => {
    if (!selectedEquipmentId) return;

    if (!isLive) {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      setConnectionStatus('disconnected');
      connectionStatusRef.current = 'disconnected';
      return;
    }

    // Save current data to cache SYNCHRONOUSLY before switching equipment
    if (currentEquipmentRef.current !== selectedEquipmentId) {
      if (sensorDataRef.current.length > 0) {
        sensorDataCache.set(currentEquipmentRef.current, [...sensorDataRef.current]);
      }
      currentEquipmentRef.current = selectedEquipmentId;
    }

    // Restore from cache if available, otherwise start fresh
    const cachedData = sensorDataCache.get(selectedEquipmentId);
    const initialData = cachedData ? [...cachedData] : [];
    setSensorData(initialData);
    sensorDataRef.current = initialData;

    setConnectionStatus('connecting');
    connectionStatusRef.current = 'connecting';

    // Close existing connection
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const url = `${SENSOR_API_URL}/stream?equipmentId=${selectedEquipmentId}`;
    const eventSource = new EventSource(url);
    eventSourceRef.current = eventSource;

    eventSource.onopen = () => {
      setConnectionStatus('connected');
      connectionStatusRef.current = 'connected';
    };

    eventSource.addEventListener('sensor-reading', (event) => {
      try {
        const reading: SensorReading = JSON.parse(event.data);
        if (reading.equipmentId === selectedEquipmentId) {
          latestReadingsRef.current.set(reading.sensorType, reading);
          if (connectionStatusRef.current !== 'connected') {
            setConnectionStatus('connected');
            connectionStatusRef.current = 'connected';
          }
        }
      } catch (e) {
        console.error('Error parsing SSE data:', e);
      }
    });

    eventSource.addEventListener('connected', () => {
      setConnectionStatus('connected');
      connectionStatusRef.current = 'connected';
    });

    eventSource.onerror = () => {
      if (eventSource.readyState === EventSource.CLOSED) {
        setConnectionStatus('disconnected');
        connectionStatusRef.current = 'disconnected';
      }
    };

    // Update chart with real readings and keep ref/cache in sync
    const interval = setInterval(() => {
      const newPoint = createDataPointFromReadings(latestReadingsRef.current);
      if (newPoint && connectionStatusRef.current === 'connected') {
        setSensorData((prev) => {
          const updated = [...prev, newPoint];
          const trimmed = updated.length > MAX_POINTS ? updated.slice(-MAX_POINTS) : updated;
          sensorDataRef.current = trimmed;
          sensorDataCache.set(selectedEquipmentId, trimmed);
          return trimmed;
        });
      }
    }, 2000);

    return () => {
      clearInterval(interval);
      eventSource.close();
      eventSourceRef.current = null;
      latestReadingsRef.current.clear();
    };
  }, [selectedEquipmentId, isLive, createDataPointFromReadings]);

  // ── X-axis sliding window: current time at right edge ────────────────
  const xDomain: [number, number] = sensorData.length > 0
    ? [sensorData[sensorData.length - 1].ts - CHART_WINDOW_MS, sensorData[sensorData.length - 1].ts]
    : [Date.now() - CHART_WINDOW_MS, Date.now()];

  // ── Derive threshold status from live sensor values ───────────────────
  const latestData = sensorData[sensorData.length - 1] || {
    temperature: 0,
    vibration: 0,
    power: 0,
    spindle_speed: 0,
    pressure: 0,
    torque: 0,
  };

  const vibLevel = getThresholdLevel('vibration', latestData.vibration);
  const tempLevel = getThresholdLevel('temperature', latestData.temperature);
  const powerLevel = getThresholdLevel('power', latestData.power);
  const overallLevel = worstLevel(vibLevel, worstLevel(tempLevel, powerLevel));

  // Build threshold alert messages
  const thresholdAlerts: { message: string; level: ThresholdLevel }[] = [];
  if (tempLevel !== 'normal') {
    thresholdAlerts.push({
      message: `Temperature at ${latestData.temperature.toFixed(1)}°C (${tempLevel} threshold: ${THRESHOLDS.temperature[tempLevel]}°C)`,
      level: tempLevel,
    });
  }
  if (vibLevel !== 'normal') {
    thresholdAlerts.push({
      message: `Vibration at ${latestData.vibration.toFixed(2)} mm/s (${vibLevel} threshold: ${THRESHOLDS.vibration[vibLevel]} mm/s)`,
      level: vibLevel,
    });
  }
  if (powerLevel !== 'normal') {
    thresholdAlerts.push({
      message: `Power draw at ${latestData.power.toFixed(1)} kW (${powerLevel} threshold: ${THRESHOLDS.power[powerLevel]} kW)`,
      level: powerLevel,
    });
  }

  // ── ML Prediction for selected equipment ──────────────────────────────
  const mlPrediction: MLPrediction | undefined = mlPredictions?.success
    ? mlPredictions.predictions.find(p => p.equipmentId === selectedEquipmentId)
    : undefined;

  const showMlBanner = mlPrediction && mlPrediction.failureProbability >= 0.3;

  // Helper: get sidebar status for an equipment from cached values or ML predictions
  function getEquipmentSidebarStatus(eqId: string): { dotClass: string; label?: string } {
    const cached = latestValuesCache.get(eqId);
    const pred = mlPredictions?.success
      ? mlPredictions.predictions.find(p => p.equipmentId === eqId)
      : undefined;

    // Check ML prediction first (higher priority signal)
    if (pred && pred.failureProbability >= 0.5) {
      return { dotClass: 'bg-critical', label: `${Math.round(pred.failureProbability * 100)}% RISK` };
    }

    // Then check threshold from cached sensor values
    if (cached) {
      const vib = getThresholdLevel('vibration', cached.vibration);
      const temp = getThresholdLevel('temperature', cached.temperature);
      const worst = worstLevel(vib, temp);
      if (worst === 'critical') {
        const reasons: string[] = [];
        if (vib === 'critical') reasons.push('VIB CRITICAL');
        if (temp === 'critical') reasons.push('TEMP CRITICAL');
        return { dotClass: 'bg-critical', label: reasons.join(' / ') };
      }
      if (worst === 'warning') {
        const reasons: string[] = [];
        if (vib === 'warning') reasons.push('VIB HIGH');
        if (temp === 'warning') reasons.push('TEMP HIGH');
        return { dotClass: 'bg-warning', label: reasons.join(' / ') };
      }
    }

    // Check ML prediction at lower threshold for warning
    if (pred && pred.failureProbability >= 0.3) {
      return { dotClass: 'bg-warning', label: `${Math.round(pred.failureProbability * 100)}% RISK` };
    }

    return { dotClass: 'bg-healthy' };
  }

  // ── Fleet-wide statistics ─────────────────────────────────────────────
  const fleetStats = useMemo(() => {
    if (equipmentList.length === 0) return null;

    const vibrations = equipmentList.map(e => e.vibration);
    const temperatures = equipmentList.map(e => e.temperature);
    const powers = equipmentList.map(e => e.power);
    const rpms = equipmentList.map(e => e.rpm);

    const avg = (arr: number[]) => arr.reduce((a, b) => a + b, 0) / arr.length;
    const min = (arr: number[]) => Math.min(...arr);
    const max = (arr: number[]) => Math.max(...arr);

    // Count equipment exceeding thresholds
    let vibWarning = 0, vibCritical = 0;
    let tempWarning = 0, tempCritical = 0;
    let powerWarning = 0, powerCritical = 0;

    for (const eq of equipmentList) {
      const vl = getThresholdLevel('vibration', eq.vibration);
      const tl = getThresholdLevel('temperature', eq.temperature);
      const pl = getThresholdLevel('power', eq.power);
      if (vl === 'critical') vibCritical++;
      else if (vl === 'warning') vibWarning++;
      if (tl === 'critical') tempCritical++;
      else if (tl === 'warning') tempWarning++;
      if (pl === 'critical') powerCritical++;
      else if (pl === 'warning') powerWarning++;
    }

    return {
      total: equipmentList.length,
      vibration: { avg: avg(vibrations), min: min(vibrations), max: max(vibrations), warning: vibWarning, critical: vibCritical },
      temperature: { avg: avg(temperatures), min: min(temperatures), max: max(temperatures), warning: tempWarning, critical: tempCritical },
      power: { avg: avg(powers), min: min(powers), max: max(powers), warning: powerWarning, critical: powerCritical },
      rpm: { avg: avg(rpms), min: min(rpms), max: max(rpms) },
    };
  }, [equipmentList]);

  return (
    <div className="space-y-6 fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <Activity className="text-ember" />
            Real-time Sensor Monitor
          </h2>
          <p className="text-slate mt-1">Live telemetry from manufacturing equipment</p>
        </div>
        <div className="flex items-center gap-3">
          {/* Connection Status */}
          <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-mono ${
            connectionStatus === 'connected'
              ? 'bg-healthy/20 text-healthy border border-healthy/30'
              : connectionStatus === 'connecting'
              ? 'bg-warning/20 text-warning border border-warning/30'
              : 'bg-steel text-slate'
          }`}>
            {connectionStatus === 'connected' ? <Wifi size={14} /> : <WifiOff size={14} />}
            {connectionStatus === 'connected' ? 'SSE Connected' : connectionStatus === 'connecting' ? 'Connecting...' : 'Disconnected'}
          </div>
          <button
            onClick={() => setIsLive(!isLive)}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
              isLive ? 'bg-healthy/20 text-healthy border border-healthy/30' : 'bg-steel text-slate'
            }`}
          >
            <div className={`w-2 h-2 rounded-full ${isLive ? 'bg-healthy animate-pulse' : 'bg-slate'}`} />
            {isLive ? 'LIVE' : 'PAUSED'}
          </button>
        </div>
      </div>

      {/* Fleet-Wide Summary */}
      {fleetStats && (
        <div className="panel">
          <div className="panel-header">
            <Activity size={16} />
            Fleet-Wide Sensor Summary
            <span className="ml-auto text-xs font-normal text-slate">{fleetStats.total} equipment monitored</span>
          </div>
          <div className="grid grid-cols-4 gap-4 p-4">
            {/* Vibration Summary */}
            <div className="bg-steel rounded-lg p-3">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-slate uppercase tracking-wider">Vibration</span>
                {(fleetStats.vibration.critical > 0 || fleetStats.vibration.warning > 0) && (
                  <div className="flex items-center gap-1">
                    {fleetStats.vibration.critical > 0 && (
                      <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-critical/20 text-critical">
                        {fleetStats.vibration.critical} CRIT
                      </span>
                    )}
                    {fleetStats.vibration.warning > 0 && (
                      <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-warning/20 text-warning">
                        {fleetStats.vibration.warning} WARN
                      </span>
                    )}
                  </div>
                )}
              </div>
              <p className="font-display text-xl font-bold text-white">
                {fleetStats.vibration.avg.toFixed(2)} <span className="text-xs text-slate font-normal">mm/s avg</span>
              </p>
              <p className="text-xs text-ash mt-1">
                Range: {fleetStats.vibration.min.toFixed(1)} – {fleetStats.vibration.max.toFixed(1)} mm/s
              </p>
            </div>

            {/* Temperature Summary */}
            <div className="bg-steel rounded-lg p-3">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-slate uppercase tracking-wider">Temperature</span>
                {(fleetStats.temperature.critical > 0 || fleetStats.temperature.warning > 0) && (
                  <div className="flex items-center gap-1">
                    {fleetStats.temperature.critical > 0 && (
                      <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-critical/20 text-critical">
                        {fleetStats.temperature.critical} CRIT
                      </span>
                    )}
                    {fleetStats.temperature.warning > 0 && (
                      <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-warning/20 text-warning">
                        {fleetStats.temperature.warning} WARN
                      </span>
                    )}
                  </div>
                )}
              </div>
              <p className="font-display text-xl font-bold text-white">
                {fleetStats.temperature.avg.toFixed(1)} <span className="text-xs text-slate font-normal">°C avg</span>
              </p>
              <p className="text-xs text-ash mt-1">
                Range: {fleetStats.temperature.min.toFixed(0)} – {fleetStats.temperature.max.toFixed(0)}°C
              </p>
            </div>

            {/* Power Summary */}
            <div className="bg-steel rounded-lg p-3">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-slate uppercase tracking-wider">Power Draw</span>
                {(fleetStats.power.critical > 0 || fleetStats.power.warning > 0) && (
                  <div className="flex items-center gap-1">
                    {fleetStats.power.critical > 0 && (
                      <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-critical/20 text-critical">
                        {fleetStats.power.critical} CRIT
                      </span>
                    )}
                    {fleetStats.power.warning > 0 && (
                      <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-warning/20 text-warning">
                        {fleetStats.power.warning} WARN
                      </span>
                    )}
                  </div>
                )}
              </div>
              <p className="font-display text-xl font-bold text-white">
                {fleetStats.power.avg.toFixed(1)} <span className="text-xs text-slate font-normal">kW avg</span>
              </p>
              <p className="text-xs text-ash mt-1">
                Range: {fleetStats.power.min.toFixed(0)} – {fleetStats.power.max.toFixed(0)} kW
              </p>
            </div>

            {/* RPM Summary */}
            <div className="bg-steel rounded-lg p-3">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-slate uppercase tracking-wider">Spindle Speed</span>
              </div>
              <p className="font-display text-xl font-bold text-white">
                {(fleetStats.rpm.avg / 1000).toFixed(1)} <span className="text-xs text-slate font-normal">K RPM avg</span>
              </p>
              <p className="text-xs text-ash mt-1">
                Range: {(fleetStats.rpm.min / 1000).toFixed(1)} – {(fleetStats.rpm.max / 1000).toFixed(1)}K RPM
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Equipment Selector — Facility-Grouped Accordion */}
      <div className="panel">
        <div className="panel-header">
          <MapPin size={16} />
          Select Equipment
          <span className="ml-auto text-xs font-normal text-slate">
            {equipmentList.length} total
          </span>
        </div>
        {equipmentLoading ? (
          <div className="p-4 text-center text-slate text-sm">Loading equipment...</div>
        ) : equipmentList.length === 0 ? (
          <div className="p-4 text-center text-slate text-sm">
            No equipment found. Ensure the sensor-data-generator is running on port 8090.
          </div>
        ) : (
          <div className="max-h-64 overflow-y-auto">
            {Object.entries(grouped).map(([facilityCode, facilityEquipment]) => {
              const expanded = expandedFacilities.has(facilityCode);
              return (
                <div key={facilityCode} className="border-b border-iron/50 last:border-0">
                  {/* Facility header */}
                  <button
                    onClick={() => {
                      setExpandedFacilities(prev => {
                        const next = new Set(prev);
                        if (next.has(facilityCode)) next.delete(facilityCode);
                        else next.add(facilityCode);
                        return next;
                      });
                    }}
                    className="w-full flex items-center gap-3 p-3 hover:bg-steel/50 transition-colors"
                  >
                    <MapPin size={14} className="text-ember" />
                    <span className="font-display text-sm text-white">{getFacilityDisplayName(facilityCode)}</span>
                    <span className="text-xs text-slate">({facilityEquipment.length})</span>
                    <ChevronRight
                      size={14}
                      className={`ml-auto text-slate transition-transform ${expanded ? 'rotate-90' : ''}`}
                    />
                  </button>

                  {/* Equipment list */}
                  {expanded && (
                    <div className="bg-graphite/30">
                      {facilityEquipment.map((eq) => {
                        const status = getEquipmentSidebarStatus(eq.equipmentId);
                        return (
                          <button
                            key={eq.equipmentId}
                            onClick={() => setSelectedEquipmentId(eq.equipmentId)}
                            className={`w-full flex items-center gap-3 px-6 py-2 text-sm transition-all ${
                              selectedEquipmentId === eq.equipmentId
                                ? 'bg-ember/20 text-ember border-l-2 border-ember font-medium'
                                : 'text-zinc-300 hover:bg-steel/50 hover:text-white border-l-2 border-transparent'
                            }`}
                          >
                            <div
                              className={`w-2 h-2 rounded-full flex-shrink-0 ${status.dotClass}`}
                            />
                            <span className="font-mono">{eq.equipmentId}</span>
                            {status.label && (
                              <span className={`ml-auto text-xs ${
                                status.dotClass === 'bg-critical' ? 'text-critical' : 'text-warning'
                              }`}>
                                {status.label}
                              </span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Layer 1: Threshold Alert Banner */}
      {thresholdAlerts.length > 0 && (
        <div className={`${
          overallLevel === 'critical'
            ? 'bg-critical/10 border-critical/30'
            : 'bg-warning/10 border-warning/30'
        } border rounded-lg p-4 flex items-start gap-4`}>
          <AlertTriangle className={overallLevel === 'critical' ? 'text-critical' : 'text-warning'} size={24} />
          <div>
            <p className={`font-display font-semibold ${
              overallLevel === 'critical' ? 'text-critical' : 'text-warning'
            }`}>
              {overallLevel === 'critical' ? 'THRESHOLD EXCEEDED' : 'THRESHOLD WARNING'}
            </p>
            {thresholdAlerts.map((alert, i) => (
              <p key={i} className="text-sm text-slate mt-1">
                {selectedEquipmentId}: {alert.message}
              </p>
            ))}
          </div>
        </div>
      )}

      {/* Layer 2: ML Prediction Banner */}
      {showMlBanner && (
        <div className={`${
          mlPrediction.riskLevel === 'CRITICAL'
            ? 'bg-purple-900/20 border-purple-500/30'
            : mlPrediction.riskLevel === 'HIGH'
            ? 'bg-purple-900/15 border-purple-500/25'
            : 'bg-purple-900/10 border-purple-500/20'
        } border rounded-lg p-4 flex items-start gap-4`}>
          <Brain className="text-purple-400" size={24} />
          <div>
            <p className="font-display font-semibold text-purple-300">
              ML MODEL: {Math.round(mlPrediction.failureProbability * 100)}% failure probability ({mlPrediction.riskLevel})
            </p>
            <p className="text-sm text-slate mt-1">
              {selectedEquipmentId} — {mlPrediction.probableCause || 'elevated baseline readings'}
            </p>
            <p className="text-xs text-ash mt-1">
              Scored by GemFire PMML model ({mlPrediction.modelId}) · {new Date(mlPrediction.scoredAt).toLocaleTimeString()} · {mlPrediction.readingsInWindow} readings in window
            </p>
          </div>
        </div>
      )}

      {/* Current Values */}
      <div className="grid grid-cols-6 gap-3">
        <MetricCard
          icon={Thermometer}
          label="Temperature"
          value={latestData.temperature.toFixed(1)}
          unit="°C"
          status={tempLevel}
          threshold={`Warn: ${THRESHOLDS.temperature.warning}°C`}
        />
        <MetricCard
          icon={Activity}
          label="Vibration"
          value={latestData.vibration.toFixed(2)}
          unit="mm/s"
          status={vibLevel}
          threshold={`Warn: ${THRESHOLDS.vibration.warning} mm/s`}
        />
        <MetricCard
          icon={Zap}
          label="Power"
          value={latestData.power.toFixed(1)}
          unit="kW"
          status={powerLevel}
          threshold={`Warn: ${THRESHOLDS.power.warning} kW`}
        />
        <MetricCard
          icon={RotateCcw}
          label="RPM"
          value={(latestData.spindle_speed / 1000).toFixed(1)}
          unit="K RPM"
          status="normal"
          threshold="Max: 15K"
        />
        <MetricCard
          icon={Activity}
          label="Pressure"
          value={latestData.pressure.toFixed(1)}
          unit="bar"
          status="normal"
          threshold="Nominal: 6.0 bar"
        />
        <MetricCard
          icon={Activity}
          label="Torque"
          value={latestData.torque.toFixed(1)}
          unit="Nm"
          status="normal"
          threshold="Nominal: 45 Nm"
        />
      </div>

      {/* Charts Grid — 3 rows × 2 cols */}
      <div className="grid grid-cols-2 gap-4">
        {/* Temperature Chart */}
        <div className="panel scanlines stagger scale-in stagger-1">
          <div className="panel-header">
            <Thermometer size={16} />
            Temperature Trend
            {tempLevel !== 'normal' && (
              <span className={`ml-auto text-xs font-normal ${tempLevel === 'critical' ? 'text-critical' : 'text-warning'}`}>
                {tempLevel === 'critical' ? 'CRITICAL' : 'ELEVATED'}
              </span>
            )}
          </div>
          <div className="p-4" style={{ height: '250px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={sensorData}>
                <defs>
                  <linearGradient id="tempGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#ff6b00" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#ff6b00" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#2a2a32" />
                <XAxis
                  dataKey="ts"
                  type="number"
                  domain={xDomain}
                  allowDataOverflow
                  tickFormatter={formatTime}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                  tickCount={7}
                />
                <YAxis
                  domain={['dataMin - 5', 'dataMax + 5']}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <Tooltip
                  labelFormatter={formatTime}
                  contentStyle={{
                    background: '#111114',
                    border: '1px solid #2a2a32',
                    borderRadius: '8px',
                    fontFamily: 'JetBrains Mono',
                    fontSize: '12px',
                  }}
                  labelStyle={{ color: '#ff6b00' }}
                />
                <ReferenceLine y={THRESHOLDS.temperature.warning} stroke="#eab308" strokeDasharray="5 5" strokeWidth={1} />
                <Area
                  type="monotone"
                  dataKey="temperature"
                  stroke="#ff6b00"
                  strokeWidth={2}
                  fill="url(#tempGradient)"
                  dot={false}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Vibration Chart */}
        <div className="panel scanlines stagger scale-in stagger-2">
          <div className="panel-header">
            <Activity size={16} />
            Vibration Analysis
            {vibLevel !== 'normal' && (
              <span className={`ml-auto text-xs font-normal ${vibLevel === 'critical' ? 'text-critical' : 'text-warning'}`}>
                {vibLevel === 'critical' ? 'CRITICAL' : 'ELEVATED'}
              </span>
            )}
          </div>
          <div className="p-4" style={{ height: '250px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={sensorData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#2a2a32" />
                <XAxis
                  dataKey="ts"
                  type="number"
                  domain={xDomain}
                  allowDataOverflow
                  tickFormatter={formatTime}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                  tickCount={7}
                />
                <YAxis
                  domain={[0, 'auto']}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <Tooltip
                  labelFormatter={formatTime}
                  contentStyle={{
                    background: '#111114',
                    border: '1px solid #2a2a32',
                    borderRadius: '8px',
                    fontFamily: 'JetBrains Mono',
                    fontSize: '12px',
                  }}
                  labelStyle={{ color: '#ff6b00' }}
                />
                <ReferenceLine y={THRESHOLDS.vibration.critical} stroke="#ef4444" strokeDasharray="5 5" strokeWidth={1} label={{ value: 'Critical', position: 'right', fill: '#ef4444', fontSize: 10 }} />
                <ReferenceLine y={THRESHOLDS.vibration.warning} stroke="#eab308" strokeDasharray="5 5" strokeWidth={1} label={{ value: 'Warning', position: 'right', fill: '#eab308', fontSize: 10 }} />
                <Line
                  type="monotone"
                  dataKey="vibration"
                  stroke={vibLevel === 'critical' ? '#ef4444' : vibLevel === 'warning' ? '#eab308' : '#10b981'}
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Power Draw Chart */}
        <div className="panel scanlines stagger scale-in stagger-3">
          <div className="panel-header">
            <Zap size={16} />
            Power Consumption
          </div>
          <div className="p-4" style={{ height: '250px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={sensorData}>
                <defs>
                  <linearGradient id="powerGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#2a2a32" />
                <XAxis
                  dataKey="ts"
                  type="number"
                  domain={xDomain}
                  allowDataOverflow
                  tickFormatter={formatTime}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                  tickCount={7}
                />
                <YAxis
                  domain={['dataMin - 2', 'dataMax + 2']}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                  tickFormatter={(v: number) => `${v.toFixed(0)}`}
                />
                <Tooltip
                  labelFormatter={formatTime}
                  contentStyle={{
                    background: '#111114',
                    border: '1px solid #2a2a32',
                    borderRadius: '8px',
                    fontFamily: 'JetBrains Mono',
                    fontSize: '12px',
                  }}
                />
                <Area
                  type="monotone"
                  dataKey="power"
                  stroke="#3b82f6"
                  strokeWidth={2}
                  fill="url(#powerGradient)"
                  dot={false}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Spindle Speed Chart */}
        <div className="panel scanlines stagger scale-in stagger-4">
          <div className="panel-header">
            <RotateCcw size={16} />
            Spindle Speed
          </div>
          <div className="p-4" style={{ height: '250px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={sensorData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#2a2a32" />
                <XAxis
                  dataKey="ts"
                  type="number"
                  domain={xDomain}
                  allowDataOverflow
                  tickFormatter={formatTime}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                  tickCount={7}
                />
                <YAxis
                  domain={['dataMin - 200', 'dataMax + 200']}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                  tickFormatter={(v: number) => `${(v / 1000).toFixed(1)}K`}
                />
                <Tooltip
                  labelFormatter={formatTime}
                  contentStyle={{
                    background: '#111114',
                    border: '1px solid #2a2a32',
                    borderRadius: '8px',
                    fontFamily: 'JetBrains Mono',
                    fontSize: '12px',
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="spindle_speed"
                  stroke="#10b981"
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Pressure Chart */}
        <div className="panel scanlines stagger scale-in stagger-5">
          <div className="panel-header">
            <Activity size={16} />
            Pressure
          </div>
          <div className="p-4" style={{ height: '250px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={sensorData}>
                <defs>
                  <linearGradient id="pressureGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#a855f7" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#a855f7" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#2a2a32" />
                <XAxis
                  dataKey="ts"
                  type="number"
                  domain={xDomain}
                  allowDataOverflow
                  tickFormatter={formatTime}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                  tickCount={7}
                />
                <YAxis
                  domain={['dataMin - 1', 'dataMax + 1']}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <Tooltip
                  labelFormatter={formatTime}
                  contentStyle={{
                    background: '#111114',
                    border: '1px solid #2a2a32',
                    borderRadius: '8px',
                    fontFamily: 'JetBrains Mono',
                    fontSize: '12px',
                  }}
                />
                <Area
                  type="monotone"
                  dataKey="pressure"
                  stroke="#a855f7"
                  strokeWidth={2}
                  fill="url(#pressureGradient)"
                  dot={false}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Torque Chart */}
        <div className="panel scanlines stagger scale-in stagger-6">
          <div className="panel-header">
            <Activity size={16} />
            Torque
          </div>
          <div className="p-4" style={{ height: '250px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={sensorData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#2a2a32" />
                <XAxis
                  dataKey="ts"
                  type="number"
                  domain={xDomain}
                  allowDataOverflow
                  tickFormatter={formatTime}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                  tickCount={7}
                />
                <YAxis
                  domain={['dataMin - 5', 'dataMax + 5']}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <Tooltip
                  labelFormatter={formatTime}
                  contentStyle={{
                    background: '#111114',
                    border: '1px solid #2a2a32',
                    borderRadius: '8px',
                    fontFamily: 'JetBrains Mono',
                    fontSize: '12px',
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="torque"
                  stroke="#f59e0b"
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  );
}

function MetricCard({
  icon: Icon,
  label,
  value,
  unit,
  status,
  threshold,
}: {
  icon: React.ElementType;
  label: string;
  value: string;
  unit: string;
  status: 'normal' | 'warning' | 'critical';
  threshold: string;
}) {
  const statusColors = {
    normal: 'text-healthy',
    warning: 'text-warning',
    critical: 'text-critical',
  };

  return (
    <div className="panel p-4">
      <div className="flex items-center gap-2 mb-3">
        <Icon size={16} className="text-ember" />
        <span className="text-xs text-slate uppercase tracking-wider">{label}</span>
      </div>
      <div className="flex items-baseline gap-2">
        <span className={`font-display text-3xl font-bold ${statusColors[status]}`}>{value}</span>
        <span className="text-sm text-slate">{unit}</span>
      </div>
      <p className="text-xs text-ash mt-2">{threshold}</p>
    </div>
  );
}
