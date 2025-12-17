package sqlancer.general.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.ast.BinaryOperatorNode.Operator;
import sqlancer.common.ast.newast.ColumnReferenceNode;
import sqlancer.common.ast.newast.NewBetweenOperatorNode;
import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.NewCaseOperatorNode;
import sqlancer.common.ast.newast.NewFunctionNode;
import sqlancer.common.ast.newast.NewInOperatorNode;
import sqlancer.common.ast.newast.NewOrderingTerm;
import sqlancer.common.ast.newast.NewOrderingTerm.Ordering;
import sqlancer.common.ast.newast.NewUnaryPostfixOperatorNode;
import sqlancer.common.ast.newast.NewUnaryPrefixOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.gen.UntypedExpressionGenerator;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;
import sqlancer.general.GeneralSchema.GeneralDataType;
import sqlancer.general.ast.GeneralBinaryArithmeticOperator;
import sqlancer.general.ast.GeneralBinaryComparisonOperator;
import sqlancer.general.ast.GeneralBinaryLogicalOperator;
import sqlancer.general.ast.GeneralBinaryOperator;
import sqlancer.general.ast.GeneralCast;
import sqlancer.general.ast.GeneralCast.GeneralCastOperator;
import sqlancer.general.ast.GeneralConstant;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralFunction;
import sqlancer.general.ast.GeneralUnaryPostfixOperator;
import sqlancer.general.ast.GeneralUnaryPrefixOperator;

import static sqlancer.general.gen.Configuration.BaseConfigurationGenerator.isTrainingPhase;
import static sqlancer.general.gen.ParameterAwareGenerator.featureSet;

public final class

GeneralExpressionGenerator
        extends UntypedExpressionGenerator<Node<GeneralExpression>, GeneralColumn> {

    private final GeneralGlobalState globalState;
    private static double[] curProbabilities = new double[Expression.values().length];
    public GeneralExpressionGenerator(GeneralGlobalState globalState) {
        this.globalState = globalState;
    }
    private final ParameterAwareGenerator parameterAwareGenerator = null; //
    @Override
    public Node<GeneralExpression> generateExpression(boolean isTyped) {

        return generateExpression(0);
    }

    public enum Expression {
        UNARY_POSTFIX(GeneralUnaryPostfixOperator.values().length),
        UNARY_PREFIX(GeneralUnaryPrefixOperator.values().length),
        BINARY_COMPARISON(GeneralBinaryComparisonOperator.values().length),
        BINARY_LOGICAL(GeneralBinaryLogicalOperator.values().length),
        BINARY_OPERATOR(GeneralBinaryOperator.getOperators().size()),
        BINARY_ARITHMETIC(GeneralBinaryArithmeticOperator.values().length),
        CAST(GeneralCastOperator.values().length),
        FUNC(GeneralFunction.getNrFunctionsNum() / 10),
        BETWEEN(1),
        CASE(1),
        IN(1);

        private int numOptions;

        Expression(int numOptions) {
            this.numOptions = numOptions;
        }

        private static double getTotal() {
            double total = 0;
            for (Expression e : Expression.values()) {
                total += e.numOptions;
            }
            return total;
        }
        // 修改此方法以使用 ParameterAwareGenerator
        public static Expression getRandomByProportion(GeneralErrorHandler handler) {

                    if (isTrainingPhase)return Randomly.fromOptions(values());
                    double rand = Randomly.getPercentage();
                    double cumulativeProbability = 0.0;

                    for (int i = 0; i < Expression.values().length; i++) {
                        Expression e = Expression.values()[i];
                        if (!handler.getOption(GeneratorNode.valueOf(e.toString()))) {
                            continue;
                        }
                        cumulativeProbability += curProbabilities[i];
                         if (rand < cumulativeProbability) {
                            return e;
                        }
                    }
                    return Randomly.fromOptions(values());
        }

//        public static Expression getRandomByProportion(GeneralErrorHandler handler) {
//            double rand = Randomly.getPercentage();
//            double total = getTotal();
//            double sum = 0;
//            for (Expression e : Expression.values()) {
//                sum += e.numOptions / total;
//                if (!handler.getOption(GeneratorNode.valueOf(e.toString()))) {
//                    continue;
//                }
//                if (rand < sum) {
//                    return e;
//                }
//            }
//            return Randomly.fromOptions(values());
//        }
    }

    @Override
    protected Node<GeneralExpression> generateExpression(int depth) {
        GeneralErrorHandler handler = globalState.getHandler();
        if (depth >= globalState.getOptions().getMaxExpressionDepth()
                || depth >= globalState.getHandler().getCurDepth(globalState.getDatabaseName())
                || Randomly.getBooleanWithRatherLowProbability()) {
            return generateLeafNode();
        }
        // 将 parameterAwareGenerator 传递给选择方法
        Expression expr = Expression.getRandomByProportion(handler);
        // TODO Handle IllegalArgumentException
        globalState.getHandler().addScore(GeneratorNode.valueOf(expr.toString()));
        switch (expr) {

            case UNARY_POSTFIX:
                featureSet.add(Expression.UNARY_POSTFIX);
                return new NewUnaryPostfixOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                        GeneralUnaryPostfixOperator.getRandomByOptions(handler));
        case UNARY_PREFIX:
            featureSet.add(Expression.UNARY_PREFIX);
            return new NewUnaryPrefixOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    GeneralUnaryPrefixOperator.getRandomByOptions(handler));
        case BINARY_COMPARISON:
            featureSet.add(Expression.BINARY_COMPARISON);
            return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), GeneralBinaryComparisonOperator.getRandomByOptions(handler));
        case BINARY_LOGICAL:
            featureSet.add(Expression.BINARY_LOGICAL);
            return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), GeneralBinaryLogicalOperator.getRandomByOptions(handler));

            case BINARY_OPERATOR:
                featureSet.add(Expression.BINARY_OPERATOR);
                return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                        generateExpression(depth + 1), GeneralBinaryOperator.getRandomByOptions(handler));
        case BINARY_ARITHMETIC:
            featureSet.add(Expression.BINARY_ARITHMETIC);
            return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), GeneralBinaryArithmeticOperator.getRandomByOptions(handler));

        case CAST:
            featureSet.add(Expression.CAST);
            return new GeneralCast(generateExpression(depth + 1), GeneralCompositeDataType.getRandomWithoutNull(),
                    GeneralCastOperator.getRandomByOptions(handler));
        case FUNC:
            featureSet.add(Expression.FUNC);
            // GeneralDBFunction func = GeneralDBFunction.getRandomByOptions(handler);
            GeneralFunction func = GeneralFunction.getRandomByOptions(handler);
            if (func != null) {
                return new NewFunctionNode<GeneralExpression, GeneralFunction>(
                        generateExpressions(func.getNrArgs(), depth + 1), func);
            }
            case BETWEEN:
            featureSet.add(Expression.BETWEEN);
            return new NewBetweenOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), generateExpression(depth + 1), Randomly.getBoolean());

        case CASE:
            featureSet.add(Expression.CASE);
            int nr = Randomly.smallNumber() + 1;
            return new NewCaseOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpressions(nr, depth + 1), generateExpressions(nr, depth + 1),
                    generateExpression(depth + 1));
            case IN:
                featureSet.add(Expression.IN);
                return new NewInOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                        generateExpressions(Randomly.smallNumber() + 1, depth + 1), Randomly.getBoolean());

        default:
            throw new AssertionError();
        }
    }

    @Override
    protected Node<GeneralExpression> generateColumn() {
        GeneralColumn column = Randomly.fromList(columns);
        return new ColumnReferenceNode<GeneralExpression, GeneralColumn>(column);
    }

    @Override
    public Node<GeneralExpression> generateConstant() {
        if (Randomly.getBooleanWithSmallProbability()) {
            return GeneralConstant.createNullConstant();
        }
        GeneralDataType type = GeneralDataType.getRandomWithProb();
        switch (type) {
        case INT:
            if (!globalState.getDbmsSpecificOptions().testIntConstants) {
                throw new IgnoreMeException();
            }
            return GeneralConstant.createIntConstant(globalState.getRandomly().getInteger());
        // case DATE:
        // if (!globalState.getDbmsSpecificOptions().testDateConstants) {
        // throw new IgnoreMeException();
        // }
        // return GeneralConstant.createDateConstant(globalState.getRandomly().getInteger());
        // case TIMESTAMP:
        // if (!globalState.getDbmsSpecificOptions().testTimestampConstants) {
        // throw new IgnoreMeException();
        // }
        // return GeneralConstant.createTimestampConstant(globalState.getRandomly().getInteger());
        case STRING:
        case VARTYPE:
            // if (!globalState.getDbmsSpecificOptions().testStringConstants) {
            // throw new IgnoreMeException();
            // }
            return GeneralConstant.createStringConstant(globalState.getRandomly().getString());
        case BOOLEAN:
            if (!globalState.getDbmsSpecificOptions().testBooleanConstants) {
                throw new IgnoreMeException();
            }
            return GeneralConstant.createBooleanConstant(Randomly.getBoolean());
        // case FLOAT:
        // if (!globalState.getDbmsSpecificOptions().testFloatConstants) {
        // throw new IgnoreMeException();
        // }
        // return GeneralConstant.createFloatConstant(globalState.getRandomly().getDouble());
        default:
            throw new AssertionError();
        }
    }

    @Override
    public List<Node<GeneralExpression>> generateOrderBys() {
        List<Node<GeneralExpression>> expr = super.generateOrderBys();
        List<Node<GeneralExpression>> newExpr = new ArrayList<>(expr.size());
        for (Node<GeneralExpression> curExpr : expr) {
            if (Randomly.getBoolean()) {
                curExpr = new NewOrderingTerm<>(curExpr, Ordering.getRandom());
            }
            newExpr.add(curExpr);
        }
        return newExpr;
    };

    public enum GeneralAggregateFunction {
        MAX(1), MIN(1), AVG(1), COUNT(1), STRING_AGG(1), FIRST(1), SUM(1), STDDEV_SAMP(1), STDDEV_POP(1), VAR_POP(1),
        VAR_SAMP(1), COVAR_POP(1), COVAR_SAMP(1);

        private int nrArgs;

        GeneralAggregateFunction(int nrArgs) {
            this.nrArgs = nrArgs;
        }

        public static GeneralAggregateFunction getRandom() {
            return Randomly.fromOptions(values());
        }

        public int getNrArgs() {
            return nrArgs;
        }

    }

    public static final class GeneralCollate implements Operator {

        private final String textRepr;

        private GeneralCollate(String textRepr) {
            this.textRepr = textRepr;
        }

        @Override
        public String getTextRepresentation() {
            return "COLLATE " + textRepr;
        }

        public static GeneralCollate getRandom() {
            return new GeneralCollate(GeneralTableGenerator.getRandomCollate());
        }

    }

    // public NewFunctionNode<GeneralExpression, GeneralAggregateFunction> generateArgsForAggregate(
    // GeneralAggregateFunction aggregateFunction) {
    // return new NewFunctionNode<GeneralExpression, GeneralAggregateFunction>(
    // generateExpressions(aggregateFunction.getNrArgs()), aggregateFunction);
    // }

    // public Node<GeneralExpression> generateAggregate() {
    // GeneralAggregateFunction aggrFunc = GeneralAggregateFunction.getRandom();
    // return generateArgsForAggregate(aggrFunc);
    // }

    @Override
    public Node<GeneralExpression> negatePredicate(Node<GeneralExpression> predicate) {
        return new NewUnaryPrefixOperatorNode<>(predicate, GeneralUnaryPrefixOperator.NOT);
    }

    @Override
    public Node<GeneralExpression> isNull(Node<GeneralExpression> expr) {
        return new NewUnaryPostfixOperatorNode<>(expr, GeneralUnaryPostfixOperator.IS_NULL);
    }

}
