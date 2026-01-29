package com.titan.gemfire;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.jpmml.evaluator.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GemFire server-side Function for PMML model evaluation.
 *
 * Deployed via {@code gfsh deploy --jar} and executed via
 * {@code FunctionService.onRegion(pmmlModelsRegion)}.
 *
 * Reads the PMML model from the PmmlModels region,
 * evaluates it against input features using JPMML-evaluator,
 * and returns the failure probability.
 *
 * Input: String[] { modelId, equipmentId, feature1=val1, feature2=val2, ... }
 * Output: String "equipmentId|probability|riskLevel"
 */
public class PmmlScoringFunction implements Function<String[]> {

    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(PmmlScoringFunction.class.getName());

    // Cached evaluator — rebuilt when model changes
    private transient volatile Evaluator cachedEvaluator;
    private transient volatile String cachedModelXml;

    @Override
    public String getId() {
        return "PmmlScoringFunction";
    }

    @Override
    public boolean hasResult() {
        return true;
    }

    @Override
    public boolean isHA() {
        return false;
    }

    @Override
    public boolean optimizeForWrite() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(FunctionContext<String[]> context) {
        try {
            String[] args = context.getArguments();
            if (args == null || args.length < 2) {
                context.getResultSender().lastResult("ERROR|Missing arguments: modelId, equipmentId, features...");
                return;
            }

            String modelId = args[0];
            String equipmentId = args[1];

            // Parse feature key=value pairs from remaining args
            Map<String, Double> features = new LinkedHashMap<>();
            for (int i = 2; i < args.length; i++) {
                String[] kv = args[i].split("=", 2);
                if (kv.length == 2) {
                    features.put(kv[0], Double.parseDouble(kv[1]));
                }
            }

            // Get PMML from the PmmlModels region via server cache
            Region<String, String> pmmlRegion = context.getCache().getRegion("PmmlModels");
            if (pmmlRegion == null) {
                context.getResultSender().lastResult("ERROR|PmmlModels region not found in server cache");
                return;
            }

            // Get or build evaluator
            Evaluator evaluator = getEvaluator(modelId, pmmlRegion);
            if (evaluator == null) {
                context.getResultSender().lastResult("ERROR|Model '" + modelId + "' not found in PmmlModels region");
                return;
            }

            // Build input map for JPMML
            Map<String, Object> inputMap = new LinkedHashMap<>();
            for (InputField inputField : evaluator.getInputFields()) {
                String fieldName = inputField.getName();
                Double value = features.get(fieldName);
                if (value != null) {
                    inputMap.put(fieldName, value);
                }
            }

            // Evaluate the model
            Map<String, ?> results = evaluator.evaluate(inputMap);

            // Extract probability from the regression output
            double probability = 0.0;
            for (Map.Entry<String, ?> entry : results.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Computable) {
                    val = ((Computable) val).getResult();
                }
                if (val instanceof Number) {
                    probability = ((Number) val).doubleValue();
                }
            }

            // Clamp to [0, 1]
            probability = Math.max(0.0, Math.min(1.0, probability));

            // Determine risk level
            String riskLevel;
            if (probability >= 0.7) riskLevel = "CRITICAL";
            else if (probability >= 0.5) riskLevel = "HIGH";
            else if (probability >= 0.3) riskLevel = "MEDIUM";
            else riskLevel = "LOW";

            String result = equipmentId + "|" + probability + "|" + riskLevel;
            context.getResultSender().lastResult(result);

        } catch (Throwable e) {
            log.log(Level.SEVERE, "PMML scoring error", e);
            String msg = e.getClass().getName() + ": " + e.getMessage();
            if (e.getCause() != null) {
                msg += " caused by " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage();
            }
            context.getResultSender().lastResult("ERROR|" + msg);
        }
    }

    private Evaluator getEvaluator(String modelId, Region<String, String> pmmlRegion) throws Exception {
        String pmmlXml = pmmlRegion.get(modelId);
        if (pmmlXml == null) {
            return null;
        }

        // Return cached evaluator if model hasn't changed
        if (cachedEvaluator != null && pmmlXml.equals(cachedModelXml)) {
            return cachedEvaluator;
        }

        // Parse and build evaluator using LoadingModelEvaluatorBuilder (JPMML 1.6.x)
        Evaluator evaluator = new LoadingModelEvaluatorBuilder()
            .load(new ByteArrayInputStream(pmmlXml.getBytes(StandardCharsets.UTF_8)))
            .build();

        cachedEvaluator = evaluator;
        cachedModelXml = pmmlXml;

        log.info("PMML model loaded: " + modelId + " with " + evaluator.getInputFields().size() + " input fields");
        return evaluator;
    }
}
