package sqlancer.general.ast;

import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.ast.BinaryOperatorNode.Operator;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralLearningManager.SQLFeature;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.learner.GeneralFragments;
import sqlancer.general.learner.GeneralStringBuilder;

public class GeneralBinaryOperator implements Operator {

    private String name;

    private static final String CONFIG_NAME = "operators.txt";
    private static final SQLFeature FEATURE = SQLFeature.OPERATOR;

    private static List<String> operators = new ArrayList<>();
    private static GeneralBinaryOperatorFragments fragments = new GeneralBinaryOperatorFragments();



    private final static class GeneralBinaryOperatorFragments extends GeneralFragments {
        public GeneralBinaryOperatorFragments() {
            super();
        }

        @Override
        public synchronized String genLearnStatement(GeneralGlobalState globalState) {
            setLearn(true);
            String stmt = getQuery(globalState).getQueryString();
            return stmt;
        }

        @Override
        public String getConfigName() {
            return CONFIG_NAME;
        }

        public String getStatementType() {
            return "OPERATOR";
        }

        @Override
        protected String getVariables() {
            return "";
        }

        @Override
        public SQLFeature getFeature() {
            return FEATURE;
        }
    }
    
    @Override
    public String getTextRepresentation() {
        return toString();
    }

    public GeneralBinaryOperator(String name) {
        this.name = name;
    }

    public static GeneralBinaryOperator getRandomByOptions(GeneralErrorHandler handler) {
        GeneralBinaryOperator op;
        String node;
        do {
            String opName = Randomly.fromList(operators);
            op = new GeneralBinaryOperator(opName);
            node = "BINOP" + op.toString();
        } while (!handler.getCompositeOption(node) || !Randomly.getBooleanWithSmallProbability());
        handler.addScore(node);
        return op;
    }

    public String toString() {
        return name;
    }

    public static SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        GeneralStringBuilder<GeneralBinaryOperatorFragments> sb = new GeneralStringBuilder<GeneralBinaryOperatorFragments>(
                globalState, fragments);
        // loop 1 to 4
        for (int i = 0; i < 4; i++) {
            sb.append("SELECT ", i);
            // string of nulls with length of i
            sb.append("(");
            for (int j = 0; j < i; j++) {
                sb.append("NULL");
                if (j != i - 1) {
                    sb.append(", ");
                }
            }
            sb.append(");");
            sb.append(String.format(" -- Hint: Function with %d arguments\n", i));
        }
        return new SQLQueryAdapter(sb.toString());
    }
}
