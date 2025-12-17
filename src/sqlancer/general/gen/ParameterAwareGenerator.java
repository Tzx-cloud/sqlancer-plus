package sqlancer.general.gen;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import sqlancer.AFLMonitor;
import sqlancer.Randomly;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.general.GeneralProvider.GeneralGlobalState;

import static sqlancer.AFLMonitor.coverageBuf;

/**
 * Implements Parameter-Aware Test Case Synthesis.
 * This class calculates the generation probability of SQL features based on
 * coverage feedback under specific database parameter configurations.
 */
public class ParameterAwareGenerator {

//    private final GeneralGlobalState globalState;
    // Temperature parameter α to enhance differentiation. α > 1.
    private static final double ALPHA = 1.5;

    // Data structures to hold counts for probability calculations.
    // In a real scenario, these would be populated by a coverage tracker.
    // For this example, we'll use placeholder data.
    // Map<ParameterConfig, Map<GeneratorNode, Integer>>

    public static Set<GeneralExpressionGenerator.Expression> featureSet = new java.util.HashSet<>();

    private int testCounts = 0;
    private final int[] featureCounts = new int[GeneralExpressionGenerator.Expression.values().length];
    //
    private final int[] edgeCounts = new int[AFLMonitor.AFL_MAP_SIZE];
    // Map<ParameterConfig, Map<GeneratorNode, Map<Edge, Integer>>>
    private final  int[][] featureEdgeCounts = new int[GeneralExpressionGenerator.Expression.values().length][AFLMonitor.AFL_MAP_SIZE];
    // Map<Edge, Integer>
    private final long[] totalEdgeHitCounts = new long[AFLMonitor.AFL_MAP_SIZE];
//    // Map<ParameterConfig, Integer>
//    private final Map<String, double[]> totalSamplesPerConfig = new HashMap<>();

//    public ParameterAwareGenerator(GeneralGlobalState globalState) {
//        this.globalState = globalState;
//        // TODO: Initialize and populate the count maps from actual coverage data.
//    }

    /**
     * Calculates the novelty score for an edge.
     * @param edge The edge identifier.
     * @return The novelty score.
     */
    private double getNovelty(int edge) {
        long hitCount = totalEdgeHitCounts[edge];
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
        // 从数组中获取计数
        int countF1 = featureCounts[feature];
        int countE1 = edgeCounts[edge];
        int countF1E1 = featureEdgeCounts[feature][edge];



        // 计算联合事件和边缘事件的计数
        int countF0 = testCounts - countF1;
        int countE0 = testCounts - countE1;
        int countF1E0 = countF1 - countF1E1;
        int countF0E1 = countE1 - countF1E1;
        int countF0E0 = countF0 - countF0E1;

        // 如果总测试次数、特征计数或边计数为零，则互信息为零，提前返回
        if (testCounts == 0 || countF1 == 0 || countE1 == 0||countE0==0||countF0==0) {
            return 0.0;
        }
        double mi = 0.0;
        // 根据互信息公式，逐项计算
        // MI = Σ p(x,y) * log2( p(x,y) / (p(x)*p(y)) )
        if (countF1E1 > 0) {
            double pF1E1 = (double) countF1E1 / testCounts;
            mi += pF1E1 * Math.log((double) (countF1E1 * testCounts) / (countF1 * countE1));
        }
        if (countF1E0 > 0) {
            double pF1E0 = (double) countF1E0 / testCounts;
            mi += pF1E0 * Math.log((double) (countF1E0 * testCounts)/ (countF1  * countE0 ));
        }
        if (countF0E1 > 0) {
            double pF0E1 = (double) countF0E1 / testCounts;
            mi += pF0E1 * Math.log((double) (countF0E1 * testCounts) / (countF0 * countE1));

        }
        if (countF0E0 > 0) {
            double pF0E0 = (double) countF0E0 / testCounts;
            mi += pF0E0 * Math.log((double) (countF0E0 * testCounts) / ( countF0  * countE0 ));
        }
        // 将对数底从自然对数e转换为2
        return mi / Math.log(2);
    }

    /**
     * Calculates the weights for all features under the current parameter configuration.
     * @return A map from GeneratorNode to its calculated weight.
     */
    public double[] getFeatureWeights() {
        double[] weights = new double[featureCounts.length];

        // 优化：交换内外循环，外层遍历边，内层遍历特征
        for (int j = 0; j < edgeCounts.length; j++) {
            // 优化：如果一个边从未被触发，它对任何特征的权重贡献都为0，跳过
            if (edgeCounts[j] == 0) {
                continue;
            }

            // 优化：在内层循环外计算一次 novelty
            // 修正：getNovelty应该使用边的索引j，而不是特征的索引i
            double novelty = getNovelty(j);

            for (int i = 0; i < featureCounts.length; i++) {
                double mi = calculateMutualInformation(i, j);
                weights[i] += mi * novelty;
            }
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

    public void updateCounts() {
        testCounts++;

        // 将对 featureCounts 的更新移到循环外，因为它与 coverageBuf 的内容无关
        for (GeneralExpressionGenerator.Expression feature : featureSet) {
            featureCounts[feature.ordinal()] += 1;
        }

        // 只遍历一次 coverageBuf
        for (int i = 0; i < AFLMonitor.AFL_MAP_SIZE; i++) {
            // 仅在覆盖信息不为零时处理
            if (coverageBuf[i] != 0) {

                // 更新基本边计数
                edgeCounts[i] += 1;
                totalEdgeHitCounts[i] +=(coverageBuf[i]& 0xFF);

                // 一次性更新所有 feature 相关的边计数
                for (GeneralExpressionGenerator.Expression feature : featureSet) {
                    featureEdgeCounts[feature.ordinal()][i] += 1;
                }
            }
        }
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
