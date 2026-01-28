// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Global Overview with World Map
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect, useCallback } from 'react';
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
import { titanApi, MOCK_FACILITIES, FACILITY_COORDINATES } from '../api/titanApi';
import type { GeneratorEquipment, MLPrediction } from '../api/titanApi';

// Natural Earth 110m TopoJSON — lightweight world boundaries
const GEO_URL = 'https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json';

// Static facility metadata keyed by facility ID
const FACILITY_META = new Map(MOCK_FACILITIES.map((f) => [f.facility_id, f]));

interface FacilityStats {
  equipmentCount: number;
  criticalCount: number;
  warningCount: number;
  healthyCount: number;
  avgHealth: number;
}

function aggregateFacilities(
  equipment: GeneratorEquipment[],
  predictions: MLPrediction[],
): { facilities: Facility[]; stats: Map<string, FacilityStats>; totalHealth: number } {
  // Build prediction map
  const predMap = new Map<string, MLPrediction>();
  for (const p of predictions) {
    predMap.set(p.equipmentId, p);
  }

  // Group equipment by facility
  const facilityGroups = new Map<string, GeneratorEquipment[]>();
  for (const eq of equipment) {
    const group = facilityGroups.get(eq.facilityId) || [];
    group.push(eq);
    facilityGroups.set(eq.facilityId, group);
  }

  const statsMap = new Map<string, FacilityStats>();
  const facilities: Facility[] = [];
  let totalHealthSum = 0;
  let totalCount = 0;

  for (const [facilityId, eqList] of facilityGroups) {
    const meta = FACILITY_META.get(facilityId);
    if (!meta) continue;

    let criticalCount = 0;
    let warningCount = 0;
    let healthyCount = 0;
    let healthSum = 0;

    for (const eq of eqList) {
      const pred = predMap.get(eq.equipmentId);
      const fp = pred ? pred.failureProbability : 0;
      const health = Math.round((1 - fp) * 100);
      healthSum += health;

      const risk = pred?.riskLevel;
      if (risk === 'CRITICAL' || risk === 'HIGH') criticalCount++;
      else if (risk === 'MEDIUM') warningCount++;
      else healthyCount++;
    }

    const avgHealth = eqList.length > 0 ? Math.round(healthSum / eqList.length) : 100;
    totalHealthSum += healthSum;
    totalCount += eqList.length;

    // Derive facility status from worst equipment
    let status: 'online' | 'warning' | 'critical' | 'offline' = 'online';
    if (criticalCount > 0) status = 'critical';
    else if (warningCount > 0) status = 'warning';

    statsMap.set(facilityId, {
      equipmentCount: eqList.length,
      criticalCount,
      warningCount,
      healthyCount,
      avgHealth,
    });

    facilities.push({
      ...meta,
      equipment_count: eqList.length,
      status,
      coordinates: FACILITY_COORDINATES[facilityId] || meta.coordinates,
    });
  }

  // Include facilities with no equipment (from static metadata)
  for (const meta of MOCK_FACILITIES) {
    if (!facilityGroups.has(meta.facility_id)) {
      facilities.push({ ...meta, equipment_count: 0, status: 'online' });
      statsMap.set(meta.facility_id, {
        equipmentCount: 0, criticalCount: 0, warningCount: 0, healthyCount: 0, avgHealth: 100,
      });
    }
  }

  const totalHealth = totalCount > 0 ? Math.round(totalHealthSum / totalCount) : 100;

  return { facilities, stats: statsMap, totalHealth };
}

interface GlobalOverviewProps {
  onFacilitySelect: (facility: Facility) => void;
}

export function GlobalOverview({ onFacilitySelect }: GlobalOverviewProps) {
  const [selectedRegion, setSelectedRegion] = useState<string | null>(null);
  const [hoveredFacility, setHoveredFacility] = useState<string | null>(null);
  const [facilities, setFacilities] = useState<Facility[]>(MOCK_FACILITIES);
  const [facilityStats, setFacilityStats] = useState<Map<string, FacilityStats>>(new Map());
  const [totalHealth, setTotalHealth] = useState(100);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    try {
      const [eqList, predData] = await Promise.all([
        titanApi.getEquipmentList(),
        titanApi.getMlPredictions(),
      ]);
      const result = aggregateFacilities(eqList, predData.predictions || []);
      setFacilities(result.facilities);
      setFacilityStats(result.stats);
      setTotalHealth(result.totalHealth);
    } catch (err) {
      console.error('Failed to fetch global overview data:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, [fetchData]);

  // Calculate summary stats
  const totalEquipment = facilities.reduce((sum, f) => sum + f.equipment_count, 0);
  const criticalFacilities = facilities.filter(f => f.status === 'critical').length;
  const warningFacilities = facilities.filter(f => f.status === 'warning').length;

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
          <p className="text-zinc-400 mt-1">
            {loading ? 'Loading...' : `Real-time manufacturing intelligence across ${facilities.length} facilities`}
          </p>
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
            value={facilities.filter(f => f.equipment_count > 0).length}
            subValue={`${facilities.length} locations worldwide`}
            color="ember"
          />
        </div>
        <div className="stagger scale-in stagger-2">
          <SummaryCard
            icon={Wrench}
            label="Total Equipment"
            value={totalEquipment}
            subValue="Monitored in real-time"
            color="info"
          />
        </div>
        <div className="stagger scale-in stagger-3">
          <SummaryCard
            icon={AlertTriangle}
            label="Critical Alerts"
            value={criticalFacilities}
            subValue={warningFacilities > 0 ? `+${warningFacilities} warnings` : 'No warnings'}
            color="critical"
          />
        </div>
        <div className="stagger scale-in stagger-4">
          <SummaryCard
            icon={TrendingUp}
            label="Avg Health"
            value={`${totalHealth}%`}
            subValue="Across all equipment"
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
              const stats = facilityStats.get(facility.facility_id);

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
                          y={-28}
                          width={140}
                          height={56}
                          rx={4}
                          fill="rgba(17, 17, 20, 0.95)"
                          stroke={statusColor}
                          strokeWidth="1"
                        />
                        <text x={18} y={-12} fill="#fff" fontSize="11" fontFamily="Orbitron">
                          {facility.facility_id}
                        </text>
                        <text x={18} y={2} fill="#71717a" fontSize="9" fontFamily="Inter">
                          {facility.city}, {facility.country}
                        </text>
                        <text x={18} y={14} fill={statusColor} fontSize="9" fontFamily="JetBrains Mono">
                          {facility.equipment_count} machines
                          {stats && stats.criticalCount > 0 ? ` · ${stats.criticalCount} critical` : ''}
                        </text>
                        <text x={18} y={24} fill="#a1a1aa" fontSize="8" fontFamily="JetBrains Mono">
                          Health: {stats?.avgHealth ?? 100}%
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
                <span className="text-xs text-slate">
                  {regionFacilities.length} facilities ·{' '}
                  {regionFacilities.reduce((s, f) => s + f.equipment_count, 0)} eq
                </span>
              </div>
              <div className="space-y-2">
                {regionFacilities.map((f) => {
                  const stats = facilityStats.get(f.facility_id);
                  return (
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
                        <span className="text-zinc-300 hover:text-white transition-colors">
                          {f.facility_id}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-ash">{stats?.equipmentCount ?? f.equipment_count}</span>
                        <ChevronRight size={14} className="text-iron" />
                      </div>
                    </div>
                  );
                })}
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
