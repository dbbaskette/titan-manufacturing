// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — ML Pipeline Dashboard
// Real-time ML scoring pipeline: Greenplum → MADlib → PMML → GemFire
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Database, Brain, FileCode, Zap, RefreshCw, Upload, ArrowRight,
  AlertTriangle, CheckCircle, TrendingUp, TrendingDown, Minus,
  Activity, Server, Loader2, Terminal,
} from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell, PieChart, Pie } from 'recharts';
import {
  titanApi,
  type MLModelData,
  type MLPredictionsData,
  type MLGemFireStatus,
  type MLPmmlData,
  type MLStep,
} from '../api/titanApi';

// ── Pipeline Stage Card ─────────────────────────────────────────────────────

interface StageProps {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  stats: { label: string; value: string | number }[];
  status: 'active' | 'idle' | 'error' | 'loading';
  glowing?: boolean;
}

function PipelineStage({ icon, title, subtitle, stats, status, glowing }: StageProps) {
  const borderColor =
    status === 'active' ? 'border-healthy' :
    status === 'error' ? 'border-critical' :
    status === 'loading' ? 'border-ember' :
    'border-iron';

  return (
    <div
      className={`
        relative bg-graphite border ${borderColor} rounded-xl p-5
        transition-all duration-500 min-w-[200px] flex-1
        ${glowing ? 'shadow-[0_0_24px_rgba(255,107,0,0.3)]' : 'shadow-panel'}
      `}
    >
      {/* Corner brackets */}
      <div className="absolute top-0 left-0 w-3 h-3 border-t-2 border-l-2 border-ember/40 rounded-tl" />
      <div className="absolute top-0 right-0 w-3 h-3 border-t-2 border-r-2 border-ember/40 rounded-tr" />
      <div className="absolute bottom-0 left-0 w-3 h-3 border-b-2 border-l-2 border-ember/40 rounded-bl" />
      <div className="absolute bottom-0 right-0 w-3 h-3 border-b-2 border-r-2 border-ember/40 rounded-br" />

      <div className="flex items-center gap-3 mb-4">
        <div className={`
          w-10 h-10 rounded-lg flex items-center justify-center
          ${status === 'active' ? 'bg-healthy/10 text-healthy' :
            status === 'error' ? 'bg-critical/10 text-critical' :
            status === 'loading' ? 'bg-ember/10 text-ember' :
            'bg-iron text-slate'}
        `}>
          {status === 'loading' ? <Loader2 size={20} className="animate-spin" /> : icon}
        </div>
        <div>
          <h3 className="font-display text-sm font-bold tracking-wider text-white">{title}</h3>
          <p className="text-[10px] text-slate font-mono">{subtitle}</p>
        </div>
      </div>

      <div className="space-y-2">
        {stats.map((s) => (
          <div key={s.label} className="flex justify-between items-center">
            <span className="text-xs text-ash">{s.label}</span>
            <span className="text-xs font-mono text-white">{s.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Flow Arrow ──────────────────────────────────────────────────────────────

function FlowArrow() {
  return (
    <div className="flex items-center justify-center w-12 flex-shrink-0">
      <div className="relative">
        <ArrowRight size={20} className="text-ember/60" />
        {/* Animated dot */}
        <div className="absolute top-1/2 -translate-y-1/2 w-1.5 h-1.5 rounded-full bg-ember animate-[flowPulse_2s_ease-in-out_infinite]" />
      </div>
    </div>
  );
}

// ── Risk Badge ──────────────────────────────────────────────────────────────

function RiskBadge({ level }: { level: string }) {
  const colors: Record<string, string> = {
    CRITICAL: 'bg-critical/20 text-critical border-critical/30',
    HIGH: 'bg-warning/20 text-warning border-warning/30',
    MEDIUM: 'bg-info/20 text-info border-info/30',
    LOW: 'bg-healthy/20 text-healthy border-healthy/30',
  };
  return (
    <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded border ${colors[level] || colors.LOW}`}>
      {level}
    </span>
  );
}

// ── Trend Arrow ─────────────────────────────────────────────────────────────

function TrendArrow({ value }: { value: number }) {
  if (value > 0.05) return <TrendingUp size={12} className="text-critical" />;
  if (value < -0.05) return <TrendingDown size={12} className="text-healthy" />;
  return <Minus size={12} className="text-slate" />;
}

// ── Main Component ──────────────────────────────────────────────────────────

export function MLPipeline() {
  const [modelData, setModelData] = useState<MLModelData | null>(null);
  const [predictions, setPredictions] = useState<MLPredictionsData | null>(null);
  const [gemfireStatus, setGemfireStatus] = useState<MLGemFireStatus | null>(null);
  const [pmmlData, setPmmlData] = useState<MLPmmlData | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionResult, setActionResult] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [activityLog, setActivityLog] = useState<(MLStep & { source: string })[]>([]);
  const logEndRef = useRef<HTMLDivElement>(null);

  const fetchAll = useCallback(async () => {
    try {
      const [model, preds, gfStatus, pmml] = await Promise.allSettled([
        titanApi.getMlModel(),
        titanApi.getMlPredictions(),
        titanApi.getMlGemFireStatus(),
        titanApi.getMlPmml(),
      ]);

      if (model.status === 'fulfilled') setModelData(model.value);
      if (preds.status === 'fulfilled') setPredictions(preds.value);
      if (gfStatus.status === 'fulfilled') setGemfireStatus(gfStatus.value);
      if (pmml.status === 'fulfilled') setPmmlData(pmml.value);
    } catch (e) {
      console.error('ML data fetch error:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAll();
    const interval = setInterval(fetchAll, 30000);
    return () => clearInterval(interval);
  }, [fetchAll]);

  const appendLog = (steps: MLStep[] | undefined, source: string) => {
    if (steps?.length) {
      setActivityLog(prev => [...prev, ...steps.map(s => ({ ...s, source }))]);
      setTimeout(() => logEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 100);
    }
  };

  const handleRetrain = async () => {
    setActionLoading('retrain');
    setActionResult(null);
    try {
      const result = await titanApi.retrainModel();
      appendLog(result.steps, 'RETRAIN');
      if (result.success) {
        setActionResult({ type: 'success', message: `Model retrained with ${result.trainingObservations} observations` });
        fetchAll();
      } else {
        setActionResult({ type: 'error', message: result.error || 'Retrain failed' });
      }
    } catch (e) {
      setActionResult({ type: 'error', message: String(e) });
    } finally {
      setActionLoading(null);
    }
  };

  const handleExportPmml = async () => {
    setActionLoading('export');
    setActionResult(null);
    try {
      const result = await titanApi.getMlPmml();
      if (result.success) {
        setActionResult({ type: 'success', message: `PMML exported: ${result.featureCount} features, ${result.format}` });
        setPmmlData(result);
      } else {
        setActionResult({ type: 'error', message: result.error || 'Export failed' });
      }
    } catch (e) {
      setActionResult({ type: 'error', message: String(e) });
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeploy = async () => {
    setActionLoading('deploy');
    setActionResult(null);
    try {
      const result = await titanApi.deployModel();
      appendLog(result.steps, 'DEPLOY');
      if (result.success) {
        setActionResult({ type: 'success', message: `Deployed to GemFire: ${result.pmmlSize} bytes → ${result.region}` });
        fetchAll();
      } else {
        setActionResult({ type: 'error', message: result.error || 'Deploy failed' });
      }
    } catch (e) {
      setActionResult({ type: 'error', message: String(e) });
    } finally {
      setActionLoading(null);
    }
  };

  // ── Derived data ──────────────────────────────────────────────────────────

  const coefficientChartData = modelData?.coefficients
    ?.filter(c => c.feature_name !== 'intercept')
    .map(c => ({
      name: c.feature_name.replace(/_/g, ' ').replace(/normalized/, 'norm'),
      value: c.coefficient,
      fullName: c.feature_name,
    }))
    .sort((a, b) => Math.abs(b.value) - Math.abs(a.value)) || [];

  const riskDistribution = predictions?.predictions
    ? (() => {
        const counts: Record<string, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
        predictions.predictions.forEach(p => {
          counts[p.riskLevel] = (counts[p.riskLevel] || 0) + 1;
        });
        return [
          { name: 'CRITICAL', value: counts.CRITICAL, color: '#ff3b3b' },
          { name: 'HIGH', value: counts.HIGH, color: '#ffb020' },
          { name: 'MEDIUM', value: counts.MEDIUM, color: '#40a0ff' },
          { name: 'LOW', value: counts.LOW, color: '#00e676' },
        ].filter(d => d.value > 0);
      })()
    : [];

  // ── Render ────────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 size={32} className="text-ember animate-spin" />
        <span className="ml-3 text-slate font-mono">Loading ML Pipeline...</span>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-2">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-wider text-white">
            ML <span className="text-ember">PIPELINE</span>
          </h2>
          <p className="text-xs text-slate mt-1 font-mono">
            Greenplum MADlib → PMML Export → GemFire Real-Time Scoring
          </p>
        </div>
        <button
          onClick={fetchAll}
          className="p-2 hover:bg-steel rounded-lg transition-colors text-slate hover:text-white"
          title="Refresh all data"
        >
          <RefreshCw size={18} />
        </button>
      </div>

      {/* ══════════════════════════════════════════════════════════════════════
          PIPELINE VISUALIZATION
         ══════════════════════════════════════════════════════════════════════ */}
      <div className="bg-carbon border border-iron rounded-xl p-6">
        <div className="flex items-center gap-2 mb-5">
          <Activity size={16} className="text-ember" />
          <h3 className="font-display text-sm font-bold tracking-wider text-white">PIPELINE STAGES</h3>
        </div>

        <div className="flex items-stretch gap-0">
          {/* Stage 1: Training Data */}
          <PipelineStage
            icon={<Database size={20} />}
            title="TRAINING DATA"
            subtitle="Greenplum / MADlib"
            status={modelData ? 'active' : 'error'}
            stats={[
              { label: 'Observations', value: modelData?.trainingObservations ?? '—' },
              { label: 'Failures', value: modelData?.failureObservations ?? '—' },
              { label: 'Method', value: 'IRLS Optimizer' },
            ]}
          />

          <FlowArrow />

          {/* Stage 2: Model */}
          <PipelineStage
            icon={<Brain size={20} />}
            title="LOGISTIC MODEL"
            subtitle={modelData?.modelId ?? 'failure_predictor_v1'}
            status={modelData?.coefficients?.length ? 'active' : 'error'}
            glowing={actionLoading === 'retrain'}
            stats={[
              { label: 'Features', value: modelData?.coefficients?.filter(c => c.feature_name !== 'intercept').length ?? '—' },
              { label: 'Intercept', value: modelData?.coefficients?.find(c => c.feature_name === 'intercept')?.coefficient?.toFixed(2) ?? '—' },
              { label: 'Type', value: 'Logistic Regression' },
            ]}
          />

          <FlowArrow />

          {/* Stage 3: PMML Export */}
          <PipelineStage
            icon={<FileCode size={20} />}
            title="PMML EXPORT"
            subtitle="PMML 4.4 XML"
            status={pmmlData?.success ? 'active' : 'idle'}
            glowing={actionLoading === 'export'}
            stats={[
              { label: 'Format', value: pmmlData?.format ?? '—' },
              { label: 'Features', value: pmmlData?.featureCount ?? '—' },
              { label: 'Size', value: pmmlData?.pmml ? `${(pmmlData.pmml.length / 1024).toFixed(1)} KB` : '—' },
            ]}
          />

          <FlowArrow />

          {/* Stage 4: GemFire Scoring */}
          <PipelineStage
            icon={<Zap size={20} />}
            title="GEMFIRE SCORING"
            subtitle="Real-time predictions"
            status={
              predictions?.success ? 'active' :
              gemfireStatus?.connected ? 'idle' : 'error'
            }
            glowing={actionLoading === 'deploy'}
            stats={[
              { label: 'Connected', value: gemfireStatus?.connected ? 'YES' : 'NO' },
              { label: 'Equipment Scored', value: predictions?.totalEquipment ?? '—' },
              { label: 'Critical', value: predictions?.criticalCount ?? 0 },
            ]}
          />
        </div>

        {/* Action buttons */}
        <div className="flex items-center gap-3 mt-5">
          <ActionButton
            label="Retrain Model"
            icon={<RefreshCw size={14} />}
            loading={actionLoading === 'retrain'}
            onClick={handleRetrain}
          />
          <ActionButton
            label="Export PMML"
            icon={<FileCode size={14} />}
            loading={actionLoading === 'export'}
            onClick={handleExportPmml}
          />
          <ActionButton
            label="Deploy to GemFire"
            icon={<Upload size={14} />}
            loading={actionLoading === 'deploy'}
            onClick={handleDeploy}
          />

          {/* Status toast */}
          {actionResult && (
            <div className={`
              flex items-center gap-2 text-xs font-mono px-3 py-2 rounded-lg ml-auto
              ${actionResult.type === 'success'
                ? 'bg-healthy/10 text-healthy border border-healthy/20'
                : 'bg-critical/10 text-critical border border-critical/20'}
            `}>
              {actionResult.type === 'success'
                ? <CheckCircle size={14} />
                : <AlertTriangle size={14} />}
              {actionResult.message}
            </div>
          )}
        </div>
      </div>

      {/* ══════════════════════════════════════════════════════════════════════
          ACTIVITY LOG
         ══════════════════════════════════════════════════════════════════════ */}
      {activityLog.length > 0 && (
        <div className="bg-carbon border border-iron rounded-xl p-5">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <Terminal size={16} className="text-ember" />
              <h3 className="font-display text-sm font-bold tracking-wider text-white">ACTIVITY LOG</h3>
            </div>
            <button
              onClick={() => setActivityLog([])}
              className="text-[10px] font-mono text-ash hover:text-white transition-colors"
            >
              CLEAR
            </button>
          </div>
          <div className="bg-black/60 rounded-lg p-3 max-h-[200px] overflow-y-auto font-mono text-xs scrollbar-thin">
            {activityLog.map((entry, i) => {
              const typeColor =
                entry.type === 'sql' ? 'text-green-400' :
                entry.type === 'exec' ? 'text-blue-400' :
                entry.type === 'result' ? 'text-white' :
                entry.type === 'error' ? 'text-critical' :
                entry.type === 'done' ? 'text-ember' :
                entry.type === 'xml' ? 'text-purple-400' :
                'text-ash';
              return (
                <div key={i} className="flex gap-2 py-0.5 leading-relaxed">
                  <span className="text-ash/50 flex-shrink-0">{entry.timestamp}</span>
                  <span className="text-ember/70 flex-shrink-0">[{entry.source}]</span>
                  <span className={`${typeColor} flex-shrink-0`}>{entry.type.toUpperCase().padEnd(6)}</span>
                  <span className="text-ash break-all">{entry.message}</span>
                </div>
              );
            })}
            <div ref={logEndRef} />
          </div>
        </div>
      )}

      {/* ══════════════════════════════════════════════════════════════════════
          BOTTOM: PREDICTIONS + MODEL INSIGHTS
         ══════════════════════════════════════════════════════════════════════ */}
      <div className="grid grid-cols-5 gap-6">

        {/* ── Left: Predictions Table (3/5) ──────────────────────────────── */}
        <div className="col-span-3 bg-carbon border border-iron rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Server size={16} className="text-ember" />
              <h3 className="font-display text-sm font-bold tracking-wider text-white">
                REAL-TIME PREDICTIONS
              </h3>
            </div>
            <span className="text-[10px] font-mono text-slate">
              {predictions?.totalEquipment ?? 0} equipment • refreshes every 30s
            </span>
          </div>

          {predictions?.success && predictions.predictions.length > 0 ? (
            <div className="overflow-auto max-h-[420px] scrollbar-thin">
              <table className="w-full text-xs">
                <thead>
                  <tr className="text-ash font-mono border-b border-iron">
                    <th className="text-left py-2 px-2">Equipment</th>
                    <th className="text-left py-2 px-2">Risk</th>
                    <th className="text-left py-2 px-2">Failure Prob.</th>
                    <th className="text-right py-2 px-1">Vib</th>
                    <th className="text-right py-2 px-1">Temp</th>
                    <th className="text-center py-2 px-1">Trend</th>
                    <th className="text-right py-2 px-2">Scored</th>
                  </tr>
                </thead>
                <tbody>
                  {predictions.predictions.map((p) => (
                    <tr
                      key={p.equipmentId}
                      className={`
                        border-b border-iron/40 hover:bg-steel/30 transition-colors
                        ${p.riskLevel === 'CRITICAL' ? 'bg-critical/5' : ''}
                      `}
                    >
                      <td className="py-2 px-2 font-mono text-white">{p.equipmentId}</td>
                      <td className="py-2 px-2"><RiskBadge level={p.riskLevel} /></td>
                      <td className="py-2 px-2">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 h-2 bg-iron rounded-full overflow-hidden max-w-[100px]">
                            <div
                              className={`h-full rounded-full transition-all duration-500 ${
                                p.failureProbability >= 0.7 ? 'bg-critical' :
                                p.failureProbability >= 0.5 ? 'bg-warning' :
                                p.failureProbability >= 0.3 ? 'bg-info' : 'bg-healthy'
                              }`}
                              style={{ width: `${Math.min(p.failureProbability * 100, 100)}%` }}
                            />
                          </div>
                          <span className="font-mono text-white w-10 text-right">
                            {(p.failureProbability * 100).toFixed(0)}%
                          </span>
                        </div>
                      </td>
                      <td className="py-2 px-1 text-right font-mono text-ash">{p.vibrationAvg?.toFixed(1)}</td>
                      <td className="py-2 px-1 text-right font-mono text-ash">{p.temperatureAvg?.toFixed(0)}°</td>
                      <td className="py-2 px-1 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <TrendArrow value={p.vibrationTrend} />
                          <TrendArrow value={p.temperatureTrend} />
                        </div>
                      </td>
                      <td className="py-2 px-2 text-right font-mono text-ash text-[10px]">
                        {p.scoredAt ? new Date(p.scoredAt).toLocaleTimeString() : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center h-48 text-slate">
              <Server size={32} className="mb-3 opacity-30" />
              <p className="text-sm font-mono">
                {predictions?.error
                  ? `Error: ${predictions.error}`
                  : 'No predictions available — GemFire scoring not active'}
              </p>
              <p className="text-xs text-ash mt-1">Enable with: docker-compose --profile gemfire up</p>
            </div>
          )}
        </div>

        {/* ── Right: Model Insights (2/5) ────────────────────────────────── */}
        <div className="col-span-2 space-y-6">

          {/* Coefficient Chart */}
          <div className="bg-carbon border border-iron rounded-xl p-5">
            <div className="flex items-center gap-2 mb-4">
              <Brain size={16} className="text-ember" />
              <h3 className="font-display text-sm font-bold tracking-wider text-white">
                MODEL COEFFICIENTS
              </h3>
            </div>

            {coefficientChartData.length > 0 ? (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={coefficientChartData} layout="vertical" margin={{ left: 8, right: 16 }}>
                  <XAxis type="number" tick={{ fill: '#6b6b80', fontSize: 10, fontFamily: 'JetBrains Mono' }} />
                  <YAxis
                    dataKey="name"
                    type="category"
                    tick={{ fill: '#6b6b80', fontSize: 9, fontFamily: 'JetBrains Mono' }}
                    width={100}
                  />
                  <Tooltip
                    contentStyle={{
                      background: '#131316',
                      border: '1px solid #252530',
                      borderRadius: '8px',
                      fontFamily: 'JetBrains Mono',
                      fontSize: '11px',
                    }}
                    formatter={(value) => [Number(value).toFixed(4), 'Coefficient']}
                    labelFormatter={(label) => String(label)}
                  />
                  <Bar dataKey="value" radius={[0, 4, 4, 0]}>
                    {coefficientChartData.map((entry, index) => (
                      <Cell
                        key={`cell-${index}`}
                        fill={entry.value >= 0 ? '#ff6b00' : '#40a0ff'}
                        fillOpacity={0.8}
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <p className="text-xs text-slate font-mono text-center py-8">No coefficient data</p>
            )}
          </div>

          {/* Risk Distribution */}
          <div className="bg-carbon border border-iron rounded-xl p-5">
            <div className="flex items-center gap-2 mb-4">
              <AlertTriangle size={16} className="text-ember" />
              <h3 className="font-display text-sm font-bold tracking-wider text-white">
                RISK DISTRIBUTION
              </h3>
            </div>

            {riskDistribution.length > 0 ? (
              <div className="flex items-center">
                <ResponsiveContainer width="50%" height={160}>
                  <PieChart>
                    <Pie
                      data={riskDistribution}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      innerRadius={40}
                      outerRadius={65}
                      stroke="none"
                    >
                      {riskDistribution.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} fillOpacity={0.85} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        background: '#131316',
                        border: '1px solid #252530',
                        borderRadius: '8px',
                        fontFamily: 'JetBrains Mono',
                        fontSize: '11px',
                      }}
                    />
                  </PieChart>
                </ResponsiveContainer>
                <div className="flex-1 space-y-2">
                  {riskDistribution.map((d) => (
                    <div key={d.name} className="flex items-center gap-2">
                      <div className="w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: d.color }} />
                      <span className="text-xs font-mono text-ash flex-1">{d.name}</span>
                      <span className="text-xs font-mono text-white font-bold">{d.value}</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <p className="text-xs text-slate font-mono text-center py-8">No scoring data</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Action Button ───────────────────────────────────────────────────────────

function ActionButton({ label, icon, loading, onClick }: {
  label: string;
  icon: React.ReactNode;
  loading: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      disabled={loading}
      className={`
        flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-mono font-medium
        transition-all duration-200
        ${loading
          ? 'bg-ember/20 text-ember cursor-wait'
          : 'bg-iron hover:bg-zinc text-white hover:shadow-[0_0_12px_rgba(255,107,0,0.2)]'
        }
      `}
    >
      {loading ? <Loader2 size={14} className="animate-spin" /> : icon}
      {label}
    </button>
  );
}
