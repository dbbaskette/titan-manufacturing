// ═══════════════════════════════════════════════════════════════════════════
// Background Sensor Data Service
// Single SSE connection streams ALL equipment; data cached per equipment.
// Charts have data ready when user navigates to Sensor Monitor.
// ═══════════════════════════════════════════════════════════════════════════

import { createContext, useContext, useRef, useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';

const SENSOR_API_URL = 'http://localhost:8081/api/sensors';
const MAX_POINTS = 60;

export interface SensorData {
  ts: number;
  temperature: number;
  vibration: number;
  power: number;
  spindle_speed: number;
  pressure: number;
  torque: number;
}

interface SensorReading {
  equipmentId: string;
  sensorType: string;
  value: number;
  unit: string;
  timestamp: string;
  qualityFlag: string;
}

interface SensorDataContextValue {
  getData: (equipmentId: string) => SensorData[];
  subscribe: (equipmentId: string) => void;
  unsubscribe: (equipmentId: string) => void;
  onData: (equipmentId: string, cb: (data: SensorData[]) => void) => () => void;
  latestValues: Map<string, { vibration: number; temperature: number }>;
}

const SensorDataContext = createContext<SensorDataContextValue | null>(null);

export function useSensorData() {
  const ctx = useContext(SensorDataContext);
  if (!ctx) throw new Error('useSensorData must be used within SensorDataProvider');
  return ctx;
}

export function SensorDataProvider({ children }: { children: ReactNode }) {
  const dataCache = useRef(new Map<string, SensorData[]>());
  const listeners = useRef(new Map<string, Set<(data: SensorData[]) => void>>());
  const [latestValues] = useState(() => new Map<string, { vibration: number; temperature: number }>());

  // Per-equipment latest readings accumulator (populated by single SSE stream)
  const latestReadings = useRef(new Map<string, Map<string, SensorReading>>());

  const notify = useCallback((equipmentId: string) => {
    const cbs = listeners.current.get(equipmentId);
    const data = dataCache.current.get(equipmentId) || [];
    if (cbs) cbs.forEach(cb => cb(data));
  }, []);

  // Single SSE connection for ALL equipment — starts on mount
  useEffect(() => {
    const url = `${SENSOR_API_URL}/stream`;
    const eventSource = new EventSource(url);

    eventSource.addEventListener('sensor-reading', (event) => {
      try {
        const reading: SensorReading = JSON.parse(event.data);
        const eqId = reading.equipmentId;
        if (!latestReadings.current.has(eqId)) {
          latestReadings.current.set(eqId, new Map());
        }
        latestReadings.current.get(eqId)!.set(reading.sensorType, reading);
      } catch { /* ignore */ }
    });

    // Every 2s, snapshot all equipment with new data into cache
    const interval = setInterval(() => {
      for (const [eqId, readings] of latestReadings.current.entries()) {
        const temp = readings.get('temperature');
        const vib = readings.get('vibration');
        const pwr = readings.get('power') || readings.get('power_draw');
        const rpm = readings.get('spindle_speed');
        const pres = readings.get('pressure');
        const torq = readings.get('torque');

        if (!temp && !vib && !pwr && !rpm && !pres && !torq) continue;

        const point: SensorData = {
          ts: Date.now(),
          temperature: temp?.value ?? 50,
          vibration: vib?.value ?? 2.0,
          power: pwr?.value ?? 15,
          spindle_speed: rpm?.value ?? 8500,
          pressure: pres?.value ?? 6.0,
          torque: torq?.value ?? 45,
        };

        latestValues.set(eqId, {
          vibration: point.vibration,
          temperature: point.temperature,
        });

        const existing = dataCache.current.get(eqId) || [];
        const updated = [...existing, point];
        const trimmed = updated.length > MAX_POINTS ? updated.slice(-MAX_POINTS) : updated;
        dataCache.current.set(eqId, trimmed);
        notify(eqId);
      }
    }, 2000);

    return () => {
      clearInterval(interval);
      eventSource.close();
    };
  }, [notify, latestValues]);

  // subscribe/unsubscribe are now no-ops since we stream everything
  const subscribe = useCallback((_equipmentId: string) => {}, []);
  const unsubscribe = useCallback((_equipmentId: string) => {}, []);

  const getData = useCallback((equipmentId: string) => {
    return dataCache.current.get(equipmentId) || [];
  }, []);

  const onData = useCallback((equipmentId: string, cb: (data: SensorData[]) => void) => {
    if (!listeners.current.has(equipmentId)) {
      listeners.current.set(equipmentId, new Set());
    }
    listeners.current.get(equipmentId)!.add(cb);
    return () => {
      listeners.current.get(equipmentId)?.delete(cb);
    };
  }, []);

  return (
    <SensorDataContext.Provider value={{ getData, subscribe, unsubscribe, onData, latestValues }}>
      {children}
    </SensorDataContext.Provider>
  );
}
