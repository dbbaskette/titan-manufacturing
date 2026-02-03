#!/usr/bin/env python3
"""
TITAN MANUFACTURING — OpenMetadata Setup

Configures data catalog with Titan's domains, glossary, classifications,
AND registers Greenplum schemas for data governance.

Usage:
    # Ensure OpenMetadata is running (docker-compose --profile governance up -d)
    python setup-titan.py

Environment Variables:
    OPENMETADATA_URL     - OpenMetadata URL (default: http://localhost:8585)
    GREENPLUM_HOST       - Greenplum host for ingestion (default: greenplum)
    GREENPLUM_PORT       - Greenplum port (default: 5432)
    GREENPLUM_DB         - Database name (default: titan-manufacturing)
    GREENPLUM_USER       - Database user (default: gpadmin)
    GREENPLUM_PASSWORD   - Database password (default: VMware1!)
"""

import requests
import time
import sys
import os
import json

# Configuration
OPENMETADATA_URL = os.getenv("OPENMETADATA_URL", "http://localhost:8585")
API_BASE = f"{OPENMETADATA_URL}/api/v1"
HEADERS = {"Content-Type": "application/json"}

# Greenplum connection for ingestion (use Docker service name for container-to-container)
GREENPLUM_HOST = os.getenv("GREENPLUM_HOST", "greenplum")
GREENPLUM_PORT = os.getenv("GREENPLUM_PORT", "5432")
GREENPLUM_DB = os.getenv("GREENPLUM_DB", "titan-manufacturing")
GREENPLUM_USER = os.getenv("GREENPLUM_USER", "gpadmin")
GREENPLUM_PASSWORD = os.getenv("GREENPLUM_PASSWORD", "VMware1!")


def wait_for_openmetadata():
    """Wait for OpenMetadata to be ready."""
    print("Waiting for OpenMetadata...")
    for i in range(60):
        try:
            resp = requests.get(f"{API_BASE}/system/version", timeout=5)
            if resp.status_code == 200:
                print(f"OpenMetadata ready! Version: {resp.json().get('version')}")
                return True
        except:
            pass
        time.sleep(5)
    return False


def create_domains():
    """Create Titan business domains."""
    domains = [
        {"name": "TitanAerospace", "displayName": "Titan Aerospace",
         "description": "Turbine blades, engine housings, landing gear for Apex Aerospace, Horizon Aircraft, SpaceX", "domainType": "Aggregate"},
        {"name": "TitanEnergy", "displayName": "Titan Energy",
         "description": "Wind turbine gearboxes, solar frames, pipeline valves for GE, Siemens", "domainType": "Aggregate"},
        {"name": "TitanMobility", "displayName": "Titan Mobility",
         "description": "EV motor housings, battery enclosures for Tesla, Ford, Rivian", "domainType": "Aggregate"},
        {"name": "TitanIndustrial", "displayName": "Titan Industrial",
         "description": "CNC parts, hydraulic pumps, bearings for Caterpillar, John Deere", "domainType": "Aggregate"},
        {"name": "Manufacturing", "displayName": "Manufacturing Operations",
         "description": "IoT sensor data, equipment monitoring, predictive maintenance", "domainType": "Aggregate"},
        {"name": "SupplyChain", "displayName": "Supply Chain",
         "description": "Inventory, logistics, supplier management", "domainType": "Aggregate"},
    ]

    print("\nCreating Titan domains...")
    for domain in domains:
        resp = requests.post(f"{API_BASE}/domains", headers=HEADERS, json=domain)
        status = "✓" if resp.status_code in [200, 201] else "-" if resp.status_code == 409 else "✗"
        print(f"  {status} {domain['name']}")


def create_glossary():
    """Create Titan business glossary."""
    glossary = {
        "name": "TitanGlossary",
        "displayName": "Titan Manufacturing Glossary",
        "description": "Business terms for Titan 5.0 AI platform"
    }

    print("\nCreating glossary...")
    resp = requests.post(f"{API_BASE}/glossaries", headers=HEADERS, json=glossary)
    if resp.status_code not in [200, 201, 409]:
        print(f"  ✗ Failed: {resp.text}")
        return

    # Get glossary ID for terms
    try:
        glossary_resp = requests.get(f"{API_BASE}/glossaries/name/TitanGlossary", headers=HEADERS)
        glossary_id = glossary_resp.json().get("id") if glossary_resp.status_code == 200 else None
    except:
        glossary_id = None

    terms = [
        {"name": "RUL", "displayName": "Remaining Useful Life",
         "description": "Predicted time until equipment failure, key metric after $12M Phoenix incident"},
        {"name": "SKU", "displayName": "Stock Keeping Unit",
         "description": "Unique product identifier across Titan's 50,000+ part catalog"},
        {"name": "Expedite", "displayName": "Expedite Order",
         "description": "Priority order requiring accelerated fulfillment (e.g., Apex Aerospace urgent requests)"},
        {"name": "FAA-Compliance", "displayName": "FAA Compliance",
         "description": "Federal Aviation Administration traceability requirements for aerospace parts"},
        {"name": "MaterialBatch", "displayName": "Material Batch",
         "description": "Traceable lot of raw material with certification for quality audits"},
    ]

    print("Creating glossary terms...")
    for term in terms:
        if glossary_id:
            term["glossary"] = glossary_id
        resp = requests.post(f"{API_BASE}/glossaryTerms", headers=HEADERS, json=term)
        status = "✓" if resp.status_code in [200, 201] else "-"
        print(f"  {status} {term['name']}")


def create_classifications():
    """Create Titan data classifications."""
    classifications = [
        {"name": "PII", "displayName": "Personally Identifiable Information",
         "description": "Operator IDs, technician certifications - flagged for FAA audits"},
        {"name": "AerospaceCompliance", "displayName": "Aerospace Compliance Required",
         "description": "Data requiring full lineage for FAA/EASA audits"},
        {"name": "SupplierConfidential", "displayName": "Supplier Confidential",
         "description": "Pricing and contract terms with suppliers"},
    ]

    print("\nCreating classifications...")
    for c in classifications:
        resp = requests.post(f"{API_BASE}/classifications", headers=HEADERS, json=c)
        status = "✓" if resp.status_code in [200, 201] else "-"
        print(f"  {status} {c['name']}")


def create_greenplum_service():
    """Register Greenplum as a database service in OpenMetadata."""
    service = {
        "name": "titan-greenplum",
        "displayName": "Titan Greenplum Data Warehouse",
        "serviceType": "Postgres",  # OpenMetadata uses Postgres connector for Greenplum
        "description": "Greenplum database containing Titan Manufacturing operational and analytics data. "
                      "Includes pgvector for semantic product search and ML model infrastructure.",
        "connection": {
            "config": {
                "type": "Postgres",
                "hostPort": f"{GREENPLUM_HOST}:{GREENPLUM_PORT}",
                "username": GREENPLUM_USER,
                "authType": {
                    "password": GREENPLUM_PASSWORD
                },
                "database": GREENPLUM_DB
            }
        }
    }

    print("\nRegistering Greenplum database service...")
    resp = requests.post(f"{API_BASE}/services/databaseServices", headers=HEADERS, json=service)

    if resp.status_code in [200, 201]:
        print("  ✓ Greenplum service registered")
        return resp.json().get("id")
    elif resp.status_code == 409:
        print("  - Greenplum service already exists")
        # Get existing service ID
        try:
            resp = requests.get(f"{API_BASE}/services/databaseServices/name/titan-greenplum", headers=HEADERS)
            return resp.json().get("id")
        except:
            return None
    else:
        print(f"  ✗ Failed to register: {resp.status_code} - {resp.text}")
        return None


def create_ingestion_pipeline(service_id):
    """Create metadata ingestion pipeline for Greenplum."""
    if not service_id:
        print("  ! Skipping ingestion pipeline (no service ID)")
        return None

    pipeline = {
        "name": "titan-greenplum-metadata",
        "displayName": "Titan Greenplum Metadata Ingestion",
        "pipelineType": "metadata",
        "service": {
            "id": service_id,
            "type": "databaseService"
        },
        "sourceConfig": {
            "config": {
                "type": "DatabaseMetadata",
                "markDeletedTables": True,
                "includeTables": True,
                "includeViews": True,
                "schemaFilterPattern": {
                    "includes": ["public"]
                },
                "tableFilterPattern": {
                    "includes": [
                        "products", "stock_levels", "suppliers", "product_suppliers",
                        "carriers", "shipments", "shipping_rates",
                        "customers", "orders", "order_lines",
                        "titan_divisions", "titan_facilities",
                        "equipment", "equipment_types", "sensor_types",
                        "sensor_readings", "anomalies", "maintenance_records",
                        "failure_events", "run_to_failure_data", "ml_model_coefficients"
                    ]
                }
            }
        },
        "airflowConfig": {
            "scheduleInterval": "0 0 * * *"  # Daily at midnight
        }
    }

    print("\nCreating metadata ingestion pipeline...")
    resp = requests.post(f"{API_BASE}/services/ingestionPipelines", headers=HEADERS, json=pipeline)

    if resp.status_code in [200, 201]:
        print("  ✓ Ingestion pipeline created")
        return resp.json().get("id")
    elif resp.status_code == 409:
        print("  - Ingestion pipeline already exists")
        return None
    else:
        print(f"  ✗ Failed: {resp.status_code} - {resp.text[:200]}")
        return None


def trigger_ingestion(pipeline_id):
    """Trigger metadata ingestion run."""
    if not pipeline_id:
        return

    print("  Triggering ingestion run...")
    resp = requests.post(f"{API_BASE}/services/ingestionPipelines/trigger/{pipeline_id}", headers=HEADERS)

    if resp.status_code in [200, 202]:
        print("  ✓ Ingestion triggered")
    else:
        print(f"  ! Could not trigger ingestion: {resp.status_code}")


def add_table_descriptions():
    """Add business descriptions to key tables."""
    descriptions = {
        "products": "Titan's 50,000+ SKU product catalog across 4 divisions (Aerospace, Energy, Mobility, Industrial). "
                   "Contains pgvector embeddings (1536 dim) for semantic search.",
        "stock_levels": "Real-time inventory levels across 12 global facilities. Includes reorder points and last count dates.",
        "suppliers": "Approved supplier registry with 15 global suppliers, quality ratings, and lead times.",
        "product_suppliers": "Product-supplier relationships with pricing. Supports multi-supplier sourcing strategy.",
        "carriers": "Shipping carrier master data (FedEx, UPS, DHL, Maersk) with tracking URL templates.",
        "shipments": "Active and historical shipment records with tracking information and delivery status.",
        "shipping_rates": "Shipping cost matrix by carrier, region, weight, and service level.",
        "equipment": "600+ CNC machines and manufacturing equipment across 12 global facilities. "
                    "PHX-CNC-007 is critical equipment involved in the $12M Phoenix Incident.",
        "sensor_readings": "High-frequency IoT sensor data (vibration, temperature, RPM, torque, pressure) "
                          "from manufacturing equipment. Used for predictive maintenance.",
        "anomalies": "ML-detected anomalies and failure predictions. Drives predictive maintenance alerts. "
                    "PHX-CNC-007 has active warnings.",
        "maintenance_records": "Work orders and maintenance history for all equipment. Includes parts used and labor hours.",
        "customers": "B2B customer registry including Apex Aerospace, Tesla, GE, and other major accounts.",
        "orders": "B2B orders with expedite flags and delivery requirements.",
        "titan_facilities": "12 global manufacturing facilities (PHX, MUC, SHA, DET, ATL, TOK, SAO, LON, SYD, SEA, CHI, DAL).",
        "ml_model_coefficients": "Logistic regression coefficients for failure prediction model.",
        "run_to_failure_data": "NASA C-MAPSS style degradation data for training RUL models.",
    }

    print("\nAdding table descriptions (after ingestion completes)...")
    print("  Note: Tables must be ingested first. Run this script again after ingestion.")

    success = 0
    for table, desc in descriptions.items():
        try:
            table_fqn = f"titan-greenplum.{GREENPLUM_DB}.public.{table}"

            # Check if table exists
            resp = requests.get(f"{API_BASE}/tables/name/{table_fqn}", headers=HEADERS)
            if resp.status_code != 200:
                continue

            # Update description
            patch = [{"op": "add", "path": "/description", "value": desc}]
            resp = requests.patch(
                f"{API_BASE}/tables/name/{table_fqn}",
                headers={**HEADERS, "Content-Type": "application/json-patch+json"},
                json=patch
            )
            if resp.status_code == 200:
                success += 1
                print(f"  ✓ {table}")
        except Exception as e:
            pass

    if success > 0:
        print(f"  Updated {success} table descriptions")
    else:
        print("  No tables found yet - run after ingestion completes")


def assign_tables_to_domains():
    """Assign tables to appropriate Titan domains."""
    domain_mappings = {
        "Manufacturing": [
            "equipment", "equipment_types", "sensor_types",
            "sensor_readings", "anomalies", "maintenance_records",
            "failure_events", "run_to_failure_data", "ml_model_coefficients"
        ],
        "SupplyChain": [
            "products", "stock_levels", "suppliers", "product_suppliers",
            "carriers", "shipments", "shipping_rates",
            "customers", "orders", "order_lines"
        ],
    }

    print("\nAssigning tables to domains (after ingestion completes)...")

    success = 0
    for domain, tables in domain_mappings.items():
        for table in tables:
            try:
                table_fqn = f"titan-greenplum.{GREENPLUM_DB}.public.{table}"

                # Check if table exists
                resp = requests.get(f"{API_BASE}/tables/name/{table_fqn}", headers=HEADERS)
                if resp.status_code != 200:
                    continue

                # Get domain reference
                domain_resp = requests.get(f"{API_BASE}/domains/name/{domain}", headers=HEADERS)
                if domain_resp.status_code != 200:
                    continue

                domain_fqn = domain_resp.json().get("fullyQualifiedName", domain)

                # Assign domain
                patch = [{"op": "add", "path": "/domain", "value": {"fullyQualifiedName": domain_fqn}}]
                resp = requests.patch(
                    f"{API_BASE}/tables/name/{table_fqn}",
                    headers={**HEADERS, "Content-Type": "application/json-patch+json"},
                    json=patch
                )
                if resp.status_code == 200:
                    success += 1
            except Exception as e:
                pass

    if success > 0:
        print(f"  Assigned {success} tables to domains")
    else:
        print("  No tables found yet - run after ingestion completes")


def main():
    print("=" * 60)
    print("TITAN MANUFACTURING — OpenMetadata Setup")
    print("=" * 60)

    print(f"\nConfiguration:")
    print(f"  OpenMetadata: {OPENMETADATA_URL}")
    print(f"  Greenplum: {GREENPLUM_HOST}:{GREENPLUM_PORT}/{GREENPLUM_DB}")

    if not wait_for_openmetadata():
        print("ERROR: OpenMetadata not available")
        sys.exit(1)

    # Create business metadata
    create_domains()
    create_glossary()
    create_classifications()

    # Register Greenplum and create ingestion pipeline
    service_id = create_greenplum_service()
    pipeline_id = create_ingestion_pipeline(service_id)
    trigger_ingestion(pipeline_id)

    # Wait a bit for ingestion to start
    if pipeline_id:
        print("\nWaiting for initial ingestion (30 seconds)...")
        time.sleep(30)

    # Post-ingestion enrichment
    add_table_descriptions()
    assign_tables_to_domains()

    print("\n" + "=" * 60)
    print("Setup complete!")
    print(f"\nAccess OpenMetadata: {OPENMETADATA_URL}")
    print("Credentials: admin / admin")
    print("\nNote: If tables weren't enriched, run this script again")
    print("after the ingestion pipeline completes.")
    print("=" * 60)


if __name__ == "__main__":
    main()
