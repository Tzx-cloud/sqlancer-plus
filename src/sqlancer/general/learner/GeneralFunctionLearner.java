package sqlancer.general.learner;

import java.util.HashMap;

import sqlancer.FeatureLearner;
import sqlancer.general.ast.GeneralFunction;

public class GeneralFunctionLearner implements FeatureLearner {
    private static HashMap<String, Integer> functions = new HashMap<>();

    @Override 
    public void learn() {
    }

    @Override
    public void update() {
        GeneralFunction.mergeFunctions(functions);
    }

    public static HashMap<String, Integer> getFunctions() {
        return functions;
    }
}
