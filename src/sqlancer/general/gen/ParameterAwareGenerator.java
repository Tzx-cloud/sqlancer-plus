package sqlancer.general.gen;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import sqlancer.AFLMonitor;
import sqlancer.Randomly;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.general.GeneralProvider.GeneralGlobalState;

/**
 * Implements Parameter-Aware Test Case Synthesis.
 * This class calculates the generation probability of SQL features based on
 * coverage feedback under specific database parameter configurations.
 */
public class ParameterAwareGenerator {

    private final GeneralGlobalState globalState;
    // Temperature parameter α to enhance differentiation. α > 1.
    private static final double ALPHA = 1.5;

    // Data structures to hold counts for probability calculations.
    // In a real scenario, these would be populated by a coverage tracker.
    // For this example, we'll use placeholder data.
    // Map<ParameterConfig, Map<GeneratorNode, Integer>>
    private final Integer testCounts = 0;
    private final Integer[] featureCounts = new Integer[GeneralExpressionGenerator.Expression.values().length];
    //
    private final Integer[] edgeCounts = new Integer[AFLMonitor.AFL_MAP_SIZE];
    // Map<ParameterConfig, Map<GeneratorNode, Map<Edge, Integer>>>
    private final Integer[][] featureEdgeCounts = new Integer[GeneralExpressionGenerator.Expression.values().length][AFLMonitor.AFL_MAP_SIZE];
    // Map<Edge, Integer>
    private final Long[] totalEdgeHitCounts = new Long[AFLMonitor.AFL_MAP_SIZE];
    // Map<ParameterConfig, Integer>
    private final Map<String, double[]> totalSamplesPerConfig = new HashMap<>();

    public ParameterAwareGenerator(GeneralGlobalState globalState) {
        this.globalState = globalState;
        // TODO: Initialize and populate the count maps from actual coverage data.
    }

    /**
     * Represents the current parameter configuration as a string.
     * In a real implementation, this would serialize the relevant DBMS parameters.
     * @return A string representing the current parameter configuration.
     */
    private String getCurrentParameterConfig() {
        // This is a placeholder. You should replace it with actual parameter serialization.
        // For example, concatenate relevant parameter names and values.
        return "default_config";
    }

    /**
     * Calculates the novelty score for an edge.
     * @param edge The edge identifier.
     * @return The novelty score.
     */
    private double getNovelty(int edge) {
        Long hitCount = totalEdgeHitCounts[edge];
        return 1.0 / Math.sqrt(1.0 + hitCount);
    }

    /**
     * Calculates the mutual information between a feature and an edge for a given parameter configuration.
     * MI(f, e | c) = Σ P(f, e | c) * log2( P(f, e | c) / (P(f | c) * P(e | c)) )
     * @param feature The SQL feature (GeneratorNode).
     * @param edge The edge identifier.
     * @return The mutual information value.
     */
    private double calculateMutualInformation(int feature, int edge) {

        int countF1 = featureCounts[feature];
        int countE1 = edgeCounts[edge];
        int countF1E1 = featureEdgeCounts[feature][edge];

        // Probabilities for f=1, e=1
        double pF1 = (double) countF1 / testCounts;
        double pF0= 1.0 - pF1;
        double pE1 = (double) countE1 / testCounts;
        double pE0 = 1.0 - pE1;
        double pF1E1 = (double) countF1E1 / testCounts;
        double pF1E0 = pF1 - pF1E1;
        double pF0E1 = pE1 - pF1E1;
        double pF0E0 = 1.0 - pF1E1 - pF1E0 - pF0E1;


        double mi = 0.0;

        if (pF1E1 > 0) {
            mi += pF1E1 * Math.log(pF1E1 / (pF1 * pE1));
            mi += pF1E0 * Math.log(pF1E0 / (pF1 * pE0));
            mi += pF0E1 * Math.log(pF0E1 / (pF0 * pE1));
            mi += pF0E0 * Math.log(pF0E0 / (pF0 * pE0));
        }

        return mi / Math.log(2); // Normalize to log base 2
    }

    /**
     * Calculates the weights for all features under the current parameter configuration.
     * @return A map from GeneratorNode to its calculated weight.
     */
    public double[] getFeatureWeights() {

        double[] weights = new double[featureCounts.length];

        for (int i=0;i<featureCounts.length;i++) {
            double totalWeight = 0.0;
            for (int j=0;j<edgeCounts.length;j++) {
                double mi = calculateMutualInformation(i, j);
                double novelty = getNovelty(i);
                totalWeight += mi * novelty;
            }
            weights[i] = totalWeight;
        }
        return weights;
    }

    /**
     * Calculates the generation probabilities for all features based on their weights.
     * @return A map from GeneratorNode to its generation probability.
     */
    public double[] getFeatureProbabilities() {
        double[] weights = getFeatureWeights();
        double[] probabilities = new double[weights.length];
        double totalWeightPowered = 0.0;

        for (int i=0;i<weights.length;i++) {
            double weight = weights[i];
            // Ensure weight is non-negative before applying power
            double poweredWeight = Math.pow(Math.max(0, weight), ALPHA);
            totalWeightPowered += poweredWeight;
        }

        if (totalWeightPowered == 0) {
            // Fallback to uniform probability if all weights are zero
            int numFeatures = GeneratorNode.values().length;
            for (int i=0;i<weights.length;i++) {
                probabilities[i]= 1.0 / numFeatures;
            }
            return probabilities;
        }

        for (int i=0;i<weights.length;i++) {
            probabilities[i]= weights[i] / totalWeightPowered;
        }
        return probabilities;
    }

    /**
     * Selects a random feature based on the calculated probabilities.
     * @return A randomly selected GeneratorNode.
     */
//    public GeneratorNode getRandomFeatureByProbability() {
//        Map<GeneratorNode, Double> probabilities = getFeatureProbabilities();
//        double rand = Randomly.getPercentage();
//        double cumulativeProbability = 0.0;
//
//        for (Map.Entry<GeneratorNode, Double> entry : probabilities.entrySet()) {
//            cumulativeProbability += entry.getValue();
//            if (rand < cumulativeProbability) {
//                return entry.getKey();
//            }
//        }
//        // Fallback in case of rounding errors
//        return Randomly.fromOptions(GeneratorNode.values());
//    }
}
