package sqlancer.general.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import sqlancer.general.ast.GeneralCast;
import sqlancer.general.ast.GeneralConstant;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralFunction;
import sqlancer.general.ast.GeneralUnaryPostfixOperator;
import sqlancer.general.ast.GeneralUnaryPrefixOperator;
import sqlancer.general.ast.GeneralCast.GeneralCastOperator;

public final class GeneralExpressionGenerator
        extends UntypedExpressionGenerator<Node<GeneralExpression>, GeneralColumn> {

    private final GeneralGlobalState globalState;

    public GeneralExpressionGenerator(GeneralGlobalState globalState) {
        this.globalState = globalState;
    }

    private enum Expression {
        UNARY_POSTFIX, UNARY_PREFIX, BINARY_COMPARISON, BINARY_LOGICAL, BINARY_ARITHMETIC, CAST, FUNC, BETWEEN, CASE,
        IN
    }

    @Override
    protected Node<GeneralExpression> generateExpression(int depth) {
        GeneralErrorHandler handler = globalState.getHandler();
        if (depth >= globalState.getOptions().getMaxExpressionDepth()
                || depth >= globalState.getHandler().getCurDepth(globalState.getDatabaseName())
                || Randomly.getBoolean()) {
            return generateLeafNode();
        }
        // if (allowAggregates && Randomly.getBoolean()) {
        // GeneralAggregateFunction aggregate = GeneralAggregateFunction.getRandom();
        // allowAggregates = false;
        // return new NewFunctionNode<>(generateExpressions(aggregate.getNrArgs(), depth + 1), aggregate);
        // }
        List<Expression> possibleOptions = new ArrayList<>(Arrays.asList(Expression.values()));
        if (!globalState.getDbmsSpecificOptions().testFunctions | !handler.getOption(GeneratorNode.FUNC)) {
            possibleOptions.remove(Expression.FUNC);
        }
        if (!globalState.getDbmsSpecificOptions().testCasts | !handler.getOption(GeneratorNode.CAST)) {
            possibleOptions.remove(Expression.CAST);
        }
        if (!globalState.getDbmsSpecificOptions().testBetween | !handler.getOption(GeneratorNode.BETWEEN)) {
            possibleOptions.remove(Expression.BETWEEN);
        }
        if (!globalState.getDbmsSpecificOptions().testIn | !handler.getOption(GeneratorNode.IN)) {
            possibleOptions.remove(Expression.IN);
        }
        if (!globalState.getDbmsSpecificOptions().testCase | !handler.getOption(GeneratorNode.CASE)) {
            possibleOptions.remove(Expression.CASE);
        }
        if (!globalState.getDbmsSpecificOptions().testBinaryComparisons
                | !handler.getOption(GeneratorNode.BINARY_COMPARISON)) {
            possibleOptions.remove(Expression.BINARY_COMPARISON);
        }
        if (!globalState.getDbmsSpecificOptions().testBinaryLogicals
                | !handler.getOption(GeneratorNode.BINARY_LOGICAL)) {
            possibleOptions.remove(Expression.BINARY_LOGICAL);
        }
        if (!handler.getOption(GeneratorNode.UNARY_POSTFIX)) {
            possibleOptions.remove(Expression.UNARY_POSTFIX);
        }
        if (!handler.getOption(GeneratorNode.UNARY_PREFIX)) {
            possibleOptions.remove(Expression.UNARY_PREFIX);
        }
        Expression expr = Randomly.fromList(possibleOptions);
        // TODO Handle IllegalArgumentException
        globalState.getHandler().addScore(GeneratorNode.valueOf(expr.toString()));
        switch (expr) {
        case UNARY_PREFIX:
            return new NewUnaryPrefixOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    GeneralUnaryPrefixOperator.getRandomByOptions(handler));
        case UNARY_POSTFIX:
            return new NewUnaryPostfixOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    GeneralUnaryPostfixOperator.getRandomByOptions(handler));
        case BINARY_COMPARISON:
            return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), GeneralBinaryComparisonOperator.getRandomByOptions(handler));
        case BINARY_LOGICAL:
            return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), GeneralBinaryLogicalOperator.getRandomByOptions(handler));
        case BINARY_ARITHMETIC:
            return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), GeneralBinaryArithmeticOperator.getRandomByOptions(handler));
        case CAST:
            return new GeneralCast(generateExpression(depth + 1), GeneralCompositeDataType.getRandomWithoutNull(),
                    GeneralCastOperator.getRandomByOptions(handler));
        case FUNC:
            // GeneralDBFunction func = GeneralDBFunction.getRandomByOptions(handler);
            GeneralFunction func = GeneralFunction.getRandomByOptions(handler);
            return new NewFunctionNode<GeneralExpression, GeneralFunction>(generateExpressions(func.getNrArgs(), depth + 1),
                    func);
        case BETWEEN:
            return new NewBetweenOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), generateExpression(depth + 1), Randomly.getBoolean());
        case IN:
            return new NewInOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpressions(Randomly.smallNumber() + 1, depth + 1), Randomly.getBoolean());
        case CASE:
            int nr = Randomly.smallNumber() + 1;
            return new NewCaseOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpressions(nr, depth + 1), generateExpressions(nr, depth + 1),
                    generateExpression(depth + 1));
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
        GeneralDataType type = GeneralDataType.getRandomWithoutNull();
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
