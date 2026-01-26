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

      {/* Selected Agent Details */}
      <div className="grid grid-cols-2 gap-6">
        {/* Agent Info */}
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
                <>
                  <div className="flex items-center gap-4 mb-6">
                    <div className="w-16 h-16 rounded-xl bg-ember/10 flex items-center justify-center">
                      <Icon size={32} className="text-ember" />
                    </div>
                    <div>
                      <h3 className="font-display text-xl font-bold text-white">{agent.name}</h3>
                      <p className="text-sm text-slate">MCP Server • Port {agent.port}</p>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4 mb-6">
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
                    <h4 className="text-xs text-ash uppercase tracking-wider mb-3">
                      Available Tools
                    </h4>
                    <div className="flex flex-wrap gap-2">
                      {agent.tools.map((tool) => (
                        <span
                          key={tool}
                          className="px-3 py-1.5 bg-steel rounded-lg text-xs font-mono text-slate"
                        >
                          {tool}
                        </span>
                      ))}
                    </div>
                  </div>
                </>
              );
            })()}
          </div>
        </div>

        {/* Architecture Diagram */}
        <div className="panel scanlines crt-flicker stagger scale-in stagger-8">
          <div className="panel-header">
            <Activity size={16} />
            Agent Architecture
          </div>
          <div className="p-6">
            <div className="relative h-80">
              {/* Orchestrator at center */}
              <div className="absolute top-8 left-1/2 -translate-x-1/2">
                <div className="w-32 h-16 bg-ember/20 border border-ember rounded-lg flex flex-col items-center justify-center">
                  <span className="font-display font-bold text-ember text-sm">Orchestrator</span>
                  <span className="text-[10px] text-ash font-mono">:8080</span>
                </div>
              </div>

              {/* Connection lines */}
              <svg
                className="absolute inset-0 w-full h-full"
                style={{ zIndex: 0 }}
              >
                {/* Lines from orchestrator to agents */}
                {[
                  { x: 60, y: 120 },
                  { x: 160, y: 140 },
                  { x: 260, y: 140 },
                  { x: 360, y: 120 },
                  { x: 100, y: 220 },
                  { x: 210, y: 240 },
                  { x: 320, y: 220 },
                ].map((pos, i) => (
                  <line
                    key={i}
                    x1="210"
                    y1="90"
                    x2={pos.x}
                    y2={pos.y}
                    stroke="#ff6b00"
                    strokeWidth="1"
                    strokeOpacity="0.3"
                    strokeDasharray="4 4"
                  />
                ))}
              </svg>

              {/* Agent nodes */}
              <div className="absolute top-28 left-4">
                <AgentNode icon={Activity} label="Sensor" active={selectedAgent === 'sensor'} />
              </div>
              <div className="absolute top-32 left-28">
                <AgentNode icon={Wrench} label="Maint" active={selectedAgent === 'maintenance'} />
              </div>
              <div className="absolute top-32 left-52">
                <AgentNode icon={Package} label="Invent" active={selectedAgent === 'inventory'} />
              </div>
              <div className="absolute top-28 right-4">
                <AgentNode icon={Truck} label="Logist" active={selectedAgent === 'logistics'} />
              </div>
              <div className="absolute top-52 left-12">
                <AgentNode icon={FileText} label="Order" active={selectedAgent === 'order'} />
              </div>
              <div className="absolute top-56 left-1/2 -translate-x-1/2">
                <AgentNode icon={MessageSquare} label="Comms" active={selectedAgent === 'communications'} />
              </div>
              <div className="absolute top-52 right-12">
                <AgentNode icon={Shield} label="Govern" active={selectedAgent === 'governance'} />
              </div>

              {/* Greenplum at bottom */}
              <div className="absolute bottom-0 left-1/2 -translate-x-1/2">
                <div className="w-40 h-12 bg-healthy/10 border border-healthy/30 rounded-lg flex items-center justify-center gap-2">
                  <div className="w-6 h-6 rounded bg-healthy/20 flex items-center justify-center">
                    <span className="text-healthy font-bold text-xs">GP</span>
                  </div>
                  <div>
                    <span className="text-xs text-healthy font-semibold">Greenplum</span>
                    <span className="block text-[10px] text-ash font-mono">:15432</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
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

function AgentNode({
  icon: Icon,
  label,
  active,
}: {
  icon: React.ElementType;
  label: string;
  active: boolean;
}) {
  return (
    <div
      className={`w-14 h-14 rounded-lg flex flex-col items-center justify-center transition-all ${
        active
          ? 'bg-ember/20 border border-ember shadow-lg shadow-ember/20'
          : 'bg-steel border border-iron'
      }`}
    >
      <Icon size={16} className={active ? 'text-ember' : 'text-slate'} />
      <span className={`text-[9px] font-semibold ${active ? 'text-ember' : 'text-ash'}`}>
        {label}
      </span>
    </div>
  );
}
