#!/bin/bash
set -e

HOSTNAME=$(hostname)

echo "Starting GemFire locator..."
gfsh start locator \
  --name=locator1 \
  --port=10334 \
  --hostname-for-clients=$HOSTNAME \
  --J=-Xmx512m

echo "Starting GemFire server..."
gfsh start server \
  --name=server1 \
  --locators=localhost[10334] \
  --server-port=40404 \
  --hostname-for-clients=$HOSTNAME \
  --J=-Xmx1g

echo "Creating Titan Manufacturing regions..."
gfsh -e "connect --locator=localhost[10334]" \
     -e "create region --name=PmmlModels --type=REPLICATE" \
     -e "create region --name=SensorPredictions --type=PARTITION" \
     -e "create region --name=EquipmentState --type=PARTITION"

echo "GemFire cluster is ready for Titan Manufacturing."
echo "  Locator:    $HOSTNAME:10334"
echo "  Server:     $HOSTNAME:40404"
echo "  Regions:    PmmlModels, SensorPredictions, EquipmentState"

# Keep container running and tail logs
tail -f /data/locator1/locator1.log /data/server1/server1.log
