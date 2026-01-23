-- =============================================================================
-- TITAN MANUFACTURING — Greenplum Data Warehouse
-- Titan 5.0 AI Platform - Analytics Database
-- =============================================================================

-- Enable pgvector for semantic search
CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================================
-- REFERENCE DATA
-- =============================================================================

-- Titan Business Divisions
CREATE TABLE titan_divisions (
    division_id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT
);

INSERT INTO titan_divisions VALUES
('AERO', 'Titan Aerospace', 'Turbine blades, engine housings, landing gear for Boeing, Airbus, SpaceX'),
('ENERGY', 'Titan Energy', 'Wind turbine gearboxes, solar frames, pipeline valves for GE, Siemens'),
('MOBILITY', 'Titan Mobility', 'EV motor housings, battery enclosures for Tesla, Ford, Rivian'),
('INDUSTRIAL', 'Titan Industrial', 'CNC parts, hydraulic pumps, bearings for Caterpillar, John Deere');

-- Global Manufacturing Facilities (12 plants)
CREATE TABLE titan_facilities (
    facility_id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    city VARCHAR(100),
    country VARCHAR(50),
    region VARCHAR(10),
    equipment_count INT,
    specialization VARCHAR(100)
);

INSERT INTO titan_facilities VALUES
('PHX', 'Phoenix Plant', 'Phoenix', 'USA', 'NA', 65, 'Aerospace precision machining'),
('DET', 'Detroit Plant', 'Detroit', 'USA', 'NA', 52, 'Automotive components'),
('ATL', 'Atlanta HQ', 'Atlanta', 'USA', 'NA', 48, 'Industrial equipment'),
('DAL', 'Dallas Plant', 'Dallas', 'USA', 'NA', 45, 'Energy sector components'),
('MUC', 'Munich Plant', 'Munich', 'Germany', 'EU', 58, 'Precision engineering'),
('LYN', 'Lyon Plant', 'Lyon', 'France', 'EU', 45, 'Aerospace composites'),
('MAN', 'Manchester Plant', 'Manchester', 'UK', 'EU', 40, 'Industrial bearings'),
('SHA', 'Shanghai Plant', 'Shanghai', 'China', 'APAC', 72, 'High-volume production'),
('TYO', 'Tokyo Plant', 'Tokyo', 'Japan', 'APAC', 55, 'Precision instruments'),
('SEO', 'Seoul Plant', 'Seoul', 'South Korea', 'APAC', 42, 'EV components'),
('SYD', 'Sydney Plant', 'Sydney', 'Australia', 'APAC', 38, 'Mining equipment'),
('MEX', 'Mexico City Plant', 'Mexico City', 'Mexico', 'LATAM', 40, 'Assembly operations');

-- =============================================================================
-- SUPPLIERS
-- =============================================================================

CREATE TABLE suppliers (
    supplier_id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(50),
    region VARCHAR(10),
    category VARCHAR(100),
    lead_time_days INT,
    quality_rating DECIMAL(3,2),
    is_active BOOLEAN DEFAULT TRUE
);

INSERT INTO suppliers VALUES
('SUP-NIPPON', 'NipponBearing Co.', 'Japan', 'APAC', 'Bearings', 21, 4.8, TRUE),
('SUP-SKF', 'SKF Industries', 'Sweden', 'EU', 'Bearings', 14, 4.9, TRUE),
('SUP-TIMKEN', 'Timken Bearings', 'USA', 'NA', 'Bearings', 7, 4.7, TRUE),
('SUP-NSK', 'NSK Ltd.', 'Japan', 'APAC', 'Bearings', 21, 4.6, TRUE),
('SUP-TIMET', 'TIMET Titanium', 'USA', 'NA', 'Raw Materials', 30, 4.9, TRUE),
('SUP-ALCOA', 'Alcoa Aluminum', 'USA', 'NA', 'Raw Materials', 14, 4.8, TRUE),
('SUP-HEXCEL', 'Hexcel Composites', 'USA', 'NA', 'Composites', 21, 4.9, TRUE),
('SUP-TORAY', 'Toray Industries', 'Japan', 'APAC', 'Composites', 28, 4.8, TRUE),
('SUP-BOSCH', 'Bosch Rexroth', 'Germany', 'EU', 'Hydraulics', 14, 4.7, TRUE),
('SUP-PARKER', 'Parker Hannifin', 'USA', 'NA', 'Hydraulics', 10, 4.6, TRUE),
('SUP-SIEMENS', 'Siemens Motors', 'Germany', 'EU', 'Motors', 21, 4.8, TRUE),
('SUP-ABB', 'ABB Drives', 'Switzerland', 'EU', 'Motors', 18, 4.7, TRUE),
('SUP-SANDVIK', 'Sandvik Coromant', 'Sweden', 'EU', 'Tooling', 7, 4.9, TRUE),
('SUP-KENNAM', 'Kennametal', 'USA', 'NA', 'Tooling', 5, 4.7, TRUE),
('SUP-MITSU', 'Mitsubishi Materials', 'Japan', 'APAC', 'Tooling', 14, 4.8, TRUE);

-- =============================================================================
-- PRODUCTS CATALOG (500+ SKUs)
-- =============================================================================

CREATE TABLE products (
    sku VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    division_id VARCHAR(20) REFERENCES titan_divisions(division_id),
    category VARCHAR(100),
    subcategory VARCHAR(100),
    unit_price DECIMAL(12,2),
    weight_kg DECIMAL(10,3),
    lead_time_days INT DEFAULT 14,
    min_order_qty INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    embedding vector(1536)
);

-- =============================================================================
-- AEROSPACE DIVISION PRODUCTS (125 SKUs)
-- =============================================================================

INSERT INTO products (sku, name, description, division_id, category, subcategory, unit_price, weight_kg, lead_time_days) VALUES
-- Turbine Components (25)
('AERO-TB-001', 'Titanium Turbine Blade Blank - Stage 1', 'High-pressure turbine blade blank, Ti-6Al-4V alloy', 'AERO', 'Turbine Components', 'Blade Blanks', 2450.00, 1.2, 30),
('AERO-TB-002', 'Titanium Turbine Blade Blank - Stage 2', 'Intermediate-pressure turbine blade, Ti-6Al-4V', 'AERO', 'Turbine Components', 'Blade Blanks', 2280.00, 1.1, 30),
('AERO-TB-003', 'Titanium Turbine Blade Blank - Stage 3', 'Low-pressure turbine blade blank', 'AERO', 'Turbine Components', 'Blade Blanks', 2100.00, 0.95, 28),
('AERO-TB-004', 'Nickel Superalloy Blade - High Temp', 'Inconel 718 turbine blade for extreme temps', 'AERO', 'Turbine Components', 'Finished Blades', 4500.00, 1.4, 45),
('AERO-TB-005', 'Turbine Disk Forging - Fan Stage', 'Ti-6Al-4V disk forging, 800mm diameter', 'AERO', 'Turbine Components', 'Disk Forgings', 18500.00, 85.0, 60),
('AERO-TB-006', 'Turbine Disk Forging - Compressor', 'Nickel alloy compressor disk', 'AERO', 'Turbine Components', 'Disk Forgings', 22000.00, 72.0, 60),
('AERO-TB-007', 'Turbine Shroud Segment', 'Ceramic matrix composite shroud', 'AERO', 'Turbine Components', 'Shrouds', 3200.00, 2.1, 35),
('AERO-TB-008', 'Turbine Nozzle Guide Vane', 'Single crystal nickel alloy NGV', 'AERO', 'Turbine Components', 'Guide Vanes', 5800.00, 1.8, 45),
('AERO-TB-009', 'Combustor Liner Panel', 'Hastelloy X liner with thermal barrier', 'AERO', 'Turbine Components', 'Combustor Parts', 2800.00, 3.5, 30),
('AERO-TB-010', 'Turbine Seal - Labyrinth Type', 'Honeycomb seal for turbine section', 'AERO', 'Turbine Components', 'Seals', 890.00, 0.6, 21),
('AERO-TB-011', 'Fan Blade - Composite', 'Carbon fiber reinforced fan blade', 'AERO', 'Turbine Components', 'Fan Blades', 8500.00, 4.2, 45),
('AERO-TB-012', 'Fan Blade - Titanium', 'Ti-6Al-4V hollow fan blade', 'AERO', 'Turbine Components', 'Fan Blades', 6200.00, 5.8, 40),
('AERO-TB-013', 'Compressor Blade - Stage 1-3', 'Titanium compressor blade set', 'AERO', 'Turbine Components', 'Compressor Blades', 1450.00, 0.8, 25),
('AERO-TB-014', 'Compressor Blade - Stage 4-6', 'Titanium compressor blade set', 'AERO', 'Turbine Components', 'Compressor Blades', 1380.00, 0.7, 25),
('AERO-TB-015', 'Compressor Stator Vane', 'Variable stator vane assembly', 'AERO', 'Turbine Components', 'Stator Vanes', 2100.00, 1.2, 28),
('AERO-TB-016', 'Turbine Case - Forward', 'Titanium forward turbine case', 'AERO', 'Turbine Components', 'Cases', 45000.00, 120.0, 90),
('AERO-TB-017', 'Turbine Case - Aft', 'Nickel alloy aft turbine case', 'AERO', 'Turbine Components', 'Cases', 52000.00, 145.0, 90),
('AERO-TB-018', 'Bearing Housing - Main', 'Main shaft bearing housing', 'AERO', 'Turbine Components', 'Housings', 8900.00, 28.0, 45),
('AERO-TB-019', 'Oil Sump Assembly', 'Engine oil sump with baffles', 'AERO', 'Turbine Components', 'Housings', 4500.00, 12.0, 30),
('AERO-TB-020', 'Diffuser Case', 'Compressor discharge diffuser', 'AERO', 'Turbine Components', 'Cases', 28000.00, 85.0, 60),
('AERO-TB-021', 'Turbine Rotor Spacer', 'Inter-stage spacer ring', 'AERO', 'Turbine Components', 'Spacers', 1200.00, 4.5, 21),
('AERO-TB-022', 'Blade Retainer Ring', 'Turbine blade retention ring', 'AERO', 'Turbine Components', 'Retainers', 2800.00, 8.2, 28),
('AERO-TB-023', 'Fuel Nozzle Assembly', 'Dual-orifice fuel nozzle', 'AERO', 'Turbine Components', 'Fuel System', 3400.00, 0.9, 35),
('AERO-TB-024', 'Igniter Plug Assembly', 'High-energy igniter for combustor', 'AERO', 'Turbine Components', 'Ignition', 1800.00, 0.4, 21),
('AERO-TB-025', 'Exhaust Nozzle Flap', 'Variable exhaust nozzle segment', 'AERO', 'Turbine Components', 'Exhaust', 4200.00, 6.5, 35),

-- Landing Gear (25)
('AERO-LG-001', 'Landing Gear Strut Housing - Main', 'Main gear strut housing, 7075-T6 aluminum', 'AERO', 'Landing Gear', 'Strut Housings', 12500.00, 45.0, 45),
('AERO-LG-002', 'Landing Gear Strut Housing - Nose', 'Nose gear strut housing', 'AERO', 'Landing Gear', 'Strut Housings', 8500.00, 28.0, 40),
('AERO-LG-003', 'Landing Gear Cylinder - Main', 'Main gear shock strut cylinder', 'AERO', 'Landing Gear', 'Cylinders', 18000.00, 32.0, 50),
('AERO-LG-004', 'Landing Gear Cylinder - Nose', 'Nose gear shock strut cylinder', 'AERO', 'Landing Gear', 'Cylinders', 12000.00, 18.0, 45),
('AERO-LG-005', 'Gear Door Actuator', 'Hydraulic gear door actuator', 'AERO', 'Landing Gear', 'Actuators', 6500.00, 8.5, 35),
('AERO-LG-006', 'Retract Actuator - Main Gear', 'Main gear retraction actuator', 'AERO', 'Landing Gear', 'Actuators', 15000.00, 22.0, 45),
('AERO-LG-007', 'Retract Actuator - Nose Gear', 'Nose gear retraction actuator', 'AERO', 'Landing Gear', 'Actuators', 9500.00, 14.0, 40),
('AERO-LG-008', 'Torque Link Assembly - Upper', 'Upper torque link, steel alloy', 'AERO', 'Landing Gear', 'Torque Links', 4200.00, 12.0, 30),
('AERO-LG-009', 'Torque Link Assembly - Lower', 'Lower torque link, steel alloy', 'AERO', 'Landing Gear', 'Torque Links', 3800.00, 10.0, 30),
('AERO-LG-010', 'Axle Assembly - Main Gear', 'Main gear axle with bearings', 'AERO', 'Landing Gear', 'Axles', 8500.00, 35.0, 40),
('AERO-LG-011', 'Wheel Hub - Main', 'Main gear wheel hub forging', 'AERO', 'Landing Gear', 'Wheel Components', 3200.00, 15.0, 28),
('AERO-LG-012', 'Wheel Hub - Nose', 'Nose gear wheel hub', 'AERO', 'Landing Gear', 'Wheel Components', 2100.00, 8.0, 25),
('AERO-LG-013', 'Brake Assembly - Carbon', 'Carbon-carbon brake stack', 'AERO', 'Landing Gear', 'Brakes', 28000.00, 42.0, 60),
('AERO-LG-014', 'Brake Disc - Steel', 'Steel brake disc, heat treated', 'AERO', 'Landing Gear', 'Brakes', 4500.00, 18.0, 30),
('AERO-LG-015', 'Brake Caliper Housing', 'Multi-piston brake caliper', 'AERO', 'Landing Gear', 'Brakes', 6800.00, 12.0, 35),
('AERO-LG-016', 'Steering Collar', 'Nose gear steering collar', 'AERO', 'Landing Gear', 'Steering', 5500.00, 8.5, 30),
('AERO-LG-017', 'Steering Actuator', 'Nose wheel steering actuator', 'AERO', 'Landing Gear', 'Steering', 12000.00, 15.0, 40),
('AERO-LG-018', 'Downlock Mechanism', 'Gear down-and-locked mechanism', 'AERO', 'Landing Gear', 'Locks', 4800.00, 6.0, 28),
('AERO-LG-019', 'Uplock Mechanism', 'Gear up-and-locked mechanism', 'AERO', 'Landing Gear', 'Locks', 4200.00, 5.5, 28),
('AERO-LG-020', 'Trunnion Fitting - Main', 'Main gear trunnion attachment', 'AERO', 'Landing Gear', 'Fittings', 9500.00, 25.0, 40),
('AERO-LG-021', 'Trunnion Fitting - Nose', 'Nose gear trunnion attachment', 'AERO', 'Landing Gear', 'Fittings', 6500.00, 15.0, 35),
('AERO-LG-022', 'Drag Brace - Main', 'Main gear drag brace assembly', 'AERO', 'Landing Gear', 'Braces', 7200.00, 18.0, 35),
('AERO-LG-023', 'Side Brace - Main', 'Main gear side brace', 'AERO', 'Landing Gear', 'Braces', 6800.00, 16.0, 35),
('AERO-LG-024', 'Extension Spring Assembly', 'Gear extension assist spring', 'AERO', 'Landing Gear', 'Springs', 2200.00, 4.0, 21),
('AERO-LG-025', 'Position Sensor - LVDT', 'Gear position transducer', 'AERO', 'Landing Gear', 'Sensors', 3500.00, 0.8, 25),

-- Structural Components (25)
('AERO-ST-001', 'Wing Spar - Inboard', 'Titanium wing spar section', 'AERO', 'Structural', 'Wing Spars', 85000.00, 280.0, 90),
('AERO-ST-002', 'Wing Spar - Outboard', 'Aluminum wing spar section', 'AERO', 'Structural', 'Wing Spars', 45000.00, 120.0, 75),
('AERO-ST-003', 'Wing Rib - Standard', 'Machined aluminum wing rib', 'AERO', 'Structural', 'Wing Ribs', 4500.00, 8.0, 30),
('AERO-ST-004', 'Wing Rib - Heavy', 'Reinforced wing rib for pylons', 'AERO', 'Structural', 'Wing Ribs', 8500.00, 15.0, 40),
('AERO-ST-005', 'Fuselage Frame - Standard', 'Aluminum fuselage frame', 'AERO', 'Structural', 'Fuselage Frames', 6200.00, 12.0, 35),
('AERO-ST-006', 'Fuselage Frame - Heavy', 'Reinforced frame for door cutouts', 'AERO', 'Structural', 'Fuselage Frames', 12000.00, 25.0, 45),
('AERO-ST-007', 'Floor Beam', 'Cargo floor support beam', 'AERO', 'Structural', 'Floor Structure', 3800.00, 8.5, 28),
('AERO-ST-008', 'Seat Track', 'Passenger seat mounting track', 'AERO', 'Structural', 'Floor Structure', 1200.00, 4.0, 21),
('AERO-ST-009', 'Pressure Bulkhead - Forward', 'Forward pressure dome', 'AERO', 'Structural', 'Bulkheads', 28000.00, 85.0, 60),
('AERO-ST-010', 'Pressure Bulkhead - Aft', 'Aft pressure dome', 'AERO', 'Structural', 'Bulkheads', 32000.00, 95.0, 60),
('AERO-ST-011', 'Wing-to-Body Fitting', 'Main wing attachment fitting', 'AERO', 'Structural', 'Fittings', 45000.00, 120.0, 75),
('AERO-ST-012', 'Pylon Fitting', 'Engine pylon attachment', 'AERO', 'Structural', 'Fittings', 35000.00, 85.0, 60),
('AERO-ST-013', 'Horizontal Stabilizer Spar', 'Empennage spar', 'AERO', 'Structural', 'Empennage', 28000.00, 65.0, 50),
('AERO-ST-014', 'Vertical Stabilizer Spar', 'Vertical tail spar', 'AERO', 'Structural', 'Empennage', 22000.00, 55.0, 50),
('AERO-ST-015', 'Rudder Hinge Fitting', 'Rudder attachment fitting', 'AERO', 'Structural', 'Empennage', 5500.00, 8.0, 30),
('AERO-ST-016', 'Elevator Hinge Fitting', 'Elevator attachment fitting', 'AERO', 'Structural', 'Empennage', 4800.00, 6.5, 30),
('AERO-ST-017', 'Flap Track', 'Trailing edge flap track', 'AERO', 'Structural', 'Flight Controls', 18000.00, 45.0, 50),
('AERO-ST-018', 'Slat Track', 'Leading edge slat track', 'AERO', 'Structural', 'Flight Controls', 15000.00, 35.0, 45),
('AERO-ST-019', 'Aileron Hinge Bracket', 'Aileron mounting bracket', 'AERO', 'Structural', 'Flight Controls', 3200.00, 4.5, 25),
('AERO-ST-020', 'Spoiler Hinge Bracket', 'Spoiler mounting bracket', 'AERO', 'Structural', 'Flight Controls', 2800.00, 3.8, 25),
('AERO-ST-021', 'Door Frame - Passenger', 'Passenger door frame assembly', 'AERO', 'Structural', 'Doors', 25000.00, 65.0, 55),
('AERO-ST-022', 'Door Frame - Cargo', 'Cargo door frame assembly', 'AERO', 'Structural', 'Doors', 35000.00, 95.0, 60),
('AERO-ST-023', 'Window Frame', 'Passenger window frame', 'AERO', 'Structural', 'Windows', 1800.00, 2.5, 21),
('AERO-ST-024', 'Cockpit Window Frame', 'Flight deck window frame', 'AERO', 'Structural', 'Windows', 8500.00, 12.0, 35),
('AERO-ST-025', 'Radome Mount Ring', 'Nose radome attachment ring', 'AERO', 'Structural', 'Nose Section', 6500.00, 15.0, 35),

-- Fasteners & Hardware (25)
('AERO-FH-001', 'Hi-Lok Fastener Kit - Wing', 'Hi-Lok fastener assortment', 'AERO', 'Fasteners', 'Hi-Lok', 2500.00, 5.0, 14),
('AERO-FH-002', 'Hi-Lok Fastener Kit - Fuselage', 'Hi-Lok fastener set', 'AERO', 'Fasteners', 'Hi-Lok', 2200.00, 4.5, 14),
('AERO-FH-003', 'Titanium Bolt Set - Structural', 'Ti-6Al-4V structural bolts', 'AERO', 'Fasteners', 'Bolts', 1800.00, 2.0, 14),
('AERO-FH-004', 'Steel Bolt Set - Landing Gear', 'High-strength steel bolts', 'AERO', 'Fasteners', 'Bolts', 1500.00, 3.5, 14),
('AERO-FH-005', 'Nut Plate Assembly', 'Floating nut plate set', 'AERO', 'Fasteners', 'Nut Plates', 850.00, 1.2, 10),
('AERO-FH-006', 'Rivet Set - Aluminum', 'Flush head aluminum rivets', 'AERO', 'Fasteners', 'Rivets', 450.00, 2.0, 7),
('AERO-FH-007', 'Rivet Set - Titanium', 'Titanium blind rivets', 'AERO', 'Fasteners', 'Rivets', 1200.00, 1.5, 14),
('AERO-FH-008', 'Washer Set - Structural', 'AN960 washer assortment', 'AERO', 'Fasteners', 'Washers', 280.00, 0.8, 7),
('AERO-FH-009', 'Cotter Pin Kit', 'Stainless cotter pins', 'AERO', 'Fasteners', 'Pins', 180.00, 0.5, 7),
('AERO-FH-010', 'Quick Release Pin Set', 'Ball-lock quick release pins', 'AERO', 'Fasteners', 'Pins', 650.00, 0.8, 10),
('AERO-FH-011', 'Bushing Kit - Bronze', 'Oilite bronze bushings', 'AERO', 'Hardware', 'Bushings', 420.00, 1.0, 10),
('AERO-FH-012', 'Bushing Kit - Steel', 'Hardened steel bushings', 'AERO', 'Hardware', 'Bushings', 380.00, 1.2, 10),
('AERO-FH-013', 'Spherical Bearing Set', 'Self-aligning rod end bearings', 'AERO', 'Hardware', 'Bearings', 1800.00, 2.5, 14),
('AERO-FH-014', 'Journal Bearing Set', 'Plain journal bearings', 'AERO', 'Hardware', 'Bearings', 950.00, 1.8, 14),
('AERO-FH-015', 'Seal Kit - Hydraulic', 'O-ring and backup ring set', 'AERO', 'Hardware', 'Seals', 320.00, 0.3, 7),
('AERO-FH-016', 'Seal Kit - Pneumatic', 'Pneumatic seal assortment', 'AERO', 'Hardware', 'Seals', 280.00, 0.3, 7),
('AERO-FH-017', 'Gasket Set - Engine', 'High-temp engine gaskets', 'AERO', 'Hardware', 'Gaskets', 450.00, 0.5, 10),
('AERO-FH-018', 'Shim Kit - Precision', 'Precision alignment shims', 'AERO', 'Hardware', 'Shims', 220.00, 0.4, 7),
('AERO-FH-019', 'Safety Wire Spool', 'Stainless safety wire', 'AERO', 'Hardware', 'Wire', 85.00, 0.5, 5),
('AERO-FH-020', 'Clamp Set - Tube', 'Cushioned tube clamps', 'AERO', 'Hardware', 'Clamps', 180.00, 0.6, 7),
('AERO-FH-021', 'Clamp Set - Wire Bundle', 'Cable tie and clamp set', 'AERO', 'Hardware', 'Clamps', 120.00, 0.4, 7),
('AERO-FH-022', 'Spring Set - Tension', 'Extension spring assortment', 'AERO', 'Hardware', 'Springs', 350.00, 0.8, 10),
('AERO-FH-023', 'Spring Set - Compression', 'Compression spring set', 'AERO', 'Hardware', 'Springs', 320.00, 0.7, 10),
('AERO-FH-024', 'Clevis Pin Set', 'Clevis pins with cotter', 'AERO', 'Hardware', 'Pins', 280.00, 0.6, 7),
('AERO-FH-025', 'Taper Pin Set', 'Taper pins for alignment', 'AERO', 'Hardware', 'Pins', 240.00, 0.5, 7),

-- Avionics Housings (25)
('AERO-AV-001', 'Flight Computer Housing', 'EMI-shielded avionics enclosure', 'AERO', 'Avionics', 'Enclosures', 4500.00, 8.0, 30),
('AERO-AV-002', 'Navigation Display Housing', 'Cockpit display enclosure', 'AERO', 'Avionics', 'Enclosures', 3200.00, 5.5, 28),
('AERO-AV-003', 'Radar Antenna Mount', 'Weather radar antenna mount', 'AERO', 'Avionics', 'Mounts', 2800.00, 4.0, 25),
('AERO-AV-004', 'Transponder Housing', 'ATC transponder enclosure', 'AERO', 'Avionics', 'Enclosures', 1500.00, 2.0, 21),
('AERO-AV-005', 'Radio Rack Assembly', 'VHF/UHF radio mounting rack', 'AERO', 'Avionics', 'Racks', 2200.00, 6.0, 25),
('AERO-AV-006', 'TCAS Computer Mount', 'Traffic collision avoidance mount', 'AERO', 'Avionics', 'Mounts', 1800.00, 3.0, 21),
('AERO-AV-007', 'FDR Housing', 'Flight data recorder housing', 'AERO', 'Avionics', 'Enclosures', 5500.00, 12.0, 35),
('AERO-AV-008', 'CVR Housing', 'Cockpit voice recorder housing', 'AERO', 'Avionics', 'Enclosures', 4800.00, 10.0, 35),
('AERO-AV-009', 'GPS Antenna Base', 'GPS antenna mounting base', 'AERO', 'Avionics', 'Mounts', 850.00, 1.5, 14),
('AERO-AV-010', 'ILS Antenna Mount', 'Instrument landing system mount', 'AERO', 'Avionics', 'Mounts', 1200.00, 2.5, 18),
('AERO-AV-011', 'Satcom Antenna Fairing', 'Satellite communication fairing', 'AERO', 'Avionics', 'Fairings', 8500.00, 15.0, 40),
('AERO-AV-012', 'ELT Mount Bracket', 'Emergency locator transmitter mount', 'AERO', 'Avionics', 'Mounts', 650.00, 1.0, 14),
('AERO-AV-013', 'ADC Housing', 'Air data computer housing', 'AERO', 'Avionics', 'Enclosures', 2500.00, 4.0, 25),
('AERO-AV-014', 'AHRS Mount', 'Attitude heading reference mount', 'AERO', 'Avionics', 'Mounts', 1800.00, 2.5, 21),
('AERO-AV-015', 'DME Antenna Base', 'Distance measuring equipment base', 'AERO', 'Avionics', 'Mounts', 950.00, 1.8, 18),
('AERO-AV-016', 'Autopilot Servo Mount', 'Flight control servo mount', 'AERO', 'Avionics', 'Mounts', 1400.00, 2.2, 21),
('AERO-AV-017', 'Yaw Damper Mount', 'Yaw damper actuator mount', 'AERO', 'Avionics', 'Mounts', 1200.00, 2.0, 21),
('AERO-AV-018', 'Stick Shaker Mount', 'Stall warning system mount', 'AERO', 'Avionics', 'Mounts', 850.00, 1.5, 18),
('AERO-AV-019', 'AOA Sensor Housing', 'Angle of attack sensor housing', 'AERO', 'Avionics', 'Enclosures', 1100.00, 1.2, 18),
('AERO-AV-020', 'Pitot Probe Mount', 'Pitot static probe mount', 'AERO', 'Avionics', 'Mounts', 750.00, 0.8, 14),
('AERO-AV-021', 'Static Port Cover', 'Flush static port assembly', 'AERO', 'Avionics', 'Enclosures', 280.00, 0.2, 10),
('AERO-AV-022', 'TAT Probe Mount', 'Total air temperature mount', 'AERO', 'Avionics', 'Mounts', 650.00, 0.6, 14),
('AERO-AV-023', 'Ice Detector Mount', 'Ice detection sensor mount', 'AERO', 'Avionics', 'Mounts', 480.00, 0.5, 14),
('AERO-AV-024', 'Lightning Sensor Base', 'Stormscope antenna base', 'AERO', 'Avionics', 'Mounts', 550.00, 0.8, 14),
('AERO-AV-025', 'EFIS Bezel', 'Electronic flight instrument bezel', 'AERO', 'Avionics', 'Enclosures', 1200.00, 1.5, 21);

-- =============================================================================
-- ENERGY DIVISION PRODUCTS (125 SKUs)
-- =============================================================================

INSERT INTO products (sku, name, description, division_id, category, subcategory, unit_price, weight_kg, lead_time_days) VALUES
-- Wind Turbine Components (50)
('ENGY-WT-001', 'Gearbox Housing - 2MW', 'Main gearbox housing casting', 'ENERGY', 'Wind Turbine', 'Gearbox', 125000.00, 2500.0, 120),
('ENGY-WT-002', 'Gearbox Housing - 4MW', 'Offshore turbine gearbox housing', 'ENERGY', 'Wind Turbine', 'Gearbox', 185000.00, 3800.0, 150),
('ENGY-WT-003', 'Planet Carrier - Stage 1', 'First stage planetary carrier', 'ENERGY', 'Wind Turbine', 'Gearbox', 45000.00, 650.0, 75),
('ENGY-WT-004', 'Planet Carrier - Stage 2', 'Second stage planetary carrier', 'ENERGY', 'Wind Turbine', 'Gearbox', 38000.00, 520.0, 70),
('ENGY-WT-005', 'Ring Gear - Internal', 'Internal ring gear forging', 'ENERGY', 'Wind Turbine', 'Gearbox', 55000.00, 850.0, 90),
('ENGY-WT-006', 'Sun Gear Shaft', 'High-speed sun gear shaft', 'ENERGY', 'Wind Turbine', 'Gearbox', 22000.00, 180.0, 60),
('ENGY-WT-007', 'Planet Gear Set', 'Planetary gear set (3 gears)', 'ENERGY', 'Wind Turbine', 'Gearbox', 35000.00, 420.0, 65),
('ENGY-WT-008', 'Main Shaft Bearing', 'Spherical roller main bearing', 'ENERGY', 'Wind Turbine', 'Bearings', 28000.00, 450.0, 45),
('ENGY-WT-009', 'Generator Bearing Set', 'Generator bearing pair', 'ENERGY', 'Wind Turbine', 'Bearings', 18000.00, 85.0, 35),
('ENGY-WT-010', 'Gearbox Bearing Kit', 'Complete gearbox bearing set', 'ENERGY', 'Wind Turbine', 'Bearings', 42000.00, 320.0, 50),
('ENGY-WT-011', 'Main Shaft Forging', 'Low-speed main shaft', 'ENERGY', 'Wind Turbine', 'Shafts', 85000.00, 12000.0, 120),
('ENGY-WT-012', 'High-Speed Shaft', 'Gearbox output shaft', 'ENERGY', 'Wind Turbine', 'Shafts', 15000.00, 120.0, 45),
('ENGY-WT-013', 'Brake Disc - Main', 'Main shaft brake disc', 'ENERGY', 'Wind Turbine', 'Brakes', 12000.00, 350.0, 40),
('ENGY-WT-014', 'Brake Caliper Assembly', 'Hydraulic brake caliper', 'ENERGY', 'Wind Turbine', 'Brakes', 8500.00, 85.0, 35),
('ENGY-WT-015', 'Yaw Bearing', 'Slewing ring yaw bearing', 'ENERGY', 'Wind Turbine', 'Bearings', 65000.00, 2800.0, 90),
('ENGY-WT-016', 'Pitch Bearing', 'Blade pitch bearing', 'ENERGY', 'Wind Turbine', 'Bearings', 35000.00, 650.0, 60),
('ENGY-WT-017', 'Yaw Drive Gearbox', 'Yaw drive planetary gearbox', 'ENERGY', 'Wind Turbine', 'Drives', 18000.00, 280.0, 50),
('ENGY-WT-018', 'Pitch Drive Unit', 'Electric pitch drive system', 'ENERGY', 'Wind Turbine', 'Drives', 22000.00, 150.0, 45),
('ENGY-WT-019', 'Hub Casting', 'Three-blade hub casting', 'ENERGY', 'Wind Turbine', 'Structural', 95000.00, 8500.0, 120),
('ENGY-WT-020', 'Nacelle Frame', 'Main nacelle bedplate', 'ENERGY', 'Wind Turbine', 'Structural', 75000.00, 5500.0, 100),
('ENGY-WT-021', 'Tower Flange - Base', 'Tower base flange forging', 'ENERGY', 'Wind Turbine', 'Tower', 45000.00, 3200.0, 75),
('ENGY-WT-022', 'Tower Flange - Mid', 'Tower section flange', 'ENERGY', 'Wind Turbine', 'Tower', 28000.00, 1800.0, 60),
('ENGY-WT-023', 'Tower Door Frame', 'Tower access door frame', 'ENERGY', 'Wind Turbine', 'Tower', 8500.00, 450.0, 35),
('ENGY-WT-024', 'Blade Root Insert', 'Blade root attachment insert', 'ENERGY', 'Wind Turbine', 'Blades', 2200.00, 25.0, 28),
('ENGY-WT-025', 'Blade Bolt Set', 'High-strength blade bolts (set)', 'ENERGY', 'Wind Turbine', 'Fasteners', 3500.00, 45.0, 21),
('ENGY-WT-026', 'Generator Rotor', 'Permanent magnet generator rotor', 'ENERGY', 'Wind Turbine', 'Generator', 85000.00, 2200.0, 90),
('ENGY-WT-027', 'Generator Stator', 'Direct-drive generator stator', 'ENERGY', 'Wind Turbine', 'Generator', 120000.00, 4500.0, 120),
('ENGY-WT-028', 'Converter Cabinet', 'Power converter housing', 'ENERGY', 'Wind Turbine', 'Electrical', 15000.00, 850.0, 45),
('ENGY-WT-029', 'Transformer Mount', 'Nacelle transformer platform', 'ENERGY', 'Wind Turbine', 'Electrical', 8500.00, 450.0, 35),
('ENGY-WT-030', 'Slip Ring Assembly', 'Pitch slip ring unit', 'ENERGY', 'Wind Turbine', 'Electrical', 4500.00, 35.0, 28),
('ENGY-WT-031', 'Coupling - Main Shaft', 'Flexible shaft coupling', 'ENERGY', 'Wind Turbine', 'Couplings', 12000.00, 280.0, 40),
('ENGY-WT-032', 'Coupling - Generator', 'Generator input coupling', 'ENERGY', 'Wind Turbine', 'Couplings', 6500.00, 85.0, 30),
('ENGY-WT-033', 'Oil Cooler', 'Gearbox oil cooling unit', 'ENERGY', 'Wind Turbine', 'Cooling', 8500.00, 120.0, 35),
('ENGY-WT-034', 'Hydraulic Power Unit', 'Pitch/brake hydraulic unit', 'ENERGY', 'Wind Turbine', 'Hydraulics', 18000.00, 350.0, 45),
('ENGY-WT-035', 'Accumulator - Pitch', 'Pitch system accumulator', 'ENERGY', 'Wind Turbine', 'Hydraulics', 3500.00, 45.0, 25),
('ENGY-WT-036', 'Filter Assembly - Oil', 'Gearbox oil filter housing', 'ENERGY', 'Wind Turbine', 'Filtration', 1200.00, 8.0, 14),
('ENGY-WT-037', 'Filter Assembly - Air', 'Nacelle ventilation filter', 'ENERGY', 'Wind Turbine', 'Filtration', 450.00, 3.0, 10),
('ENGY-WT-038', 'Seal Kit - Main Shaft', 'Main shaft seal set', 'ENERGY', 'Wind Turbine', 'Seals', 2800.00, 5.0, 21),
('ENGY-WT-039', 'Seal Kit - Gearbox', 'Gearbox shaft seal kit', 'ENERGY', 'Wind Turbine', 'Seals', 1800.00, 3.0, 18),
('ENGY-WT-040', 'Anemometer Mount', 'Nacelle wind sensor mount', 'ENERGY', 'Wind Turbine', 'Sensors', 650.00, 2.5, 14),
('ENGY-WT-041', 'Vibration Sensor Mount', 'Gearbox vibration monitor mount', 'ENERGY', 'Wind Turbine', 'Sensors', 380.00, 0.8, 10),
('ENGY-WT-042', 'Lightning Receptor', 'Blade lightning protection tip', 'ENERGY', 'Wind Turbine', 'Safety', 1500.00, 2.0, 18),
('ENGY-WT-043', 'Aviation Light Mount', 'Obstruction light bracket', 'ENERGY', 'Wind Turbine', 'Safety', 280.00, 1.2, 10),
('ENGY-WT-044', 'Service Crane Mount', 'Nacelle service crane base', 'ENERGY', 'Wind Turbine', 'Service', 4500.00, 85.0, 30),
('ENGY-WT-045', 'Ladder Section', 'Internal tower ladder section', 'ENERGY', 'Wind Turbine', 'Access', 1200.00, 35.0, 21),
('ENGY-WT-046', 'Platform Grating', 'Service platform floor grating', 'ENERGY', 'Wind Turbine', 'Access', 850.00, 45.0, 18),
('ENGY-WT-047', 'Cable Tray Section', 'Power cable routing tray', 'ENERGY', 'Wind Turbine', 'Electrical', 180.00, 8.0, 10),
('ENGY-WT-048', 'Grounding Kit', 'Tower grounding components', 'ENERGY', 'Wind Turbine', 'Electrical', 650.00, 12.0, 14),
('ENGY-WT-049', 'Foundation Anchor Cage', 'Foundation anchor bolt cage', 'ENERGY', 'Wind Turbine', 'Foundation', 25000.00, 2800.0, 45),
('ENGY-WT-050', 'Grout Plate', 'Tower base grout leveling plate', 'ENERGY', 'Wind Turbine', 'Foundation', 3500.00, 450.0, 25),

-- Solar Components (25)
('ENGY-SL-001', 'Solar Frame Rail - 4m', 'Anodized aluminum rail', 'ENERGY', 'Solar', 'Mounting', 85.00, 4.2, 10),
('ENGY-SL-002', 'Solar Frame Rail - 6m', 'Extended mounting rail', 'ENERGY', 'Solar', 'Mounting', 125.00, 6.3, 10),
('ENGY-SL-003', 'Mid Clamp Set', 'Panel mid clamp (4 pack)', 'ENERGY', 'Solar', 'Mounting', 18.00, 0.4, 7),
('ENGY-SL-004', 'End Clamp Set', 'Panel end clamp (4 pack)', 'ENERGY', 'Solar', 'Mounting', 22.00, 0.5, 7),
('ENGY-SL-005', 'L-Foot Mount', 'Roof L-foot bracket', 'ENERGY', 'Solar', 'Mounting', 8.50, 0.3, 7),
('ENGY-SL-006', 'Tile Hook', 'Tile roof attachment hook', 'ENERGY', 'Solar', 'Mounting', 12.00, 0.4, 7),
('ENGY-SL-007', 'Ground Mount Post', 'Steel ground mount post', 'ENERGY', 'Solar', 'Mounting', 45.00, 8.5, 14),
('ENGY-SL-008', 'Tracker Motor Mount', 'Single-axis tracker motor base', 'ENERGY', 'Solar', 'Trackers', 280.00, 15.0, 21),
('ENGY-SL-009', 'Tracker Bearing', 'Tracker rotation bearing', 'ENERGY', 'Solar', 'Trackers', 180.00, 8.0, 18),
('ENGY-SL-010', 'Tracker Controller Housing', 'Tracker control enclosure', 'ENERGY', 'Solar', 'Trackers', 120.00, 3.5, 14),
('ENGY-SL-011', 'Combiner Box - 8 String', '8-string combiner enclosure', 'ENERGY', 'Solar', 'Electrical', 350.00, 8.0, 18),
('ENGY-SL-012', 'Combiner Box - 16 String', '16-string combiner enclosure', 'ENERGY', 'Solar', 'Electrical', 520.00, 12.0, 18),
('ENGY-SL-013', 'Junction Box', 'Field junction box', 'ENERGY', 'Solar', 'Electrical', 85.00, 2.5, 10),
('ENGY-SL-014', 'Cable Gland Kit', 'Waterproof cable entry kit', 'ENERGY', 'Solar', 'Electrical', 28.00, 0.3, 7),
('ENGY-SL-015', 'Grounding Lug Set', 'Equipment grounding lugs', 'ENERGY', 'Solar', 'Electrical', 15.00, 0.2, 7),
('ENGY-SL-016', 'Inverter Mounting Plate', 'String inverter wall mount', 'ENERGY', 'Solar', 'Mounting', 65.00, 4.5, 14),
('ENGY-SL-017', 'Inverter Housing - 5kW', '5kW inverter enclosure', 'ENERGY', 'Solar', 'Enclosures', 180.00, 12.0, 21),
('ENGY-SL-018', 'Inverter Housing - 10kW', '10kW inverter enclosure', 'ENERGY', 'Solar', 'Enclosures', 280.00, 18.0, 21),
('ENGY-SL-019', 'Central Inverter Pad', 'Concrete inverter mounting pad', 'ENERGY', 'Solar', 'Mounting', 450.00, 250.0, 28),
('ENGY-SL-020', 'Rapid Shutdown Box', 'NEC rapid shutdown enclosure', 'ENERGY', 'Solar', 'Safety', 220.00, 4.0, 14),
('ENGY-SL-021', 'DC Disconnect Housing', 'DC isolation switch housing', 'ENERGY', 'Solar', 'Safety', 180.00, 5.0, 14),
('ENGY-SL-022', 'AC Disconnect Housing', 'AC isolation switch housing', 'ENERGY', 'Solar', 'Safety', 150.00, 4.5, 14),
('ENGY-SL-023', 'Meter Socket', 'Net metering socket base', 'ENERGY', 'Solar', 'Metering', 85.00, 2.0, 10),
('ENGY-SL-024', 'Production Meter Box', 'PV production meter enclosure', 'ENERGY', 'Solar', 'Metering', 120.00, 3.0, 14),
('ENGY-SL-025', 'Weather Station Mount', 'Irradiance sensor mounting', 'ENERGY', 'Solar', 'Monitoring', 95.00, 2.5, 14),

-- Pipeline & Valve Components (50)
('ENGY-PV-001', 'Gate Valve Body - 8 inch', 'API 600 gate valve body', 'ENERGY', 'Pipeline', 'Valve Bodies', 4500.00, 85.0, 45),
('ENGY-PV-002', 'Gate Valve Body - 12 inch', 'API 600 gate valve body', 'ENERGY', 'Pipeline', 'Valve Bodies', 8500.00, 150.0, 50),
('ENGY-PV-003', 'Gate Valve Body - 24 inch', 'Large bore gate valve', 'ENERGY', 'Pipeline', 'Valve Bodies', 25000.00, 450.0, 75),
('ENGY-PV-004', 'Ball Valve Body - 4 inch', 'Trunnion ball valve body', 'ENERGY', 'Pipeline', 'Valve Bodies', 2800.00, 35.0, 35),
('ENGY-PV-005', 'Ball Valve Body - 8 inch', 'Trunnion ball valve body', 'ENERGY', 'Pipeline', 'Valve Bodies', 6500.00, 95.0, 45),
('ENGY-PV-006', 'Ball Valve Body - 16 inch', 'Large bore ball valve', 'ENERGY', 'Pipeline', 'Valve Bodies', 18000.00, 280.0, 60),
('ENGY-PV-007', 'Check Valve Body - 6 inch', 'Swing check valve body', 'ENERGY', 'Pipeline', 'Valve Bodies', 1800.00, 28.0, 30),
('ENGY-PV-008', 'Check Valve Body - 10 inch', 'Swing check valve body', 'ENERGY', 'Pipeline', 'Valve Bodies', 3500.00, 65.0, 40),
('ENGY-PV-009', 'Butterfly Valve Body - 12 inch', 'Triple offset butterfly', 'ENERGY', 'Pipeline', 'Valve Bodies', 5500.00, 45.0, 40),
('ENGY-PV-010', 'Butterfly Valve Body - 24 inch', 'Triple offset butterfly', 'ENERGY', 'Pipeline', 'Valve Bodies', 15000.00, 120.0, 55),
('ENGY-PV-011', 'Valve Bonnet - Bolted', 'Standard bolted bonnet', 'ENERGY', 'Pipeline', 'Valve Parts', 1200.00, 18.0, 25),
('ENGY-PV-012', 'Valve Bonnet - Pressure Seal', 'High-pressure seal bonnet', 'ENERGY', 'Pipeline', 'Valve Parts', 2800.00, 35.0, 35),
('ENGY-PV-013', 'Valve Stem - Rising', 'Rising stem with handwheel', 'ENERGY', 'Pipeline', 'Valve Parts', 650.00, 8.0, 21),
('ENGY-PV-014', 'Valve Stem - Non-Rising', 'Non-rising stem assembly', 'ENERGY', 'Pipeline', 'Valve Parts', 580.00, 6.5, 21),
('ENGY-PV-015', 'Gate Disc - Solid', 'Solid wedge gate disc', 'ENERGY', 'Pipeline', 'Valve Parts', 850.00, 12.0, 25),
('ENGY-PV-016', 'Gate Disc - Flexible', 'Flexible wedge gate disc', 'ENERGY', 'Pipeline', 'Valve Parts', 1100.00, 15.0, 28),
('ENGY-PV-017', 'Ball - Trunnion', 'Chrome-plated ball assembly', 'ENERGY', 'Pipeline', 'Valve Parts', 1800.00, 25.0, 30),
('ENGY-PV-018', 'Seat Ring Set', 'Metal seat ring pair', 'ENERGY', 'Pipeline', 'Valve Parts', 450.00, 3.0, 18),
('ENGY-PV-019', 'Packing Set', 'Graphite packing set', 'ENERGY', 'Pipeline', 'Valve Parts', 120.00, 0.5, 10),
('ENGY-PV-020', 'Gasket Kit - RTJ', 'Ring type joint gasket kit', 'ENERGY', 'Pipeline', 'Seals', 280.00, 1.0, 14),
('ENGY-PV-021', 'Gasket Kit - Spiral', 'Spiral wound gasket kit', 'ENERGY', 'Pipeline', 'Seals', 180.00, 0.8, 14),
('ENGY-PV-022', 'Actuator Mount - Pneumatic', 'Pneumatic actuator bracket', 'ENERGY', 'Pipeline', 'Actuators', 850.00, 12.0, 21),
('ENGY-PV-023', 'Actuator Mount - Electric', 'Electric actuator bracket', 'ENERGY', 'Pipeline', 'Actuators', 950.00, 15.0, 21),
('ENGY-PV-024', 'Handwheel - 12 inch', 'Cast iron handwheel', 'ENERGY', 'Pipeline', 'Operators', 180.00, 8.0, 14),
('ENGY-PV-025', 'Handwheel - 18 inch', 'Large cast iron handwheel', 'ENERGY', 'Pipeline', 'Operators', 280.00, 15.0, 14),
('ENGY-PV-026', 'Gear Operator - Bevel', 'Bevel gear operator', 'ENERGY', 'Pipeline', 'Operators', 1500.00, 25.0, 25),
('ENGY-PV-027', 'Gear Operator - Worm', 'Worm gear operator', 'ENERGY', 'Pipeline', 'Operators', 1800.00, 35.0, 28),
('ENGY-PV-028', 'Limit Switch Box', 'Valve position indicator box', 'ENERGY', 'Pipeline', 'Controls', 450.00, 3.0, 18),
('ENGY-PV-029', 'Solenoid Valve Mount', 'Pilot solenoid mounting', 'ENERGY', 'Pipeline', 'Controls', 220.00, 1.5, 14),
('ENGY-PV-030', 'Filter/Regulator Mount', 'Air prep mounting bracket', 'ENERGY', 'Pipeline', 'Controls', 85.00, 0.8, 10),
('ENGY-PV-031', 'Flange - Weld Neck 6 inch', '150# weld neck flange', 'ENERGY', 'Pipeline', 'Flanges', 180.00, 8.0, 14),
('ENGY-PV-032', 'Flange - Weld Neck 12 inch', '150# weld neck flange', 'ENERGY', 'Pipeline', 'Flanges', 450.00, 25.0, 18),
('ENGY-PV-033', 'Flange - Blind 8 inch', '150# blind flange', 'ENERGY', 'Pipeline', 'Flanges', 120.00, 12.0, 14),
('ENGY-PV-034', 'Flange - Blind 16 inch', '150# blind flange', 'ENERGY', 'Pipeline', 'Flanges', 380.00, 45.0, 18),
('ENGY-PV-035', 'Elbow - 90 deg 6 inch', 'Long radius 90 elbow', 'ENERGY', 'Pipeline', 'Fittings', 85.00, 5.0, 10),
('ENGY-PV-036', 'Elbow - 45 deg 8 inch', '45 degree elbow', 'ENERGY', 'Pipeline', 'Fittings', 95.00, 6.5, 10),
('ENGY-PV-037', 'Tee - Equal 6 inch', 'Equal tee fitting', 'ENERGY', 'Pipeline', 'Fittings', 120.00, 8.0, 14),
('ENGY-PV-038', 'Reducer - Con 8x6 inch', 'Concentric reducer', 'ENERGY', 'Pipeline', 'Fittings', 65.00, 4.0, 10),
('ENGY-PV-039', 'Reducer - Ecc 10x8 inch', 'Eccentric reducer', 'ENERGY', 'Pipeline', 'Fittings', 95.00, 6.0, 10),
('ENGY-PV-040', 'Cap - 6 inch', 'Pipe cap fitting', 'ENERGY', 'Pipeline', 'Fittings', 45.00, 2.5, 7),
('ENGY-PV-041', 'Coupling - 4 inch', 'Threaded coupling', 'ENERGY', 'Pipeline', 'Fittings', 35.00, 1.8, 7),
('ENGY-PV-042', 'Union - 2 inch', 'Threaded union', 'ENERGY', 'Pipeline', 'Fittings', 28.00, 0.8, 7),
('ENGY-PV-043', 'Stud Bolt Set - 3/4 inch', 'B7 stud bolt set (10)', 'ENERGY', 'Pipeline', 'Fasteners', 45.00, 2.0, 7),
('ENGY-PV-044', 'Stud Bolt Set - 1 inch', 'B7 stud bolt set (10)', 'ENERGY', 'Pipeline', 'Fasteners', 85.00, 4.5, 7),
('ENGY-PV-045', 'Stud Bolt Set - 1-1/4 inch', 'B7 stud bolt set (10)', 'ENERGY', 'Pipeline', 'Fasteners', 120.00, 6.0, 10),
('ENGY-PV-046', 'Pig Launcher Barrel', 'Pipeline pig launcher', 'ENERGY', 'Pipeline', 'Pigging', 18000.00, 450.0, 60),
('ENGY-PV-047', 'Pig Receiver Barrel', 'Pipeline pig receiver', 'ENERGY', 'Pipeline', 'Pigging', 15000.00, 380.0, 55),
('ENGY-PV-048', 'Pig Signaler', 'Pig passage indicator', 'ENERGY', 'Pipeline', 'Pigging', 850.00, 5.0, 21),
('ENGY-PV-049', 'Insulation Kit - Valve', 'Removable valve insulation', 'ENERGY', 'Pipeline', 'Insulation', 350.00, 8.0, 14),
('ENGY-PV-050', 'Heat Trace Junction', 'Heat trace junction box', 'ENERGY', 'Pipeline', 'Heat Trace', 180.00, 2.5, 14);

-- =============================================================================
-- MOBILITY DIVISION PRODUCTS (125 SKUs)
-- =============================================================================

INSERT INTO products (sku, name, description, division_id, category, subcategory, unit_price, weight_kg, lead_time_days) VALUES
-- EV Motor Components (40)
('MOBL-MH-001', 'EV Motor Housing - 150kW', 'Die-cast aluminum motor housing', 'MOBILITY', 'EV Motors', 'Housings', 890.00, 18.0, 30),
('MOBL-MH-002', 'EV Motor Housing - 250kW', 'Performance motor housing', 'MOBILITY', 'EV Motors', 'Housings', 1250.00, 25.0, 35),
('MOBL-MH-003', 'EV Motor Housing - 350kW', 'Dual motor unit housing', 'MOBILITY', 'EV Motors', 'Housings', 1650.00, 35.0, 40),
('MOBL-MH-004', 'Motor End Bell - Drive End', 'Front end bell casting', 'MOBILITY', 'EV Motors', 'End Bells', 320.00, 6.5, 21),
('MOBL-MH-005', 'Motor End Bell - Non-Drive', 'Rear end bell casting', 'MOBILITY', 'EV Motors', 'End Bells', 280.00, 5.5, 21),
('MOBL-MH-006', 'Rotor Shaft - IPM', 'Interior PM motor shaft', 'MOBILITY', 'EV Motors', 'Shafts', 450.00, 8.0, 25),
('MOBL-MH-007', 'Rotor Shaft - Induction', 'Induction motor shaft', 'MOBILITY', 'EV Motors', 'Shafts', 380.00, 7.0, 25),
('MOBL-MH-008', 'Stator Lamination Stack', 'Silicon steel stator stack', 'MOBILITY', 'EV Motors', 'Laminations', 520.00, 12.0, 28),
('MOBL-MH-009', 'Rotor Lamination Stack', 'Silicon steel rotor stack', 'MOBILITY', 'EV Motors', 'Laminations', 380.00, 8.0, 28),
('MOBL-MH-010', 'Motor Bearing - Drive End', 'High-speed motor bearing', 'MOBILITY', 'EV Motors', 'Bearings', 185.00, 0.8, 14),
('MOBL-MH-011', 'Motor Bearing - Non-Drive', 'Motor support bearing', 'MOBILITY', 'EV Motors', 'Bearings', 145.00, 0.6, 14),
('MOBL-MH-012', 'Resolver Mount', 'Position sensor mounting', 'MOBILITY', 'EV Motors', 'Sensors', 85.00, 0.3, 14),
('MOBL-MH-013', 'Motor Seal Kit', 'Shaft and housing seal set', 'MOBILITY', 'EV Motors', 'Seals', 65.00, 0.2, 10),
('MOBL-MH-014', 'Cooling Jacket', 'Water cooling jacket', 'MOBILITY', 'EV Motors', 'Cooling', 420.00, 4.5, 25),
('MOBL-MH-015', 'Oil Spray Ring', 'Internal oil cooling ring', 'MOBILITY', 'EV Motors', 'Cooling', 180.00, 0.8, 18),
('MOBL-MH-016', 'Terminal Block', 'High-current motor terminal', 'MOBILITY', 'EV Motors', 'Electrical', 95.00, 0.4, 14),
('MOBL-MH-017', 'Bus Bar Assembly', 'Phase bus bar set', 'MOBILITY', 'EV Motors', 'Electrical', 220.00, 1.2, 18),
('MOBL-MH-018', 'Connector Housing', 'High-voltage connector mount', 'MOBILITY', 'EV Motors', 'Electrical', 145.00, 0.5, 14),
('MOBL-MH-019', 'Motor Mount Bracket - Front', 'Front motor mount', 'MOBILITY', 'EV Motors', 'Mounts', 280.00, 3.5, 21),
('MOBL-MH-020', 'Motor Mount Bracket - Rear', 'Rear motor mount', 'MOBILITY', 'EV Motors', 'Mounts', 250.00, 3.0, 21),

-- Battery Components (40)
('MOBL-BT-001', 'Battery Enclosure - 60kWh', 'Aluminum battery pack enclosure', 'MOBILITY', 'Battery', 'Enclosures', 2800.00, 65.0, 45),
('MOBL-BT-002', 'Battery Enclosure - 100kWh', 'Large pack enclosure', 'MOBILITY', 'Battery', 'Enclosures', 3800.00, 95.0, 50),
('MOBL-BT-003', 'Battery Cover - Structural', 'Load-bearing battery cover', 'MOBILITY', 'Battery', 'Covers', 850.00, 25.0, 35),
('MOBL-BT-004', 'Module Housing - Standard', 'Battery module housing', 'MOBILITY', 'Battery', 'Module Parts', 180.00, 2.5, 21),
('MOBL-BT-005', 'Module End Plate', 'Compression end plate', 'MOBILITY', 'Battery', 'Module Parts', 45.00, 0.8, 14),
('MOBL-BT-006', 'Cell Holder Tray', 'Cylindrical cell holder', 'MOBILITY', 'Battery', 'Module Parts', 35.00, 0.5, 14),
('MOBL-BT-007', 'Bus Bar - Cell Level', 'Nickel cell bus bar', 'MOBILITY', 'Battery', 'Bus Bars', 8.50, 0.05, 10),
('MOBL-BT-008', 'Bus Bar - Module Level', 'Copper module bus bar', 'MOBILITY', 'Battery', 'Bus Bars', 45.00, 0.3, 14),
('MOBL-BT-009', 'Bus Bar - Pack Level', 'High-current pack bus bar', 'MOBILITY', 'Battery', 'Bus Bars', 120.00, 0.8, 18),
('MOBL-BT-010', 'Cooling Plate - Single', 'Single module cold plate', 'MOBILITY', 'Battery', 'Thermal', 280.00, 3.5, 25),
('MOBL-BT-011', 'Cooling Plate - Dual', 'Dual module cold plate', 'MOBILITY', 'Battery', 'Thermal', 450.00, 6.0, 28),
('MOBL-BT-012', 'Thermal Gap Filler', 'Thermally conductive pad', 'MOBILITY', 'Battery', 'Thermal', 25.00, 0.1, 10),
('MOBL-BT-013', 'Thermal Interface Material', 'TIM dispensing compound', 'MOBILITY', 'Battery', 'Thermal', 85.00, 0.5, 14),
('MOBL-BT-014', 'Coolant Manifold', 'Pack cooling manifold', 'MOBILITY', 'Battery', 'Thermal', 180.00, 1.5, 21),
('MOBL-BT-015', 'Quick Connect Fitting', 'Coolant quick disconnect', 'MOBILITY', 'Battery', 'Thermal', 35.00, 0.15, 10),
('MOBL-BT-016', 'BMS Housing', 'Battery management enclosure', 'MOBILITY', 'Battery', 'Electronics', 220.00, 2.0, 21),
('MOBL-BT-017', 'Cell Monitoring Board', 'CMU mounting bracket', 'MOBILITY', 'Battery', 'Electronics', 45.00, 0.2, 14),
('MOBL-BT-018', 'HV Connector - Pack', 'High-voltage pack connector', 'MOBILITY', 'Battery', 'Connectors', 180.00, 0.5, 18),
('MOBL-BT-019', 'LV Connector - BMS', 'Low-voltage BMS connector', 'MOBILITY', 'Battery', 'Connectors', 25.00, 0.1, 10),
('MOBL-BT-020', 'Service Disconnect Housing', 'Manual disconnect enclosure', 'MOBILITY', 'Battery', 'Safety', 120.00, 0.8, 18),
('MOBL-BT-021', 'Contactor Mount', 'Main contactor bracket', 'MOBILITY', 'Battery', 'Safety', 65.00, 0.4, 14),
('MOBL-BT-022', 'Fuse Holder', 'Pack fuse holder', 'MOBILITY', 'Battery', 'Safety', 85.00, 0.3, 14),
('MOBL-BT-023', 'Current Sensor Mount', 'Hall effect sensor mount', 'MOBILITY', 'Battery', 'Sensors', 28.00, 0.1, 10),
('MOBL-BT-024', 'Vent Valve Housing', 'Pressure relief valve mount', 'MOBILITY', 'Battery', 'Safety', 45.00, 0.2, 14),
('MOBL-BT-025', 'Mounting Bracket Set', 'Pack mounting brackets', 'MOBILITY', 'Battery', 'Mounts', 180.00, 2.5, 21),

-- Drivetrain Components (45)
('MOBL-DT-001', 'Reduction Gearbox Housing', 'Single-speed reducer housing', 'MOBILITY', 'Drivetrain', 'Gearbox', 650.00, 12.0, 30),
('MOBL-DT-002', 'Differential Housing', 'Open differential housing', 'MOBILITY', 'Drivetrain', 'Differential', 520.00, 8.0, 28),
('MOBL-DT-003', 'Half Shaft - Inner', 'CV joint inner shaft', 'MOBILITY', 'Drivetrain', 'Shafts', 280.00, 4.5, 21),
('MOBL-DT-004', 'Half Shaft - Outer', 'CV joint outer shaft', 'MOBILITY', 'Drivetrain', 'Shafts', 320.00, 5.0, 21),
('MOBL-DT-005', 'CV Joint - Inner', 'Tripod inner CV joint', 'MOBILITY', 'Drivetrain', 'CV Joints', 145.00, 1.5, 18),
('MOBL-DT-006', 'CV Joint - Outer', 'Rzeppa outer CV joint', 'MOBILITY', 'Drivetrain', 'CV Joints', 180.00, 2.0, 18),
('MOBL-DT-007', 'CV Boot - Inner', 'Tripod joint boot', 'MOBILITY', 'Drivetrain', 'Boots', 25.00, 0.2, 10),
('MOBL-DT-008', 'CV Boot - Outer', 'Rzeppa joint boot', 'MOBILITY', 'Drivetrain', 'Boots', 28.00, 0.25, 10),
('MOBL-DT-009', 'Wheel Hub - Front', 'Front wheel hub assembly', 'MOBILITY', 'Drivetrain', 'Hubs', 185.00, 3.5, 21),
('MOBL-DT-010', 'Wheel Hub - Rear', 'Rear wheel hub assembly', 'MOBILITY', 'Drivetrain', 'Hubs', 165.00, 3.0, 21),
('MOBL-DT-011', 'Wheel Bearing - Front', 'Front hub bearing', 'MOBILITY', 'Drivetrain', 'Bearings', 85.00, 0.8, 14),
('MOBL-DT-012', 'Wheel Bearing - Rear', 'Rear hub bearing', 'MOBILITY', 'Drivetrain', 'Bearings', 75.00, 0.7, 14),
('MOBL-DT-013', 'Axle Nut', 'Hub axle retaining nut', 'MOBILITY', 'Drivetrain', 'Fasteners', 12.00, 0.1, 7),
('MOBL-DT-014', 'Splash Shield - Front', 'Front brake splash shield', 'MOBILITY', 'Drivetrain', 'Shields', 35.00, 0.5, 14),
('MOBL-DT-015', 'Splash Shield - Rear', 'Rear brake splash shield', 'MOBILITY', 'Drivetrain', 'Shields', 32.00, 0.45, 14),
('MOBL-DT-016', 'Knuckle - Front', 'Steering knuckle casting', 'MOBILITY', 'Drivetrain', 'Suspension', 280.00, 5.5, 28),
('MOBL-DT-017', 'Knuckle - Rear', 'Rear knuckle casting', 'MOBILITY', 'Drivetrain', 'Suspension', 220.00, 4.5, 25),
('MOBL-DT-018', 'Control Arm - Upper', 'Upper A-arm', 'MOBILITY', 'Drivetrain', 'Suspension', 145.00, 2.0, 21),
('MOBL-DT-019', 'Control Arm - Lower', 'Lower A-arm', 'MOBILITY', 'Drivetrain', 'Suspension', 180.00, 3.0, 21),
('MOBL-DT-020', 'Ball Joint', 'Suspension ball joint', 'MOBILITY', 'Drivetrain', 'Suspension', 45.00, 0.4, 14);

-- =============================================================================
-- INDUSTRIAL DIVISION PRODUCTS (125 SKUs)
-- =============================================================================

INSERT INTO products (sku, name, description, division_id, category, subcategory, unit_price, weight_kg, lead_time_days) VALUES
-- CNC Machine Parts (40)
('INDL-BRG-7420', 'CNC Spindle Bearing - Angular Contact', 'High-precision spindle bearing set', 'INDUSTRIAL', 'Bearings', 'Spindle Bearings', 320.00, 0.8, 14),
('INDL-BRG-7421', 'CNC Spindle Bearing - Cylindrical', 'NN series spindle bearing', 'INDUSTRIAL', 'Bearings', 'Spindle Bearings', 450.00, 1.2, 18),
('INDL-BRG-7422', 'Ball Screw Bearing - Fixed', 'Fixed side ball screw support', 'INDUSTRIAL', 'Bearings', 'Ball Screw Bearings', 280.00, 0.6, 14),
('INDL-BRG-7423', 'Ball Screw Bearing - Float', 'Floating side ball screw support', 'INDUSTRIAL', 'Bearings', 'Ball Screw Bearings', 180.00, 0.4, 14),
('INDL-BRG-7424', 'Linear Guide Rail - 400mm', 'Precision linear rail', 'INDUSTRIAL', 'Linear Motion', 'Rails', 185.00, 2.5, 14),
('INDL-BRG-7425', 'Linear Guide Rail - 800mm', 'Extended linear rail', 'INDUSTRIAL', 'Linear Motion', 'Rails', 320.00, 5.0, 18),
('INDL-BRG-7426', 'Linear Guide Block', 'Recirculating ball block', 'INDUSTRIAL', 'Linear Motion', 'Blocks', 145.00, 0.8, 14),
('INDL-BRG-7427', 'Ball Screw Assembly - 20x5', '20mm diameter, 5mm lead', 'INDUSTRIAL', 'Linear Motion', 'Ball Screws', 280.00, 3.5, 21),
('INDL-BRG-7428', 'Ball Screw Assembly - 32x10', '32mm diameter, 10mm lead', 'INDUSTRIAL', 'Linear Motion', 'Ball Screws', 450.00, 6.0, 25),
('INDL-BRG-7429', 'Ball Screw Nut - Single', 'Single nut assembly', 'INDUSTRIAL', 'Linear Motion', 'Nuts', 120.00, 0.4, 14),
('INDL-BRG-7430', 'Ball Screw Nut - Double', 'Preloaded double nut', 'INDUSTRIAL', 'Linear Motion', 'Nuts', 220.00, 0.7, 18),
('INDL-CNC-001', 'Spindle Housing - BT40', 'Vertical spindle housing', 'INDUSTRIAL', 'CNC Components', 'Spindle Parts', 2800.00, 35.0, 40),
('INDL-CNC-002', 'Spindle Housing - HSK63', 'High-speed spindle housing', 'INDUSTRIAL', 'CNC Components', 'Spindle Parts', 3500.00, 28.0, 45),
('INDL-CNC-003', 'Spindle Shaft - Solid', 'Solid spindle shaft', 'INDUSTRIAL', 'CNC Components', 'Spindle Parts', 1200.00, 8.0, 30),
('INDL-CNC-004', 'Spindle Shaft - Hollow', 'Through-spindle coolant shaft', 'INDUSTRIAL', 'CNC Components', 'Spindle Parts', 1800.00, 6.5, 35),
('INDL-CNC-005', 'Tool Holder - BT40', 'BT40 tool holder blank', 'INDUSTRIAL', 'CNC Components', 'Tool Holders', 85.00, 0.8, 10),
('INDL-CNC-006', 'Tool Holder - HSK63', 'HSK63 tool holder blank', 'INDUSTRIAL', 'CNC Components', 'Tool Holders', 120.00, 0.6, 10),
('INDL-CNC-007', 'Collet Chuck - ER32', 'ER32 collet chuck', 'INDUSTRIAL', 'CNC Components', 'Chucks', 180.00, 1.2, 14),
('INDL-CNC-008', 'Collet Chuck - ER40', 'ER40 collet chuck', 'INDUSTRIAL', 'CNC Components', 'Chucks', 220.00, 1.5, 14),
('INDL-CNC-009', 'Collet Set - ER32', 'ER32 collet set (18 pc)', 'INDUSTRIAL', 'CNC Components', 'Collets', 280.00, 2.0, 14),
('INDL-CNC-010', 'Collet Set - ER40', 'ER40 collet set (23 pc)', 'INDUSTRIAL', 'CNC Components', 'Collets', 380.00, 3.0, 14),
('INDL-CNC-011', 'Drawbar Assembly', 'Pneumatic drawbar', 'INDUSTRIAL', 'CNC Components', 'Drawbar', 650.00, 4.5, 25),
('INDL-CNC-012', 'Drawbar Spring Set', 'Belleville spring stack', 'INDUSTRIAL', 'CNC Components', 'Drawbar', 120.00, 0.8, 14),
('INDL-CNC-013', 'Turret - 8 Station', '8-position tool turret', 'INDUSTRIAL', 'CNC Components', 'Turrets', 4500.00, 45.0, 50),
('INDL-CNC-014', 'Turret - 12 Station', '12-position tool turret', 'INDUSTRIAL', 'CNC Components', 'Turrets', 6500.00, 65.0, 60),
('INDL-CNC-015', 'Turret Index Motor', 'Servo turret index drive', 'INDUSTRIAL', 'CNC Components', 'Turrets', 1200.00, 8.0, 30),
('INDL-CNC-016', 'Coolant Pump - Low Pressure', '20 bar coolant pump', 'INDUSTRIAL', 'CNC Components', 'Coolant', 450.00, 12.0, 21),
('INDL-CNC-017', 'Coolant Pump - High Pressure', '70 bar through-spindle pump', 'INDUSTRIAL', 'CNC Components', 'Coolant', 1800.00, 25.0, 30),
('INDL-CNC-018', 'Coolant Nozzle Set', 'Flexible coolant nozzles', 'INDUSTRIAL', 'CNC Components', 'Coolant', 85.00, 0.5, 10),
('INDL-CNC-019', 'Chip Conveyor Section', 'Hinged belt conveyor section', 'INDUSTRIAL', 'CNC Components', 'Chip Handling', 650.00, 35.0, 28),
('INDL-CNC-020', 'Chip Auger', 'Spiral chip auger', 'INDUSTRIAL', 'CNC Components', 'Chip Handling', 280.00, 8.0, 21),

-- Hydraulic Components (40)
('INDL-HYD-001', 'Hydraulic Pump - Gear', 'External gear pump 20cc', 'INDUSTRIAL', 'Hydraulics', 'Pumps', 450.00, 5.0, 21),
('INDL-HYD-002', 'Hydraulic Pump - Vane', 'Variable vane pump 30cc', 'INDUSTRIAL', 'Hydraulics', 'Pumps', 850.00, 8.0, 28),
('INDL-HYD-003', 'Hydraulic Pump - Piston', 'Axial piston pump 45cc', 'INDUSTRIAL', 'Hydraulics', 'Pumps', 2200.00, 18.0, 40),
('INDL-HYD-004', 'Hydraulic Motor - Gear', 'Gear motor 25cc', 'INDUSTRIAL', 'Hydraulics', 'Motors', 380.00, 4.5, 21),
('INDL-HYD-005', 'Hydraulic Motor - Piston', 'Bent axis piston motor', 'INDUSTRIAL', 'Hydraulics', 'Motors', 1800.00, 15.0, 35),
('INDL-HYD-006', 'Directional Valve - 4/3', '4-way 3-position DCV', 'INDUSTRIAL', 'Hydraulics', 'Valves', 320.00, 2.5, 18),
('INDL-HYD-007', 'Directional Valve - 4/2', '4-way 2-position DCV', 'INDUSTRIAL', 'Hydraulics', 'Valves', 280.00, 2.0, 18),
('INDL-HYD-008', 'Proportional Valve', 'Proportional DCV with amp', 'INDUSTRIAL', 'Hydraulics', 'Valves', 1200.00, 4.0, 30),
('INDL-HYD-009', 'Servo Valve', 'Servo proportional valve', 'INDUSTRIAL', 'Hydraulics', 'Valves', 3500.00, 3.5, 45),
('INDL-HYD-010', 'Relief Valve - Direct', 'Direct acting relief valve', 'INDUSTRIAL', 'Hydraulics', 'Valves', 85.00, 0.5, 10),
('INDL-HYD-011', 'Relief Valve - Pilot', 'Pilot operated relief', 'INDUSTRIAL', 'Hydraulics', 'Valves', 180.00, 1.2, 14),
('INDL-HYD-012', 'Check Valve', 'Inline check valve', 'INDUSTRIAL', 'Hydraulics', 'Valves', 45.00, 0.3, 7),
('INDL-HYD-013', 'Flow Control Valve', 'Pressure compensated FCV', 'INDUSTRIAL', 'Hydraulics', 'Valves', 145.00, 0.8, 14),
('INDL-HYD-014', 'Cylinder - Tie Rod 2x8', '2 inch bore x 8 inch stroke', 'INDUSTRIAL', 'Hydraulics', 'Cylinders', 280.00, 8.0, 21),
('INDL-HYD-015', 'Cylinder - Tie Rod 3x12', '3 inch bore x 12 inch stroke', 'INDUSTRIAL', 'Hydraulics', 'Cylinders', 450.00, 15.0, 25),
('INDL-HYD-016', 'Cylinder - Tie Rod 4x18', '4 inch bore x 18 inch stroke', 'INDUSTRIAL', 'Hydraulics', 'Cylinders', 750.00, 28.0, 30),
('INDL-HYD-017', 'Cylinder - Mill Type', 'NFPA mill duty cylinder', 'INDUSTRIAL', 'Hydraulics', 'Cylinders', 1200.00, 45.0, 40),
('INDL-HYD-018', 'Cylinder Seal Kit', 'Complete cylinder seal kit', 'INDUSTRIAL', 'Hydraulics', 'Seals', 65.00, 0.2, 10),
('INDL-HYD-019', 'Filter Assembly - Return', '10 micron return filter', 'INDUSTRIAL', 'Hydraulics', 'Filtration', 180.00, 3.0, 14),
('INDL-HYD-020', 'Filter Assembly - Pressure', '3 micron pressure filter', 'INDUSTRIAL', 'Hydraulics', 'Filtration', 280.00, 2.5, 18),

-- Pneumatic Components (25)
('INDL-PNE-001', 'Air Cylinder - Standard', '40mm bore x 100mm stroke', 'INDUSTRIAL', 'Pneumatics', 'Cylinders', 85.00, 0.8, 10),
('INDL-PNE-002', 'Air Cylinder - Compact', '32mm bore x 50mm stroke', 'INDUSTRIAL', 'Pneumatics', 'Cylinders', 65.00, 0.4, 10),
('INDL-PNE-003', 'Air Cylinder - Rodless', 'Magnetic rodless cylinder', 'INDUSTRIAL', 'Pneumatics', 'Cylinders', 280.00, 2.5, 21),
('INDL-PNE-004', 'Gripper - Parallel', '2-jaw parallel gripper', 'INDUSTRIAL', 'Pneumatics', 'Grippers', 320.00, 1.2, 21),
('INDL-PNE-005', 'Gripper - Angular', 'Angular gripper 180 deg', 'INDUSTRIAL', 'Pneumatics', 'Grippers', 280.00, 0.9, 21),
('INDL-PNE-006', 'Solenoid Valve - 5/2', '5-port 2-position valve', 'INDUSTRIAL', 'Pneumatics', 'Valves', 65.00, 0.3, 10),
('INDL-PNE-007', 'Solenoid Valve - 5/3', '5-port 3-position valve', 'INDUSTRIAL', 'Pneumatics', 'Valves', 85.00, 0.4, 10),
('INDL-PNE-008', 'Valve Manifold - 4 Station', '4-station valve block', 'INDUSTRIAL', 'Pneumatics', 'Valves', 120.00, 0.8, 14),
('INDL-PNE-009', 'Valve Manifold - 8 Station', '8-station valve block', 'INDUSTRIAL', 'Pneumatics', 'Valves', 220.00, 1.5, 14),
('INDL-PNE-010', 'FRL Unit - Mini', 'Filter-regulator-lubricator', 'INDUSTRIAL', 'Pneumatics', 'Air Prep', 85.00, 0.6, 10),
('INDL-PNE-011', 'FRL Unit - Standard', 'Standard FRL combination', 'INDUSTRIAL', 'Pneumatics', 'Air Prep', 145.00, 1.2, 14),
('INDL-PNE-012', 'Quick Connect Set', 'Push-to-connect fittings', 'INDUSTRIAL', 'Pneumatics', 'Fittings', 25.00, 0.1, 7),
('INDL-PNE-013', 'Speed Controller', 'Meter-out flow control', 'INDUSTRIAL', 'Pneumatics', 'Fittings', 18.00, 0.05, 7),
('INDL-PNE-014', 'Tubing - 8mm x 25m', 'Polyurethane tubing', 'INDUSTRIAL', 'Pneumatics', 'Tubing', 45.00, 1.5, 7),
('INDL-PNE-015', 'Tubing - 12mm x 25m', 'Polyurethane tubing', 'INDUSTRIAL', 'Pneumatics', 'Tubing', 65.00, 2.5, 7),

-- General Industrial Hardware (20)
('INDL-GEN-001', 'Motor Coupling - Jaw', 'Flexible jaw coupling', 'INDUSTRIAL', 'Power Transmission', 'Couplings', 85.00, 0.8, 10),
('INDL-GEN-002', 'Motor Coupling - Disc', 'Disc type coupling', 'INDUSTRIAL', 'Power Transmission', 'Couplings', 145.00, 1.2, 14),
('INDL-GEN-003', 'Timing Belt - HTD 5M', 'HTD 5M timing belt', 'INDUSTRIAL', 'Power Transmission', 'Belts', 45.00, 0.3, 7),
('INDL-GEN-004', 'Timing Belt - HTD 8M', 'HTD 8M timing belt', 'INDUSTRIAL', 'Power Transmission', 'Belts', 85.00, 0.6, 10),
('INDL-GEN-005', 'Timing Pulley - 5M', 'HTD 5M timing pulley', 'INDUSTRIAL', 'Power Transmission', 'Pulleys', 35.00, 0.3, 10),
('INDL-GEN-006', 'Timing Pulley - 8M', 'HTD 8M timing pulley', 'INDUSTRIAL', 'Power Transmission', 'Pulleys', 55.00, 0.5, 10),
('INDL-GEN-007', 'V-Belt - B Section', 'B section V-belt', 'INDUSTRIAL', 'Power Transmission', 'Belts', 18.00, 0.2, 7),
('INDL-GEN-008', 'V-Belt Pulley - B', 'B section pulley', 'INDUSTRIAL', 'Power Transmission', 'Pulleys', 28.00, 0.4, 10),
('INDL-GEN-009', 'Chain - Roller 40', '#40 roller chain', 'INDUSTRIAL', 'Power Transmission', 'Chains', 35.00, 1.0, 7),
('INDL-GEN-010', 'Chain - Roller 60', '#60 roller chain', 'INDUSTRIAL', 'Power Transmission', 'Chains', 65.00, 2.0, 10),
('INDL-GEN-011', 'Sprocket - 40', '#40 chain sprocket', 'INDUSTRIAL', 'Power Transmission', 'Sprockets', 25.00, 0.4, 10),
('INDL-GEN-012', 'Sprocket - 60', '#60 chain sprocket', 'INDUSTRIAL', 'Power Transmission', 'Sprockets', 45.00, 0.8, 10),
('INDL-GEN-013', 'Shaft Collar - Set Screw', 'Set screw shaft collar', 'INDUSTRIAL', 'Power Transmission', 'Shaft Accessories', 8.00, 0.1, 5),
('INDL-GEN-014', 'Shaft Collar - Clamp', 'Clamp type shaft collar', 'INDUSTRIAL', 'Power Transmission', 'Shaft Accessories', 12.00, 0.12, 5),
('INDL-GEN-015', 'Pillow Block - Light', 'Light duty pillow block', 'INDUSTRIAL', 'Power Transmission', 'Mounted Bearings', 45.00, 0.8, 10),
('INDL-GEN-016', 'Pillow Block - Heavy', 'Heavy duty pillow block', 'INDUSTRIAL', 'Power Transmission', 'Mounted Bearings', 120.00, 2.5, 14),
('INDL-GEN-017', 'Flange Block - 2 Bolt', '2-bolt flange bearing', 'INDUSTRIAL', 'Power Transmission', 'Mounted Bearings', 55.00, 1.0, 10),
('INDL-GEN-018', 'Flange Block - 4 Bolt', '4-bolt flange bearing', 'INDUSTRIAL', 'Power Transmission', 'Mounted Bearings', 85.00, 1.5, 10),
('INDL-GEN-019', 'Take-Up Unit', 'Take-up bearing unit', 'INDUSTRIAL', 'Power Transmission', 'Mounted Bearings', 145.00, 2.8, 14),
('INDL-GEN-020', 'Bearing Insert', 'Set screw bearing insert', 'INDUSTRIAL', 'Power Transmission', 'Mounted Bearings', 25.00, 0.3, 7);

-- =============================================================================
-- STOCK LEVELS (Multi-facility inventory)
-- =============================================================================

CREATE TABLE stock_levels (
    sku VARCHAR(50),
    facility_id VARCHAR(10),
    quantity INT,
    reorder_point INT,
    last_count_date DATE DEFAULT CURRENT_DATE,
    PRIMARY KEY (sku, facility_id),
    FOREIGN KEY (facility_id) REFERENCES titan_facilities(facility_id)
);

-- Generate stock levels for key products across facilities
INSERT INTO stock_levels (sku, facility_id, quantity, reorder_point) VALUES
-- Aerospace products at PHX and MUC
('AERO-TB-001', 'PHX', 320, 100),
('AERO-TB-001', 'MUC', 400, 100),
('AERO-TB-001', 'LYN', 150, 50),
('AERO-LG-001', 'PHX', 45, 15),
('AERO-LG-001', 'MUC', 38, 15),
('AERO-ST-001', 'PHX', 12, 5),
('AERO-ST-001', 'MUC', 8, 5),
-- Industrial bearings - critical for Phoenix incident
('INDL-BRG-7420', 'PHX', 45, 20),
('INDL-BRG-7420', 'DET', 62, 20),
('INDL-BRG-7420', 'ATL', 38, 15),
('INDL-BRG-7420', 'SHA', 85, 25),
('INDL-BRG-7421', 'PHX', 28, 10),
('INDL-BRG-7421', 'MUC', 35, 15),
-- EV components at Detroit and Shanghai
('MOBL-MH-001', 'DET', 180, 50),
('MOBL-MH-001', 'SHA', 420, 100),
('MOBL-MH-001', 'SEO', 250, 75),
('MOBL-BT-001', 'DET', 65, 20),
('MOBL-BT-001', 'SHA', 120, 40),
-- Wind turbine at Munich and Dallas
('ENGY-WT-001', 'MUC', 8, 3),
('ENGY-WT-001', 'DAL', 5, 2),
('ENGY-WT-008', 'MUC', 25, 10),
('ENGY-WT-008', 'DAL', 18, 8);

-- =============================================================================
-- CUSTOMERS (B2B Accounts)
-- =============================================================================

CREATE TABLE customers (
    customer_id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tier VARCHAR(20),
    industry VARCHAR(50),
    country VARCHAR(50),
    credit_limit DECIMAL(15,2),
    payment_terms_days INT DEFAULT 30
);

INSERT INTO customers VALUES
('CUST-BOEING', 'Boeing Commercial Airplanes', 'STRATEGIC', 'Aerospace', 'USA', 50000000.00, 45),
('CUST-AIRBUS', 'Airbus SE', 'STRATEGIC', 'Aerospace', 'France', 45000000.00, 45),
('CUST-SPACEX', 'SpaceX', 'STRATEGIC', 'Aerospace', 'USA', 25000000.00, 30),
('CUST-TESLA', 'Tesla Inc.', 'STRATEGIC', 'Automotive', 'USA', 35000000.00, 30),
('CUST-FORD', 'Ford Motor Company', 'MAJOR', 'Automotive', 'USA', 20000000.00, 30),
('CUST-RIVIAN', 'Rivian Automotive', 'MAJOR', 'Automotive', 'USA', 15000000.00, 30),
('CUST-GE', 'GE Renewable Energy', 'STRATEGIC', 'Energy', 'USA', 30000000.00, 45),
('CUST-SIEMENS', 'Siemens Gamesa', 'STRATEGIC', 'Energy', 'Germany', 28000000.00, 45),
('CUST-VESTAS', 'Vestas Wind Systems', 'MAJOR', 'Energy', 'Denmark', 22000000.00, 30),
('CUST-CAT', 'Caterpillar Inc.', 'MAJOR', 'Industrial', 'USA', 18000000.00, 30),
('CUST-DEERE', 'John Deere', 'MAJOR', 'Industrial', 'USA', 16000000.00, 30),
('CUST-KOMATSU', 'Komatsu Ltd.', 'STANDARD', 'Industrial', 'Japan', 12000000.00, 30);

-- =============================================================================
-- ORDERS (Sample B2B Orders)
-- =============================================================================

CREATE TABLE orders (
    order_id VARCHAR(30) PRIMARY KEY,
    customer_id VARCHAR(20) REFERENCES customers(customer_id),
    order_date TIMESTAMP DEFAULT NOW(),
    required_date TIMESTAMP,
    status VARCHAR(50) DEFAULT 'PENDING',
    priority VARCHAR(20) DEFAULT 'STANDARD',
    total_amount DECIMAL(15,2),
    shipping_address TEXT,
    notes TEXT
);

CREATE TABLE order_lines (
    line_id SERIAL PRIMARY KEY,
    order_id VARCHAR(30) REFERENCES orders(order_id),
    sku VARCHAR(50) REFERENCES products(sku),
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2),
    line_total DECIMAL(12,2)
);

-- Boeing expedite order (key demo scenario)
INSERT INTO orders VALUES
('TM-2024-45892', 'CUST-BOEING', NOW(), NOW() + INTERVAL '5 days', 'EXPEDITE', 'EXPEDITE', 1225000.00,
 'Boeing Everett Factory, 3003 W Casino Rd, Everett, WA 98204', 'URGENT: 787 production line - split shipment authorized');

INSERT INTO order_lines (order_id, sku, quantity, unit_price, line_total) VALUES
('TM-2024-45892', 'AERO-TB-001', 500, 2450.00, 1225000.00);

-- Additional sample orders
INSERT INTO orders VALUES
('TM-2024-45893', 'CUST-TESLA', NOW() - INTERVAL '2 days', NOW() + INTERVAL '14 days', 'IN_PROGRESS', 'STANDARD', 445000.00,
 'Tesla Fremont Factory, 45500 Fremont Blvd, Fremont, CA 94538', 'Model Y motor production'),
('TM-2024-45894', 'CUST-GE', NOW() - INTERVAL '5 days', NOW() + INTERVAL '30 days', 'CONFIRMED', 'STANDARD', 850000.00,
 'GE Wind Energy, 300 Garlington Rd, Greenville, SC 29615', 'Haliade-X project components'),
('TM-2024-45895', 'CUST-CAT', NOW() - INTERVAL '1 day', NOW() + INTERVAL '21 days', 'PENDING', 'STANDARD', 125000.00,
 'Caterpillar Aurora, 14009 Old Galena Rd, Aurora, IL 60506', 'D11 dozer rebuild parts');

INSERT INTO order_lines (order_id, sku, quantity, unit_price, line_total) VALUES
('TM-2024-45893', 'MOBL-MH-001', 500, 890.00, 445000.00),
('TM-2024-45894', 'ENGY-WT-001', 4, 125000.00, 500000.00),
('TM-2024-45894', 'ENGY-WT-008', 12, 28000.00, 336000.00),
('TM-2024-45895', 'INDL-HYD-003', 25, 2200.00, 55000.00),
('TM-2024-45895', 'INDL-HYD-017', 30, 1200.00, 36000.00),
('TM-2024-45895', 'INDL-BRG-7420', 100, 320.00, 32000.00);

-- =============================================================================
-- SUPPLIER-PRODUCT MAPPING
-- =============================================================================

CREATE TABLE product_suppliers (
    sku VARCHAR(50) REFERENCES products(sku),
    supplier_id VARCHAR(20) REFERENCES suppliers(supplier_id),
    is_primary BOOLEAN DEFAULT FALSE,
    unit_cost DECIMAL(12,2),
    min_order_qty INT DEFAULT 1,
    PRIMARY KEY (sku, supplier_id)
);

-- Map key products to suppliers
INSERT INTO product_suppliers VALUES
-- Bearings
('INDL-BRG-7420', 'SUP-NIPPON', TRUE, 180.00, 10),
('INDL-BRG-7420', 'SUP-SKF', FALSE, 195.00, 5),
('INDL-BRG-7420', 'SUP-TIMKEN', FALSE, 185.00, 10),
('INDL-BRG-7421', 'SUP-SKF', TRUE, 280.00, 5),
('INDL-BRG-7421', 'SUP-NSK', FALSE, 275.00, 10),
-- Aerospace materials
('AERO-TB-001', 'SUP-TIMET', TRUE, 1450.00, 50),
('AERO-TB-001', 'SUP-ALCOA', FALSE, 1520.00, 25),
('AERO-ST-001', 'SUP-TIMET', TRUE, 52000.00, 1),
-- Wind turbine bearings
('ENGY-WT-008', 'SUP-SKF', TRUE, 16500.00, 1),
('ENGY-WT-008', 'SUP-TIMKEN', FALSE, 17200.00, 1);

-- =============================================================================
-- SUMMARY VIEW
-- =============================================================================

CREATE VIEW product_summary AS
SELECT
    d.name as division,
    p.category,
    COUNT(*) as product_count,
    AVG(p.unit_price) as avg_price,
    SUM(COALESCE(s.quantity, 0)) as total_stock
FROM products p
JOIN titan_divisions d ON p.division_id = d.division_id
LEFT JOIN stock_levels s ON p.sku = s.sku
GROUP BY d.name, p.category
ORDER BY d.name, p.category;

-- Product count by division
SELECT division_id, COUNT(*) as products FROM products GROUP BY division_id ORDER BY division_id;

-- =============================================================================
-- EQUIPMENT & SENSOR DATA (Merged from TimescaleDB)
-- =============================================================================

-- Equipment registry (600+ machines across 12 facilities)
CREATE TABLE equipment (
    equipment_id VARCHAR(20) PRIMARY KEY,
    facility_id VARCHAR(10) REFERENCES titan_facilities(facility_id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    model VARCHAR(100),
    manufacturer VARCHAR(100),
    install_date DATE,
    last_maintenance DATE,
    status VARCHAR(20) DEFAULT 'operational',
    criticality VARCHAR(20) DEFAULT 'STANDARD'
);

-- Equipment types for Titan manufacturing
CREATE TABLE equipment_types (
    type_code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100),
    category VARCHAR(50),
    avg_lifespan_years INT,
    maintenance_interval_days INT
);

INSERT INTO equipment_types VALUES
('CNC-MILL', 'CNC Milling Machine', 'Machining', 15, 90),
('CNC-LATHE', 'CNC Lathe', 'Machining', 15, 90),
('CNC-5AX', '5-Axis CNC Center', 'Machining', 12, 60),
('HYD-PRESS', 'Hydraulic Press', 'Forming', 20, 120),
('LASER-CUT', 'Laser Cutting System', 'Cutting', 10, 45),
('WELD-ROB', 'Robotic Welding Cell', 'Joining', 12, 60),
('HEAT-TREAT', 'Heat Treatment Furnace', 'Thermal', 25, 180),
('CMM', 'Coordinate Measuring Machine', 'Quality', 15, 90),
('EDM', 'Electrical Discharge Machine', 'Machining', 12, 60),
('GRIND', 'Precision Grinding Machine', 'Finishing', 15, 90);

-- Sensor readings table (time-series data)
CREATE TABLE sensor_readings (
    reading_id BIGSERIAL,
    time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    equipment_id VARCHAR(20) NOT NULL,
    sensor_type VARCHAR(50) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(20),
    quality_flag VARCHAR(10) DEFAULT 'GOOD'
);

-- Indexes for time-series queries (replacing hypertable functionality)
CREATE INDEX idx_sensor_time ON sensor_readings (time DESC);
CREATE INDEX idx_sensor_equip_time ON sensor_readings (equipment_id, time DESC);
CREATE INDEX idx_sensor_type_time ON sensor_readings (sensor_type, time DESC);
CREATE INDEX idx_sensor_equip_type ON sensor_readings (equipment_id, sensor_type, time DESC);

-- Sensor types reference
CREATE TABLE sensor_types (
    sensor_type VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100),
    unit VARCHAR(20),
    normal_min DOUBLE PRECISION,
    normal_max DOUBLE PRECISION,
    warning_threshold DOUBLE PRECISION,
    critical_threshold DOUBLE PRECISION
);

INSERT INTO sensor_types VALUES
('vibration', 'Vibration Level', 'mm/s', 0.5, 2.5, 3.5, 5.0),
('temperature', 'Operating Temperature', 'celsius', 30, 55, 70, 85),
('spindle_speed', 'Spindle Speed', 'rpm', 0, 15000, 16000, 18000),
('power_draw', 'Power Consumption', 'kW', 5, 50, 65, 80),
('coolant_temp', 'Coolant Temperature', 'celsius', 15, 25, 35, 45),
('oil_pressure', 'Oil Pressure', 'bar', 2, 6, 7, 8),
('axis_load_x', 'X-Axis Load', 'percent', 0, 70, 85, 95),
('axis_load_y', 'Y-Axis Load', 'percent', 0, 70, 85, 95),
('axis_load_z', 'Z-Axis Load', 'percent', 0, 70, 85, 95);

-- Anomalies table
CREATE TABLE anomalies (
    anomaly_id SERIAL PRIMARY KEY,
    equipment_id VARCHAR(20) REFERENCES equipment(equipment_id),
    sensor_type VARCHAR(50),
    detected_at TIMESTAMPTZ DEFAULT NOW(),
    severity VARCHAR(20),
    anomaly_type VARCHAR(50),
    description TEXT,
    predicted_failure_date TIMESTAMPTZ,
    confidence_score DECIMAL(5,4),
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    resolution_notes TEXT
);

CREATE INDEX idx_anomaly_equip ON anomalies (equipment_id);
CREATE INDEX idx_anomaly_severity ON anomalies (severity, resolved);
CREATE INDEX idx_anomaly_time ON anomalies (detected_at DESC);

-- Maintenance records table
CREATE TABLE maintenance_records (
    record_id SERIAL PRIMARY KEY,
    equipment_id VARCHAR(20) REFERENCES equipment(equipment_id),
    maintenance_type VARCHAR(50),
    scheduled_date TIMESTAMP,
    completed_date TIMESTAMP,
    technician_id VARCHAR(20),
    technician_name VARCHAR(100),
    work_order_id VARCHAR(30),
    status VARCHAR(30) DEFAULT 'SCHEDULED',
    parts_used TEXT,
    labor_hours DECIMAL(5,2),
    cost DECIMAL(12,2),
    notes TEXT
);

CREATE INDEX idx_maint_equip ON maintenance_records (equipment_id);
CREATE INDEX idx_maint_status ON maintenance_records (status);
CREATE INDEX idx_maint_date ON maintenance_records (scheduled_date);

-- =============================================================================
-- EQUIPMENT DATA (600+ machines across 12 facilities)
-- =============================================================================

-- Phoenix Plant (65 machines) - Site of the $12M incident
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality) VALUES
-- CNC Mills
('PHX-CNC-001', 'PHX', 'CNC Mill Alpha-1', 'CNC-MILL', 'DMG MORI DMU 50', 'DMG MORI', '2022-01-15', '2024-10-15', 'operational', 'HIGH'),
('PHX-CNC-002', 'PHX', 'CNC Mill Alpha-2', 'CNC-MILL', 'DMG MORI DMU 50', 'DMG MORI', '2022-01-15', '2024-10-20', 'operational', 'HIGH'),
('PHX-CNC-003', 'PHX', 'CNC Mill Alpha-3', 'CNC-MILL', 'Mazak VTC-800', 'Mazak', '2022-03-20', '2024-09-15', 'operational', 'STANDARD'),
('PHX-CNC-004', 'PHX', 'CNC Mill Alpha-4', 'CNC-MILL', 'Mazak VTC-800', 'Mazak', '2022-03-20', '2024-09-18', 'operational', 'STANDARD'),
('PHX-CNC-005', 'PHX', 'CNC Mill Beta-1', 'CNC-MILL', 'Haas VF-4', 'Haas', '2021-06-10', '2024-11-01', 'operational', 'STANDARD'),
-- CNC Lathes (including the critical PHX-CNC-007)
('PHX-CNC-006', 'PHX', 'CNC Lathe Delta-1', 'CNC-LATHE', 'Okuma LB3000', 'Okuma', '2021-05-01', '2024-08-15', 'operational', 'HIGH'),
('PHX-CNC-007', 'PHX', 'CNC Lathe Delta-2', 'CNC-LATHE', 'Okuma LB3000', 'Okuma', '2021-06-10', '2024-07-20', 'warning', 'CRITICAL'),
('PHX-CNC-008', 'PHX', 'CNC Lathe Delta-3', 'CNC-LATHE', 'Okuma LB3000', 'Okuma', '2021-08-15', '2024-10-05', 'operational', 'HIGH'),
('PHX-CNC-009', 'PHX', 'CNC Lathe Delta-4', 'CNC-LATHE', 'Mazak QT-250', 'Mazak', '2022-02-01', '2024-09-25', 'operational', 'STANDARD'),
('PHX-CNC-010', 'PHX', 'CNC Lathe Delta-5', 'CNC-LATHE', 'Mazak QT-250', 'Mazak', '2022-02-01', '2024-10-10', 'operational', 'STANDARD'),
-- 5-Axis Centers
('PHX-5AX-001', 'PHX', '5-Axis Center Gamma-1', 'CNC-5AX', 'DMG MORI DMU 80P', 'DMG MORI', '2023-01-15', '2024-10-01', 'operational', 'CRITICAL'),
('PHX-5AX-002', 'PHX', '5-Axis Center Gamma-2', 'CNC-5AX', 'DMG MORI DMU 80P', 'DMG MORI', '2023-01-20', '2024-09-28', 'operational', 'CRITICAL'),
('PHX-5AX-003', 'PHX', '5-Axis Center Gamma-3', 'CNC-5AX', 'Makino D500', 'Makino', '2023-03-10', '2024-11-05', 'operational', 'HIGH'),
-- Hydraulic Presses
('PHX-HYD-001', 'PHX', 'Hydraulic Press Omega-1', 'HYD-PRESS', 'Schuler TBA 2500', 'Schuler', '2020-11-01', '2024-08-01', 'operational', 'HIGH'),
('PHX-HYD-002', 'PHX', 'Hydraulic Press Omega-2', 'HYD-PRESS', 'Schuler TBA 2500', 'Schuler', '2020-11-15', '2024-08-05', 'operational', 'HIGH'),
('PHX-HYD-003', 'PHX', 'Hydraulic Press Omega-3', 'HYD-PRESS', 'Komatsu H2F-300', 'Komatsu', '2021-03-01', '2024-09-15', 'operational', 'STANDARD'),
-- Laser Cutting
('PHX-LAS-001', 'PHX', 'Laser Cutter Sigma-1', 'LASER-CUT', 'Trumpf TruLaser 5030', 'Trumpf', '2022-06-01', '2024-10-20', 'operational', 'HIGH'),
('PHX-LAS-002', 'PHX', 'Laser Cutter Sigma-2', 'LASER-CUT', 'Trumpf TruLaser 5030', 'Trumpf', '2022-06-15', '2024-10-25', 'operational', 'HIGH'),
-- Welding Robots
('PHX-WLD-001', 'PHX', 'Welding Robot Tau-1', 'WELD-ROB', 'Fanuc Arc Mate 120iD', 'Fanuc', '2021-09-01', '2024-09-01', 'operational', 'STANDARD'),
('PHX-WLD-002', 'PHX', 'Welding Robot Tau-2', 'WELD-ROB', 'Fanuc Arc Mate 120iD', 'Fanuc', '2021-09-15', '2024-09-05', 'operational', 'STANDARD'),
('PHX-WLD-003', 'PHX', 'Welding Robot Tau-3', 'WELD-ROB', 'KUKA KR 16 arc HW', 'KUKA', '2022-01-10', '2024-10-15', 'operational', 'STANDARD'),
-- Heat Treatment
('PHX-HT-001', 'PHX', 'Heat Treatment Furnace-1', 'HEAT-TREAT', 'Ipsen VTTC', 'Ipsen', '2019-06-01', '2024-06-01', 'operational', 'HIGH'),
('PHX-HT-002', 'PHX', 'Heat Treatment Furnace-2', 'HEAT-TREAT', 'Ipsen VTTC', 'Ipsen', '2019-06-15', '2024-06-15', 'operational', 'HIGH'),
-- Quality (CMM)
('PHX-CMM-001', 'PHX', 'CMM Precision-1', 'CMM', 'Zeiss Contura', 'Zeiss', '2021-01-15', '2024-07-15', 'operational', 'CRITICAL'),
('PHX-CMM-002', 'PHX', 'CMM Precision-2', 'CMM', 'Zeiss Contura', 'Zeiss', '2021-02-01', '2024-07-20', 'operational', 'CRITICAL'),
-- EDM
('PHX-EDM-001', 'PHX', 'Wire EDM Epsilon-1', 'EDM', 'Sodick ALN600G', 'Sodick', '2022-04-01', '2024-10-01', 'operational', 'HIGH'),
('PHX-EDM-002', 'PHX', 'Wire EDM Epsilon-2', 'EDM', 'Mitsubishi MV2400R', 'Mitsubishi', '2022-05-15', '2024-10-10', 'operational', 'STANDARD'),
-- Grinders
('PHX-GRN-001', 'PHX', 'Precision Grinder Zeta-1', 'GRIND', 'Studer S41', 'Studer', '2020-08-01', '2024-08-01', 'operational', 'HIGH'),
('PHX-GRN-002', 'PHX', 'Precision Grinder Zeta-2', 'GRIND', 'Studer S41', 'Studer', '2020-08-15', '2024-08-10', 'operational', 'HIGH');

-- Additional Phoenix equipment (30 more)
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'PHX-CNC-' || LPAD((10 + row_number() OVER ())::text, 3, '0'),
    'PHX',
    'CNC Machine ' || (10 + row_number() OVER ()),
    CASE (row_number() OVER () % 3) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'CNC-LATHE' ELSE 'CNC-5AX' END,
    CASE (row_number() OVER () % 5) WHEN 0 THEN 'DMG MORI DMU 50' WHEN 1 THEN 'Mazak VTC-800' WHEN 2 THEN 'Haas VF-4' WHEN 3 THEN 'Okuma LB3000' ELSE 'Makino D500' END,
    CASE (row_number() OVER () % 4) WHEN 0 THEN 'DMG MORI' WHEN 1 THEN 'Mazak' WHEN 2 THEN 'Haas' ELSE 'Okuma' END,
    '2021-01-01'::date + (random() * 1000)::int,
    '2024-06-01'::date + (random() * 150)::int,
    'operational',
    CASE (row_number() OVER () % 4) WHEN 0 THEN 'CRITICAL' WHEN 1 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 35);

-- Detroit Plant (52 machines) - Automotive focus
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'DET-' || CASE (gs % 5) WHEN 0 THEN 'CNC' WHEN 1 THEN 'HYD' WHEN 2 THEN 'WLD' WHEN 3 THEN 'LAS' ELSE 'GRN' END || '-' || LPAD(((gs-1)/5 + 1)::text, 3, '0'),
    'DET',
    CASE (gs % 5) WHEN 0 THEN 'CNC Machine ' WHEN 1 THEN 'Hydraulic Press ' WHEN 2 THEN 'Welding Robot ' WHEN 3 THEN 'Laser Cutter ' ELSE 'Grinder ' END || ((gs-1)/5 + 1),
    CASE (gs % 5) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'HYD-PRESS' WHEN 2 THEN 'WELD-ROB' WHEN 3 THEN 'LASER-CUT' ELSE 'GRIND' END,
    CASE (gs % 5) WHEN 0 THEN 'Mazak VTC-800' WHEN 1 THEN 'Schuler TBA 2500' WHEN 2 THEN 'Fanuc Arc Mate 120iD' WHEN 3 THEN 'Trumpf TruLaser 5030' ELSE 'Studer S41' END,
    CASE (gs % 5) WHEN 0 THEN 'Mazak' WHEN 1 THEN 'Schuler' WHEN 2 THEN 'Fanuc' WHEN 3 THEN 'Trumpf' ELSE 'Studer' END,
    '2020-01-01'::date + (random() * 1200)::int,
    '2024-05-01'::date + (random() * 180)::int,
    CASE WHEN random() < 0.05 THEN 'maintenance' WHEN random() < 0.02 THEN 'warning' ELSE 'operational' END,
    CASE (gs % 4) WHEN 0 THEN 'CRITICAL' WHEN 1 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 52) gs;

-- Atlanta HQ (48 machines)
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'ATL-' || CASE (gs % 5) WHEN 0 THEN 'CNC' WHEN 1 THEN 'HYD' WHEN 2 THEN 'HT' WHEN 3 THEN 'CMM' ELSE 'EDM' END || '-' || LPAD(((gs-1)/5 + 1)::text, 3, '0'),
    'ATL',
    CASE (gs % 5) WHEN 0 THEN 'CNC Machine ' WHEN 1 THEN 'Hydraulic Press ' WHEN 2 THEN 'Heat Treat Furnace ' WHEN 3 THEN 'CMM ' ELSE 'EDM ' END || ((gs-1)/5 + 1),
    CASE (gs % 5) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'HYD-PRESS' WHEN 2 THEN 'HEAT-TREAT' WHEN 3 THEN 'CMM' ELSE 'EDM' END,
    CASE (gs % 5) WHEN 0 THEN 'Haas VF-4' WHEN 1 THEN 'Komatsu H2F-300' WHEN 2 THEN 'Ipsen VTTC' WHEN 3 THEN 'Zeiss Contura' ELSE 'Sodick ALN600G' END,
    CASE (gs % 5) WHEN 0 THEN 'Haas' WHEN 1 THEN 'Komatsu' WHEN 2 THEN 'Ipsen' WHEN 3 THEN 'Zeiss' ELSE 'Sodick' END,
    '2019-01-01'::date + (random() * 1500)::int,
    '2024-04-01'::date + (random() * 200)::int,
    CASE WHEN random() < 0.03 THEN 'maintenance' ELSE 'operational' END,
    CASE (gs % 3) WHEN 0 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 48) gs;

-- Dallas Plant (45 machines) - Energy focus
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'DAL-' || CASE (gs % 4) WHEN 0 THEN 'CNC' WHEN 1 THEN '5AX' WHEN 2 THEN 'LAS' ELSE 'WLD' END || '-' || LPAD(((gs-1)/4 + 1)::text, 3, '0'),
    'DAL',
    CASE (gs % 4) WHEN 0 THEN 'CNC Machine ' WHEN 1 THEN '5-Axis Center ' WHEN 2 THEN 'Laser Cutter ' ELSE 'Welding Robot ' END || ((gs-1)/4 + 1),
    CASE (gs % 4) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'CNC-5AX' WHEN 2 THEN 'LASER-CUT' ELSE 'WELD-ROB' END,
    CASE (gs % 4) WHEN 0 THEN 'DMG MORI DMU 50' WHEN 1 THEN 'DMG MORI DMU 80P' WHEN 2 THEN 'Trumpf TruLaser 5030' ELSE 'KUKA KR 16 arc HW' END,
    CASE (gs % 4) WHEN 0 THEN 'DMG MORI' WHEN 1 THEN 'DMG MORI' WHEN 2 THEN 'Trumpf' ELSE 'KUKA' END,
    '2020-06-01'::date + (random() * 1300)::int,
    '2024-06-01'::date + (random() * 150)::int,
    'operational',
    CASE (gs % 3) WHEN 0 THEN 'CRITICAL' WHEN 1 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 45) gs;

-- Munich Plant (58 machines) - Precision engineering
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'MUC-' || CASE (gs % 5) WHEN 0 THEN 'CNC' WHEN 1 THEN '5AX' WHEN 2 THEN 'GRN' WHEN 3 THEN 'CMM' ELSE 'EDM' END || '-' || LPAD(((gs-1)/5 + 1)::text, 3, '0'),
    'MUC',
    CASE (gs % 5) WHEN 0 THEN 'CNC Maschine ' WHEN 1 THEN '5-Achsen Zentrum ' WHEN 2 THEN 'Schleifmaschine ' WHEN 3 THEN 'Messmaschine ' ELSE 'Erodiermaschine ' END || ((gs-1)/5 + 1),
    CASE (gs % 5) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'CNC-5AX' WHEN 2 THEN 'GRIND' WHEN 3 THEN 'CMM' ELSE 'EDM' END,
    CASE (gs % 5) WHEN 0 THEN 'DMG MORI DMU 50' WHEN 1 THEN 'DMG MORI DMU 80P' WHEN 2 THEN 'Studer S41' WHEN 3 THEN 'Zeiss Contura' ELSE 'Sodick ALN600G' END,
    CASE (gs % 5) WHEN 0 THEN 'DMG MORI' WHEN 1 THEN 'DMG MORI' WHEN 2 THEN 'Studer' WHEN 3 THEN 'Zeiss' ELSE 'Sodick' END,
    '2019-06-01'::date + (random() * 1600)::int,
    '2024-05-01'::date + (random() * 180)::int,
    CASE WHEN random() < 0.02 THEN 'maintenance' ELSE 'operational' END,
    CASE (gs % 4) WHEN 0 THEN 'CRITICAL' WHEN 1 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 58) gs;

-- Lyon Plant (45 machines) - Aerospace composites
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'LYN-' || CASE (gs % 4) WHEN 0 THEN 'CNC' WHEN 1 THEN 'LAS' WHEN 2 THEN 'HT' ELSE 'CMM' END || '-' || LPAD(((gs-1)/4 + 1)::text, 3, '0'),
    'LYN',
    CASE (gs % 4) WHEN 0 THEN 'Machine CNC ' WHEN 1 THEN 'Decoupe Laser ' WHEN 2 THEN 'Four Traitement ' ELSE 'Machine Mesure ' END || ((gs-1)/4 + 1),
    CASE (gs % 4) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'LASER-CUT' WHEN 2 THEN 'HEAT-TREAT' ELSE 'CMM' END,
    CASE (gs % 4) WHEN 0 THEN 'Mazak VTC-800' WHEN 1 THEN 'Trumpf TruLaser 5030' WHEN 2 THEN 'Ipsen VTTC' ELSE 'Zeiss Contura' END,
    CASE (gs % 4) WHEN 0 THEN 'Mazak' WHEN 1 THEN 'Trumpf' WHEN 2 THEN 'Ipsen' ELSE 'Zeiss' END,
    '2020-01-01'::date + (random() * 1400)::int,
    '2024-04-01'::date + (random() * 200)::int,
    'operational',
    CASE (gs % 3) WHEN 0 THEN 'CRITICAL' WHEN 1 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 45) gs;

-- Manchester Plant (40 machines) - Industrial bearings
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'MAN-' || CASE (gs % 4) WHEN 0 THEN 'CNC' WHEN 1 THEN 'GRN' WHEN 2 THEN 'HYD' ELSE 'CMM' END || '-' || LPAD(((gs-1)/4 + 1)::text, 3, '0'),
    'MAN',
    CASE (gs % 4) WHEN 0 THEN 'CNC Machine ' WHEN 1 THEN 'Grinder ' WHEN 2 THEN 'Hydraulic Press ' ELSE 'CMM ' END || ((gs-1)/4 + 1),
    CASE (gs % 4) WHEN 0 THEN 'CNC-LATHE' WHEN 1 THEN 'GRIND' WHEN 2 THEN 'HYD-PRESS' ELSE 'CMM' END,
    CASE (gs % 4) WHEN 0 THEN 'Okuma LB3000' WHEN 1 THEN 'Studer S41' WHEN 2 THEN 'Schuler TBA 2500' ELSE 'Zeiss Contura' END,
    CASE (gs % 4) WHEN 0 THEN 'Okuma' WHEN 1 THEN 'Studer' WHEN 2 THEN 'Schuler' ELSE 'Zeiss' END,
    '2019-01-01'::date + (random() * 1700)::int,
    '2024-03-01'::date + (random() * 250)::int,
    CASE WHEN random() < 0.04 THEN 'maintenance' ELSE 'operational' END,
    CASE (gs % 3) WHEN 0 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 40) gs;

-- Shanghai Plant (72 machines) - High-volume production
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'SHA-' || CASE (gs % 6) WHEN 0 THEN 'CNC' WHEN 1 THEN 'HYD' WHEN 2 THEN 'WLD' WHEN 3 THEN 'LAS' WHEN 4 THEN 'HT' ELSE 'CMM' END || '-' || LPAD(((gs-1)/6 + 1)::text, 3, '0'),
    'SHA',
    CASE (gs % 6) WHEN 0 THEN 'CNC机床 ' WHEN 1 THEN '液压机 ' WHEN 2 THEN '焊接机器人 ' WHEN 3 THEN '激光切割机 ' WHEN 4 THEN '热处理炉 ' ELSE '三坐标测量机 ' END || ((gs-1)/6 + 1),
    CASE (gs % 6) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'HYD-PRESS' WHEN 2 THEN 'WELD-ROB' WHEN 3 THEN 'LASER-CUT' WHEN 4 THEN 'HEAT-TREAT' ELSE 'CMM' END,
    CASE (gs % 6) WHEN 0 THEN 'Mazak VTC-800' WHEN 1 THEN 'Komatsu H2F-300' WHEN 2 THEN 'Fanuc Arc Mate 120iD' WHEN 3 THEN 'Trumpf TruLaser 5030' WHEN 4 THEN 'Ipsen VTTC' ELSE 'Zeiss Contura' END,
    CASE (gs % 6) WHEN 0 THEN 'Mazak' WHEN 1 THEN 'Komatsu' WHEN 2 THEN 'Fanuc' WHEN 3 THEN 'Trumpf' WHEN 4 THEN 'Ipsen' ELSE 'Zeiss' END,
    '2021-01-01'::date + (random() * 1100)::int,
    '2024-06-01'::date + (random() * 150)::int,
    CASE WHEN random() < 0.03 THEN 'maintenance' WHEN random() < 0.01 THEN 'warning' ELSE 'operational' END,
    CASE (gs % 4) WHEN 0 THEN 'CRITICAL' WHEN 1 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 72) gs;

-- Tokyo Plant (55 machines) - Precision instruments
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'TYO-' || CASE (gs % 5) WHEN 0 THEN 'CNC' WHEN 1 THEN '5AX' WHEN 2 THEN 'GRN' WHEN 3 THEN 'EDM' ELSE 'CMM' END || '-' || LPAD(((gs-1)/5 + 1)::text, 3, '0'),
    'TYO',
    CASE (gs % 5) WHEN 0 THEN 'CNCマシン ' WHEN 1 THEN '5軸加工機 ' WHEN 2 THEN '研削盤 ' WHEN 3 THEN '放電加工機 ' ELSE '三次元測定機 ' END || ((gs-1)/5 + 1),
    CASE (gs % 5) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'CNC-5AX' WHEN 2 THEN 'GRIND' WHEN 3 THEN 'EDM' ELSE 'CMM' END,
    CASE (gs % 5) WHEN 0 THEN 'Mazak VTC-800' WHEN 1 THEN 'Makino D500' WHEN 2 THEN 'Studer S41' WHEN 3 THEN 'Mitsubishi MV2400R' ELSE 'Zeiss Contura' END,
    CASE (gs % 5) WHEN 0 THEN 'Mazak' WHEN 1 THEN 'Makino' WHEN 2 THEN 'Studer' WHEN 3 THEN 'Mitsubishi' ELSE 'Zeiss' END,
    '2020-01-01'::date + (random() * 1400)::int,
    '2024-05-01'::date + (random() * 180)::int,
    'operational',
    CASE (gs % 3) WHEN 0 THEN 'CRITICAL' WHEN 1 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 55) gs;

-- Seoul Plant (42 machines) - EV components
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'SEO-' || CASE (gs % 4) WHEN 0 THEN 'CNC' WHEN 1 THEN 'WLD' WHEN 2 THEN 'LAS' ELSE 'CMM' END || '-' || LPAD(((gs-1)/4 + 1)::text, 3, '0'),
    'SEO',
    CASE (gs % 4) WHEN 0 THEN 'CNC 기계 ' WHEN 1 THEN '용접 로봇 ' WHEN 2 THEN '레이저 커터 ' ELSE '측정기 ' END || ((gs-1)/4 + 1),
    CASE (gs % 4) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'WELD-ROB' WHEN 2 THEN 'LASER-CUT' ELSE 'CMM' END,
    CASE (gs % 4) WHEN 0 THEN 'Mazak VTC-800' WHEN 1 THEN 'Fanuc Arc Mate 120iD' WHEN 2 THEN 'Trumpf TruLaser 5030' ELSE 'Zeiss Contura' END,
    CASE (gs % 4) WHEN 0 THEN 'Mazak' WHEN 1 THEN 'Fanuc' WHEN 2 THEN 'Trumpf' ELSE 'Zeiss' END,
    '2021-06-01'::date + (random() * 1000)::int,
    '2024-07-01'::date + (random() * 120)::int,
    CASE WHEN random() < 0.02 THEN 'maintenance' ELSE 'operational' END,
    CASE (gs % 3) WHEN 0 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 42) gs;

-- Sydney Plant (38 machines) - Mining equipment
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'SYD-' || CASE (gs % 4) WHEN 0 THEN 'CNC' WHEN 1 THEN 'HYD' WHEN 2 THEN 'WLD' ELSE 'HT' END || '-' || LPAD(((gs-1)/4 + 1)::text, 3, '0'),
    'SYD',
    CASE (gs % 4) WHEN 0 THEN 'CNC Machine ' WHEN 1 THEN 'Hydraulic Press ' WHEN 2 THEN 'Welding Robot ' ELSE 'Heat Treatment ' END || ((gs-1)/4 + 1),
    CASE (gs % 4) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'HYD-PRESS' WHEN 2 THEN 'WELD-ROB' ELSE 'HEAT-TREAT' END,
    CASE (gs % 4) WHEN 0 THEN 'Haas VF-4' WHEN 1 THEN 'Schuler TBA 2500' WHEN 2 THEN 'KUKA KR 16 arc HW' ELSE 'Ipsen VTTC' END,
    CASE (gs % 4) WHEN 0 THEN 'Haas' WHEN 1 THEN 'Schuler' WHEN 2 THEN 'KUKA' ELSE 'Ipsen' END,
    '2020-01-01'::date + (random() * 1400)::int,
    '2024-04-01'::date + (random() * 200)::int,
    'operational',
    CASE (gs % 3) WHEN 0 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 38) gs;

-- Mexico City Plant (40 machines) - Assembly operations
INSERT INTO equipment (equipment_id, facility_id, name, type, model, manufacturer, install_date, last_maintenance, status, criticality)
SELECT
    'MEX-' || CASE (gs % 4) WHEN 0 THEN 'CNC' WHEN 1 THEN 'WLD' WHEN 2 THEN 'HYD' ELSE 'CMM' END || '-' || LPAD(((gs-1)/4 + 1)::text, 3, '0'),
    'MEX',
    CASE (gs % 4) WHEN 0 THEN 'Máquina CNC ' WHEN 1 THEN 'Robot Soldador ' WHEN 2 THEN 'Prensa Hidráulica ' ELSE 'MMC ' END || ((gs-1)/4 + 1),
    CASE (gs % 4) WHEN 0 THEN 'CNC-MILL' WHEN 1 THEN 'WELD-ROB' WHEN 2 THEN 'HYD-PRESS' ELSE 'CMM' END,
    CASE (gs % 4) WHEN 0 THEN 'Haas VF-4' WHEN 1 THEN 'Fanuc Arc Mate 120iD' WHEN 2 THEN 'Komatsu H2F-300' ELSE 'Zeiss Contura' END,
    CASE (gs % 4) WHEN 0 THEN 'Haas' WHEN 1 THEN 'Fanuc' WHEN 2 THEN 'Komatsu' ELSE 'Zeiss' END,
    '2021-01-01'::date + (random() * 1100)::int,
    '2024-05-01'::date + (random() * 180)::int,
    CASE WHEN random() < 0.03 THEN 'maintenance' ELSE 'operational' END,
    CASE (gs % 3) WHEN 0 THEN 'HIGH' ELSE 'STANDARD' END
FROM generate_series(1, 40) gs;

-- =============================================================================
-- SENSOR DATA FOR PHX-CNC-007 (Phoenix Incident Demo)
-- =============================================================================

-- Generate degrading vibration data for PHX-CNC-007 (7 days of hourly readings)
INSERT INTO sensor_readings (time, equipment_id, sensor_type, value, unit, quality_flag)
SELECT
    NOW() - (interval '1 hour' * gs),
    'PHX-CNC-007',
    'vibration',
    2.5 + (random() * 1.0) + (gs::float / 168 * 1.2),  -- Increasing trend from 2.5 to ~4.2 mm/s
    'mm/s',
    CASE WHEN (2.5 + gs::float / 168 * 1.2) > 3.5 THEN 'WARNING' ELSE 'GOOD' END
FROM generate_series(0, 167) gs;

-- Temperature also trending up
INSERT INTO sensor_readings (time, equipment_id, sensor_type, value, unit, quality_flag)
SELECT
    NOW() - (interval '1 hour' * gs),
    'PHX-CNC-007',
    'temperature',
    48 + (random() * 6) + (gs::float / 168 * 8),  -- Increasing from 48 to ~62°C
    'celsius',
    CASE WHEN (48 + gs::float / 168 * 8) > 55 THEN 'WARNING' ELSE 'GOOD' END
FROM generate_series(0, 167) gs;

-- Spindle speed slightly erratic
INSERT INTO sensor_readings (time, equipment_id, sensor_type, value, unit, quality_flag)
SELECT
    NOW() - (interval '1 hour' * gs),
    'PHX-CNC-007',
    'spindle_speed',
    8500 + (random() * 500) - (gs::float / 168 * 200),  -- Slight decrease
    'rpm',
    'GOOD'
FROM generate_series(0, 167) gs;

-- Normal readings for PHX-CNC-001 (healthy machine for comparison)
INSERT INTO sensor_readings (time, equipment_id, sensor_type, value, unit, quality_flag)
SELECT
    NOW() - (interval '1 hour' * gs),
    'PHX-CNC-001',
    'vibration',
    1.8 + (random() * 0.4),  -- Stable around 1.8-2.2 mm/s
    'mm/s',
    'GOOD'
FROM generate_series(0, 167) gs;

INSERT INTO sensor_readings (time, equipment_id, sensor_type, value, unit, quality_flag)
SELECT
    NOW() - (interval '1 hour' * gs),
    'PHX-CNC-001',
    'temperature',
    45 + (random() * 5),  -- Stable around 45-50°C
    'celsius',
    'GOOD'
FROM generate_series(0, 167) gs;

-- =============================================================================
-- ANOMALY DETECTION FOR PHX-CNC-007
-- =============================================================================

INSERT INTO anomalies (equipment_id, sensor_type, detected_at, severity, anomaly_type, description, predicted_failure_date, confidence_score)
VALUES
('PHX-CNC-007', 'vibration', NOW() - INTERVAL '6 hours', 'WARNING', 'TRENDING_UP',
 'Vibration levels trending upward at 0.8mm/s per day - similar pattern to bearing failure incident of 2023',
 NOW() + INTERVAL '48 hours', 0.73),
('PHX-CNC-007', 'temperature', NOW() - INTERVAL '4 hours', 'WARNING', 'TRENDING_UP',
 'Operating temperature increasing beyond normal range - correlates with vibration anomaly',
 NOW() + INTERVAL '48 hours', 0.68);

-- =============================================================================
-- MAINTENANCE HISTORY
-- =============================================================================

-- Recent maintenance for PHX-CNC-007
INSERT INTO maintenance_records (equipment_id, maintenance_type, scheduled_date, completed_date, technician_id, technician_name, work_order_id, status, parts_used, labor_hours, cost, notes)
VALUES
('PHX-CNC-007', 'PREVENTIVE', '2024-07-20', '2024-07-20', 'TECH-042', 'Marcus Johnson', 'WO-2024-3421', 'COMPLETED', 'Oil filter, coolant flush', 4.5, 850.00, 'Routine preventive maintenance - all parameters within spec'),
('PHX-CNC-007', 'REPAIR', '2024-04-15', '2024-04-16', 'TECH-042', 'Marcus Johnson', 'WO-2024-1892', 'COMPLETED', 'Spindle bearing SKF-7420', 12.0, 4200.00, 'Replaced worn spindle bearing - vibration was at 3.8mm/s'),
('PHX-CNC-007', 'PREVENTIVE', '2024-01-10', '2024-01-10', 'TECH-035', 'Sarah Chen', 'WO-2024-0156', 'COMPLETED', 'Lubrication, alignment check', 3.0, 450.00, 'Standard PM - minor alignment adjustment made');

-- Scheduled maintenance (not yet completed)
INSERT INTO maintenance_records (equipment_id, maintenance_type, scheduled_date, technician_id, technician_name, work_order_id, status, notes)
VALUES
('PHX-CNC-007', 'EMERGENCY', NOW() + INTERVAL '2 days', 'TECH-042', 'Marcus Johnson', 'WO-2024-8901', 'SCHEDULED', 'URGENT: Bearing replacement recommended based on predictive analysis - SKU-BRG-7420 reserved');

-- =============================================================================
-- SUMMARY VIEWS FOR EQUIPMENT
-- =============================================================================

CREATE VIEW equipment_status_summary AS
SELECT
    f.name as facility,
    f.facility_id,
    COUNT(*) as total_equipment,
    SUM(CASE WHEN e.status = 'operational' THEN 1 ELSE 0 END) as operational,
    SUM(CASE WHEN e.status = 'warning' THEN 1 ELSE 0 END) as warning,
    SUM(CASE WHEN e.status = 'maintenance' THEN 1 ELSE 0 END) as in_maintenance,
    SUM(CASE WHEN e.criticality = 'CRITICAL' THEN 1 ELSE 0 END) as critical_equipment
FROM equipment e
JOIN titan_facilities f ON e.facility_id = f.facility_id
GROUP BY f.name, f.facility_id
ORDER BY f.facility_id;

CREATE VIEW recent_anomalies AS
SELECT
    a.anomaly_id,
    a.equipment_id,
    e.name as equipment_name,
    f.name as facility,
    a.sensor_type,
    a.severity,
    a.anomaly_type,
    a.description,
    a.predicted_failure_date,
    a.confidence_score,
    a.detected_at
FROM anomalies a
JOIN equipment e ON a.equipment_id = e.equipment_id
JOIN titan_facilities f ON e.facility_id = f.facility_id
WHERE a.resolved = FALSE
ORDER BY
    CASE a.severity WHEN 'CRITICAL' THEN 1 WHEN 'WARNING' THEN 2 ELSE 3 END,
    a.detected_at DESC;

-- Equipment count summary
SELECT facility_id, COUNT(*) as equipment_count FROM equipment GROUP BY facility_id ORDER BY facility_id;
