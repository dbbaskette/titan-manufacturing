// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — AI Chat Interface
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useRef, useEffect } from 'react';
import { MessageSquare, Send, Bot, User, Cpu, Loader2, Sparkles } from 'lucide-react';
import type { ChatMessage } from '../types';

const SUGGESTED_PROMPTS = [
  'Check the health status of PHX-CNC-007',
  'What is the stock level for SKU-BRG-7420?',
  'Show me all equipment in Phoenix facility',
  'Trace titanium batch TI-2024-0892',
  'Which machines need maintenance soon?',
];

// Mock responses for demo
const MOCK_RESPONSES: Record<string, { response: string; agents: string[] }> = {
  'phx-cnc-007': {
    response: `## PHX-CNC-007 Health Analysis

**Status: CRITICAL**

I've analyzed the sensor data and detected a concerning pattern:

| Metric | Current | Threshold | Status |
|--------|---------|-----------|--------|
| Vibration | 4.2 mm/s | 3.5 mm/s | ⚠️ Exceeded |
| Temperature | 76°C | 85°C | ⚡ Trending Up |
| Power Draw | 48 kW | 55 kW | ✓ Normal |

### Prediction
- **Failure Probability:** 73%
- **Remaining Useful Life:** ~48 hours
- **Root Cause:** Bearing degradation pattern detected

### Recommended Actions
1. 🔧 Schedule emergency bearing replacement (SKU-BRG-7420)
2. 📦 Order parts from Timken (fastest: 7-day lead time)
3. ⚠️ Reduce spindle speed to 80% immediately

This pattern matches the Phoenix Incident scenario - early intervention can prevent an estimated **$12M in unplanned downtime**.`,
    agents: ['Sensor Agent', 'Maintenance Agent'],
  },
  'stock': {
    response: `## Inventory Check: SKU-BRG-7420

**Precision Ball Bearing - 7420 Series**

### Stock Levels by Facility

| Facility | Available | Reserved | Reorder Point |
|----------|-----------|----------|---------------|
| Phoenix | 12 units | 4 | 20 |
| Munich | 28 units | 0 | 15 |
| Shanghai | 45 units | 8 | 25 |
| Detroit | 8 units | 2 | 15 |

### Summary
- **Total Available:** 93 units
- **Status:** ⚠️ Phoenix below reorder point
- **Lead Time:** 7 days (Timken), 14 days (SKF)

### Alternatives Available
- SKU-BRG-7421 (Compatible) - 156 units global
- SKU-BRG-7419 (Downgrade) - 89 units global`,
    agents: ['Inventory Agent'],
  },
  'phoenix': {
    response: `## Phoenix Facility Equipment Status

**Location:** Phoenix, AZ, USA
**Specialization:** Aerospace precision machining
**Equipment Count:** 65 machines

### Equipment by Status

| Status | Count | Equipment IDs |
|--------|-------|---------------|
| 🟢 Running | 58 | CNC-001 to CNC-006, CNC-008 to CNC-052, ... |
| 🟡 Warning | 4 | CNC-053, LTH-012, GRD-008, ASM-003 |
| 🔴 Critical | 1 | **CNC-007** (Bearing degradation) |
| 🔵 Idle | 2 | CNC-054, CNC-055 |

### Active Alerts
1. **PHX-CNC-007** - Vibration exceeds threshold (73% failure risk)
2. **PHX-LTH-012** - Temperature trending above normal
3. **PHX-GRD-008** - Coolant pressure dropping

### Today's Metrics
- Utilization Rate: 89%
- Units Produced: 1,247
- Quality Score: 99.2%`,
    agents: ['Sensor Agent', 'Maintenance Agent'],
  },
  'titanium': {
    response: `## Material Batch Traceability

**Batch ID:** TI-2024-0892
**Material:** Ti-6Al-4V Titanium Alloy

### Supply Chain Lineage

\`\`\`
TIMET Titanium (Supplier)
    ↓ Mill Certificate #MC-2024-0892
    ↓ Shipped: 2024-01-05
Phoenix Plant (Receiving)
    ↓ Incoming Inspection: PASSED
    ↓ QC Report #QC-PHX-2024-0147
Production Line (CNC-007, CNC-012)
    ↓ Work Orders: WO-2024-1847, WO-2024-1848
    ↓ 320 units produced
Boeing 787 Landing Gear (End Use)
    ↓ FAA-8110-3 Form attached
    ↓ Certificate of Conformance issued
\`\`\`

### Certifications
- ✓ FAA-8110-3 Form (Airworthiness)
- ✓ Mill Test Certificate
- ✓ Certificate of Conformance
- ✓ AS9100D Quality Audit

### Compliance Status
**FAA Audit Ready** - All documentation complete`,
    agents: ['Governance Agent'],
  },
  'maintenance': {
    response: `## Upcoming Maintenance Schedule

### Critical (Next 7 Days)

| Equipment | Issue | Priority | Scheduled |
|-----------|-------|----------|-----------|
| PHX-CNC-007 | Bearing replacement | 🔴 URGENT | Pending |
| SHA-CNC-015 | Spindle calibration | 🟡 High | Jan 28 |
| MUC-LTH-003 | Coolant system flush | 🟡 High | Jan 29 |

### Preventive (Next 30 Days)

| Equipment | Service Type | Due Date |
|-----------|--------------|----------|
| DET-ASM-005 | Annual inspection | Feb 12 |
| TYO-CNC-001 | Tool replacement | Feb 15 |
| ATL-GRD-002 | Calibration | Feb 18 |
| MAN-LTH-008 | Oil change | Feb 22 |

### Predicted Failures (ML Model)
Based on sensor patterns, these machines may need attention:
1. **SHA-CNC-001** - 25% failure probability (30 days)
2. **MAN-GRD-005** - 18% failure probability (45 days)`,
    agents: ['Maintenance Agent', 'Sensor Agent'],
  },
};

export function ChatInterface() {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: '1',
      role: 'system',
      content:
        'Welcome to Titan 5.0 AI Assistant. I can help you monitor equipment health, track inventory, manage orders, and ensure compliance. What would you like to know?',
      timestamp: new Date().toISOString(),
    },
  ]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [activeAgents, setActiveAgents] = useState<string[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim() || isLoading) return;

    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: input,
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    // Determine which mock response to use
    const lowerInput = input.toLowerCase();
    let mockKey = 'maintenance';
    if (lowerInput.includes('phx-cnc-007') || lowerInput.includes('health') || lowerInput.includes('cnc-007')) {
      mockKey = 'phx-cnc-007';
    } else if (lowerInput.includes('stock') || lowerInput.includes('sku') || lowerInput.includes('inventory')) {
      mockKey = 'stock';
    } else if (lowerInput.includes('phoenix') || lowerInput.includes('facility')) {
      mockKey = 'phoenix';
    } else if (lowerInput.includes('titanium') || lowerInput.includes('batch') || lowerInput.includes('trace')) {
      mockKey = 'titanium';
    }

    const mockData = MOCK_RESPONSES[mockKey];
    setActiveAgents(mockData.agents);

    // Simulate agent processing
    await new Promise((r) => setTimeout(r, 1500));

    const assistantMessage: ChatMessage = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: mockData.response,
      timestamp: new Date().toISOString(),
      agentActions: mockData.agents.map((agent) => ({
        agent,
        tool: 'analyze',
        status: 'complete' as const,
      })),
    };

    setMessages((prev) => [...prev, assistantMessage]);
    setIsLoading(false);
    setActiveAgents([]);
  };

  return (
    <div className="h-[calc(100vh-8rem)] flex flex-col fade-in">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <MessageSquare className="text-ember" />
            AI Manufacturing Assistant
          </h2>
          <p className="text-slate mt-1">Natural language interface to Titan 5.0 platform</p>
        </div>
        <div className="flex items-center gap-2 px-3 py-1.5 bg-healthy/10 border border-healthy/30 rounded-lg">
          <Sparkles size={14} className="text-healthy" />
          <span className="text-xs font-mono text-healthy">7 Agents Online</span>
        </div>
      </div>

      <div className="flex-1 flex gap-6 min-h-0">
        {/* Chat Area */}
        <div className="flex-1 flex flex-col panel stagger scale-in stagger-1">
          {/* Messages */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {messages.map((msg) => (
              <MessageBubble key={msg.id} message={msg} />
            ))}
            {isLoading && (
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-lg bg-ember/20 flex items-center justify-center">
                  <Bot size={16} className="text-ember" />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-2">
                    <Loader2 size={14} className="animate-spin text-ember" />
                    <span className="text-sm text-slate">Processing with agents...</span>
                  </div>
                  <div className="flex gap-2">
                    {activeAgents.map((agent) => (
                      <span
                        key={agent}
                        className="px-2 py-1 bg-ember/10 border border-ember/30 rounded text-xs text-ember"
                      >
                        {agent}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Input */}
          <div className="p-4 border-t border-iron">
            <div className="flex gap-3">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSend()}
                placeholder="Ask about equipment, inventory, orders, or compliance..."
                className="input-field flex-1"
                disabled={isLoading}
              />
              <button
                onClick={handleSend}
                disabled={!input.trim() || isLoading}
                className="btn-primary px-4 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Send size={18} />
              </button>
            </div>
          </div>
        </div>

        {/* Sidebar - Suggestions & Agents */}
        <div className="w-80 space-y-4 flex-shrink-0">
          {/* Suggested Prompts */}
          <div className="panel stagger slide-in-left stagger-2">
            <div className="panel-header">
              <Sparkles size={16} />
              Suggested Queries
            </div>
            <div className="p-3 space-y-2">
              {SUGGESTED_PROMPTS.map((prompt, i) => (
                <button
                  key={i}
                  onClick={() => setInput(prompt)}
                  className="w-full text-left p-3 bg-steel hover:bg-iron rounded-lg text-sm text-slate hover:text-white transition-colors"
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>

          {/* Active Agents */}
          <div className="panel stagger slide-in-left stagger-3">
            <div className="panel-header">
              <Cpu size={16} />
              MCP Agents
            </div>
            <div className="p-3 space-y-2">
              {[
                { name: 'Sensor Agent', port: 8081, active: activeAgents.includes('Sensor Agent') },
                { name: 'Maintenance Agent', port: 8082, active: activeAgents.includes('Maintenance Agent') },
                { name: 'Inventory Agent', port: 8083, active: activeAgents.includes('Inventory Agent') },
                { name: 'Logistics Agent', port: 8084, active: activeAgents.includes('Logistics Agent') },
                { name: 'Order Agent', port: 8085, active: activeAgents.includes('Order Agent') },
                { name: 'Communications Agent', port: 8086, active: activeAgents.includes('Communications Agent') },
                { name: 'Governance Agent', port: 8087, active: activeAgents.includes('Governance Agent') },
              ].map((agent) => (
                <div
                  key={agent.name}
                  className={`flex items-center justify-between p-2 rounded-lg transition-all ${
                    agent.active ? 'bg-ember/10 border border-ember/30' : 'bg-steel'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <div
                      className={`status-dot ${
                        agent.active ? 'status-dot-warning' : 'status-dot-healthy'
                      }`}
                    />
                    <span className={`text-sm ${agent.active ? 'text-ember' : 'text-slate'}`}>
                      {agent.name}
                    </span>
                  </div>
                  <span className="text-xs font-mono text-ash">:{agent.port}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function MessageBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user';
  const isSystem = message.role === 'system';

  return (
    <div className={`flex items-start gap-3 ${isUser ? 'flex-row-reverse' : ''}`}>
      <div
        className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${
          isUser ? 'bg-info/20' : isSystem ? 'bg-steel' : 'bg-ember/20'
        }`}
      >
        {isUser ? (
          <User size={16} className="text-info" />
        ) : (
          <Bot size={16} className={isSystem ? 'text-slate' : 'text-ember'} />
        )}
      </div>
      <div className={`flex-1 ${isUser ? 'text-right' : ''}`}>
        <div
          className={`inline-block p-4 rounded-lg max-w-[90%] text-left ${
            isUser
              ? 'bg-info/20 border border-info/30'
              : isSystem
              ? 'bg-steel border border-iron'
              : 'bg-graphite border border-iron'
          }`}
        >
          <div
            className="text-sm prose prose-invert prose-sm max-w-none
            prose-headings:font-display prose-headings:text-ember prose-headings:font-bold
            prose-h2:text-lg prose-h2:mb-3 prose-h2:mt-0
            prose-h3:text-base prose-h3:mb-2
            prose-p:text-slate prose-p:my-2
            prose-table:text-xs prose-th:text-ember prose-th:font-mono
            prose-td:text-slate prose-td:py-1
            prose-code:text-ember prose-code:bg-carbon prose-code:px-1 prose-code:rounded
            prose-pre:bg-carbon prose-pre:border prose-pre:border-iron
            prose-li:text-slate prose-li:my-0.5
            prose-strong:text-white"
            dangerouslySetInnerHTML={{ __html: formatMarkdown(message.content) }}
          />
        </div>
        {message.agentActions && message.agentActions.length > 0 && (
          <div className="mt-2 flex gap-2 flex-wrap">
            {message.agentActions.map((action, i) => (
              <span
                key={i}
                className="px-2 py-1 bg-ember/10 border border-ember/30 rounded text-xs text-ember"
              >
                {action.agent}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// Simple markdown to HTML converter
function formatMarkdown(text: string): string {
  return text
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/```[\s\S]*?```/g, (match) => {
      const code = match.replace(/```\w*\n?/g, '');
      return `<pre><code>${code}</code></pre>`;
    })
    .replace(/^\|(.+)\|$/gm, (_, content) => {
      const cells = content.split('|').map((c: string) => c.trim());
      const isHeader = cells.some((c: string) => c.includes('---'));
      if (isHeader) return '';
      const cellTag = content.includes('---') ? 'th' : 'td';
      return `<tr>${cells.map((c: string) => `<${cellTag}>${c}</${cellTag}>`).join('')}</tr>`;
    })
    .replace(/(<tr>.*<\/tr>\n?)+/g, (match) => `<table>${match}</table>`)
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>\n?)+/g, (match) => `<ul>${match}</ul>`)
    .replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br />');
}
