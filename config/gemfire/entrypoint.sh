#!/bin/bash
set -e

GEMFIRE_HOST=${GEMFIRE_HOSTNAME:-gemfire}

echo "Starting GemFire locator..."
gfsh start locator \
  --name=locator1 \
  --port=10334 \
  --hostname-for-clients=$GEMFIRE_HOST \
  --J=-Xmx512m

echo "Starting GemFire server..."
gfsh start server \
  --name=server1 \
  --locators=localhost[10334] \
  --server-port=40404 \
  --hostname-for-clients=$GEMFIRE_HOST \
  --classpath=/opt/gemfire/lib/gemfire-old-client-support-10.2.1.jar \
  --J=-Xmx1g

echo "Creating Titan Manufacturing regions..."
gfsh -e "connect --locator=localhost[10334]" \
     -e "create region --name=PmmlModels --type=REPLICATE --if-not-exists" \
     -e "create region --name=SensorPredictions --type=PARTITION --if-not-exists" \
     -e "create region --name=EquipmentState --type=PARTITION --if-not-exists" || true

# Deploy PMML scoring function JAR via gfsh deploy (standard GemFire pattern)
if [ -f /opt/gemfire/extensions/gemfire-scoring-function.jar ]; then
  echo "Deploying PMML scoring function to GemFire..."
  gfsh -e "connect --locator=localhost[10334]" \
       -e "deploy --jar=/opt/gemfire/extensions/gemfire-scoring-function.jar" || true
  echo "PMML scoring function deployed."
else
  echo "WARNING: gemfire-scoring-function.jar not found at /opt/gemfire/extensions/"
fi

echo "GemFire cluster is ready for Titan Manufacturing."
echo "  Locator:    $GEMFIRE_HOST:10334"
echo "  Server:     $GEMFIRE_HOST:40404"
echo "  Regions:    PmmlModels, SensorPredictions, EquipmentState"
echo "  Functions:  PmmlScoringFunction"

# Keep container running and tail logs
tail -f /data/locator1/locator1.log /data/server1/server1.log
