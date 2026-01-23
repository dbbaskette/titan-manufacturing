#!/usr/bin/env python3
"""
TITAN MANUFACTURING — OpenMetadata Setup
Configures data catalog with Titan's domains, glossary, and classifications.
"""

import requests
import time
import sys

OPENMETADATA_URL = "http://localhost:8585"
API_BASE = f"{OPENMETADATA_URL}/api/v1"
HEADERS = {"Content-Type": "application/json"}


def wait_for_openmetadata():
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
    """Create Titan business domains"""
    domains = [
        {"name": "TitanAerospace", "displayName": "Titan Aerospace", 
         "description": "Turbine blades, engine housings, landing gear for Boeing, Airbus, SpaceX", "domainType": "Aggregate"},
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
    """Create Titan business glossary"""
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
    
    terms = [
        {"name": "RUL", "displayName": "Remaining Useful Life", 
         "description": "Predicted time until equipment failure, key metric after $12M Phoenix incident"},
        {"name": "SKU", "displayName": "Stock Keeping Unit",
         "description": "Unique product identifier across Titan's 50,000+ part catalog"},
        {"name": "Expedite", "displayName": "Expedite Order",
         "description": "Priority order requiring accelerated fulfillment (e.g., Boeing urgent requests)"},
        {"name": "FAA-Compliance", "displayName": "FAA Compliance",
         "description": "Federal Aviation Administration traceability requirements for aerospace parts"},
        {"name": "MaterialBatch", "displayName": "Material Batch",
         "description": "Traceable lot of raw material with certification for quality audits"},
    ]
    
    print("Creating glossary terms...")
    for term in terms:
        resp = requests.post(f"{API_BASE}/glossaryTerms", headers=HEADERS, json=term)
        status = "✓" if resp.status_code in [200, 201] else "-"
        print(f"  {status} {term['name']}")


def create_classifications():
    """Create Titan data classifications"""
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


def main():
    print("=" * 60)
    print("TITAN MANUFACTURING — OpenMetadata Setup")
    print("=" * 60)
    
    if not wait_for_openmetadata():
        print("ERROR: OpenMetadata not available")
        sys.exit(1)
    
    create_domains()
    create_glossary()
    create_classifications()
    
    print("\n" + "=" * 60)
    print("Setup complete!")
    print(f"Access OpenMetadata: {OPENMETADATA_URL}")
    print("Credentials: admin / admin")
    print("=" * 60)


if __name__ == "__main__":
    main()
