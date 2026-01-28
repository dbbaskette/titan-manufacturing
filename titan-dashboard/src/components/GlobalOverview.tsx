// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Global Overview with World Map
// ═══════════════════════════════════════════════════════════════════════════

import { useState } from 'react';
import {
  ComposableMap,
  Geographies,
  Geography,
  Marker,
  Line,
} from 'react-simple-maps';
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

// Natural Earth 110m TopoJSON — lightweight world boundaries
const GEO_URL = 'https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json';

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

  const getStatusColor = (status: string) =>
    status === 'critical' ? '#ef4444' : status === 'warning' ? '#f59e0b' : '#ff6b00';

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
        <div className="relative" style={{ height: '450px' }}>
          <ComposableMap
            projection="geoMercator"
            projectionConfig={{
              scale: 130,
              center: [20, 20],
            }}
            style={{ width: '100%', height: '100%' }}
          >
            {/* Country boundaries */}
            <Geographies geography={GEO_URL}>
              {({ geographies }) =>
                geographies.map((geo) => (
                  <Geography
                    key={geo.rsmKey}
                    geography={geo}
                    fill="rgba(255, 107, 0, 0.08)"
                    stroke="rgba(255, 107, 0, 0.2)"
                    strokeWidth={0.5}
                    style={{
                      default: { outline: 'none' },
                      hover: { outline: 'none', fill: 'rgba(255, 107, 0, 0.12)' },
                      pressed: { outline: 'none' },
                    }}
                  />
                ))
              }
            </Geographies>

            {/* Connection lines between facilities in same/adjacent regions */}
            {facilities.map((f1, i) =>
              facilities.slice(i + 1).map((f2) => {
                const shouldShow = !selectedRegion || f1.region === selectedRegion || f2.region === selectedRegion;
                return (
                  <Line
                    key={`${f1.facility_id}-${f2.facility_id}`}
                    from={[f1.coordinates.lng, f1.coordinates.lat]}
                    to={[f2.coordinates.lng, f2.coordinates.lat]}
                    stroke="rgba(255, 107, 0, 0.08)"
                    strokeWidth={0.5}
                    strokeLinecap="round"
                    style={{ opacity: shouldShow ? 0.4 : 0.05 }}
                  />
                );
              })
            )}

            {/* Facility markers */}
            {facilities.map((facility) => {
              const isFiltered = selectedRegion && facility.region !== selectedRegion;
              const isHovered = hoveredFacility === facility.facility_id;
              const statusColor = getStatusColor(facility.status);

              return (
                <Marker
                  key={facility.facility_id}
                  coordinates={[facility.coordinates.lng, facility.coordinates.lat]}
                >
                  <g
                    className="cursor-pointer"
                    style={{ opacity: isFiltered ? 0.2 : 1, transition: 'opacity 0.3s' }}
                    onMouseEnter={() => setHoveredFacility(facility.facility_id)}
                    onMouseLeave={() => setHoveredFacility(null)}
                    onClick={() => onFacilitySelect(facility)}
                  >
                    {/* Pulse ring for critical/warning */}
                    {(facility.status === 'critical' || facility.status === 'warning') && (
                      <circle r={15} fill="none" stroke={statusColor} strokeWidth="1" opacity={0.3}>
                        <animate
                          attributeName="r"
                          from="6"
                          to="20"
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

                    {/* Glow */}
                    <circle r={isHovered ? 14 : 10} fill={statusColor} opacity={0.2} />

                    {/* Main dot */}
                    <circle
                      r={isHovered ? 7 : 5}
                      fill={statusColor}
                      stroke="rgba(0,0,0,0.5)"
                      strokeWidth="1"
                      style={{
                        filter: `drop-shadow(0 0 ${isHovered ? 10 : 4}px ${statusColor})`,
                        transition: 'r 0.2s',
                      }}
                    />

                    {/* Label on hover */}
                    {isHovered && (
                      <g>
                        <rect
                          x={10}
                          y={-22}
                          width={120}
                          height={44}
                          rx={4}
                          fill="rgba(17, 17, 20, 0.95)"
                          stroke={statusColor}
                          strokeWidth="1"
                        />
                        <text x={18} y={-6} fill="#fff" fontSize="11" fontFamily="Orbitron">
                          {facility.facility_id}
                        </text>
                        <text x={18} y={8} fill="#71717a" fontSize="9" fontFamily="Inter">
                          {facility.city}, {facility.country}
                        </text>
                        <text x={18} y={20} fill={statusColor} fontSize="9" fontFamily="JetBrains Mono">
                          {facility.equipment_count} machines
                        </text>
                      </g>
                    )}
                  </g>
                </Marker>
              );
            })}
          </ComposableMap>

          {/* Legend */}
          <div className="absolute bottom-4 left-6 flex gap-4 text-xs">
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
