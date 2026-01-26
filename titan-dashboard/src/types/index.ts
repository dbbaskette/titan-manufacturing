// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — TypeScript Types
// ═══════════════════════════════════════════════════════════════════════════

export interface Facility {
  facility_id: string;
  name: string;
  city: string;
  country: string;
  region: 'NA' | 'EU' | 'APAC' | 'LATAM';
  equipment_count: number;
  specialization: string;
  status: 'online' | 'warning' | 'critical' | 'offline';
  coordinates: { lat: number; lng: number };
}

export interface Equipment {
  equipment_id: string;
  facility_id: string;
  name: string;
  equipment_type: string;
  manufacturer: string;
  model: string;
  status: 'running' | 'idle' | 'maintenance' | 'error';
  health_score: number;
}

export interface SensorReading {
  time: string;
  equipment_id: string;
  sensor_type: 'temperature' | 'vibration' | 'power' | 'pressure' | 'spindle_speed';
  value: number;
  unit: string;
  quality_flag: 'good' | 'warning' | 'critical';
}

export interface HealthAnalysis {
  equipment_id: string;
  timestamp: string;
  health_score: number;
  failure_probability: number;
  remaining_useful_life_hours: number;
  status: 'healthy' | 'degraded' | 'critical';
  recommendations: string[];
  anomalies: Anomaly[];
}

export interface Anomaly {
  id: string;
  equipment_id: string;
  sensor_type: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
  message: string;
  detected_at: string;
  value: number;
  threshold: number;
}

export interface Order {
  order_id: string;
  customer_id: string;
  customer_name: string;
  status: 'pending' | 'validated' | 'processing' | 'shipped' | 'delivered';
  priority: 'standard' | 'expedite' | 'critical';
  total_amount: number;
  order_date: string;
  expected_delivery: string;
  items: OrderItem[];
}

export interface OrderItem {
  sku: string;
  name: string;
  quantity: number;
  unit_price: number;
}

export interface Agent {
  name: string;
  type: string;
  status: 'online' | 'offline' | 'busy';
  port: number;
  tools: string[];
  lastActivity?: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: string;
  agentActions?: AgentAction[];
}

export interface AgentAction {
  agent: string;
  tool: string;
  status: 'pending' | 'running' | 'complete' | 'error';
  result?: string;
}

export interface DemoScenario {
  id: string;
  name: string;
  description: string;
  icon: string;
  prompt: string;
  category: 'maintenance' | 'supply-chain' | 'compliance' | 'fulfillment';
}

// API Response types
export interface ChatResponse {
  response: string;
  agentActions?: AgentAction[];
  metadata?: Record<string, unknown>;
}

export interface FacilityStatus {
  facility: Facility;
  equipment: Equipment[];
  activeAlerts: number;
  utilizationRate: number;
}
