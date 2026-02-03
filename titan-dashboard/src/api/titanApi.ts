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

  // Generator equipment list
  async getEquipmentList(): Promise<GeneratorEquipment[]> {
    return this.fetch<GeneratorEquipment[]>('/generator/equipment');
  }

  // Health check
  async healthCheck(): Promise<string> {
    const response = await fetch(`${API_BASE}/health`);
    return response.text();
  }

  // ML Pipeline endpoints
  async getMlModel(): Promise<MLModelData> {
    return this.fetch<MLModelData>('/ml/model');
  }

  async getMlPredictions(): Promise<MLPredictionsData> {
    return this.fetch<MLPredictionsData>('/ml/predictions');
  }

  async getMlGemFireStatus(): Promise<MLGemFireStatus> {
    return this.fetch<MLGemFireStatus>('/ml/gemfire/status');
  }

  async getMlPmml(): Promise<MLPmmlData> {
    return this.fetch<MLPmmlData>('/ml/pmml');
  }

  async retrainModel(): Promise<MLRetrainResult> {
    return this.fetch<MLRetrainResult>('/ml/retrain', { method: 'POST' });
  }

  async deployModel(): Promise<MLDeployResult> {
    return this.fetch<MLDeployResult>('/ml/deploy', { method: 'POST' });
  }

  // Order endpoints
  async getOrders(): Promise<OrderSummary[]> {
    return this.fetch<OrderSummary[]>('/orders');
  }

  async getOrderCounts(): Promise<OrderCounts> {
    return this.fetch<OrderCounts>('/orders/counts');
  }

  async getOrderDetails(orderId: string): Promise<OrderDetails> {
    return this.fetch<OrderDetails>(`/orders/${orderId}`);
  }

  async getOrderEvents(orderId: string): Promise<OrderEvent[]> {
    return this.fetch<OrderEvent[]>(`/orders/${orderId}/events`);
  }

  async updateOrderStatus(orderId: string, status: string): Promise<{ success: boolean; status: string }> {
    return this.fetch(`/orders/${orderId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
  }

  // Recommendations endpoints
  async getRecommendations(): Promise<Recommendation[]> {
    return this.fetch<Recommendation[]>('/recommendations');
  }

  async approveRecommendation(recommendationId: string, approvedBy?: string): Promise<ApprovalResponse> {
    return this.fetch<ApprovalResponse>(`/recommendations/${recommendationId}/approve`, {
      method: 'POST',
      body: JSON.stringify({ approvedBy: approvedBy || 'dashboard-user' }),
    });
  }

  async dismissRecommendation(recommendationId: string, reason?: string): Promise<DismissResponse> {
    return this.fetch<DismissResponse>(`/recommendations/${recommendationId}/dismiss`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason || 'Dismissed by operator' }),
    });
  }

  async getResolvedRecommendations(limit?: number): Promise<Recommendation[]> {
    const query = limit ? `?limit=${limit}` : '';
    return this.fetch<Recommendation[]>(`/recommendations/resolved${query}`);
  }

  // Automated actions endpoints
  async getAutomatedActions(limit?: number): Promise<AutomatedAction[]> {
    const query = limit ? `?limit=${limit}` : '';
    return this.fetch<AutomatedAction[]>(`/automated-actions${query}`);
  }

  // Settings endpoints
  async getSettings(): Promise<AppSetting[]> {
    return this.fetch<AppSetting[]>('/settings');
  }

  async updateSetting(key: string, value: string): Promise<AppSetting> {
    return this.fetch<AppSetting>(`/settings/${key}`, {
      method: 'PUT',
      body: JSON.stringify({ value }),
    });
  }

  async getLlmModels(): Promise<LlmModel[]> {
    return this.fetch<LlmModel[]>('/settings/llm-models');
  }

  async setDefaultLlmModel(modelId: string): Promise<{ model_id: string; is_default: boolean }> {
    return this.fetch(`/settings/llm-models/${modelId}/default`, { method: 'PUT' });
  }

  async getAdminEmail(): Promise<{ admin_email: string }> {
    return this.fetch('/settings/admin-email');
  }
}

// Generator types
export interface GeneratorEquipment {
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

// ML Pipeline types
export interface MLCoefficient {
  feature_name: string;
  coefficient: number;
  description: string;
}

export interface MLModelData {
  modelId: string;
  method: string;
  coefficients: MLCoefficient[];
  trainingObservations: number;
  failureObservations: number;
}

export interface MLPrediction {
  equipmentId: string;
  failureProbability: number;
  riskLevel: string;
  probableCause?: string;
  vibrationAvg: number;
  temperatureAvg: number;
  vibrationTrend: number;
  temperatureTrend: number;
  drivers?: Record<string, number>;
  readingsInWindow: number;
  modelId: string;
  scoredAt: string;
}

export interface MLPredictionsData {
  success: boolean;
  totalEquipment: number;
  criticalCount: number;
  predictions: MLPrediction[];
  scoringSource: string;
  error?: string;
}

export interface MLGemFireStatus {
  connected: boolean;
  deployedModels: {
    success?: boolean;
    modelCount?: number;
    models?: { modelId: string; pmmlSize: number; deployed: boolean }[];
  };
}

export interface MLPmmlData {
  success: boolean;
  modelId: string;
  format: string;
  pmml: string;
  featureCount: number;
  error?: string;
}

export interface MLStep {
  type: string;
  message: string;
  timestamp: string;
}

export interface MLRetrainResult {
  success: boolean;
  modelId?: string;
  trainingObservations?: number;
  coefficients?: Record<string, number>;
  method?: string;
  message?: string;
  error?: string;
  steps?: MLStep[];
}

export interface MLDeployResult {
  success: boolean;
  modelId?: string;
  region?: string;
  pmmlSize?: number;
  message?: string;
  error?: string;
  steps?: MLStep[];
}

// Order types
export interface OrderSummary {
  order_id: string;
  customer_id: string;
  customer_name: string;
  tier: string;
  order_date: string;
  required_date: string;
  status: string;
  priority: string;
  total_amount: number;
  shipping_address: string;
  notes: string;
  line_count: number;
}

export interface OrderLine {
  line_id: number;
  sku: string;
  product_name: string;
  category: string;
  quantity: number;
  unit_price: number;
  line_total: number;
  qty_shipped: number;
}

export interface OrderEvent {
  event_id: number;
  event_type: string;
  event_timestamp: string;
  event_data: string | null;
  created_by: string;
  notes: string;
}

export interface OrderShipment {
  shipment_id: string;
  tracking_number: string;
  status: string;
  ship_date: string;
  delivery_date: string;
  origin_facility: string;
  carrier_name: string;
  service_type: string;
  tracking_url_template: string;
}

export interface CustomerContract {
  contract_id?: string;
  contract_type: string;
  priority_level: number;
  discount_percent: number;
  payment_terms: number;
  credit_limit?: number;
  valid_from?: string;
  valid_to?: string;
}

export interface OrderDetails extends OrderSummary {
  lines: OrderLine[];
  events: OrderEvent[];
  shipments: OrderShipment[];
  contract: CustomerContract;
}

export interface OrderCounts {
  counts: {
    pending: number;
    validated: number;
    processing: number;
    shipped: number;
    delivered: number;
  };
  totalOrders: number;
  totalActiveValue: number;
}

// Recommendation types
export interface ReservedPart {
  sku: string;
  description: string;
  quantity: number;
  unitPrice: number;
  reservationId: string;
}

export interface Recommendation {
  recommendation_id: string;
  equipment_id: string;
  facility_id: string;
  risk_level: string;
  failure_probability: number;
  probable_cause: string;
  recommended_action: string;
  recommended_parts: ReservedPart[] | string;
  estimated_cost: number;
  status: string;
  created_at: string;
  expires_at: string;
  approved_at?: string;
  approved_by?: string;
  work_order_id?: string;
  notes?: string;
}

export interface ApprovalResponse {
  success: boolean;
  workOrderId: string | null;
  message: string;
}

export interface DismissResponse {
  success: boolean;
  message: string;
}

export interface AppSetting {
  setting_key: string;
  setting_value: string;
  encrypted: boolean;
  updated_at: string;
  updated_by: string;
}

export interface LlmModel {
  model_id: string;
  provider: string;
  model_name: string;
  base_url: string | null;
  is_default: boolean;
  created_at: string;
}

export interface AutomatedAction {
  action_id: string;
  event_id: string;
  equipment_id: string;
  facility_id: string;
  action_type: string;
  risk_level: string;
  failure_probability: number;
  probable_cause: string;
  work_order_id: string;
  parts_reserved: ReservedPart[] | string;
  notification_sent: boolean;
  status: string;
  executed_at: string;
  execution_summary: string;
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
    id: 'apex-expedite',
    name: 'Apex Expedite',
    description: 'Rush order for 500 turbine blade blanks with split shipment',
    icon: 'Plane',
    prompt: 'Apex Aerospace needs 500 turbine blade blanks ASAP - order TM-2024-45892. Validate and fulfill.',
    category: 'fulfillment' as const,
  },
  {
    id: 'faa-audit',
    name: 'FAA Audit',
    description: 'Trace titanium batch TI-2024-0892 for 787 landing gear compliance',
    icon: 'Shield',
    prompt: 'Trace titanium batch TI-2024-0892 used in Apex widebody landing gear for FAA audit',
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
