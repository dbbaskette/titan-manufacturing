import React, { useState } from 'react';

const agents = {
  orchestrator: {
    title: "Titan 5.0 Orchestrator",
    role: "Central coordinator receiving requests from operators and executives, planning multi-agent workflows",
    tools: ["route_request", "plan_workflow", "synthesize_response"],
    tanzu: "Spring AI + MCP Client",
    color: "#1a365d"
  },
  sensor: {
    title: "Sensor Agent",
    role: "Monitors IoT data from 600+ CNC machines across 12 Titan facilities worldwide",
    tools: ["subscribe_mqtt", "detect_anomaly", "get_sensor_readings", "list_equipment", "get_facility_status"],
    tanzu: "TimescaleDB - Time-series",
    mcp: "sensor-mcp-server:8081",
    color: "#38A169"
  },
  maintenance: {
    title: "Maintenance Agent",
    role: "Predicts equipment failures, estimates RUL, schedules preventive maintenance to avoid incidents like the $12M Phoenix failure",
    tools: ["predict_failure", "estimate_rul", "schedule_maintenance", "check_parts", "get_maintenance_history"],
    tanzu: "TensorFlow Models",
    mcp: "maintenance-mcp-server:8082",
    color: "#D69E2E"
  },
  inventory: {
    title: "Inventory Agent",
    role: "Manages Titan's 50,000+ SKU catalog across 4 divisions, handles supplier relationships",
    tools: ["check_stock", "check_availability", "calculate_reorder", "search_products", "find_alternative_supplier"],
    tanzu: "Greenplum + pgvector",
    mcp: "inventory-mcp-server:8083",
    color: "#805AD5"
  },
  logistics: {
    title: "Logistics Agent",
    role: "Optimizes global shipping from 12 facilities to aerospace, energy, and mobility customers",
    tools: ["plan_route", "select_carrier", "predict_eta", "track_shipment", "calculate_shipping_cost"],
    tanzu: "Greenplum Analytics",
    mcp: "logistics-mcp-server:8084",
    color: "#DD6B20"
  },
  order: {
    title: "Order Agent",
    role: "Handles B2B order fulfillment for Boeing, Tesla, GE and other enterprise customers",
    tools: ["validate_order", "check_contract_terms", "initiate_fulfillment", "get_order_status"],
    tanzu: "RabbitMQ + PostgreSQL",
    mcp: "order-mcp-server:8085",
    color: "#E53E3E"
  },
  comms: {
    title: "Communications Agent",
    role: "Sends notifications to procurement teams, handles customer inquiries via RAG",
    tools: ["send_notification", "handle_inquiry", "get_order_status", "draft_customer_update"],
    tanzu: "pgvector - RAG Pipeline",
    mcp: "communications-mcp-server:8086",
    color: "#00B5D8"
  },
  governance: {
    title: "Governance Agent",
    role: "Manages data catalog, lineage tracking for FAA compliance, PII detection",
    tools: ["search_data_assets", "get_lineage", "check_pii", "get_quality_results", "validate_compliance"],
    tanzu: "OpenMetadata Catalog",
    mcp: "governance-mcp-server:8087",
    color: "#319795"
  }
};

const scenarios = [
  {
    name: "🔧 The Phoenix Incident",
    trigger: "CNC-007 showing vibration anomalies — same pattern as $12M incident",
    flow: ["governance", "sensor", "maintenance", "inventory", "maintenance"],
    description: "Governance validates access → Sensor confirms anomaly → Maintenance predicts 73% failure, 48hr RUL → Inventory checks bearings → Maintenance schedules replacement"
  },
  {
    name: "✈️ Boeing Expedite",
    trigger: "Order #TM-2024-45892 for 500 turbine blade blanks — ASAP",
    flow: ["governance", "order", "inventory", "logistics", "comms"],
    description: "Governance checks aerospace compliance → Order validates contract → Inventory: Phoenix (320) + Munich (400) → Logistics plans split air freight → Comms confirms to Boeing"
  },
  {
    name: "📋 FAA Audit",
    trigger: "Traceability needed for titanium batch TI-2024-0892 in 787 landing gear",
    flow: ["governance", "governance", "governance", "governance"],
    description: "Search batch in catalog → Trace upstream to supplier → Column-level lineage through QC → Flag PII in operator certs → Generate compliance report"
  },
  {
    name: "⚠️ Supply Chain Crisis",
    trigger: "NipponBearing declared force majeure — find alternatives NOW",
    flow: ["inventory", "inventory", "maintenance", "logistics", "comms"],
    description: "Identify affected SKUs → Search alternative suppliers via pgvector → Assess maintenance impact → Recalculate lead times → Draft customer notifications"
  }
];

const facilities = [
  { name: "Phoenix", region: "NA", equipment: 65 },
  { name: "Detroit", region: "NA", equipment: 52 },
  { name: "Atlanta HQ", region: "NA", equipment: 48 },
  { name: "Munich", region: "EU", equipment: 58 },
  { name: "Lyon", region: "EU", equipment: 45 },
  { name: "Shanghai", region: "APAC", equipment: 72 },
  { name: "Tokyo", region: "APAC", equipment: 55 },
];

export default function TitanArchitecture() {
  const [selectedAgent, setSelectedAgent] = useState(null);
  const [activeScenario, setActiveScenario] = useState(null);
  const [activeStep, setActiveStep] = useState(0);

  const AgentNode = ({ id, x, y }) => {
    const agent = agents[id];
    const isActive = activeScenario !== null && scenarios[activeScenario].flow[activeStep] === id;
    const isInFlow = activeScenario !== null && scenarios[activeScenario].flow.includes(id);
    
    return (
      <g 
        transform={`translate(${x}, ${y})`}
        onClick={() => setSelectedAgent(id)}
        style={{ cursor: 'pointer' }}
      >
        <rect
          width="140"
          height="70"
          rx="8"
          fill={isActive ? agent.color : isInFlow ? `${agent.color}40` : "#fff"}
          stroke={agent.color}
          strokeWidth={isActive ? 3 : 2}
          className={isActive ? "animate-pulse" : ""}
        />
        <text
          x="70"
          y="25"
          textAnchor="middle"
          fill={isActive ? "#fff" : agent.color}
          fontSize="11"
          fontWeight="bold"
        >
          {agent.title.split(' ')[0]}
        </text>
        <text
          x="70"
          y="40"
          textAnchor="middle"
          fill={isActive ? "#fff" : agent.color}
          fontSize="11"
          fontWeight="bold"
        >
          {agent.title.split(' ').slice(1).join(' ')}
        </text>
        <text
          x="70"
          y="58"
          textAnchor="middle"
          fill={isActive ? "#ddd" : "#666"}
          fontSize="9"
        >
          {agent.mcp || agent.tanzu}
        </text>
      </g>
    );
  };

  return (
    <div className="min-h-screen bg-slate-50 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-slate-800">🏭 TITAN MANUFACTURING</h1>
          <p className="text-xl text-slate-600 mt-2">Titan 5.0 AI Platform Architecture</p>
          <p className="text-slate-500 italic">"Forging the future with intelligent manufacturing"</p>
        </div>

        {/* Stats Bar */}
        <div className="grid grid-cols-4 gap-4 mb-6">
          {[
            { label: "Revenue", value: "$4.2B" },
            { label: "Employees", value: "8,500" },
            { label: "Facilities", value: "12" },
            { label: "SKUs", value: "50,000+" },
          ].map(stat => (
            <div key={stat.label} className="bg-white rounded-lg shadow p-4 text-center">
              <div className="text-2xl font-bold text-slate-800">{stat.value}</div>
              <div className="text-sm text-slate-500">{stat.label}</div>
            </div>
          ))}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Architecture Diagram */}
          <div className="lg:col-span-2 bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-lg font-bold text-slate-700 mb-4">System Architecture</h2>
            <svg viewBox="0 0 800 500" className="w-full">
              <defs>
                <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                  <polygon points="0 0, 10 3.5, 0 7" fill="#64748b" />
                </marker>
              </defs>

              {/* Orchestrator Layer */}
              <rect x="50" y="20" width="700" height="90" rx="10" fill="#EFF6FF" stroke="#1a365d" strokeWidth="2" />
              <text x="400" y="45" textAnchor="middle" fill="#1a365d" fontSize="14" fontWeight="bold">TITAN 5.0 ORCHESTRATOR</text>
              <AgentNode id="orchestrator" x="330" y="50" />

              {/* Manufacturing Layer */}
              <rect x="50" y="130" width="700" height="100" rx="10" fill="#F0FDF4" stroke="#38A169" strokeWidth="1" strokeDasharray="5,5" />
              <text x="70" y="150" fill="#38A169" fontSize="12" fontWeight="bold">MANUFACTURING OPS</text>
              <AgentNode id="sensor" x="80" y="160" />
              <AgentNode id="maintenance" x="250" y="160" />
              <AgentNode id="governance" x="580" y="160" />

              {/* Supply Chain Layer */}
              <rect x="50" y="250" width="700" height="100" rx="10" fill="#FFFBEB" stroke="#D69E2E" strokeWidth="1" strokeDasharray="5,5" />
              <text x="70" y="270" fill="#D69E2E" fontSize="12" fontWeight="bold">SUPPLY CHAIN</text>
              <AgentNode id="inventory" x="150" y="280" />
              <AgentNode id="logistics" x="400" y="280" />

              {/* Customer Layer */}
              <rect x="50" y="370" width="700" height="100" rx="10" fill="#FEF2F2" stroke="#E53E3E" strokeWidth="1" strokeDasharray="5,5" />
              <text x="70" y="390" fill="#E53E3E" fontSize="12" fontWeight="bold">CUSTOMER OPS</text>
              <AgentNode id="order" x="150" y="400" />
              <AgentNode id="comms" x="400" y="400" />

              {/* Connection Lines */}
              <line x1="400" y1="120" x2="150" y2="160" stroke="#64748b" strokeWidth="1.5" markerEnd="url(#arrowhead)" />
              <line x1="400" y1="120" x2="320" y2="160" stroke="#64748b" strokeWidth="1.5" markerEnd="url(#arrowhead)" />
              <line x1="400" y1="120" x2="650" y2="160" stroke="#64748b" strokeWidth="1.5" markerEnd="url(#arrowhead)" />
              <line x1="400" y1="120" x2="220" y2="280" stroke="#64748b" strokeWidth="1.5" markerEnd="url(#arrowhead)" />
              <line x1="400" y1="120" x2="470" y2="280" stroke="#64748b" strokeWidth="1.5" markerEnd="url(#arrowhead)" />
              <line x1="400" y1="120" x2="220" y2="400" stroke="#64748b" strokeWidth="1.5" markerEnd="url(#arrowhead)" />
              <line x1="400" y1="120" x2="470" y2="400" stroke="#64748b" strokeWidth="1.5" markerEnd="url(#arrowhead)" />

              {/* Governance connections */}
              <line x1="650" y1="230" x2="650" y2="260" stroke="#319795" strokeWidth="1" strokeDasharray="3,3" />
              <line x1="650" y1="260" x2="540" y2="280" stroke="#319795" strokeWidth="1" strokeDasharray="3,3" markerEnd="url(#arrowhead)" />
              <line x1="650" y1="260" x2="290" y2="280" stroke="#319795" strokeWidth="1" strokeDasharray="3,3" markerEnd="url(#arrowhead)" />
              <text x="680" y="250" fill="#319795" fontSize="9">lineage</text>
            </svg>
          </div>

          {/* Side Panel */}
          <div className="space-y-4">
            {/* Agent Details */}
            {selectedAgent && (
              <div className="bg-white rounded-xl shadow-lg p-4" style={{ borderLeft: `4px solid ${agents[selectedAgent].color}` }}>
                <h3 className="font-bold text-lg" style={{ color: agents[selectedAgent].color }}>
                  {agents[selectedAgent].title}
                </h3>
                <p className="text-gray-600 text-sm mt-2">{agents[selectedAgent].role}</p>
                <div className="mt-3">
                  <span className="text-xs font-semibold text-gray-500">TOOLS:</span>
                  <div className="flex flex-wrap gap-1 mt-1">
                    {agents[selectedAgent].tools.map(tool => (
                      <span key={tool} className="px-2 py-1 bg-gray-100 rounded text-xs">{tool}</span>
                    ))}
                  </div>
                </div>
                <div className="mt-3">
                  <span className="text-xs font-semibold text-gray-500">TANZU COMPONENT:</span>
                  <p className="text-sm">{agents[selectedAgent].tanzu}</p>
                </div>
              </div>
            )}

            {/* Scenarios */}
            <div className="bg-white rounded-xl shadow-lg p-4">
              <h3 className="font-bold text-gray-800 mb-3">Demo Scenarios</h3>
              {scenarios.map((scenario, idx) => (
                <button
                  key={idx}
                  onClick={() => { setActiveScenario(idx); setActiveStep(0); }}
                  className={`w-full text-left p-3 rounded-lg mb-2 transition-all ${
                    activeScenario === idx ? 'bg-blue-50 border-2 border-blue-300' : 'bg-gray-50 hover:bg-gray-100'
                  }`}
                >
                  <div className="font-semibold text-sm">{scenario.name}</div>
                  <div className="text-xs text-gray-500 mt-1">{scenario.trigger}</div>
                </button>
              ))}
            </div>

            {/* Workflow Steps */}
            {activeScenario !== null && (
              <div className="bg-white rounded-xl shadow-lg p-4">
                <h4 className="font-bold text-gray-800 mb-2">Workflow</h4>
                <p className="text-xs text-gray-600 mb-3">{scenarios[activeScenario].description}</p>
                <div className="flex gap-2">
                  <button
                    onClick={() => setActiveStep(Math.max(0, activeStep - 1))}
                    disabled={activeStep === 0}
                    className="px-3 py-1 bg-gray-200 rounded disabled:opacity-50"
                  >
                    ←
                  </button>
                  <span className="flex-1 text-center py-1">
                    Step {activeStep + 1} / {scenarios[activeScenario].flow.length}
                  </span>
                  <button
                    onClick={() => setActiveStep(Math.min(scenarios[activeScenario].flow.length - 1, activeStep + 1))}
                    disabled={activeStep === scenarios[activeScenario].flow.length - 1}
                    className="px-3 py-1 bg-gray-200 rounded disabled:opacity-50"
                  >
                    →
                  </button>
                </div>
                <div className="mt-2 text-center text-sm font-semibold" style={{ color: agents[scenarios[activeScenario].flow[activeStep]].color }}>
                  {agents[scenarios[activeScenario].flow[activeStep]].title}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Facilities */}
        <div className="mt-8 bg-white rounded-xl shadow-lg p-6">
          <h3 className="font-bold text-gray-800 mb-4">Global Facilities</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-4">
            {facilities.map(f => (
              <div key={f.name} className="bg-slate-50 rounded-lg p-3 text-center">
                <div className="font-semibold text-sm">{f.name}</div>
                <div className="text-xs text-gray-500">{f.region}</div>
                <div className="text-lg font-bold text-slate-700">{f.equipment}</div>
                <div className="text-xs text-gray-400">machines</div>
              </div>
            ))}
          </div>
        </div>

        {/* Infrastructure */}
        <div className="mt-6 bg-white rounded-xl shadow-lg p-6">
          <h3 className="font-bold text-gray-800 mb-4">Infrastructure</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {[
              { name: "OpenMetadata", port: "8585", desc: "Data Catalog" },
              { name: "Titan 5.0 API", port: "8080", desc: "Orchestrator" },
              { name: "Greenplum", port: "5432", desc: "Analytics" },
              { name: "TimescaleDB", port: "5433", desc: "Sensors" },
              { name: "RabbitMQ", port: "15672", desc: "Events" },
              { name: "Grafana", port: "3000", desc: "Monitoring" },
            ].map(svc => (
              <div key={svc.name} className="bg-slate-50 rounded-lg p-3 text-center">
                <div className="font-semibold text-sm">{svc.name}</div>
                <div className="text-xs text-gray-500">{svc.desc}</div>
                <code className="text-xs text-blue-600">:{svc.port}</code>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
