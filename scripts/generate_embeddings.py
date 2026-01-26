#!/usr/bin/env python3
"""
TITAN MANUFACTURING — pgvector Embedding Generator

Generates embeddings for the products table using OpenAI or Ollama.
Embeddings enable semantic search for natural language product queries.

Usage:
    # With OpenAI
    export OPENAI_API_KEY=sk-...
    python generate_embeddings.py

    # With Ollama (nomic-embed-text)
    export OPENAI_BASE_URL=http://localhost:11434/v1
    export EMBEDDING_MODEL=nomic-embed-text
    python generate_embeddings.py

Environment Variables:
    GREENPLUM_HOST      - Database host (default: localhost)
    GREENPLUM_PORT      - Database port (default: 15432)
    GREENPLUM_DB        - Database name (default: titan-manufacturing)
    GREENPLUM_USER      - Database user (default: gpadmin)
    GREENPLUM_PASSWORD  - Database password (default: VMware1!)
    OPENAI_API_KEY      - OpenAI API key (required for OpenAI)
    OPENAI_BASE_URL     - API base URL (default: https://api.openai.com/v1)
    EMBEDDING_MODEL     - Model name (default: text-embedding-3-small)
    BATCH_SIZE          - Number of products per commit (default: 50)
"""

import os
import sys
import time
import psycopg2
from psycopg2.extras import execute_values

# Try to import openai, provide helpful error if not installed
try:
    from openai import OpenAI
except ImportError:
    print("Error: openai package not installed.")
    print("Install with: pip install openai psycopg2-binary")
    sys.exit(1)

# Configuration from environment
GREENPLUM_HOST = os.getenv("GREENPLUM_HOST", "localhost")
GREENPLUM_PORT = os.getenv("GREENPLUM_PORT", "15432")
GREENPLUM_DB = os.getenv("GREENPLUM_DB", "titan-manufacturing")
GREENPLUM_USER = os.getenv("GREENPLUM_USER", "gpadmin")
GREENPLUM_PASSWORD = os.getenv("GREENPLUM_PASSWORD", "VMware1!")

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
BATCH_SIZE = int(os.getenv("BATCH_SIZE", "50"))

# Embedding dimension (text-embedding-3-small = 1536)
EMBEDDING_DIM = 1536


def get_db_connection():
    """Create database connection."""
    conn_string = f"host={GREENPLUM_HOST} port={GREENPLUM_PORT} dbname={GREENPLUM_DB} user={GREENPLUM_USER} password={GREENPLUM_PASSWORD}"
    return psycopg2.connect(conn_string)


def create_product_text(product):
    """
    Create searchable text from product fields.
    This text will be embedded for semantic search.
    """
    sku, name, description, category, subcategory, division_id = product

    # Build a rich text representation
    parts = [name]

    if description:
        parts.append(description)

    if category:
        parts.append(f"Category: {category}")

    if subcategory:
        parts.append(f"Subcategory: {subcategory}")

    if division_id:
        division_names = {
            "AERO": "Aerospace Division - turbine blades, engine housings, landing gear",
            "ENERGY": "Energy Division - wind turbines, solar frames, valves",
            "MOBILITY": "Mobility Division - EV motors, battery enclosures",
            "INDUSTRIAL": "Industrial Division - CNC parts, bearings, hydraulics"
        }
        parts.append(division_names.get(division_id, f"Division: {division_id}"))

    return ". ".join(parts)


def generate_embedding(client, text):
    """Generate embedding for a single text using OpenAI API."""
    try:
        response = client.embeddings.create(
            model=EMBEDDING_MODEL,
            input=text
        )
        return response.data[0].embedding
    except Exception as e:
        print(f"  Error generating embedding: {e}")
        return None


def format_embedding_for_pgvector(embedding):
    """Format embedding list as pgvector string."""
    return "[" + ",".join(str(x) for x in embedding) + "]"


def main():
    print("=" * 60)
    print("TITAN MANUFACTURING — pgvector Embedding Generator")
    print("=" * 60)

    # Validate API key
    if not OPENAI_API_KEY and "localhost" not in OPENAI_BASE_URL:
        print("\nError: OPENAI_API_KEY environment variable not set.")
        print("Set it with: export OPENAI_API_KEY=sk-...")
        print("Or use Ollama: export OPENAI_BASE_URL=http://localhost:11434/v1")
        sys.exit(1)

    print(f"\nConfiguration:")
    print(f"  Database: {GREENPLUM_HOST}:{GREENPLUM_PORT}/{GREENPLUM_DB}")
    print(f"  API Base: {OPENAI_BASE_URL}")
    print(f"  Model: {EMBEDDING_MODEL}")
    print(f"  Batch Size: {BATCH_SIZE}")

    # Initialize OpenAI client
    client = OpenAI(
        api_key=OPENAI_API_KEY or "ollama",  # Ollama doesn't need a real key
        base_url=OPENAI_BASE_URL
    )

    # Connect to database
    print(f"\nConnecting to Greenplum...")
    try:
        conn = get_db_connection()
        cur = conn.cursor()
        print("  Connected successfully")
    except Exception as e:
        print(f"  Error connecting to database: {e}")
        sys.exit(1)

    # Check current embedding status
    cur.execute("SELECT COUNT(*) FROM products WHERE embedding IS NOT NULL")
    existing_count = cur.fetchone()[0]

    cur.execute("SELECT COUNT(*) FROM products WHERE is_active = TRUE")
    total_count = cur.fetchone()[0]

    print(f"\nEmbedding status:")
    print(f"  Total active products: {total_count}")
    print(f"  Already have embeddings: {existing_count}")
    print(f"  Need embeddings: {total_count - existing_count}")

    if existing_count == total_count:
        print("\nAll products already have embeddings. Nothing to do.")
        cur.close()
        conn.close()
        return

    # Fetch products without embeddings
    print(f"\nFetching products without embeddings...")
    cur.execute("""
        SELECT sku, name, description, category, subcategory, division_id
        FROM products
        WHERE is_active = TRUE AND embedding IS NULL
        ORDER BY sku
    """)
    products = cur.fetchall()
    print(f"  Found {len(products)} products to process")

    # Generate embeddings
    print(f"\nGenerating embeddings...")
    success_count = 0
    error_count = 0
    start_time = time.time()

    for i, product in enumerate(products):
        sku = product[0]
        text = create_product_text(product)

        # Generate embedding
        embedding = generate_embedding(client, text)

        if embedding:
            # Update database
            embedding_str = format_embedding_for_pgvector(embedding)
            try:
                cur.execute(
                    "UPDATE products SET embedding = %s::vector WHERE sku = %s",
                    (embedding_str, sku)
                )
                success_count += 1
            except Exception as e:
                print(f"  Error updating {sku}: {e}")
                error_count += 1
        else:
            error_count += 1

        # Commit and show progress periodically
        if (i + 1) % BATCH_SIZE == 0:
            conn.commit()
            elapsed = time.time() - start_time
            rate = (i + 1) / elapsed
            remaining = (len(products) - i - 1) / rate if rate > 0 else 0
            print(f"  Processed {i + 1}/{len(products)} ({success_count} success, {error_count} errors) "
                  f"- {rate:.1f}/sec, ~{remaining:.0f}s remaining")

            # Rate limiting for OpenAI API
            if "openai.com" in OPENAI_BASE_URL:
                time.sleep(0.1)

    # Final commit
    conn.commit()

    elapsed = time.time() - start_time
    print(f"\nCompleted in {elapsed:.1f} seconds")
    print(f"  Successful: {success_count}")
    print(f"  Errors: {error_count}")

    # Verify final state
    cur.execute("SELECT COUNT(*) FROM products WHERE embedding IS NOT NULL")
    final_count = cur.fetchone()[0]
    print(f"\nFinal state: {final_count}/{total_count} products have embeddings")

    # Test semantic search if embeddings were generated
    if success_count > 0:
        print("\n" + "-" * 40)
        print("Testing semantic search...")

        # Generate a test query embedding
        test_query = "high temperature bearing for CNC spindle"
        print(f"  Query: '{test_query}'")

        query_embedding = generate_embedding(client, test_query)
        if query_embedding:
            query_str = format_embedding_for_pgvector(query_embedding)
            cur.execute("""
                SELECT sku, name, 1 - (embedding <=> %s::vector) as similarity
                FROM products
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> %s::vector
                LIMIT 5
            """, (query_str, query_str))

            results = cur.fetchall()
            print("  Top 5 results:")
            for sku, name, similarity in results:
                print(f"    {similarity:.3f} - {sku}: {name}")

    cur.close()
    conn.close()

    print("\n" + "=" * 60)
    print("Embedding generation complete!")
    print("=" * 60)


if __name__ == "__main__":
    main()
