// ═══════════════════════════════════════════════════════════════════════════
// TITAN MANUFACTURING 5.0 — Equipment Utilities
// ═══════════════════════════════════════════════════════════════════════════

const GENERATOR_API = 'http://localhost:8090/api/generator';

export interface GeneratorEquipment {
  equipmentId: string;
  facilityId: string;
  pattern: string;
  cycles: number;
  vibration: number;
  temperature: number;
  rpm: number;
  power: number;
  pressure: number;
  torque: number;
}

const FACILITY_NAMES: Record<string, string> = {
  ATL: 'Atlanta',
  DAL: 'Dallas',
  DET: 'Detroit',
  LYN: 'Lyon',
  MAN: 'Manchester',
  MEX: 'Mexico City',
  MUC: 'Munich',
  PHX: 'Phoenix',
  SEO: 'Seoul',
  SHA: 'Shanghai',
  SYD: 'Sydney',
  TYO: 'Tokyo',
};

export function getFacilityDisplayName(code: string): string {
  return FACILITY_NAMES[code] || code;
}

/** Sort equipment by facility code (alpha), then equipment number (numeric). */
export function sortEquipment<T extends { equipmentId: string }>(equipment: T[]): T[] {
  return [...equipment].sort((a, b) => {
    const facA = a.equipmentId.split('-')[0];
    const facB = b.equipmentId.split('-')[0];
    if (facA !== facB) return facA.localeCompare(facB);
    const numA = parseInt(a.equipmentId.split('-').pop() || '0', 10);
    const numB = parseInt(b.equipmentId.split('-').pop() || '0', 10);
    return numA - numB;
  });
}

/** Group sorted equipment by facility code. */
export function groupByFacility<T extends { equipmentId: string }>(equipment: T[]): Record<string, T[]> {
  const groups: Record<string, T[]> = {};
  for (const eq of equipment) {
    const facility = eq.equipmentId.split('-')[0];
    if (!groups[facility]) groups[facility] = [];
    groups[facility].push(eq);
  }
  return groups;
}

/** Fetch equipment list from the generator API. */
export async function fetchGeneratorEquipment(): Promise<GeneratorEquipment[]> {
  const res = await fetch(`${GENERATOR_API}/equipment`);
  if (!res.ok) throw new Error('Generator service not available');
  return res.json();
}
