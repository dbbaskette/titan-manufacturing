// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — API Client
// ═══════════════════════════════════════════════════════════════════════════

import type { ChatResponse, HealthAnalysis, FacilityStatus, Agent, Facility } from '../types';

const API_BASE = '/api';

// Facility coordinates for the global map
export const FACILITY_COORDINATES: Record<string, { lat: number; lng: number }> = {
  PHX: { lat: 33.4484, lng: -112.0740 },
  DET: { lat: 42.3314, lng: -83.0458 },
  ATL: { lat: 33.7490, lng: -84.3880 },
  DAL: { lat: 32.7767, lng: -96.7970 },
  MUC: { lat: 48.1351, lng: 11.5820 },
  LYN: { lat: 45.7640, lng: 4.8357 },
  MAN: { lat: 53.4808, lng: -2.2426 },
  SHA: { lat: 31.2304, lng: 121.4737 },
  TYO: { lat: 35.6762, lng: 139.6503 },
  SEO: { lat: 37.5665, lng: 126.9780 },
  SYD: { lat: -33.8688, lng: 151.2093 },
  MEX: { lat: 19.4326, lng: -99.1332 },
};

class TitanApi {
  private async fetch<T>(endpoint: string, options?: RequestInit): Promise<T> {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
    });

    if (!response.ok) {
      throw new Error(`API Error: ${response.status} ${response.statusText}`);
    }

    return response.json();
  }

  // Chat endpoint
  async chat(message: string): Promise<ChatResponse> {
    return this.fetch<ChatResponse>('/chat', {
      method: 'POST',
      body: JSON.stringify({ message }),
    });
  }

  // Equipment health
  async getEquipmentHealth(equipmentId: string): Promise<HealthAnalysis> {
    return this.fetch<HealthAnalysis>(`/equipment/${equipmentId}/health`);
  }

  // Facility status
  async getFacilityStatus(facilityId: string): Promise<FacilityStatus> {
    return this.fetch<FacilityStatus>(`/facilities/${facilityId}/status`);
  }

  // List all agents
  async getAgents(): Promise<{ agents: Agent[] }> {
    return this.fetch<{ agents: Agent[] }>('/agents');
  }

  // List all facilities
  async getFacilities(): Promise<{ facilities: Facility[] }> {
    return this.fetch<{ facilities: Facility[] }>('/facilities');
  }

  // Health check
  async healthCheck(): Promise<string> {
    const response = await fetch(`${API_BASE}/health`);
    return response.text();
  }
}

export const titanApi = new TitanApi();

// Mock data for demo/offline mode
export const MOCK_FACILITIES: Facility[] = [
  { facility_id: 'PHX', name: 'Phoenix Plant', city: 'Phoenix', country: 'USA', region: 'NA', equipment_count: 65, specialization: 'Aerospace precision machining', status: 'critical', coordinates: FACILITY_COORDINATES.PHX },
  { facility_id: 'DET', name: 'Detroit Plant', city: 'Detroit', country: 'USA', region: 'NA', equipment_count: 52, specialization: 'Automotive components', status: 'online', coordinates: FACILITY_COORDINATES.DET },
  { facility_id: 'ATL', name: 'Atlanta HQ', city: 'Atlanta', country: 'USA', region: 'NA', equipment_count: 48, specialization: 'Industrial equipment', status: 'online', coordinates: FACILITY_COORDINATES.ATL },
  { facility_id: 'DAL', name: 'Dallas Plant', city: 'Dallas', country: 'USA', region: 'NA', equipment_count: 45, specialization: 'Energy sector components', status: 'online', coordinates: FACILITY_COORDINATES.DAL },
  { facility_id: 'MUC', name: 'Munich Plant', city: 'Munich', country: 'Germany', region: 'EU', equipment_count: 58, specialization: 'Precision engineering', status: 'online', coordinates: FACILITY_COORDINATES.MUC },
  { facility_id: 'LYN', name: 'Lyon Plant', city: 'Lyon', country: 'France', region: 'EU', equipment_count: 45, specialization: 'Aerospace composites', status: 'online', coordinates: FACILITY_COORDINATES.LYN },
  { facility_id: 'MAN', name: 'Manchester Plant', city: 'Manchester', country: 'UK', region: 'EU', equipment_count: 40, specialization: 'Industrial bearings', status: 'warning', coordinates: FACILITY_COORDINATES.MAN },
  { facility_id: 'SHA', name: 'Shanghai Plant', city: 'Shanghai', country: 'China', region: 'APAC', equipment_count: 72, specialization: 'High-volume production', status: 'online', coordinates: FACILITY_COORDINATES.SHA },
  { facility_id: 'TYO', name: 'Tokyo Plant', city: 'Tokyo', country: 'Japan', region: 'APAC', equipment_count: 55, specialization: 'Precision instruments', status: 'online', coordinates: FACILITY_COORDINATES.TYO },
  { facility_id: 'SEO', name: 'Seoul Plant', city: 'Seoul', country: 'South Korea', region: 'APAC', equipment_count: 42, specialization: 'EV components', status: 'online', coordinates: FACILITY_COORDINATES.SEO },
  { facility_id: 'SYD', name: 'Sydney Plant', city: 'Sydney', country: 'Australia', region: 'APAC', equipment_count: 38, specialization: 'Mining equipment', status: 'online', coordinates: FACILITY_COORDINATES.SYD },
  { facility_id: 'MEX', name: 'Mexico City Plant', city: 'Mexico City', country: 'Mexico', region: 'LATAM', equipment_count: 40, specialization: 'Assembly operations', status: 'online', coordinates: FACILITY_COORDINATES.MEX },
];

export const MOCK_AGENTS: Agent[] = [
  { name: 'Sensor Agent', type: 'sensor', status: 'online', port: 8081, tools: ['list_equipment', 'get_sensor_readings', 'get_facility_status', 'detect_anomaly'] },
  { name: 'Maintenance Agent', type: 'maintenance', status: 'online', port: 8082, tools: ['predict_failure', 'estimate_rul', 'schedule_maintenance', 'get_maintenance_history'] },
  { name: 'Inventory Agent', type: 'inventory', status: 'online', port: 8083, tools: ['check_stock', 'search_products', 'find_alternatives', 'calculate_reorder'] },
  { name: 'Logistics Agent', type: 'logistics', status: 'online', port: 8084, tools: ['get_carriers', 'create_shipment', 'track_shipment', 'estimate_shipping'] },
  { name: 'Order Agent', type: 'order', status: 'online', port: 8085, tools: ['validate_order', 'check_contract_terms', 'initiate_fulfillment', 'get_order_status'] },
  { name: 'Communications Agent', type: 'communications', status: 'online', port: 8086, tools: ['send_notification', 'handle_inquiry', 'draft_customer_update'] },
  { name: 'Governance Agent', type: 'governance', status: 'online', port: 8087, tools: ['get_table_metadata', 'trace_data_lineage', 'check_data_quality', 'trace_material_batch'] },
];

export const DEMO_SCENARIOS = [
  {
    id: 'phoenix-incident',
    name: 'Phoenix Incident',
    description: 'Detect bearing failure in PHX-CNC-007 before $12M downtime',
    icon: 'AlertTriangle',
    prompt: 'Check the health status of PHX-CNC-007 and recommend maintenance actions',
    category: 'maintenance' as const,
  },
  {
    id: 'boeing-expedite',
    name: 'Boeing Expedite',
    description: 'Rush order for 500 turbine blade blanks with split shipment',
    icon: 'Plane',
    prompt: 'Boeing needs 500 turbine blade blanks ASAP - order TM-2024-45892. Validate and fulfill.',
    category: 'fulfillment' as const,
  },
  {
    id: 'faa-audit',
    name: 'FAA Audit',
    description: 'Trace titanium batch TI-2024-0892 for 787 landing gear compliance',
    icon: 'Shield',
    prompt: 'Trace titanium batch TI-2024-0892 used in Boeing 787 landing gear for FAA audit',
    category: 'compliance' as const,
  },
  {
    id: 'supply-crisis',
    name: 'Supply Chain Crisis',
    description: 'Find alternative suppliers when NipponBearing delays shipment',
    icon: 'Package',
    prompt: 'Our NipponBearing supplier has delayed shipment. Find alternative suppliers for SKU-BRG-7420',
    category: 'supply-chain' as const,
  },
];
