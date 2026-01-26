// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Real-time Sensor Monitor
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect } from 'react';
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
} from 'recharts';
import { Activity, Thermometer, Zap, RotateCcw, AlertTriangle } from 'lucide-react';

interface SensorData {
  time: string;
  temperature: number;
  vibration: number;
  power: number;
  spindle_speed: number;
}

// Generate mock sensor data with realistic patterns
function generateSensorData(count: number, degraded: boolean = false): SensorData[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const time = new Date(now - (count - i) * 5000).toLocaleTimeString('en-US', {
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });

    // Base values with some noise
    let temperature = 68 + Math.sin(i * 0.1) * 3 + Math.random() * 2;
    let vibration = 2.2 + Math.sin(i * 0.15) * 0.3 + Math.random() * 0.2;
    let power = 42 + Math.sin(i * 0.08) * 4 + Math.random() * 2;
    let spindle_speed = 12000 + Math.sin(i * 0.05) * 500 + Math.random() * 100;

    // Add degradation pattern for PHX-CNC-007
    if (degraded) {
      const degradationFactor = Math.min(1, i / count);
      temperature += degradationFactor * 8;
      vibration += degradationFactor * 1.8; // Trending towards 4.2
      power += degradationFactor * 5;
    }

    return { time, temperature, vibration, power, spindle_speed };
  });
}

const EQUIPMENT_LIST = [
  { id: 'PHX-CNC-007', name: 'CNC Mill #7', facility: 'Phoenix', status: 'critical', degraded: true },
  { id: 'PHX-CNC-003', name: 'CNC Mill #3', facility: 'Phoenix', status: 'healthy', degraded: false },
  { id: 'MUC-LTH-012', name: 'Lathe #12', facility: 'Munich', status: 'healthy', degraded: false },
  { id: 'SHA-CNC-001', name: 'CNC Mill #1', facility: 'Shanghai', status: 'warning', degraded: false },
  { id: 'DET-ASM-005', name: 'Assembly #5', facility: 'Detroit', status: 'healthy', degraded: false },
];

export function SensorMonitor() {
  const [selectedEquipment, setSelectedEquipment] = useState(EQUIPMENT_LIST[0]);
  const [sensorData, setSensorData] = useState<SensorData[]>([]);
  const [isLive, setIsLive] = useState(true);

  // Initialize and update sensor data
  useEffect(() => {
    setSensorData(generateSensorData(30, selectedEquipment.degraded));

    if (isLive) {
      const interval = setInterval(() => {
        setSensorData((prev) => {
          const newPoint = generateSensorData(1, selectedEquipment.degraded)[0];
          return [...prev.slice(1), newPoint];
        });
      }, 2000);
      return () => clearInterval(interval);
    }
  }, [selectedEquipment, isLive]);

  const latestData = sensorData[sensorData.length - 1] || {
    temperature: 0,
    vibration: 0,
    power: 0,
    spindle_speed: 0,
  };

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

      {/* Equipment Selector */}
      <div className="flex gap-2 overflow-x-auto pb-2">
        {EQUIPMENT_LIST.map((eq) => (
          <button
            key={eq.id}
            onClick={() => setSelectedEquipment(eq)}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm whitespace-nowrap transition-all ${
              selectedEquipment.id === eq.id
                ? 'bg-ember text-white'
                : 'bg-steel text-slate hover:bg-iron hover:text-white'
            }`}
          >
            <div
              className={`status-dot ${
                eq.status === 'critical'
                  ? 'status-dot-critical'
                  : eq.status === 'warning'
                  ? 'status-dot-warning'
                  : 'status-dot-healthy'
              }`}
            />
            {eq.id}
          </button>
        ))}
      </div>

      {/* Alert Banner for Critical Equipment */}
      {selectedEquipment.status === 'critical' && (
        <div className="bg-critical/10 border border-critical/30 rounded-lg p-4 flex items-center gap-4">
          <AlertTriangle className="text-critical" size={24} />
          <div>
            <p className="font-display font-semibold text-critical">ANOMALY DETECTED</p>
            <p className="text-sm text-slate">
              {selectedEquipment.id} showing bearing degradation pattern. Vibration trending towards
              failure threshold.
            </p>
          </div>
          <button className="btn-primary ml-auto">View Analysis</button>
        </div>
      )}

      {/* Current Values */}
      <div className="grid grid-cols-4 gap-4">
        <MetricCard
          icon={Thermometer}
          label="Temperature"
          value={latestData.temperature.toFixed(1)}
          unit="°C"
          status={latestData.temperature > 75 ? 'warning' : 'normal'}
          threshold="Max: 85°C"
        />
        <MetricCard
          icon={Activity}
          label="Vibration"
          value={latestData.vibration.toFixed(2)}
          unit="mm/s"
          status={latestData.vibration > 3.5 ? 'critical' : latestData.vibration > 3 ? 'warning' : 'normal'}
          threshold="Threshold: 3.5 mm/s"
        />
        <MetricCard
          icon={Zap}
          label="Power Draw"
          value={latestData.power.toFixed(1)}
          unit="kW"
          status="normal"
          threshold="Rated: 55 kW"
        />
        <MetricCard
          icon={RotateCcw}
          label="Spindle Speed"
          value={(latestData.spindle_speed / 1000).toFixed(1)}
          unit="K RPM"
          status="normal"
          threshold="Max: 15K RPM"
        />
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-2 gap-4">
        {/* Temperature & Vibration Chart */}
        <div className="panel scanlines stagger scale-in stagger-1">
          <div className="panel-header">
            <Thermometer size={16} />
            Temperature Trend
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
                  dataKey="time"
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <YAxis
                  domain={['dataMin - 5', 'dataMax + 5']}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <Tooltip
                  contentStyle={{
                    background: '#111114',
                    border: '1px solid #2a2a32',
                    borderRadius: '8px',
                    fontFamily: 'JetBrains Mono',
                    fontSize: '12px',
                  }}
                  labelStyle={{ color: '#ff6b00' }}
                />
                <Area
                  type="monotone"
                  dataKey="temperature"
                  stroke="#ff6b00"
                  strokeWidth={2}
                  fill="url(#tempGradient)"
                  dot={false}
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
            {selectedEquipment.degraded && (
              <span className="ml-auto text-xs text-critical font-normal">Trending Up</span>
            )}
          </div>
          <div className="p-4" style={{ height: '250px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={sensorData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#2a2a32" />
                <XAxis
                  dataKey="time"
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <YAxis
                  domain={[0, 5]}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <Tooltip
                  contentStyle={{
                    background: '#111114',
                    border: '1px solid #2a2a32',
                    borderRadius: '8px',
                    fontFamily: 'JetBrains Mono',
                    fontSize: '12px',
                  }}
                  labelStyle={{ color: '#ff6b00' }}
                />
                {/* Threshold line */}
                <Line
                  type="monotone"
                  dataKey={() => 3.5}
                  stroke="#ef4444"
                  strokeDasharray="5 5"
                  strokeWidth={1}
                  dot={false}
                  name="Threshold"
                />
                <Line
                  type="monotone"
                  dataKey="vibration"
                  stroke={selectedEquipment.degraded ? '#ef4444' : '#10b981'}
                  strokeWidth={2}
                  dot={false}
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
                  dataKey="time"
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <YAxis
                  domain={[30, 60]}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <Tooltip
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
                  dataKey="time"
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                />
                <YAxis
                  domain={[10000, 14000]}
                  tick={{ fill: '#71717a', fontSize: 10 }}
                  tickLine={{ stroke: '#2a2a32' }}
                  axisLine={{ stroke: '#2a2a32' }}
                  tickFormatter={(v) => `${(v / 1000).toFixed(0)}K`}
                />
                <Tooltip
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
