// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Settings Panel
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect, useCallback } from 'react';
import { X, Lock, Save, Plus, Check, Radio, Loader2 } from 'lucide-react';
import { titanApi, type LlmModel } from '../api/titanApi';

interface SettingsPanelProps {
  isOpen: boolean;
  onClose: () => void;
}

export function SettingsPanel({ isOpen, onClose }: SettingsPanelProps) {
  const [models, setModels] = useState<LlmModel[]>([]);
  const [commsEmail, setCommsEmail] = useState('');
  const [savedCommsEmail, setSavedCommsEmail] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [saving, setSaving] = useState(false);
  const [settingDefault, setSettingDefault] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);

  const loadSettings = useCallback(async () => {
    try {
      const [modelsData, settingsData, adminData] = await Promise.all([
        titanApi.getLlmModels(),
        titanApi.getSettings(),
        titanApi.getAdminEmail(),
      ]);
      setModels(modelsData);
      const emailSetting = settingsData.find(s => s.setting_key === 'communications_email');
      const email = emailSetting?.setting_value || '';
      setCommsEmail(email);
      setSavedCommsEmail(email);
      setAdminEmail(adminData.admin_email);
      setLoaded(true);
    } catch (err) {
      console.error('Failed to load settings:', err);
    }
  }, []);

  useEffect(() => {
    if (isOpen && !loaded) {
      loadSettings();
    }
  }, [isOpen, loaded, loadSettings]);

  const handleSaveEmail = async () => {
    setSaving(true);
    try {
      await titanApi.updateSetting('communications_email', commsEmail);
      setSavedCommsEmail(commsEmail);
    } catch (err) {
      console.error('Failed to save communications email:', err);
    } finally {
      setSaving(false);
    }
  };

  const handleSetDefault = async (modelId: string) => {
    setSettingDefault(modelId);
    try {
      await titanApi.setDefaultLlmModel(modelId);
      setModels(prev => prev.map(m => ({ ...m, is_default: m.model_id === modelId })));
    } catch (err) {
      console.error('Failed to set default model:', err);
    } finally {
      setSettingDefault(null);
    }
  };

  const providerLabel = (provider: string) => {
    switch (provider) {
      case 'openai': return 'OpenAI';
      case 'anthropic': return 'Anthropic';
      case 'ollama': return 'Ollama';
      default: return provider;
    }
  };

  const providerColor = (provider: string) => {
    switch (provider) {
      case 'openai': return 'text-green-400';
      case 'anthropic': return 'text-orange-400';
      case 'ollama': return 'text-blue-400';
      default: return 'text-slate';
    }
  };

  if (!isOpen) return null;

  return (
    <>
      {/* Backdrop */}
      <div className="fixed inset-0 bg-black/50 z-[60]" onClick={onClose} />

      {/* Panel */}
      <div className="fixed right-0 top-0 h-full w-[480px] bg-carbon border-l border-iron z-[70] flex flex-col shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-iron">
          <h2 className="font-display font-bold text-lg text-white">Settings</h2>
          <button onClick={onClose} className="p-2 hover:bg-steel rounded-lg transition-colors text-slate hover:text-white">
            <X size={20} />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-8">
          {/* LLM Models Section */}
          <section>
            <h3 className="font-display font-semibold text-sm text-ember uppercase tracking-wider mb-4">
              LLM Models
            </h3>
            <div className="space-y-2">
              {models.map(model => (
                <div
                  key={model.model_id}
                  className={`flex items-center justify-between p-3 rounded-lg border transition-colors ${
                    model.is_default
                      ? 'bg-ember/10 border-ember/30'
                      : 'bg-steel/50 border-iron hover:border-zinc-500'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => handleSetDefault(model.model_id)}
                      disabled={model.is_default || settingDefault !== null}
                      className="flex-shrink-0"
                    >
                      {settingDefault === model.model_id ? (
                        <Loader2 size={18} className="text-ember animate-spin" />
                      ) : model.is_default ? (
                        <Check size={18} className="text-ember" />
                      ) : (
                        <Radio size={18} className="text-zinc-500 hover:text-white" />
                      )}
                    </button>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className={`text-xs font-mono font-bold ${providerColor(model.provider)}`}>
                          {providerLabel(model.provider)}
                        </span>
                        {model.is_default && (
                          <span className="text-[10px] bg-ember/20 text-ember px-1.5 py-0.5 rounded font-mono">
                            DEFAULT
                          </span>
                        )}
                      </div>
                      <div className="text-sm text-white font-medium">{model.model_name}</div>
                      {model.base_url && (
                        <div className="text-xs text-zinc-500 font-mono">{model.base_url}</div>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <button
              disabled
              className="mt-3 flex items-center gap-2 px-3 py-2 rounded-lg border border-dashed border-iron text-zinc-500 text-sm cursor-not-allowed"
              title="Coming soon"
            >
              <Plus size={14} />
              Add Model
            </button>
          </section>

          {/* Communications Email */}
          <section>
            <h3 className="font-display font-semibold text-sm text-ember uppercase tracking-wider mb-4">
              Communications Email
            </h3>
            <p className="text-xs text-zinc-400 mb-3">
              This email is used as the sender for demo notifications.
            </p>
            <div className="flex gap-2">
              <input
                type="email"
                value={commsEmail}
                onChange={e => setCommsEmail(e.target.value)}
                placeholder="notifications@yourcompany.com"
                className="flex-1 bg-steel border border-iron rounded-lg px-3 py-2 text-sm text-white placeholder-zinc-500 focus:outline-none focus:border-ember"
              />
              <button
                onClick={handleSaveEmail}
                disabled={saving || commsEmail === savedCommsEmail}
                className="flex items-center gap-1.5 px-4 py-2 bg-ember hover:bg-ember/80 disabled:bg-zinc-700 disabled:text-zinc-500 text-white text-sm rounded-lg transition-colors"
              >
                {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                Save
              </button>
            </div>
          </section>

          {/* Admin Email (Read-Only) */}
          <section>
            <h3 className="font-display font-semibold text-sm text-ember uppercase tracking-wider mb-4">
              Admin Email
            </h3>
            <p className="text-xs text-zinc-400 mb-3">
              Set via <code className="text-zinc-300">TITAN_ADMIN_EMAIL</code> environment variable. Receives BCC of all demo notifications.
            </p>
            <div className="flex items-center gap-2 px-3 py-2 bg-steel/50 border border-iron rounded-lg">
              <Lock size={14} className="text-zinc-500 flex-shrink-0" />
              <span className="text-sm text-zinc-400 font-mono">
                {adminEmail || '(not configured)'}
              </span>
            </div>
          </section>
        </div>
      </div>
    </>
  );
}
