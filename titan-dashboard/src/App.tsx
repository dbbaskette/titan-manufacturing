// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Main Application
// "Forging the future with intelligent manufacturing"
// ═══════════════════════════════════════════════════════════════════════════

import { useState } from 'react';
import {
  Layout,
  GlobalOverview,
  SensorMonitor,
  EquipmentHealth,
  OrderTracker,
  ChatInterface,
  DemoScenarios,
  AgentStatus,
  SimulationControl,
  MLPipeline,
} from './components';
import type { Facility } from './types';

function App() {
  const [currentView, setCurrentView] = useState('overview');

  const handleFacilitySelect = (facility: Facility) => {
    // If Phoenix with critical status, show equipment health
    if (facility.status === 'critical') {
      setCurrentView('equipment');
    } else {
      setCurrentView('sensors');
    }
  };

  const renderView = () => {
    switch (currentView) {
      case 'overview':
        return <GlobalOverview onFacilitySelect={handleFacilitySelect} />;
      case 'sensors':
        return <SensorMonitor />;
      case 'equipment':
        return <EquipmentHealth />;
      case 'orders':
        return <OrderTracker />;
      case 'chat':
        return <ChatInterface />;
      case 'demos':
        return <DemoScenarios />;
      case 'agents':
        return <AgentStatus />;
      case 'simulation':
        return <SimulationControl />;
      case 'ml-pipeline':
        return <MLPipeline />;
      default:
        return <GlobalOverview onFacilitySelect={handleFacilitySelect} />;
    }
  };

  return (
    <Layout currentView={currentView} onViewChange={setCurrentView}>
      {renderView()}
    </Layout>
  );
}

export default App;
