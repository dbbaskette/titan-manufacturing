# ML Training Data Matrix — Titan Manufacturing 5.0

## Overview

This document defines the training data specifications for the equipment failure prediction model.
The model uses logistic regression trained on synthetic sensor data to predict failure probability.

## Sensor Thresholds

| Sensor | Warning | Critical | Max/Normalization |
|--------|---------|----------|-------------------|
| Vibration | 3.0 mm/s | 3.5 mm/s | 5.0 mm/s |
| Temperature | 70°C | 85°C | 85°C |
| RPM | N/A | N/A | 10,000 RPM |
| Power | 50 kW | 55 kW | 50 kW |
| Pressure | N/A | N/A | 10 bar |
| Torque | N/A | N/A | 80 Nm |

## Degradation Patterns & Sensor Signatures

### Key Distinguishers by Pattern

| Pattern | Primary Indicators | Secondary Indicators |
|---------|-------------------|---------------------|
| **Bearing Degradation** | HIGH vibration ↑↑, HIGH torque ↑↑ | Temperature correlates |
| **Motor Burnout** | HIGH temperature ↑↑, HIGH power ↑↑, LOW RPM ↓↓ | Motor struggling under load |
| **Electrical Fault** | Power SPIKES ↑↑↑, RPM erratic/dropping ↓↓ | Vibration rising, temp climbing |
| **Coolant Failure** | HIGH temperature ↑↑, LOW pressure ↓↓ | Distinctive pressure drop |
| **Spindle Wear** | HIGH vibration ↑↑, HIGH torque ↑↑, LOW RPM ↓↓ | Mechanical friction |

## Complete Training Data Matrix

### Normal Operation (Healthy)
**Target Probability:** 0-15%
**Label:** failed=0

| Sensor | Range | Notes |
|--------|-------|-------|
| Vibration | 1.5-2.5 mm/s | Well below warning |
| Temperature | 45-55°C | Normal operating temp |
| RPM | 8000-9500 | Full speed |
| Power | 20-30 kW | Normal load |
| Pressure | 5.5-6.5 bar | Normal |
| Torque | 40-55 Nm | Normal |

---

### Bearing Degradation

#### Early Stage (15-40% probability)
**Label:** 70% failed=0, 30% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.5-3.3 mm/s | ↑ approaching warning |
| Temperature | 50-65°C | ↑ slight |
| RPM | 7800-9000 | → normal |
| Power | 25-35 kW | ↑ slight |
| Pressure | 5.0-6.0 bar | → normal |
| Torque | 50-62 Nm | ↑ starting to rise |

#### Moderate Stage (40-70% probability)
**Label:** 20% failed=0, 80% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 3.0-4.0 mm/s | ↑↑ warning level |
| Temperature | 60-75°C | ↑ elevated |
| RPM | 7200-8500 | ↓ slight drop |
| Power | 28-40 kW | ↑ elevated |
| Pressure | 4.8-5.8 bar | → normal |
| Torque | 55-70 Nm | ↑↑ elevated |

#### Critical Stage (70-100% probability)
**Label:** 100% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 4.0-7.0+ mm/s | ↑↑↑ critical+ |
| Temperature | 65-90°C | ↑↑ high |
| RPM | 6500-8000 | ↓ dropping |
| Power | 28-45 kW | ↑ high |
| Pressure | 4.5-5.5 bar | → normal |
| Torque | 60-85 Nm | ↑↑↑ very high |

---

### Motor Burnout

#### Early Stage (15-40% probability)
**Label:** 70% failed=0, 30% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.0-2.8 mm/s | → normal |
| Temperature | 60-72°C | ↑ rising toward warning |
| RPM | 7000-8500 | ↓ slight drop |
| Power | 30-40 kW | ↑ elevated |
| Pressure | 5.0-6.0 bar | → normal |
| Torque | 45-58 Nm | → normal |

#### Moderate Stage (40-70% probability)
**Label:** 20% failed=0, 80% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.2-3.2 mm/s | ↑ slight |
| Temperature | 72-82°C | ↑↑ warning level |
| RPM | 6200-7500 | ↓↓ dropping |
| Power | 38-48 kW | ↑↑ high |
| Pressure | 4.8-5.8 bar | → normal |
| Torque | 48-62 Nm | → normal |

#### Critical Stage (70-100% probability)
**Label:** 100% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.5-4.0 mm/s | ↑ elevated |
| Temperature | 85-110+°C | ↑↑↑ critical+ |
| RPM | 5000-7000 | ↓↓↓ very low |
| Power | 42-65 kW | ↑↑↑ very high |
| Pressure | 4.5-5.5 bar | → normal |
| Torque | 50-65 Nm | → normal |

---

### Electrical Fault

#### Early Stage (15-40% probability)
**Label:** 70% failed=0, 30% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.3-3.2 mm/s | ↑ rising |
| Temperature | 52-68°C | ↑ slight |
| RPM | 7000-8500 | → erratic start |
| Power | 32-42 kW | ↑ occasional spikes |
| Pressure | 4.8-5.8 bar | → normal |
| Torque | 45-58 Nm | → normal |

#### Moderate Stage (40-70% probability)
**Label:** 20% failed=0, 80% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.8-4.0 mm/s | ↑↑ warning |
| Temperature | 58-78°C | ↑ elevated |
| RPM | 5800-7800 | ↓↓ dropping/erratic |
| Power | 40-52 kW | ↑↑ spikes |
| Pressure | 4.5-5.5 bar | ↓ slight |
| Torque | 48-62 Nm | → normal |

#### Critical Stage (70-100% probability)
**Label:** 100% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 3.5-6.5 mm/s | ↑↑↑ high |
| Temperature | 60-90°C | ↑↑ elevated |
| RPM | 4500-7500 | ↓↓↓ erratic/low |
| Power | 48-75 kW | ↑↑↑ severe spikes |
| Pressure | 4.2-5.2 bar | ↓ slight |
| Torque | 48-65 Nm | → normal |

---

### Coolant Failure

#### Early Stage (15-40% probability)
**Label:** 70% failed=0, 30% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.0-2.8 mm/s | → normal |
| Temperature | 60-72°C | ↑ rising |
| RPM | 7500-8800 | → normal |
| Power | 22-32 kW | → normal |
| Pressure | 4.5-5.5 bar | ↓ starting to drop |
| Torque | 42-55 Nm | → normal |

#### Moderate Stage (40-70% probability)
**Label:** 20% failed=0, 80% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.2-3.2 mm/s | ↑ slight |
| Temperature | 72-85°C | ↑↑ warning level |
| RPM | 7200-8500 | → normal |
| Power | 24-35 kW | → normal |
| Pressure | 3.5-4.8 bar | ↓↓ low |
| Torque | 44-58 Nm | → normal |

#### Critical Stage (70-100% probability)
**Label:** 100% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.5-3.8 mm/s | ↑ elevated |
| Temperature | 80-110+°C | ↑↑↑ critical+ |
| RPM | 7000-8200 | → normal |
| Power | 25-38 kW | → normal |
| Pressure | 2.0-4.0 bar | ↓↓↓ very low |
| Torque | 42-58 Nm | → normal |

---

### Spindle Wear

#### Early Stage (15-40% probability)
**Label:** 70% failed=0, 30% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 2.5-3.5 mm/s | ↑ rising |
| Temperature | 52-65°C | ↑ slight |
| RPM | 7200-8500 | ↓ starting to drop |
| Power | 28-38 kW | ↑ slight |
| Pressure | 5.0-6.0 bar | → normal |
| Torque | 55-68 Nm | ↑ rising |

#### Moderate Stage (40-70% probability)
**Label:** 20% failed=0, 80% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 3.2-4.5 mm/s | ↑↑ warning level |
| Temperature | 55-72°C | ↑ elevated |
| RPM | 6200-7800 | ↓↓ dropping |
| Power | 30-42 kW | ↑ elevated |
| Pressure | 4.8-5.8 bar | → normal |
| Torque | 62-75 Nm | ↑↑ high |

#### Critical Stage (70-100% probability)
**Label:** 100% failed=1

| Sensor | Range | Delta from Normal |
|--------|-------|-------------------|
| Vibration | 3.8-6.5 mm/s | ↑↑↑ critical |
| Temperature | 58-78°C | ↑ elevated |
| RPM | 5500-7200 | ↓↓↓ very low |
| Power | 32-45 kW | ↑↑ high |
| Pressure | 4.5-5.5 bar | → normal |
| Torque | 68-90 Nm | ↑↑↑ very high |

---

## Training Data Distribution

### Sample Counts per Category

For realistic model training, the distribution should reflect that:
- Most equipment operates normally
- Early warning signs are more common than critical failures
- Critical failures are rare

**Recommended Distribution (per training run):**

| Category | Count | % of Total |
|----------|-------|------------|
| Normal (healthy) | 1000 | 50% |
| Early warning (all patterns) | 600 | 30% |
| Moderate degradation (all patterns) | 300 | 15% |
| Critical (all patterns) | 100 | 5% |
| **Total** | **2000** | **100%** |

### Distribution Within Each Pattern

For each degradation pattern (e.g., Bearing Degradation):

| Stage | % of Pattern Samples | failed=0 % | failed=1 % |
|-------|---------------------|------------|------------|
| Early | 60% | 70% | 30% |
| Moderate | 30% | 20% | 80% |
| Critical | 10% | 0% | 100% |

This creates a smooth probability curve where:
- Normal → ~5% failure probability
- Early warning → 15-40% probability
- Moderate → 40-70% probability
- Critical → 70-100% probability

---

## API Endpoint

Generate training data via:

```bash
POST /api/ml/training/generate?normalCount=1000&failureCountPerPattern=200
```

Then retrain the model:

```bash
POST /api/ml/retrain
```

---

## Revision History

| Date | Change |
|------|--------|
| 2026-01-28 | Initial comprehensive matrix with 16 states |
