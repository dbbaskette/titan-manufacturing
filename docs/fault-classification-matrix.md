# Fault Classification Matrix

## Sensor Baselines

| Sensor | Baseline | Normal Range |
|--------|----------|-------------|
| Vibration | 2.0 mm/s | 1.8 – 2.4 |
| Temperature | 50.0°C | 48 – 56 |
| Power | 15.0 kW | 13 – 17 |
| RPM | 8500 | 8200 – 9500 |
| Pressure | 6.0 bar | 5.7 – 6.3 |
| Torque | 45.0 Nm | 42 – 48 |

## Sensor Deviation Matrix by Fault Type

Each cell shows: **direction**, rate/cycle, and the approximate range when the fault reaches HIGH risk (~50-70 cycles depending on type).

| Sensor | Bearing Degradation | Motor Burnout | Spindle Wear | Coolant Failure | Electrical Fault |
|--------|:---:|:---:|:---:|:---:|:---:|
| **Vibration** | **↑↑↑ PRIMARY** +0.035/cyc → 4.0-5.5 | ↑ erratic +0.02/cyc → 3.0-4.0 | ↑↑ +0.04/cyc → 4.0-5.5 | ↑ mild +0.02/cyc → 3.0-3.5 | ↑ erratic +0.03/cyc → 3.0-4.5 |
| **Temperature** | ↑↑ coupled to vib (×6) → 60-70 | **↑↑↑ PRIMARY** +0.35/cyc → 70-85 | ↑ +0.12/cyc → 56-62 | ↑↑ +0.25/cyc → 65-75 | ↑ +0.15/cyc → 58-65 |
| **Power** | ↑ mild +0.03/cyc → 16-17 | **↑↑** +0.12/cyc → 22-28 | ↑ mild +0.03/cyc → 16-17 | ≈ stable +0.02/cyc → 16 | **↑↑↑ PRIMARY** +0.15/cyc + spikes → 25-45 |
| **RPM** | ↓ slow -2/cyc → 8300-8400 | **↓↓** steep -18/cyc → 7000-7600 | **↓↓ PRIMARY** -20/cyc → 6500-7200 | ≈ stable (noise only) → 8400-8600 | **↓↓** steep -25/cyc → 6500-7300 |
| **Pressure** | ≈ stable (noise only) → 5.8-6.2 | ≈ stable (noise only) → 5.8-6.2 | ≈ stable (noise only) → 5.8-6.2 | **↓↓ PRIMARY** -0.045/cyc → 3.0-4.5 | ↓ mild -0.02/cyc → 4.8-5.5 |
| **Torque** | ↑ +0.05/cyc → 48-50 | ↑ +0.04/cyc → 47-49 | **↑↑** +0.1/cyc + spikes → 50-55 | ≈ mild +0.02/cyc → 46-47 | ↑ erratic +0.06/cyc → 48-52 |

## Discriminating Features Per Fault

The key insight: **no single sensor uniquely identifies a fault**. But certain *combinations* are distinctive. This table identifies the strongest discriminators for each fault type.

| Fault Type | Must Be True | Must NOT Be True | Confidence Boosters |
|------------|-------------|-------------------|---------------------|
| **Bearing Degradation** | Vibration HIGH (>3.5) | Power NOT elevated (≤17), Pressure NOT low (≥5.5) | RPM mild decline, Temp coupled to vibration |
| **Motor Burnout** | Temp HIGH (>65) AND Power elevated (>20) | Pressure NOT low (≥5.5) | RPM steep decline, Erratic vibration spikes |
| **Spindle Wear** | RPM LOW (<7500) AND (Torque elevated (>50) OR Vibration elevated (>3.5)) | Power NOT elevated (≤17), Pressure NOT low (≥5.5) | Torque spikes, Temp modest rise |
| **Coolant Failure** | Pressure LOW (<5.0) AND Temp elevated (>55) | Power NOT elevated (≤17), RPM NOT degraded (≥8200) | Vibration mild, Torque mild |
| **Electrical Fault** | Power elevated (>20) AND RPM LOW (<7500) | — | Erratic patterns across all sensors, Pressure mild drop |

## Scoring Approach: Best-Match Across All Sensors

Instead of sequential if/else rules that short-circuit, score each fault type against **all 6 sensors** simultaneously and pick the highest-scoring match.

For each fault type, define expected ranges. Score = number of sensors that fall within the expected range for that fault type. **The fault type with the most matching sensors wins.**

### Expected Ranges at HIGH Risk Level

| Sensor | Bearing | Motor Burnout | Spindle Wear | Coolant Failure | Electrical Fault |
|--------|---------|---------------|--------------|-----------------|------------------|
| **Vibration** | 3.5 – 8.0 | 2.5 – 5.0 | 3.0 – 7.0 | 2.0 – 3.5 | 2.5 – 6.0 |
| **Temperature** | 58 – 95 | 68 – 120 | 52 – 65 | 60 – 95 | 55 – 75 |
| **Power** | 15 – 18 | 20 – 40 | 15 – 18 | 14 – 17 | 22 – 50 |
| **RPM** | 8000 – 8500 | 6000 – 7800 | 5000 – 7500 | 8200 – 9000 | 3000 – 7500 |
| **Pressure** | 5.5 – 6.5 | 5.5 – 6.5 | 5.5 – 6.5 | 1.0 – 5.0 | 4.0 – 5.8 |
| **Torque** | 47 – 55 | 46 – 52 | 49 – 65 | 44 – 48 | 47 – 58 |

### Tiebreaker: Weighted Scoring

Some sensors are more discriminating than others for each fault type. Apply weights:

| Sensor | Bearing | Motor Burnout | Spindle Wear | Coolant Failure | Electrical Fault |
|--------|---------|---------------|--------------|-----------------|------------------|
| **Vibration** | **3** | 1 | 2 | 1 | 1 |
| **Temperature** | 1 | **3** | 1 | 2 | 1 |
| **Power** | 1 | 2 | 1 | 1 | **3** |
| **RPM** | 1 | 2 | **3** | 1 | 2 |
| **Pressure** | 1 | 1 | 1 | **3** | 1 |
| **Torque** | 1 | 1 | **2** | 1 | 1 |
| **Max Possible** | **8** | **10** | **10** | **9** | **9** |

**Algorithm:**
```
For each fault_type:
    score = 0
    For each sensor:
        if sensor_value in expected_range[fault_type][sensor]:
            score += weight[fault_type][sensor]
    fault_scores[fault_type] = score

diagnosis = fault_type with highest score
```

### Why This Fixes the Electrical/Coolant Confusion

With the old sequential rules:
- Electrical fault: temp=58, pressure=5.3, power=25 → matches `tempElevated && pressureLow` → **"Coolant"** (WRONG)

With matrix scoring:
- **Coolant score**: Temp ✓(2) + Pressure ✓(3) + Power ✗ + RPM ✓(1) + Vib ✓(1) + Torque ✓(1) = **8/9**
- **Electrical score**: Temp ✓(1) + Pressure ✓(1) + Power ✓(**3**) + RPM ✓(**2**) + Vib ✓(1) + Torque ✓(1) = **9/9**
- Electrical wins because Power=25 and RPM dropping are heavily weighted for electrical but not for coolant.

## Simulation Rates Reference

How fast each fault progresses (cycles at 5-second intervals):

| Fault Type | Cycles to HIGH Cap | Time to HIGH |
|------------|-------------------|--------------|
| Bearing Degradation | 70 | ~5.8 min |
| Motor Burnout | 80 | ~6.7 min |
| Spindle Wear | 90 | ~7.5 min |
| Coolant Failure | 100 | ~8.3 min |
| Electrical Fault | 85 | ~7.1 min |
