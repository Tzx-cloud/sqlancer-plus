package sqlancer.general.gen.Configuration;

import sqlancer.AFLMonitor;
import sqlancer.GlobalState;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.common.query.SQLQueryAdapter;
import java.util.*;
import java.util.function.Function;

public abstract class BaseConfigurationGenerator {
    protected final Randomly r;
    protected final StringBuilder sb = new StringBuilder();
    protected boolean isSingleThreaded;

    // 训练相关的静态变量
    protected static Map<String, Double> databaseParameterProbabilities = new HashMap<>();
    public static Map<String, double[]> parameterFeatureProbabilities = new HashMap<>();
    public static boolean isTrainingPhase = false;
    public static final int TRAINING_SAMPLES = 1;

    // 覆盖率相关
    public static Map<String, byte[]> parameterEdgeCoverage = new HashMap<>();

    public enum Scope {
        GLOBAL, SESSION
    }

    public interface ConfigurationAction {
        String getName();
        Object generateValue(Randomly r);
        Scope[] getScopes();
        boolean canBeUsedInScope(Scope scope);
    }

    protected static class GenericAction implements ConfigurationAction {
        private final String name;
        private final Function<Randomly, Object> producer;
        private final Scope[] scopes;

        public GenericAction(String name, Function<Randomly, Object> producer, Scope... scopes) {
            if (scopes.length == 0) {
                throw new AssertionError("Action must have at least one scope: " + name);
            }
            this.name = name;
            this.producer = producer;
            this.scopes = scopes.clone();
        }
        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object generateValue(Randomly r) {
            return producer.apply(r);
        }

        @Override
        public Scope[] getScopes() {
            return scopes.clone();
        }

        @Override
        public boolean canBeUsedInScope(Scope scope) {
            for (Scope s : scopes) {
                if (s == scope) {
                    return true;
                }
            }
            return false;
        }


    }

    public BaseConfigurationGenerator(Randomly r, MainOptions options) {
        this.r = r;
        this.isSingleThreaded = options.getNumberConcurrentThreads() == 1;
    }



    // 抽象方法，子类必须实现
    protected abstract String getDatabaseType();
    public abstract ConfigurationAction[] getAllActions();
    protected abstract SQLQueryAdapter generateConfigForAction(Object action);
    protected abstract String getActionName(Object action);
    public abstract SQLQueryAdapter generateConfigForParameter( ConfigurationAction action);
    public abstract SQLQueryAdapter generateDefaultConfigForParameter( ConfigurationAction action);


    public void calculateParameterWeights() {
        Double[] edgeScores = calculateEdgeScores();
        Map<String, Double> parameterWeights = new HashMap<>();
        double totalWeight = 0.0;

        // 计算每个参数的权重
        for (String parameter : parameterEdgeCoverage.keySet()) {
            double weight = 0.0;
            byte[] coveredEdges = parameterEdgeCoverage.get(parameter);
            for(int i=0;i< coveredEdges.length;i++){
                if(coveredEdges[i]!=0){
                    weight += edgeScores[i];
                }
            }
            parameterWeights.put(parameter, weight);
            totalWeight += weight;
        }

        // 计算概率
        for (Map.Entry<String, Double> entry : parameterWeights.entrySet()) {
            databaseParameterProbabilities.put(entry.getKey(),entry.getValue() / totalWeight);
        }
         parameterEdgeCoverage.clear();
    }

    private Double[] calculateEdgeScores() {
        Double[] edgeScores = new Double[AFLMonitor.AFL_MAP_SIZE];
        int totalParameters = parameterEdgeCoverage.size();

        for (int i=0;i< AFLMonitor.AFL_MAP_SIZE;i++) {
            int coveringParametersCount = 0;
            for (byte[] coveredEdges : parameterEdgeCoverage.values()) {
                if (coveredEdges[i]!=0) {
                    coveringParametersCount++;
                }
            }

            if (coveringParametersCount > 0) {
                double score = Math.log(1.0 + (double) totalParameters / coveringParametersCount);
                edgeScores[i]=score;
            }
        }

        return edgeScores;
    }

    private SQLQueryAdapter generateByWeight() {
        Object selectedAction = selectActionByWeight();
        return generateConfigForAction(selectedAction);
    }

    private Object selectActionByWeight() {
        Object[] actions = getAllActions();
        double random = Math.random();
        double cumulativeProbability = 0.0;

        for (Object action : actions) {
            String parameterName = getActionName(action);
            double probability = databaseParameterProbabilities
                    .getOrDefault(parameterName, 1.0 / actions.length);
            cumulativeProbability += probability;

            if (random <= cumulativeProbability) {
                return action;
            }
        }

        return Randomly.fromOptions(actions);
    }


}
