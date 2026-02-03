// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Agent Status Monitor
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect } from 'react';
import { Cpu, Activity, Wrench, Package, Truck, FileText, MessageSquare, Shield, RefreshCw } from 'lucide-react';
import { MOCK_AGENTS } from '../api/titanApi';

interface AgentMetrics {
  requestsHandled: number;
  avgResponseTime: number;
  uptime: string;
  lastActivity: string;
}

// Simulated metrics for each agent
const generateMetrics = (): Record<string, AgentMetrics> => ({
  sensor: {
    requestsHandled: Math.floor(Math.random() * 500 + 1500),
    avgResponseTime: Math.floor(Math.random() * 50 + 20),
    uptime: '99.9%',
    lastActivity: 'Just now',
  },
  maintenance: {
    requestsHandled: Math.floor(Math.random() * 200 + 300),
    avgResponseTime: Math.floor(Math.random() * 100 + 50),
    uptime: '99.8%',
    lastActivity: '2 min ago',
  },
  inventory: {
    requestsHandled: Math.floor(Math.random() * 400 + 800),
    avgResponseTime: Math.floor(Math.random() * 40 + 25),
    uptime: '99.9%',
    lastActivity: '30 sec ago',
  },
  logistics: {
    requestsHandled: Math.floor(Math.random() * 150 + 200),
    avgResponseTime: Math.floor(Math.random() * 80 + 40),
    uptime: '99.7%',
    lastActivity: '5 min ago',
  },
  order: {
    requestsHandled: Math.floor(Math.random() * 300 + 400),
    avgResponseTime: Math.floor(Math.random() * 60 + 30),
    uptime: '99.9%',
    lastActivity: '1 min ago',
  },
  communications: {
    requestsHandled: Math.floor(Math.random() * 100 + 150),
    avgResponseTime: Math.floor(Math.random() * 120 + 80),
    uptime: '99.5%',
    lastActivity: '8 min ago',
  },
  governance: {
    requestsHandled: Math.floor(Math.random() * 50 + 75),
    avgResponseTime: Math.floor(Math.random() * 200 + 100),
    uptime: '99.9%',
    lastActivity: '15 min ago',
  },
});

const AGENT_ICONS: Record<string, React.ElementType> = {
  sensor: Activity,
  maintenance: Wrench,
  inventory: Package,
  logistics: Truck,
  order: FileText,
  communications: MessageSquare,
  governance: Shield,
};

export function AgentStatus() {
  const [metrics, setMetrics] = useState<Record<string, AgentMetrics>>(generateMetrics());
  const [selectedAgent, setSelectedAgent] = useState<string>('sensor');
  const [isRefreshing, setIsRefreshing] = useState(false);

  const refreshMetrics = () => {
    setIsRefreshing(true);
    setTimeout(() => {
      setMetrics(generateMetrics());
      setIsRefreshing(false);
    }, 500);
  };

  // Auto-refresh every 30 seconds
  useEffect(() => {
    const interval = setInterval(() => {
      setMetrics(generateMetrics());
    }, 30000);
    return () => clearInterval(interval);
  }, []);

  const agents = MOCK_AGENTS;

  return (
    <div className="space-y-6 fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <Cpu className="text-ember" />
            MCP Agent Status
          </h2>
          <p className="text-slate mt-1">Model Context Protocol server health and metrics</p>
        </div>
        <button
          onClick={refreshMetrics}
          disabled={isRefreshing}
          className="btn-secondary flex items-center gap-2"
        >
          <RefreshCw size={14} className={isRefreshing ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {/* Agent Grid */}
      <div className="grid grid-cols-4 gap-4">
        {agents.map((agent, index) => {
          const Icon = AGENT_ICONS[agent.type] || Cpu;
          const agentMetrics = metrics[agent.type];
          const isSelected = selectedAgent === agent.type;

          return (
            <div
              key={agent.name}
              onClick={() => setSelectedAgent(agent.type)}
              className={`panel card-interactive cursor-pointer stagger scale-in stagger-${index + 1} ${
                isSelected ? 'border-ember' : ''
              }`}
            >
              <div className="p-4">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-lg bg-ember/10 flex items-center justify-center">
                      <Icon size={20} className="text-ember" />
                    </div>
                    <div>
                      <h3 className="font-display font-semibold text-white text-sm">
                        {agent.name.replace(' Agent', '')}
                      </h3>
                      <p className="text-xs text-ash font-mono">:{agent.port}</p>
                    </div>
                  </div>
                  <div
                    className={`status-dot ${
                      agent.status === 'online' ? 'status-dot-healthy' : 'status-dot-critical'
                    }`}
                  />
                </div>

                <div className="grid grid-cols-2 gap-2 text-center">
                  <div className="bg-steel rounded p-2">
                    <div className="text-lg font-display font-bold text-ember">
                      {agentMetrics?.requestsHandled || 0}
                    </div>
                    <div className="text-[10px] text-ash uppercase">Requests</div>
                  </div>
                  <div className="bg-steel rounded p-2">
                    <div className="text-lg font-display font-bold text-white">
                      {agentMetrics?.avgResponseTime || 0}ms
                    </div>
                    <div className="text-[10px] text-ash uppercase">Avg Time</div>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Agent Details */}
      <div className="panel stagger slide-in-left stagger-8">
        <div className="panel-header">
          <Cpu size={16} />
          Agent Details
        </div>
        <div className="p-6">
          {(() => {
            const agent = agents.find((a) => a.type === selectedAgent);
            const Icon = AGENT_ICONS[selectedAgent] || Cpu;
            const agentMetrics = metrics[selectedAgent];

            if (!agent) return null;

            return (
              <div className="flex items-start gap-8">
                <div className="flex items-center gap-4">
                  <div className="w-16 h-16 rounded-xl bg-ember/10 flex items-center justify-center">
                    <Icon size={32} className="text-ember" />
                  </div>
                  <div>
                    <h3 className="font-display text-xl font-bold text-white">{agent.name}</h3>
                    <p className="text-sm text-slate">MCP Server • Port {agent.port}</p>
                  </div>
                </div>

                <div className="grid grid-cols-4 gap-4 flex-1">
                  <MetricBox label="Uptime" value={agentMetrics?.uptime || '—'} />
                  <MetricBox label="Last Activity" value={agentMetrics?.lastActivity || '—'} />
                  <MetricBox
                    label="Total Requests"
                    value={agentMetrics?.requestsHandled?.toLocaleString() || '0'}
                  />
                  <MetricBox
                    label="Avg Response"
                    value={`${agentMetrics?.avgResponseTime || 0}ms`}
                  />
                </div>

                <div>
                  <h4 className="text-xs text-ash uppercase tracking-wider mb-2">Tools</h4>
                  <div className="flex flex-wrap gap-1.5">
                    {agent.tools.map((tool) => (
                      <span
                        key={tool}
                        className="px-2 py-1 bg-steel rounded text-[10px] font-mono text-slate"
                      >
                        {tool}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            );
          })()}
        </div>
      </div>

      {/* GOAP Flow Diagram */}
      <GoapFlowDiagram />
    </div>
  );
}

function MetricBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-steel rounded-lg p-3">
      <p className="text-[10px] text-ash uppercase tracking-wider mb-1">{label}</p>
      <p className="font-display font-bold text-white">{value}</p>
    </div>
  );
}

// ─── GOAP Flow Diagram ───────────────────────────────────────────────────────

const TOOL_COLORS: Record<string, string> = {
  'maintenance-tools': '#ff6b00',
  'sensor-tools': '#22c55e',
  'inventory-tools': '#3b82f6',
  'logistics': '#a855f7',
  'governance-tools': '#eab308',
};

interface FlowNode {
  id: string;
  label: string;
  x: number;
  y: number;
  type: 'input' | 'action' | 'branch' | 'output';
  tool?: string;
  llm?: boolean;
}

interface FlowEdge {
  from: string;
  to: string;
  label?: string;
  branch?: 'top' | 'bottom';
}

const NODES: FlowNode[] = [
  { id: 'input', label: 'CriticalAnomaly\nInput', x: 30, y: 150, type: 'input' },
  { id: 'diagnose', label: 'diagnose\nAnomaly', x: 150, y: 150, type: 'action', tool: 'maintenance-tools', llm: true },
  { id: 'urgency', label: 'assess\nUrgency', x: 270, y: 150, type: 'action' },
  { id: 'branch1', label: 'Urgency?', x: 380, y: 150, type: 'branch' },
  { id: 'shutdown', label: 'emergency\nShutdown', x: 480, y: 80, type: 'action', tool: 'sensor-tools', llm: true },
  { id: 'parts', label: 'assess\nParts', x: 540, y: 150, type: 'action', tool: 'inventory-tools', llm: true },
  { id: 'branch2', label: 'Parts?', x: 650, y: 150, type: 'branch' },
  { id: 'scheduleLocal', label: 'schedule\nLocal', x: 740, y: 80, type: 'action', tool: 'maintenance-tools', llm: true },
  { id: 'procure', label: 'procure\nCrossFacility', x: 740, y: 220, type: 'action', tool: 'logistics', llm: true },
  { id: 'scheduleProcured', label: 'schedule\nProcured', x: 850, y: 220, type: 'action', tool: 'maintenance-tools', llm: true },
  { id: 'compliance', label: 'check\nCompliance', x: 920, y: 150, type: 'action' },
  { id: 'branch3', label: 'Regulated?', x: 1030, y: 150, type: 'branch' },
  { id: 'verify', label: 'verify\nCompliance', x: 1130, y: 80, type: 'action', tool: 'governance-tools', llm: true },
  { id: 'finalize', label: 'finalize\nResponse', x: 1200, y: 150, type: 'action' },
  { id: 'output', label: 'CriticalAnomaly\nResponse', x: 1320, y: 150, type: 'output' },
];

const EDGES: FlowEdge[] = [
  { from: 'input', to: 'diagnose' },
  { from: 'diagnose', to: 'urgency' },
  { from: 'urgency', to: 'branch1' },
  { from: 'branch1', to: 'shutdown', label: 'IMMEDIATE', branch: 'top' },
  { from: 'branch1', to: 'parts', label: 'DEFERRABLE' },
  { from: 'shutdown', to: 'parts' },
  { from: 'parts', to: 'branch2' },
  { from: 'branch2', to: 'scheduleLocal', label: 'Available', branch: 'top' },
  { from: 'branch2', to: 'procure', label: 'Unavailable', branch: 'bottom' },
  { from: 'scheduleLocal', to: 'compliance' },
  { from: 'procure', to: 'scheduleProcured' },
  { from: 'scheduleProcured', to: 'compliance' },
  { from: 'compliance', to: 'branch3' },
  { from: 'branch3', to: 'verify', label: 'Yes', branch: 'top' },
  { from: 'branch3', to: 'finalize', label: 'No' },
  { from: 'verify', to: 'finalize' },
  { from: 'finalize', to: 'output' },
];

function GoapFlowDiagram() {
  const nodeMap = Object.fromEntries(NODES.map((n) => [n.id, n]));

  const getCenter = (n: FlowNode): [number, number] => {
    if (n.type === 'branch') return [n.x, n.y];
    return [n.x + 45, n.y];
  };

  return (
    <div className="panel scanlines stagger scale-in stagger-9">
      <div className="panel-header">
        <Activity size={16} />
        GOAP Anomaly Resolution Flow
      </div>
      <div className="p-4 overflow-x-auto">
        <svg viewBox="0 0 1400 300" className="w-full" style={{ minWidth: 900, height: 260 }}>
          <defs>
            <marker id="arrow" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
              <path d="M0,0 L8,3 L0,6" fill="#ff6b00" fillOpacity="0.6" />
            </marker>
          </defs>

          {/* Edges */}
          {EDGES.map((edge, i) => {
            const from = nodeMap[edge.from];
            const to = nodeMap[edge.to];
            const [fx, fy] = getCenter(from);
            const [tx, ty] = getCenter(to);

            // For branch outputs, start from the diamond edge
            let startX = fx + (from.type === 'branch' ? 22 : 45);
            let startY = fy;
            let endX = tx - (to.type === 'branch' ? 22 : 45);
            let endY = ty;

            if (edge.branch === 'top' && from.type === 'branch') {
              startX = fx;
              startY = fy - 22;
            } else if (edge.branch === 'bottom' && from.type === 'branch') {
              startX = fx;
              startY = fy + 22;
            }

            // Build path
            let path: string;
            if (startY !== endY) {
              const midX = (startX + endX) / 2;
              path = `M${startX},${startY} C${midX},${startY} ${midX},${endY} ${endX},${endY}`;
            } else {
              path = `M${startX},${startY} L${endX},${endY}`;
            }

            return (
              <g key={i}>
                <path
                  d={path}
                  fill="none"
                  stroke="#ff6b00"
                  strokeWidth="1.5"
                  strokeOpacity="0.4"
                  strokeDasharray="6 3"
                  markerEnd="url(#arrow)"
                />
                {edge.label && (
                  <text
                    x={(startX + endX) / 2}
                    y={(startY + endY) / 2 - 6}
                    textAnchor="middle"
                    fill="#8a8a8a"
                    fontSize="9"
                    fontFamily="monospace"
                  >
                    {edge.label}
                  </text>
                )}
              </g>
            );
          })}

          {/* Nodes */}
          {NODES.map((node) => {
            if (node.type === 'branch') {
              // Diamond
              return (
                <g key={node.id}>
                  <polygon
                    points={`${node.x},${node.y - 22} ${node.x + 22},${node.y} ${node.x},${node.y + 22} ${node.x - 22},${node.y}`}
                    fill="#1a1a1a"
                    stroke="#ff6b00"
                    strokeWidth="1.5"
                    strokeOpacity="0.7"
                  />
                  <text
                    x={node.x}
                    y={node.y + 3}
                    textAnchor="middle"
                    fill="#ff6b00"
                    fontSize="8"
                    fontWeight="bold"
                    fontFamily="monospace"
                  >
                    {node.label}
                  </text>
                </g>
              );
            }

            if (node.type === 'input' || node.type === 'output') {
              // Rounded terminal node
              return (
                <g key={node.id}>
                  <rect
                    x={node.x}
                    y={node.y - 22}
                    width={90}
                    height={44}
                    rx={22}
                    fill="#1a1a1a"
                    stroke="#ff6b00"
                    strokeWidth="2"
                    strokeOpacity="0.8"
                  />
                  {node.label.split('\n').map((line, li) => (
                    <text
                      key={li}
                      x={node.x + 45}
                      y={node.y - 4 + li * 13}
                      textAnchor="middle"
                      fill="#ff6b00"
                      fontSize="9"
                      fontWeight="bold"
                      fontFamily="monospace"
                    >
                      {line}
                    </text>
                  ))}
                </g>
              );
            }

            // Action node
            const isLlm = node.llm;
            const borderColor = isLlm ? '#ff6b00' : '#555';
            const textColor = isLlm ? '#fff' : '#aaa';

            return (
              <g key={node.id}>
                <rect
                  x={node.x}
                  y={node.y - 22}
                  width={90}
                  height={44}
                  rx={8}
                  fill={isLlm ? 'rgba(255,107,0,0.08)' : '#141414'}
                  stroke={borderColor}
                  strokeWidth={isLlm ? 1.5 : 1}
                  strokeOpacity={isLlm ? 0.7 : 0.4}
                />
                {node.label.split('\n').map((line, li) => (
                  <text
                    key={li}
                    x={node.x + 45}
                    y={node.y - 4 + li * 13}
                    textAnchor="middle"
                    fill={textColor}
                    fontSize="10"
                    fontWeight="600"
                    fontFamily="monospace"
                  >
                    {line}
                  </text>
                ))}
                {/* Tool badge */}
                {node.tool && (
                  <>
                    <rect
                      x={node.x + 8}
                      y={node.y + 24}
                      width={74}
                      height={14}
                      rx={7}
                      fill={TOOL_COLORS[node.tool] || '#666'}
                      fillOpacity="0.2"
                    />
                    <text
                      x={node.x + 45}
                      y={node.y + 34}
                      textAnchor="middle"
                      fill={TOOL_COLORS[node.tool] || '#888'}
                      fontSize="8"
                      fontFamily="monospace"
                    >
                      {node.tool}
                    </text>
                  </>
                )}
                {/* Pure logic indicator */}
                {!isLlm && !node.tool && (
                  <text
                    x={node.x + 45}
                    y={node.y + 34}
                    textAnchor="middle"
                    fill="#555"
                    fontSize="7"
                    fontFamily="monospace"
                    fontStyle="italic"
                  >
                    pure logic
                  </text>
                )}
              </g>
            );
          })}
        </svg>

        {/* Legend */}
        <div className="flex items-center gap-6 mt-3 px-2">
          <div className="flex items-center gap-2 text-[10px] text-ash">
            <div className="w-3 h-3 rounded border border-ember/70 bg-ember/10" />
            <span>LLM-backed action</span>
          </div>
          <div className="flex items-center gap-2 text-[10px] text-ash">
            <div className="w-3 h-3 rounded border border-iron bg-carbon" />
            <span>Pure logic</span>
          </div>
          <div className="flex items-center gap-2 text-[10px] text-ash">
            <svg width="12" height="12"><polygon points="6,0 12,6 6,12 0,6" fill="none" stroke="#ff6b00" strokeWidth="1.5" /></svg>
            <span>Branch point</span>
          </div>
          {Object.entries(TOOL_COLORS).map(([tool, color]) => (
            <div key={tool} className="flex items-center gap-1.5 text-[10px]">
              <div className="w-2 h-2 rounded-full" style={{ backgroundColor: color }} />
              <span style={{ color }}>{tool}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
