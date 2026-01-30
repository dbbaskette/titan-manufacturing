// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Recommendations Dashboard
// Agent-generated maintenance recommendations and automated actions
// ═══════════════════════════════════════════════════════════════════════════

import React, { useState, useEffect, useCallback } from 'react';
import {
  AlertTriangle,
  CheckCircle,
  XCircle,
  Clock,
  Wrench,
  Zap,
  Package,
  Bell,
  ChevronRight,
  RefreshCw,
  Ban,
} from 'lucide-react';
import { titanApi } from '../api/titanApi';
import type { Recommendation, AutomatedAction, ReservedPart } from '../api/titanApi';

// Facility code → display name
const FACILITY_NAMES: Record<string, string> = {
  PHX: 'Phoenix', DET: 'Detroit', ATL: 'Atlanta', DAL: 'Dallas',
  MUC: 'Munich', LYN: 'Lyon', MAN: 'Manchester', SHA: 'Shanghai',
  TYO: 'Tokyo', SEO: 'Seoul', SYD: 'Sydney', MEX: 'Mexico City',
};

function parsePartsJson(parts: ReservedPart[] | string | null): ReservedPart[] {
  if (!parts) return [];
  if (typeof parts === 'string') {
    try {
      return JSON.parse(parts);
    } catch {
      return [];
    }
  }
  return parts;
}

function formatTimeAgo(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  return `${diffDays}d ago`;
}

function formatExpiresIn(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = date.getTime() - now.getTime();
  if (diffMs < 0) return 'expired';
  const diffHours = Math.floor(diffMs / 3600000);
  if (diffHours < 1) return '<1h left';
  if (diffHours < 24) return `${diffHours}h left`;
  return `${Math.floor(diffHours / 24)}d left`;
}

type TabType = 'pending' | 'approved' | 'denied' | 'automated';

export function Recommendations() {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [resolvedRecs, setResolvedRecs] = useState<Recommendation[]>([]);
  const [automatedActions, setAutomatedActions] = useState<AutomatedAction[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedRec, setSelectedRec] = useState<Recommendation | null>(null);
  const [selectedAction, setSelectedAction] = useState<AutomatedAction | null>(null);
  const [selectedResolved, setSelectedResolved] = useState<Recommendation | null>(null);
  const [activeTab, setActiveTab] = useState<TabType>('pending');
  const [processing, setProcessing] = useState<string | null>(null);
  const [approvingRec, setApprovingRec] = useState<Recommendation | null>(null);
  const [approveStage, setApproveStage] = useState(0);

  // IDs being processed — prevents fetchData from re-adding them to the pending list
  const processingIdsRef = React.useRef<Set<string>>(new Set());

  const selectedRecRef = React.useRef(selectedRec);
  selectedRecRef.current = selectedRec;
  const activeTabRef = React.useRef(activeTab);
  activeTabRef.current = activeTab;

  const fetchData = useCallback(async () => {
    try {
      const [recs, resolved, actions] = await Promise.all([
        titanApi.getRecommendations(),
        titanApi.getResolvedRecommendations(50),
        titanApi.getAutomatedActions(20),
      ]);
      // Filter out any items currently being processed (approve/deny in flight)
      const filteredRecs = recs.filter((r) => !processingIdsRef.current.has(r.recommendation_id));
      setRecommendations(filteredRecs);
      setResolvedRecs(resolved);
      setAutomatedActions(actions);

      // Select first item if none selected
      if (filteredRecs.length > 0 && !selectedRecRef.current && activeTabRef.current === 'pending') {
        setSelectedRec(filteredRecs[0]);
      }
    } catch (err) {
      console.error('Failed to fetch recommendations:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, [fetchData]);

  // Derived lists from resolved
  const approvedRecs = resolvedRecs.filter((r) => r.status === 'APPROVED' || r.status === 'COMPLETED' || r.status === 'SUPERSEDED');
  const deniedRecs = resolvedRecs.filter((r) => r.status === 'DISMISSED');

  const handleApprove = async (rec: Recommendation) => {
    setProcessing(rec.recommendation_id);
    setApprovingRec(rec);
    setApproveStage(0);
    processingIdsRef.current.add(rec.recommendation_id);
    // Immediately remove from pending list
    setRecommendations((prev) => {
      const remaining = prev.filter((r) => r.recommendation_id !== rec.recommendation_id);
      if (selectedRecRef.current?.recommendation_id === rec.recommendation_id) {
        setSelectedRec(null);
      }
      return remaining;
    });

    // Animate stages on a fixed timeline (runs regardless of API speed)
    const stageSequence = async () => {
      await new Promise((r) => setTimeout(r, 1500));
      setApproveStage(1);
      await new Promise((r) => setTimeout(r, 2000));
      setApproveStage(2);
      await new Promise((r) => setTimeout(r, 2000));
      setApproveStage(3);
      await new Promise((r) => setTimeout(r, 2000));
    };

    // Run API call and stage animation in parallel; wait for BOTH
    const apiCall = titanApi.approveRecommendation(rec.recommendation_id).catch((err) => {
      console.error('Failed to approve recommendation:', err);
    });

    await Promise.all([apiCall, stageSequence()]);

    // Show completion stage
    setApproveStage(4);
    await new Promise((r) => setTimeout(r, 2500));

    processingIdsRef.current.delete(rec.recommendation_id);
    setProcessing(null);
    setApprovingRec(null);
    setApproveStage(0);
  };

  const handleDeny = async (rec: Recommendation) => {
    setProcessing(rec.recommendation_id);
    processingIdsRef.current.add(rec.recommendation_id);
    // Immediately remove from pending list
    setRecommendations((prev) => {
      const remaining = prev.filter((r) => r.recommendation_id !== rec.recommendation_id);
      if (selectedRecRef.current?.recommendation_id === rec.recommendation_id) {
        setSelectedRec(remaining.length > 0 ? remaining[0] : null);
      }
      return remaining;
    });
    try {
      await titanApi.dismissRecommendation(rec.recommendation_id);
    } catch (err) {
      console.error('Failed to deny recommendation:', err);
    } finally {
      processingIdsRef.current.delete(rec.recommendation_id);
      setProcessing(null);
    }
  };

  const switchTab = (tab: TabType) => {
    setActiveTab(tab);
    setSelectedRec(null);
    setSelectedResolved(null);
    setSelectedAction(null);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-slate">Loading recommendations...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6 fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wide text-white flex items-center gap-3">
            <Bell className="text-ember" />
            Agent Recommendations
          </h2>
          <p className="text-zinc-400 mt-1">
            AI-generated maintenance recommendations and automated responses
          </p>
        </div>
        <button
          onClick={() => fetchData()}
          className="btn-secondary flex items-center gap-2"
        >
          <RefreshCw size={16} />
          Refresh
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-5 gap-4">
        <SummaryCard
          label="Pending"
          count={recommendations.length}
          icon={Clock}
          color="warning"
          onClick={() => switchTab('pending')}
          active={activeTab === 'pending'}
        />
        <SummaryCard
          label="Approved"
          count={approvedRecs.length}
          icon={CheckCircle}
          color="healthy"
          onClick={() => switchTab('approved')}
          active={activeTab === 'approved'}
        />
        <SummaryCard
          label="Denied"
          count={deniedRecs.length}
          icon={Ban}
          color="critical"
          onClick={() => switchTab('denied')}
          active={activeTab === 'denied'}
        />
        <SummaryCard
          label="Auto-Executed"
          count={automatedActions.length}
          icon={Zap}
          color="info"
          onClick={() => switchTab('automated')}
          active={activeTab === 'automated'}
        />
        <SummaryCard
          label="Parts Reserved"
          count={
            recommendations.reduce((sum, r) => sum + parsePartsJson(r.recommended_parts).length, 0) +
            automatedActions.reduce((sum, a) => sum + parsePartsJson(a.parts_reserved).length, 0)
          }
          icon={Package}
          color="healthy"
        />
      </div>

      {/* Main Content */}
      <div className="grid grid-cols-3 gap-6">
        {/* List Panel */}
        <div className="col-span-1 panel">
          <div className="panel-header justify-between">
            <div className="flex items-center gap-2">
              {activeTab === 'pending' && <Clock size={16} />}
              {activeTab === 'approved' && <CheckCircle size={16} />}
              {activeTab === 'denied' && <Ban size={16} />}
              {activeTab === 'automated' && <Zap size={16} />}
              {activeTab === 'pending' && 'Pending'}
              {activeTab === 'approved' && 'Approved'}
              {activeTab === 'denied' && 'Denied'}
              {activeTab === 'automated' && 'Auto-Executed'}
            </div>
            <span className="text-xs text-slate font-normal">
              {activeTab === 'pending' && `${recommendations.length} items`}
              {activeTab === 'approved' && `${approvedRecs.length} items`}
              {activeTab === 'denied' && `${deniedRecs.length} items`}
              {activeTab === 'automated' && `${automatedActions.length} items`}
            </span>
          </div>

          {/* Tab Toggle */}
          <div className="p-3 border-b border-iron">
            <div className="flex gap-2">
              <button
                onClick={() => switchTab('pending')}
                className={`flex-1 px-3 py-2 text-xs font-medium rounded-lg transition-all ${
                  activeTab === 'pending'
                    ? 'bg-warning/20 text-warning border border-warning/30'
                    : 'bg-steel text-slate hover:text-white'
                }`}
              >
                Pending ({recommendations.length})
              </button>
              <button
                onClick={() => switchTab('approved')}
                className={`flex-1 px-3 py-2 text-xs font-medium rounded-lg transition-all ${
                  activeTab === 'approved'
                    ? 'bg-healthy/20 text-healthy border border-healthy/30'
                    : 'bg-steel text-slate hover:text-white'
                }`}
              >
                Approved ({approvedRecs.length})
              </button>
              <button
                onClick={() => switchTab('denied')}
                className={`flex-1 px-3 py-2 text-xs font-medium rounded-lg transition-all ${
                  activeTab === 'denied'
                    ? 'bg-critical/20 text-critical border border-critical/30'
                    : 'bg-steel text-slate hover:text-white'
                }`}
              >
                Denied ({deniedRecs.length})
              </button>
              <button
                onClick={() => switchTab('automated')}
                className={`flex-1 px-3 py-2 text-xs font-medium rounded-lg transition-all ${
                  activeTab === 'automated'
                    ? 'bg-info/20 text-info border border-info/30'
                    : 'bg-steel text-slate hover:text-white'
                }`}
              >
                Auto ({automatedActions.length})
              </button>
            </div>
          </div>

          {/* List */}
          <div className="max-h-[500px] overflow-y-auto">
            {activeTab === 'pending' ? (
              recommendations.length === 0 ? (
                <div className="p-8 text-center text-slate text-sm">
                  No pending recommendations
                </div>
              ) : (
                recommendations.map((rec) => (
                  <RecommendationItem
                    key={rec.recommendation_id}
                    rec={rec}
                    selected={selectedRec?.recommendation_id === rec.recommendation_id}
                    onClick={() => {
                      setSelectedRec(rec);
                      setSelectedAction(null);
                      setSelectedResolved(null);
                    }}
                  />
                ))
              )
            ) : activeTab === 'approved' ? (
              approvedRecs.length === 0 ? (
                <div className="p-8 text-center text-slate text-sm">
                  No approved recommendations
                </div>
              ) : (
                approvedRecs.map((rec) => (
                  <ResolvedItem
                    key={rec.recommendation_id}
                    rec={rec}
                    selected={selectedResolved?.recommendation_id === rec.recommendation_id}
                    onClick={() => {
                      setSelectedResolved(rec);
                      setSelectedRec(null);
                      setSelectedAction(null);
                    }}
                  />
                ))
              )
            ) : activeTab === 'denied' ? (
              deniedRecs.length === 0 ? (
                <div className="p-8 text-center text-slate text-sm">
                  No denied recommendations
                </div>
              ) : (
                deniedRecs.map((rec) => (
                  <ResolvedItem
                    key={rec.recommendation_id}
                    rec={rec}
                    selected={selectedResolved?.recommendation_id === rec.recommendation_id}
                    onClick={() => {
                      setSelectedResolved(rec);
                      setSelectedRec(null);
                      setSelectedAction(null);
                    }}
                  />
                ))
              )
            ) : automatedActions.length === 0 ? (
              <div className="p-8 text-center text-slate text-sm">
                No automated actions recorded
              </div>
            ) : (
              automatedActions.map((action) => (
                <AutomatedActionItem
                  key={action.action_id}
                  action={action}
                  selected={selectedAction?.action_id === action.action_id}
                  onClick={() => {
                    setSelectedAction(action);
                    setSelectedRec(null);
                    setSelectedResolved(null);
                  }}
                />
              ))
            )}
          </div>
        </div>

        {/* Details Panel */}
        <div className="col-span-2 space-y-4">
          {approvingRec ? (
            <ApprovalProcessingPanel rec={approvingRec} stage={approveStage} />
          ) : activeTab === 'pending' && selectedRec ? (
            <RecommendationDetails
              rec={selectedRec}
              onApprove={() => handleApprove(selectedRec)}
              onDeny={() => handleDeny(selectedRec)}
              processing={processing === selectedRec.recommendation_id}
            />
          ) : (activeTab === 'approved' || activeTab === 'denied') && selectedResolved ? (
            <ResolvedDetails rec={selectedResolved} />
          ) : activeTab === 'automated' && selectedAction ? (
            <AutomatedActionDetails action={selectedAction} />
          ) : (
            <div className="panel p-12 text-center">
              <Bell size={48} className="mx-auto text-iron mb-4" />
              <p className="text-slate">Select an item to view details</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Subcomponents ───────────────────────────────────────────────────────────

function SummaryCard({
  label,
  count,
  icon: Icon,
  color,
  onClick,
  active,
}: {
  label: string;
  count: number;
  icon: React.ElementType;
  color: 'healthy' | 'warning' | 'critical' | 'info';
  onClick?: () => void;
  active?: boolean;
}) {
  const colors = {
    healthy: 'bg-healthy/10 border-healthy/30 text-healthy',
    warning: 'bg-warning/10 border-warning/30 text-warning',
    critical: 'bg-critical/10 border-critical/30 text-critical',
    info: 'bg-info/10 border-info/30 text-info',
  };

  const Component = onClick ? 'button' : 'div';

  return (
    <Component
      onClick={onClick}
      className={`panel ${onClick ? 'card-interactive' : ''} p-4 text-left transition-all ${
        active ? colors[color] : ''
      }`}
    >
      <div className="flex items-center justify-between">
        <Icon size={20} className={active ? '' : 'text-slate'} />
        <span className="font-display text-2xl font-bold">{count}</span>
      </div>
      <p className="text-sm text-slate mt-2">{label}</p>
    </Component>
  );
}

function RecommendationItem({
  rec,
  selected,
  onClick,
}: {
  rec: Recommendation;
  selected: boolean;
  onClick: () => void;
}) {
  const riskColor = rec.risk_level === 'CRITICAL' ? 'text-critical' : 'text-warning';
  const parts = parsePartsJson(rec.recommended_parts);

  return (
    <div
      onClick={onClick}
      className={`p-4 border-b border-iron cursor-pointer transition-all hover:bg-steel ${
        selected ? 'bg-steel border-l-2 border-l-warning' : ''
      }`}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="font-mono text-sm text-white">{rec.equipment_id}</span>
        <span className={`text-xs font-mono uppercase ${riskColor}`}>{rec.risk_level}</span>
      </div>
      <p className="text-xs text-zinc-300 truncate">{rec.probable_cause}</p>
      <div className="flex items-center justify-between mt-2">
        <span className="text-xs text-ash">{FACILITY_NAMES[rec.facility_id] || rec.facility_id}</span>
        <div className="flex items-center gap-2">
          {parts.length > 0 && (
            <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-info/20 text-info">
              {parts.length} parts
            </span>
          )}
          <span className="text-xs text-ash">{formatTimeAgo(rec.created_at)}</span>
        </div>
      </div>
    </div>
  );
}

function AutomatedActionItem({
  action,
  selected,
  onClick,
}: {
  action: AutomatedAction;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <div
      onClick={onClick}
      className={`p-4 border-b border-iron cursor-pointer transition-all hover:bg-steel ${
        selected ? 'bg-steel border-l-2 border-l-info' : ''
      }`}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="font-mono text-sm text-white">{action.equipment_id}</span>
        <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-critical/20 text-critical">
          AUTO-EXECUTED
        </span>
      </div>
      <p className="text-xs text-zinc-300 truncate">{action.probable_cause}</p>
      <div className="flex items-center justify-between mt-2">
        <span className="text-xs text-ash">
          WO: {action.work_order_id || '—'}
        </span>
        <div className="flex items-center gap-2">
          {action.notification_sent && (
            <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-healthy/20 text-healthy">
              notified
            </span>
          )}
          <span className="text-xs text-ash">{formatTimeAgo(action.executed_at)}</span>
        </div>
      </div>
    </div>
  );
}

function ResolvedItem({
  rec,
  selected,
  onClick,
}: {
  rec: Recommendation;
  selected: boolean;
  onClick: () => void;
}) {
  const statusConfig: Record<string, { label: string; color: string; bg: string }> = {
    APPROVED: { label: 'APPROVED', color: 'text-healthy', bg: 'bg-healthy/20' },
    COMPLETED: { label: 'COMPLETED', color: 'text-healthy', bg: 'bg-healthy/20' },
    DISMISSED: { label: 'DENIED', color: 'text-critical', bg: 'bg-critical/20' },
    SUPERSEDED: { label: 'SUPERSEDED', color: 'text-warning', bg: 'bg-warning/20' },
  };
  const cfg = statusConfig[rec.status] || statusConfig.DISMISSED;
  const borderColor = rec.status === 'DISMISSED' ? 'border-l-critical' : 'border-l-healthy';

  return (
    <div
      onClick={onClick}
      className={`p-4 border-b border-iron cursor-pointer transition-all hover:bg-steel ${
        selected ? `bg-steel border-l-2 ${borderColor}` : ''
      }`}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="font-mono text-sm text-white">{rec.equipment_id}</span>
        <span className={`px-1.5 py-0.5 rounded text-[9px] font-mono uppercase ${cfg.bg} ${cfg.color}`}>
          {cfg.label}
        </span>
      </div>
      <p className="text-xs text-zinc-300 truncate">{rec.probable_cause}</p>
      <div className="flex items-center justify-between mt-2">
        <span className="text-xs text-ash">{FACILITY_NAMES[rec.facility_id] || rec.facility_id}</span>
        <div className="flex items-center gap-2">
          {rec.work_order_id && (
            <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-info/20 text-info">
              WO
            </span>
          )}
          <span className="text-xs text-ash">{formatTimeAgo(rec.approved_at || rec.created_at)}</span>
        </div>
      </div>
    </div>
  );
}

function ResolvedDetails({ rec }: { rec: Recommendation }) {
  const parts = parsePartsJson(rec.recommended_parts);
  const statusConfig: Record<string, { label: string; color: string; bg: string; border: string; icon: React.ElementType; message: string }> = {
    APPROVED: { label: 'APPROVED', color: 'text-healthy', bg: 'bg-healthy/10', border: 'border-healthy/30', icon: CheckCircle, message: 'Maintenance approved and scheduled' },
    COMPLETED: { label: 'COMPLETED', color: 'text-healthy', bg: 'bg-healthy/10', border: 'border-healthy/30', icon: CheckCircle, message: 'Maintenance completed successfully' },
    DISMISSED: { label: 'DENIED', color: 'text-critical', bg: 'bg-critical/10', border: 'border-critical/30', icon: Ban, message: 'Recommendation denied by operator' },
    SUPERSEDED: { label: 'SUPERSEDED', color: 'text-warning', bg: 'bg-warning/10', border: 'border-warning/30', icon: AlertTriangle, message: 'Superseded by CRITICAL alert — auto-response triggered' },
  };
  const cfg = statusConfig[rec.status] || statusConfig.DISMISSED;
  const StatusIcon = cfg.icon;

  return (
    <>
      {/* Header Card */}
      <div className="panel p-6">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h3 className="font-display text-xl font-bold text-white">{rec.equipment_id}</h3>
              <span className={`px-2 py-0.5 rounded text-xs font-mono uppercase border ${cfg.bg} ${cfg.border} ${cfg.color}`}>
                {cfg.label}
              </span>
            </div>
            <p className="text-zinc-300">{rec.probable_cause}</p>
            <p className="text-sm text-ash mt-1">
              {FACILITY_NAMES[rec.facility_id] || rec.facility_id} • Created {formatTimeAgo(rec.created_at)}
            </p>
          </div>
          <div className="text-right">
            <div className="text-3xl font-display font-bold text-slate">
              {Math.round(rec.failure_probability * 100)}%
            </div>
            <div className="text-xs text-ash">Failure Probability</div>
          </div>
        </div>

        {/* Status Banner */}
        <div className={`mt-4 ${cfg.bg} border ${cfg.border} rounded-lg p-4`}>
          <div className="flex items-center gap-3">
            <StatusIcon className={`${cfg.color} flex-shrink-0`} />
            <div>
              <p className={`font-semibold ${cfg.color}`}>{cfg.message}</p>
              {rec.approved_by && (
                <p className="text-sm text-zinc-300">
                  By {rec.approved_by} • {rec.approved_at ? formatTimeAgo(rec.approved_at) : ''}
                </p>
              )}
              {rec.notes && (
                <p className="text-sm text-zinc-400 mt-1">{rec.notes}</p>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Work Order */}
      {rec.work_order_id && (
        <div className="panel">
          <div className="panel-header">
            <Wrench size={16} />
            Work Order
          </div>
          <div className="p-4">
            <div className="flex items-center justify-between p-3 bg-steel rounded-lg">
              <div>
                <p className="text-sm text-white font-mono">{rec.work_order_id}</p>
                <p className="text-xs text-ash">Preventive Maintenance Scheduled</p>
              </div>
              <ChevronRight size={16} className="text-ash" />
            </div>
          </div>
        </div>
      )}

      {/* Recommended Action */}
      <div className="panel">
        <div className="panel-header">
          <Wrench size={16} />
          Recommended Action
        </div>
        <div className="p-4">
          <p className="text-white">{rec.recommended_action}</p>
          <div className="mt-3 flex items-center gap-2">
            <span className="text-xs text-ash">Estimated Cost:</span>
            <span className="font-mono text-info">${rec.estimated_cost?.toLocaleString() || '—'}</span>
          </div>
        </div>
      </div>

      {/* Reserved Parts */}
      {parts.length > 0 && (
        <div className="panel">
          <div className="panel-header">
            <Package size={16} />
            Parts
          </div>
          <div className="p-4 space-y-2">
            {parts.map((part, i) => (
              <div key={i} className="flex items-center justify-between p-3 bg-steel rounded-lg">
                <div>
                  <p className="text-sm text-white font-mono">{part.sku}</p>
                  <p className="text-xs text-ash">{part.description}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm text-white">Qty: {part.quantity}</p>
                  <p className="text-xs text-ash">${part.unitPrice?.toFixed(2) || '—'}/unit</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </>
  );
}

function RecommendationDetails({
  rec,
  onApprove,
  onDeny,
  processing,
}: {
  rec: Recommendation;
  onApprove: () => void;
  onDeny: () => void;
  processing: boolean;
}) {
  const parts = parsePartsJson(rec.recommended_parts);
  const riskColor = rec.risk_level === 'CRITICAL' ? 'text-critical' : 'text-warning';
  const riskBg = rec.risk_level === 'CRITICAL' ? 'bg-critical/10 border-critical/30' : 'bg-warning/10 border-warning/30';

  return (
    <>
      {/* Header Card */}
      <div className="panel p-6">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h3 className="font-display text-xl font-bold text-white">{rec.equipment_id}</h3>
              <span className={`px-2 py-0.5 rounded text-xs font-mono uppercase border ${riskBg} ${riskColor}`}>
                {rec.risk_level}
              </span>
              <span className="px-2 py-0.5 rounded text-xs font-mono uppercase border bg-warning/10 border-warning/30 text-warning">
                PENDING
              </span>
            </div>
            <p className="text-zinc-300">{rec.probable_cause}</p>
            <p className="text-sm text-ash mt-1">
              {FACILITY_NAMES[rec.facility_id] || rec.facility_id} • Created {formatTimeAgo(rec.created_at)}
            </p>
          </div>
          <div className="text-right">
            <div className="text-3xl font-display font-bold text-warning">
              {Math.round(rec.failure_probability * 100)}%
            </div>
            <div className="text-xs text-ash">Failure Probability</div>
          </div>
        </div>

        {/* Expiration Warning */}
        <div className="mt-4 flex items-center gap-2 text-xs text-ash">
          <Clock size={14} />
          Parts reservation expires {formatExpiresIn(rec.expires_at)}
        </div>
      </div>

      {/* Recommended Action */}
      <div className="panel">
        <div className="panel-header">
          <Wrench size={16} />
          Recommended Action
        </div>
        <div className="p-4">
          <p className="text-white">{rec.recommended_action}</p>
          <div className="mt-3 flex items-center gap-2">
            <span className="text-xs text-ash">Estimated Cost:</span>
            <span className="font-mono text-info">${rec.estimated_cost?.toLocaleString() || '—'}</span>
          </div>
        </div>
      </div>

      {/* Reserved Parts */}
      {parts.length > 0 && (
        <div className="panel">
          <div className="panel-header">
            <Package size={16} />
            Reserved Parts
          </div>
          <div className="p-4 space-y-2">
            {parts.map((part, i) => (
              <div key={i} className="flex items-center justify-between p-3 bg-steel rounded-lg">
                <div>
                  <p className="text-sm text-white font-mono">{part.sku}</p>
                  <p className="text-xs text-ash">{part.description}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm text-white">Qty: {part.quantity}</p>
                  <p className="text-xs text-ash">${part.unitPrice?.toFixed(2) || '—'}/unit</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Action Buttons */}
      <div className="panel p-4">
        <p className="text-xs text-ash mb-3 uppercase tracking-wider">Take Action</p>
        <div className="flex gap-3">
          <button
            onClick={onApprove}
            disabled={processing}
            className="flex-1 btn-primary flex items-center justify-center gap-2 py-3"
          >
            {processing ? (
              <RefreshCw size={18} className="animate-spin" />
            ) : (
              <CheckCircle size={18} />
            )}
            Approve
          </button>
          <button
            onClick={onDeny}
            disabled={processing}
            className="flex-1 btn-secondary flex items-center justify-center gap-2 py-3"
          >
            <Ban size={18} />
            Deny
          </button>
        </div>
      </div>
    </>
  );
}

function ApprovalProcessingPanel({ rec, stage }: { rec: Recommendation; stage: number }) {
  const steps = [
    { label: 'Initiating approval workflow', detail: 'Sending request to Titan orchestrator...' },
    { label: 'AI agent analyzing maintenance plan', detail: 'Embabel goal-driven agent evaluating work order feasibility...' },
    { label: 'Scheduling maintenance window', detail: `Creating work order for ${rec.equipment_id}...` },
    { label: 'Reserving parts & notifying team', detail: 'Coordinating with inventory and communications agents...' },
    { label: 'Approval complete', detail: 'Work order created and maintenance scheduled.' },
  ];

  return (
    <div className="panel p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-lg bg-info/20 border border-info/30 flex items-center justify-center">
          <Wrench className="text-info" size={20} />
        </div>
        <div>
          <h3 className="font-display text-lg font-bold text-white">Approving Maintenance</h3>
          <p className="text-sm text-ash">{rec.equipment_id} — {rec.probable_cause}</p>
        </div>
      </div>

      {/* Progress Steps */}
      <div className="space-y-3">
        {steps.map((step, i) => {
          const isActive = i === stage;
          const isDone = i < stage;
          const isPending = i > stage;

          return (
            <div
              key={i}
              className={`flex items-start gap-3 p-3 rounded-lg transition-all ${
                isActive ? 'bg-info/10 border border-info/30' : isDone ? 'bg-healthy/5 border border-healthy/20' : 'bg-steel/50 border border-iron'
              }`}
            >
              <div className="mt-0.5 flex-shrink-0">
                {isDone ? (
                  <CheckCircle size={18} className="text-healthy" />
                ) : isActive ? (
                  <RefreshCw size={18} className="text-info animate-spin" />
                ) : (
                  <Clock size={18} className="text-iron" />
                )}
              </div>
              <div>
                <p className={`text-sm font-medium ${isPending ? 'text-slate' : isDone ? 'text-zinc-300' : 'text-white'}`}>
                  {step.label}
                </p>
                {(isActive || isDone) && (
                  <p className={`text-xs mt-0.5 ${isDone ? 'text-ash' : 'text-info'}`}>
                    {step.detail}
                  </p>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Progress bar */}
      <div className="w-full bg-iron rounded-full h-1.5">
        <div
          className={`h-1.5 rounded-full transition-all duration-1000 ${stage >= 4 ? 'bg-healthy' : 'bg-info'}`}
          style={{ width: `${Math.min(100, (stage / 4) * 100)}%` }}
        />
      </div>
    </div>
  );
}

function AutomatedActionDetails({ action }: { action: AutomatedAction }) {
  const parts = parsePartsJson(action.parts_reserved);

  return (
    <>
      {/* Header Card */}
      <div className="panel p-6">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h3 className="font-display text-xl font-bold text-white">{action.equipment_id}</h3>
              <span className="px-2 py-0.5 rounded text-xs font-mono uppercase border bg-critical/10 border-critical/30 text-critical">
                CRITICAL
              </span>
              <span className="px-2 py-0.5 rounded text-xs font-mono uppercase border bg-healthy/10 border-healthy/30 text-healthy">
                AUTO-EXECUTED
              </span>
            </div>
            <p className="text-zinc-300">{action.probable_cause}</p>
            <p className="text-sm text-ash mt-1">
              {FACILITY_NAMES[action.facility_id] || action.facility_id} • Executed {formatTimeAgo(action.executed_at)}
            </p>
          </div>
          <div className="text-right">
            <div className="text-3xl font-display font-bold text-critical">
              {Math.round(action.failure_probability * 100)}%
            </div>
            <div className="text-xs text-ash">Failure Probability</div>
          </div>
        </div>

        {/* Success Banner */}
        <div className="mt-4 bg-healthy/10 border border-healthy/30 rounded-lg p-4">
          <div className="flex items-center gap-3">
            <Zap className="text-healthy flex-shrink-0" />
            <div>
              <p className="font-semibold text-healthy">Automated Response Complete</p>
              <p className="text-sm text-zinc-300">{action.execution_summary}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Work Order */}
      {action.work_order_id && (
        <div className="panel">
          <div className="panel-header">
            <Wrench size={16} />
            Work Order Created
          </div>
          <div className="p-4">
            <div className="flex items-center justify-between p-3 bg-steel rounded-lg">
              <div>
                <p className="text-sm text-white font-mono">{action.work_order_id}</p>
                <p className="text-xs text-ash">Emergency Maintenance Scheduled</p>
              </div>
              <ChevronRight size={16} className="text-ash" />
            </div>
          </div>
        </div>
      )}

      {/* Reserved Parts */}
      {parts.length > 0 && (
        <div className="panel">
          <div className="panel-header">
            <Package size={16} />
            Parts Reserved
          </div>
          <div className="p-4 space-y-2">
            {parts.map((part, i) => (
              <div key={i} className="flex items-center justify-between p-3 bg-steel rounded-lg">
                <div>
                  <p className="text-sm text-white font-mono">{part.sku}</p>
                  <p className="text-xs text-ash">{part.description}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm text-white">Qty: {part.quantity}</p>
                  <p className="text-xs text-ash">${part.unitPrice?.toFixed(2) || '—'}/unit</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Notification Status */}
      <div className="panel">
        <div className="panel-header">
          <Bell size={16} />
          Notifications
        </div>
        <div className="p-4">
          <div className="flex items-center gap-3 p-3 bg-steel rounded-lg">
            {action.notification_sent ? (
              <>
                <CheckCircle className="text-healthy" size={20} />
                <div>
                  <p className="text-sm text-white">Plant Manager Notified</p>
                  <p className="text-xs text-ash">Emergency alert sent via communications agent</p>
                </div>
              </>
            ) : (
              <>
                <XCircle className="text-warning" size={20} />
                <div>
                  <p className="text-sm text-white">Notification Pending</p>
                  <p className="text-xs text-ash">Manager notification in progress</p>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
