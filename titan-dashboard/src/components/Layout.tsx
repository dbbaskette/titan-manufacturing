// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Dashboard Layout
// ═══════════════════════════════════════════════════════════════════════════

import { useState, useEffect } from 'react';
import {
  Globe,
  Activity,
  Wrench,
  Bell,
  Package,
  MessageSquare,
  Play,
  Settings,
  Menu,
  X,
  Cpu,
  Zap,
  Brain,
} from 'lucide-react';

interface LayoutProps {
  children: React.ReactNode;
  currentView: string;
  onViewChange: (view: string) => void;
}

const NAV_ITEMS = [
  { id: 'overview', label: 'Global Overview', icon: Globe },
  { id: 'sensors', label: 'Sensor Monitor', icon: Activity },
  { id: 'equipment', label: 'Equipment Health', icon: Wrench },
  { id: 'recommendations', label: 'Recommendations', icon: Bell },
  { id: 'orders', label: 'Order Tracking', icon: Package },
  { id: 'chat', label: 'AI Assistant', icon: MessageSquare },
  { id: 'demos', label: 'Demo Scenarios', icon: Play },
  { id: 'ml-pipeline', label: 'ML Pipeline', icon: Brain },
  { id: 'agents', label: 'Agent Status', icon: Cpu },
  { id: 'simulation', label: 'Simulation Control', icon: Zap },
];

export function Layout({ children, currentView, onViewChange }: LayoutProps) {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [time, setTime] = useState(new Date());

  // Update time every second
  useEffect(() => {
    const interval = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen flex flex-col">
      {/* Top Bar */}
      <header className="h-14 bg-carbon border-b border-iron flex items-center justify-between px-4 z-50">
        <div className="flex items-center gap-4">
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="p-2 hover:bg-steel rounded-lg transition-colors"
          >
            {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
          </button>

          {/* Logo */}
          <div className="flex items-center gap-3">
            <img src="/titan-icon.jpg" alt="Titan" className="w-11 h-11 object-contain rounded-lg" />
            <div>
              <h1 className="font-display font-bold text-lg tracking-wider text-white">
                TITAN <span className="text-ember">5.0</span>
              </h1>
              <p className="text-[10px] text-slate tracking-widest uppercase -mt-1">
                Manufacturing Intelligence
              </p>
            </div>
          </div>
        </div>

        {/* Status Bar */}
        <div className="flex items-center gap-6">
          {/* System Status */}
          <div className="flex items-center gap-2">
            <div className="status-dot status-dot-healthy" />
            <span className="text-xs font-mono text-slate">All Systems Operational</span>
          </div>

          {/* Active Alerts */}
          <div className="flex items-center gap-2 px-3 py-1.5 bg-critical/20 border border-critical/30 rounded-lg">
            <div className="status-dot status-dot-critical" />
            <span className="text-xs font-mono text-critical">2 Active Alerts</span>
          </div>

          {/* Time */}
          <div className="font-mono text-sm text-slate">
            <span className="text-ember">{time.toLocaleTimeString('en-US', { hour12: false })}</span>
            <span className="mx-2 text-iron">|</span>
            <span>{time.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}</span>
          </div>

          {/* Settings */}
          <button className="p-2 hover:bg-steel rounded-lg transition-colors text-slate hover:text-white">
            <Settings size={18} />
          </button>
        </div>
      </header>

      <div className="flex flex-1">
        {/* Sidebar */}
        <aside
          className={`${
            sidebarOpen ? 'w-56' : 'w-0'
          } bg-carbon border-r border-iron transition-all duration-300 overflow-hidden flex-shrink-0`}
        >
          <nav className="p-3 space-y-1">
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon;
              const isActive = currentView === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => onViewChange(item.id)}
                  className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all ${
                    isActive
                      ? 'bg-ember/20 text-ember border border-ember/30'
                      : 'text-zinc-300 hover:bg-steel hover:text-white border border-transparent'
                  }`}
                >
                  <Icon size={18} className={isActive ? 'text-ember' : ''} />
                  <span className="text-sm font-medium">{item.label}</span>
                </button>
              );
            })}
          </nav>

          {/* Quick Stats */}
          <div className="absolute bottom-0 left-0 right-0 p-4 border-t border-iron">
            <div className="grid grid-cols-2 gap-2 text-center">
              <div className="p-2 bg-steel rounded-lg">
                <div className="text-lg font-display font-bold text-ember">12</div>
                <div className="text-[10px] text-slate uppercase">Facilities</div>
              </div>
              <div className="p-2 bg-steel rounded-lg">
                <div className="text-lg font-display font-bold text-ember">600+</div>
                <div className="text-[10px] text-slate uppercase">Machines</div>
              </div>
            </div>
          </div>
        </aside>

        {/* Main Content */}
        <main className="flex-1 overflow-auto grid-pattern">
          <div className="p-6">{children}</div>
        </main>
      </div>
    </div>
  );
}
