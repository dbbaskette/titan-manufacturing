// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Demo Scenarios
// ═══════════════════════════════════════════════════════════════════════════

import { useState } from 'react';
import {
  Play,
  AlertTriangle,
  Plane,
  Shield,
  Package,
  ArrowRight,
  CheckCircle,
  Loader2,
  Cpu,
} from 'lucide-react';

interface Scenario {
  id: string;
  name: string;
  description: string;
  icon: React.ElementType;
  color: string;
  category: string;
  agents: string[];
  steps: string[];
  prompt: string;
}

const SCENARIOS: Scenario[] = [
  {
    id: 'phoenix-incident',
    name: 'Phoenix Incident',
    description:
      'Detect bearing failure in PHX-CNC-007 before catastrophic failure. This scenario demonstrates predictive maintenance preventing $12M in unplanned downtime.',
    icon: AlertTriangle,
    color: 'critical',
    category: 'Predictive Maintenance',
    agents: ['Sensor Agent', 'Maintenance Agent', 'Inventory Agent'],
    steps: [
      'Sensor Agent detects vibration anomaly (4.2 mm/s)',
      'Maintenance Agent predicts 73% failure probability',
      'Maintenance Agent estimates 48-hour RUL',
      'Inventory Agent locates replacement bearing (SKU-BRG-7420)',
      'Maintenance Agent schedules emergency replacement',
    ],
    prompt: 'Check the health status of PHX-CNC-007 and recommend maintenance actions',
  },
  {
    id: 'boeing-expedite',
    name: 'Boeing Expedite',
    description:
      'Rush order for 500 turbine blade blanks with contract validation and split shipment across facilities. Demonstrates multi-agent order fulfillment.',
    icon: Plane,
    color: 'info',
    category: 'Order Fulfillment',
    agents: ['Order Agent', 'Inventory Agent', 'Logistics Agent', 'Communications Agent'],
    steps: [
      'Order Agent validates Boeing contract terms',
      'Order Agent checks aerospace compliance requirements',
      'Inventory Agent finds stock: Phoenix 320 + Munich 400',
      'Logistics Agent plans split air freight shipment',
      'Communications Agent confirms ETA to Boeing',
    ],
    prompt: 'Boeing needs 500 turbine blade blanks ASAP - order TM-2024-45892. Validate and fulfill.',
  },
  {
    id: 'faa-audit',
    name: 'FAA Audit',
    description:
      'Trace titanium batch TI-2024-0892 for Boeing 787 landing gear regulatory compliance. Demonstrates data lineage and governance capabilities.',
    icon: Shield,
    color: 'healthy',
    category: 'Data Governance',
    agents: ['Governance Agent'],
    steps: [
      'Governance Agent retrieves batch TI-2024-0892 metadata',
      'Traces upstream lineage to TIMET supplier',
      'Retrieves Mill Certificate and CoC documents',
      'Validates FAA-8110-3 Form availability',
      'Generates comprehensive compliance report',
    ],
    prompt: 'Trace titanium batch TI-2024-0892 used in Boeing 787 landing gear for FAA audit',
  },
  {
    id: 'supply-crisis',
    name: 'Supply Chain Crisis',
    description:
      'Find alternative suppliers when primary source delays shipment. Demonstrates supply chain resilience with semantic search across global inventory.',
    icon: Package,
    color: 'warning',
    category: 'Supply Chain',
    agents: ['Inventory Agent', 'Logistics Agent'],
    steps: [
      'Inventory Agent receives stockout alert for SKU-BRG-7420',
      'Searches global inventory using pgvector semantic search',
      'Finds alternative: Timken (7-day lead) vs SKF (14-day)',
      'Calculates EOQ and safety stock requirements',
      'Logistics Agent estimates expedited shipping costs',
    ],
    prompt: 'Our NipponBearing supplier has delayed shipment. Find alternative suppliers for SKU-BRG-7420',
  },
];

export function DemoScenarios() {
  const [selectedScenario, setSelectedScenario] = useState<Scenario | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [currentStep, setCurrentStep] = useState(-1);
  const [completedSteps, setCompletedSteps] = useState<number[]>([]);

  const runScenario = async (scenario: Scenario) => {
    setSelectedScenario(scenario);
    setIsRunning(true);
    setCurrentStep(-1);
    setCompletedSteps([]);

    for (let i = 0; i < scenario.steps.length; i++) {
      setCurrentStep(i);
      await new Promise((r) => setTimeout(r, 1500));
      setCompletedSteps((prev) => [...prev, i]);
    }

    setCurrentStep(-1);
    setIsRunning(false);
  };

  const colorClasses = {
    critical: 'text-critical bg-critical/10 border-critical/30',
    info: 'text-info bg-info/10 border-info/30',
    healthy: 'text-healthy bg-healthy/10 border-healthy/30',
    warning: 'text-warning bg-warning/10 border-warning/30',
  };

  return (
    <div className="space-y-6 fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <Play className="text-ember" />
            Demo Scenarios
          </h2>
          <p className="text-slate mt-1">Pre-built workflows showcasing multi-agent coordination</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        {/* Scenario Cards */}
        <div className="space-y-4">
          {SCENARIOS.map((scenario, index) => {
            const Icon = scenario.icon;
            const isSelected = selectedScenario?.id === scenario.id;

            return (
              <div
                key={scenario.id}
                className={`panel card-interactive cursor-pointer stagger slide-in-left stagger-${index + 1} ${
                  isSelected ? 'border-ember' : ''
                }`}
                onClick={() => !isRunning && setSelectedScenario(scenario)}
              >
                <div className="p-5">
                  <div className="flex items-start gap-4">
                    <div
                      className={`p-3 rounded-lg border ${
                        colorClasses[scenario.color as keyof typeof colorClasses]
                      }`}
                    >
                      <Icon size={24} />
                    </div>
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <h3 className="font-display font-bold text-white">{scenario.name}</h3>
                        <span className="px-2 py-0.5 bg-steel rounded text-[10px] text-slate uppercase">
                          {scenario.category}
                        </span>
                      </div>
                      <p className="text-sm text-slate line-clamp-2">{scenario.description}</p>
                      <div className="flex gap-2 mt-3">
                        {scenario.agents.map((agent) => (
                          <span
                            key={agent}
                            className="px-2 py-1 bg-steel rounded text-xs text-ash"
                          >
                            {agent}
                          </span>
                        ))}
                      </div>
                    </div>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        runScenario(scenario);
                      }}
                      disabled={isRunning}
                      className="btn-primary px-4 py-2 disabled:opacity-50"
                    >
                      <Play size={14} />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Scenario Detail / Execution View */}
        <div className="panel stagger scale-in stagger-5">
          {selectedScenario ? (
            <>
              <div className="panel-header">
                <selectedScenario.icon size={16} />
                {selectedScenario.name}
                {isRunning && (
                  <span className="ml-auto flex items-center gap-2 text-xs text-ember font-normal">
                    <Loader2 size={12} className="animate-spin" />
                    Running
                  </span>
                )}
              </div>

              <div className="p-5">
                {/* Description */}
                <p className="text-sm text-slate mb-6">{selectedScenario.description}</p>

                {/* Agents Involved */}
                <div className="mb-6">
                  <h4 className="text-xs text-ash uppercase tracking-wider mb-3">Agents Involved</h4>
                  <div className="flex gap-3">
                    {selectedScenario.agents.map((agent, i) => (
                      <div key={agent} className="flex items-center gap-2">
                        <div className="w-8 h-8 rounded-lg bg-ember/10 flex items-center justify-center">
                          <Cpu size={14} className="text-ember" />
                        </div>
                        <span className="text-sm text-white">{agent}</span>
                        {i < selectedScenario.agents.length - 1 && (
                          <ArrowRight size={14} className="text-iron ml-2" />
                        )}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Execution Steps */}
                <div>
                  <h4 className="text-xs text-ash uppercase tracking-wider mb-3">Execution Flow</h4>
                  <div className="space-y-3">
                    {selectedScenario.steps.map((step, i) => {
                      const isComplete = completedSteps.includes(i);
                      const isCurrent = currentStep === i;

                      return (
                        <div
                          key={i}
                          className={`flex items-center gap-3 p-3 rounded-lg transition-all ${
                            isCurrent
                              ? 'bg-ember/10 border border-ember/30'
                              : isComplete
                              ? 'bg-healthy/10 border border-healthy/30'
                              : 'bg-steel border border-transparent'
                          }`}
                        >
                          <div
                            className={`w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 ${
                              isComplete
                                ? 'bg-healthy text-white'
                                : isCurrent
                                ? 'bg-ember text-white'
                                : 'bg-iron text-slate'
                            }`}
                          >
                            {isComplete ? (
                              <CheckCircle size={14} />
                            ) : isCurrent ? (
                              <Loader2 size={14} className="animate-spin" />
                            ) : (
                              <span className="text-xs font-mono">{i + 1}</span>
                            )}
                          </div>
                          <span
                            className={`text-sm ${
                              isComplete
                                ? 'text-healthy'
                                : isCurrent
                                ? 'text-ember'
                                : 'text-slate'
                            }`}
                          >
                            {step}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Prompt */}
                <div className="mt-6 p-4 bg-carbon rounded-lg border border-iron">
                  <h4 className="text-xs text-ash uppercase tracking-wider mb-2">Natural Language Prompt</h4>
                  <p className="font-mono text-sm text-ember">"{selectedScenario.prompt}"</p>
                </div>

                {/* Action Button */}
                <div className="mt-6">
                  <button
                    onClick={() => runScenario(selectedScenario)}
                    disabled={isRunning}
                    className="btn-primary w-full flex items-center justify-center gap-2 disabled:opacity-50"
                  >
                    {isRunning ? (
                      <>
                        <Loader2 size={16} className="animate-spin" />
                        Running Scenario...
                      </>
                    ) : completedSteps.length > 0 ? (
                      <>
                        <Play size={16} />
                        Run Again
                      </>
                    ) : (
                      <>
                        <Play size={16} />
                        Run Scenario
                      </>
                    )}
                  </button>
                </div>
              </div>
            </>
          ) : (
            <div className="h-full flex flex-col items-center justify-center p-12 text-center">
              <Play size={48} className="text-iron mb-4" />
              <h3 className="font-display font-bold text-white mb-2">Select a Scenario</h3>
              <p className="text-sm text-slate max-w-sm">
                Choose a demo scenario to see how Titan 5.0's AI agents coordinate to solve
                manufacturing challenges
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
