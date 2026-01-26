// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Global Overview with World Map
// ═══════════════════════════════════════════════════════════════════════════

import { useState } from 'react';
import {
  Globe,
  AlertTriangle,
  Wrench,
  TrendingUp,
  Activity,
  MapPin,
  ChevronRight,
} from 'lucide-react';
import type { Facility } from '../types';
import { MOCK_FACILITIES } from '../api/titanApi';

interface GlobalOverviewProps {
  onFacilitySelect: (facility: Facility) => void;
}

export function GlobalOverview({ onFacilitySelect }: GlobalOverviewProps) {
  const [selectedRegion, setSelectedRegion] = useState<string | null>(null);
  const [hoveredFacility, setHoveredFacility] = useState<string | null>(null);

  const facilities = MOCK_FACILITIES;

  // Calculate summary stats
  const totalEquipment = facilities.reduce((sum, f) => sum + f.equipment_count, 0);
  const criticalCount = facilities.filter(f => f.status === 'critical').length;
  const warningCount = facilities.filter(f => f.status === 'warning').length;

  // Group facilities by region
  const regions = {
    NA: facilities.filter(f => f.region === 'NA'),
    EU: facilities.filter(f => f.region === 'EU'),
    APAC: facilities.filter(f => f.region === 'APAC'),
    LATAM: facilities.filter(f => f.region === 'LATAM'),
  };

  // Convert lat/lng to SVG coordinates (simple mercator-ish projection)
  const coordToSvg = (lat: number, lng: number) => {
    const x = ((lng + 180) / 360) * 800 + 50;
    const y = ((90 - lat) / 180) * 400 + 20;
    return { x, y };
  };

  return (
    <div className="space-y-6 fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <Globe className="text-ember" />
            Global Operations Overview
          </h2>
          <p className="text-slate mt-1">Real-time manufacturing intelligence across 12 facilities</p>
        </div>
        <div className="flex gap-2">
          {['NA', 'EU', 'APAC', 'LATAM'].map(region => (
            <button
              key={region}
              onClick={() => setSelectedRegion(selectedRegion === region ? null : region)}
              className={`px-3 py-1.5 rounded-lg text-xs font-display font-semibold transition-all ${
                selectedRegion === region
                  ? 'bg-ember text-white'
                  : 'bg-steel text-slate hover:bg-iron hover:text-white'
              }`}
            >
              {region}
            </button>
          ))}
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-4 gap-4">
        <div className="stagger scale-in stagger-1">
          <SummaryCard
            icon={MapPin}
            label="Active Facilities"
            value={facilities.length}
            subValue="12 locations worldwide"
            color="ember"
          />
        </div>
        <div className="stagger scale-in stagger-2">
          <SummaryCard
            icon={Wrench}
            label="Total Equipment"
            value={totalEquipment}
            subValue="CNC, Lathes, Mills, Assembly"
            color="info"
          />
        </div>
        <div className="stagger scale-in stagger-3">
          <SummaryCard
            icon={AlertTriangle}
            label="Critical Alerts"
            value={criticalCount}
            subValue={warningCount > 0 ? `+${warningCount} warnings` : 'No warnings'}
            color="critical"
          />
        </div>
        <div className="stagger scale-in stagger-4">
          <SummaryCard
            icon={TrendingUp}
            label="Avg Utilization"
            value="87%"
            subValue="+2.3% from last week"
            color="healthy"
          />
        </div>
      </div>

      {/* World Map */}
      <div className="panel scanlines crt-flicker stagger scale-in stagger-5">
        <div className="panel-header">
          <Activity size={16} />
          Facility Network
        </div>
        <div className="relative p-6" style={{ height: '450px' }}>
          {/* SVG World Map */}
          <svg
            viewBox="0 0 900 460"
            className="w-full h-full"
            style={{ filter: 'drop-shadow(0 0 20px rgba(255, 107, 0, 0.1))' }}
          >
            {/* Grid lines */}
            <defs>
              <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
                <path
                  d="M 40 0 L 0 0 0 40"
                  fill="none"
                  stroke="rgba(255, 107, 0, 0.05)"
                  strokeWidth="0.5"
                />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#grid)" />

            {/* Simplified continent outlines */}
            {/* North America */}
            <path
              d="M 80,80 Q 120,60 180,70 Q 220,80 240,120 Q 250,160 230,200 Q 200,220 160,210 Q 120,200 100,160 Q 80,120 80,80"
              fill="rgba(255, 107, 0, 0.08)"
              stroke="rgba(255, 107, 0, 0.2)"
              strokeWidth="1"
            />
            {/* South America */}
            <path
              d="M 180,240 Q 200,230 220,260 Q 230,300 220,350 Q 200,380 180,370 Q 160,350 165,300 Q 170,260 180,240"
              fill="rgba(255, 107, 0, 0.08)"
              stroke="rgba(255, 107, 0, 0.2)"
              strokeWidth="1"
            />
            {/* Europe */}
            <path
              d="M 420,80 Q 460,70 500,80 Q 520,100 510,130 Q 490,150 450,140 Q 420,130 410,100 Q 415,85 420,80"
              fill="rgba(255, 107, 0, 0.08)"
              stroke="rgba(255, 107, 0, 0.2)"
              strokeWidth="1"
            />
            {/* Africa */}
            <path
              d="M 450,160 Q 490,150 520,180 Q 530,240 510,300 Q 480,340 450,330 Q 420,300 430,240 Q 440,180 450,160"
              fill="rgba(255, 107, 0, 0.08)"
              stroke="rgba(255, 107, 0, 0.2)"
              strokeWidth="1"
            />
            {/* Asia */}
            <path
              d="M 540,60 Q 620,50 720,70 Q 780,100 800,150 Q 790,200 740,220 Q 680,230 620,210 Q 560,180 530,130 Q 520,90 540,60"
              fill="rgba(255, 107, 0, 0.08)"
              stroke="rgba(255, 107, 0, 0.2)"
              strokeWidth="1"
            />
            {/* Australia */}
            <path
              d="M 720,300 Q 760,290 800,310 Q 820,340 800,370 Q 760,390 720,370 Q 700,340 720,300"
              fill="rgba(255, 107, 0, 0.08)"
              stroke="rgba(255, 107, 0, 0.2)"
              strokeWidth="1"
            />

            {/* Connection lines between facilities */}
            {facilities.map((f1, i) =>
              facilities.slice(i + 1).map((f2) => {
                const p1 = coordToSvg(f1.coordinates.lat, f1.coordinates.lng);
                const p2 = coordToSvg(f2.coordinates.lat, f2.coordinates.lng);
                const shouldShow = !selectedRegion || f1.region === selectedRegion || f2.region === selectedRegion;
                return (
                  <line
                    key={`${f1.facility_id}-${f2.facility_id}`}
                    x1={p1.x}
                    y1={p1.y}
                    x2={p2.x}
                    y2={p2.y}
                    stroke="rgba(255, 107, 0, 0.1)"
                    strokeWidth="0.5"
                    opacity={shouldShow ? 0.3 : 0.05}
                    className="transition-opacity duration-300"
                  />
                );
              })
            )}

            {/* Facility markers */}
            {facilities.map((facility) => {
              const { x, y } = coordToSvg(facility.coordinates.lat, facility.coordinates.lng);
              const isFiltered = selectedRegion && facility.region !== selectedRegion;
              const isHovered = hoveredFacility === facility.facility_id;
              const statusColor =
                facility.status === 'critical'
                  ? '#ef4444'
                  : facility.status === 'warning'
                  ? '#f59e0b'
                  : '#ff6b00';

              return (
                <g
                  key={facility.facility_id}
                  className="cursor-pointer transition-all duration-200"
                  style={{ opacity: isFiltered ? 0.2 : 1 }}
                  onMouseEnter={() => setHoveredFacility(facility.facility_id)}
                  onMouseLeave={() => setHoveredFacility(null)}
                  onClick={() => onFacilitySelect(facility)}
                >
                  {/* Pulse ring for critical/warning */}
                  {(facility.status === 'critical' || facility.status === 'warning') && (
                    <circle
                      cx={x}
                      cy={y}
                      r={isHovered ? 20 : 15}
                      fill="none"
                      stroke={statusColor}
                      strokeWidth="1"
                      opacity={0.3}
                    >
                      <animate
                        attributeName="r"
                        from="8"
                        to="25"
                        dur={facility.status === 'critical' ? '1s' : '2s'}
                        repeatCount="indefinite"
                      />
                      <animate
                        attributeName="opacity"
                        from="0.5"
                        to="0"
                        dur={facility.status === 'critical' ? '1s' : '2s'}
                        repeatCount="indefinite"
                      />
                    </circle>
                  )}

                  {/* Glow effect */}
                  <circle cx={x} cy={y} r={isHovered ? 16 : 12} fill={statusColor} opacity={0.2} />

                  {/* Main marker */}
                  <circle
                    cx={x}
                    cy={y}
                    r={isHovered ? 8 : 6}
                    fill={statusColor}
                    stroke="rgba(0,0,0,0.5)"
                    strokeWidth="1"
                    style={{
                      filter: `drop-shadow(0 0 ${isHovered ? 12 : 6}px ${statusColor})`,
                    }}
                  />

                  {/* Label on hover */}
                  {isHovered && (
                    <g>
                      <rect
                        x={x + 12}
                        y={y - 24}
                        width={120}
                        height={44}
                        rx={4}
                        fill="rgba(17, 17, 20, 0.95)"
                        stroke={statusColor}
                        strokeWidth="1"
                      />
                      <text x={x + 20} y={y - 8} fill="#fff" fontSize="11" fontFamily="Orbitron">
                        {facility.facility_id}
                      </text>
                      <text x={x + 20} y={y + 6} fill="#71717a" fontSize="9" fontFamily="Inter">
                        {facility.city}, {facility.country}
                      </text>
                      <text x={x + 20} y={y + 18} fill={statusColor} fontSize="9" fontFamily="JetBrains Mono">
                        {facility.equipment_count} machines
                      </text>
                    </g>
                  )}
                </g>
              );
            })}
          </svg>

          {/* Legend */}
          <div className="absolute bottom-6 left-6 flex gap-4 text-xs">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-ember" />
              <span className="text-slate">Online</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-warning" />
              <span className="text-slate">Warning</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-critical" />
              <span className="text-slate">Critical</span>
            </div>
          </div>
        </div>
      </div>

      {/* Regional Breakdown */}
      <div className="grid grid-cols-4 gap-4">
        {Object.entries(regions).map(([region, regionFacilities], index) => (
          <div
            key={region}
            className={`panel card-interactive cursor-pointer stagger slide-in-left stagger-${index + 6} ${
              selectedRegion === region ? 'border-ember' : ''
            }`}
            onClick={() => setSelectedRegion(selectedRegion === region ? null : region)}
          >
            <div className="p-4">
              <div className="flex items-center justify-between mb-3">
                <span className="font-display font-bold text-ember">{region}</span>
                <span className="text-xs text-slate">{regionFacilities.length} facilities</span>
              </div>
              <div className="space-y-2">
                {regionFacilities.map((f) => (
                  <div
                    key={f.facility_id}
                    className="flex items-center justify-between text-sm"
                    onClick={(e) => {
                      e.stopPropagation();
                      onFacilitySelect(f);
                    }}
                  >
                    <div className="flex items-center gap-2">
                      <div
                        className={`status-dot ${
                          f.status === 'critical'
                            ? 'status-dot-critical'
                            : f.status === 'warning'
                            ? 'status-dot-warning'
                            : 'status-dot-healthy'
                        }`}
                      />
                      <span className="text-slate hover:text-white transition-colors">
                        {f.facility_id}
                      </span>
                    </div>
                    <ChevronRight size={14} className="text-iron" />
                  </div>
                ))}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function SummaryCard({
  icon: Icon,
  label,
  value,
  subValue,
  color,
}: {
  icon: React.ElementType;
  label: string;
  value: string | number;
  subValue: string;
  color: 'ember' | 'info' | 'critical' | 'healthy';
}) {
  const colorClasses = {
    ember: 'text-ember bg-ember/10 border-ember/20',
    info: 'text-info bg-info/10 border-info/20',
    critical: 'text-critical bg-critical/10 border-critical/20',
    healthy: 'text-healthy bg-healthy/10 border-healthy/20',
  };

  return (
    <div className="panel card-interactive p-4">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs text-slate uppercase tracking-wider mb-1">{label}</p>
          <p className="font-display text-3xl font-bold text-white glow-text">{value}</p>
          <p className="text-xs text-slate mt-1">{subValue}</p>
        </div>
        <div className={`p-2 rounded-lg border ${colorClasses[color]}`}>
          <Icon size={20} />
        </div>
      </div>
    </div>
  );
}
