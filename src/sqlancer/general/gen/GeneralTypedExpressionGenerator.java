package sqlancer.general.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sqlancer.Randomly;
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
import sqlancer.common.gen.TypedExpressionGenerator;
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
import sqlancer.general.ast.GeneralColumnReference;
import sqlancer.general.ast.GeneralConstant;
import sqlancer.general.ast.GeneralDBFunction;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralUnaryPostfixOperator;
import sqlancer.general.ast.GeneralUnaryPrefixOperator;
import sqlancer.general.ast.GeneralCast.GeneralCastOperator;

public class GeneralTypedExpressionGenerator
        extends TypedExpressionGenerator<Node<GeneralExpression>, GeneralColumn, GeneralCompositeDataType> {

    private final GeneralGlobalState globalState;

    public GeneralTypedExpressionGenerator(GeneralGlobalState globalState) {
        this.globalState = globalState;
    }

    public Node<GeneralExpression> generateExpression() {
        return generateExpression(GeneralDataType.BOOLEAN.get());
    }

    @Override
    public Node<GeneralExpression> generateExpression(GeneralCompositeDataType dataType) {
        return generateExpression(dataType, 0);
    }

    // public Node<GeneralExpression> generateAggregate() {
    // return getAggregate(getRandomType());
    // }

    public Node<GeneralExpression> generateHavingClause() {
        allowAggregates = true;
        Node<GeneralExpression> expression = generateExpression(GeneralDataType.BOOLEAN.get());
        allowAggregates = false;
        return expression;
    }

    @Override
    public List<Node<GeneralExpression>> generateOrderBys() {
        List<Node<GeneralExpression>> expr = super.generateOrderBys();
        List<Node<GeneralExpression>> orderingTerms = new ArrayList<>(expr.size());
        for (Node<GeneralExpression> curExpr : expr) {
            if (Randomly.getBoolean()) {
                curExpr = new NewOrderingTerm<>(curExpr, Ordering.getRandom());
            }
            orderingTerms.add(curExpr);
        }
        return orderingTerms;
    }

    public List<GeneralDBFunction> getFunctionsCompatibleWith(GeneralCompositeDataType returnType) {
        return Stream.of(GeneralDBFunction.values())
                .filter(f -> globalState.getHandler().getCompositeOption(f.toString(), returnType.toString()))
                .collect(Collectors.toList());
    }

    @Override
    public Node<GeneralExpression> generateExpression(GeneralCompositeDataType type, int depth) {
        // if (allowAggregates && Randomly.getBoolean()) {
        // return getAggregate(type);
        // }
        GeneralErrorHandler handler = globalState.getHandler();
        if (depth >= globalState.getOptions().getMaxExpressionDepth()
                || depth >= globalState.getHandler().getCurDepth(globalState.getDatabaseName())
                || Randomly.getBoolean()) {
            return generateLeafNode(type);
        } else {
            if (Randomly.getBooleanWithRatherLowProbability()) {
                handler.addScore(GeneratorNode.FUNC);
                if (Randomly.getBoolean() || !handler.getOption(GeneratorNode.UNTYPE_EXPR)) {
                    // TODO current implementation does not support automatically type inference
                    List<GeneralDBFunction> applicableFunctions = getFunctionsCompatibleWith(type);
                    if (!applicableFunctions.isEmpty()) {
                        GeneralDBFunction function = Randomly.fromList(applicableFunctions);
                        return new NewFunctionNode<GeneralExpression, GeneralDBFunction>(
                                generateExpressions(type, function.getNrArgs(), depth + 1), function);
                    }
                } else {
                    GeneralDBFunction function = GeneralDBFunction.getRandomByOptions(handler);
                    // handler.addScore(GeneratorNode.UNTYPE_EXPR);
                    return new NewFunctionNode<GeneralExpression, GeneralDBFunction>(
                            generateExpressions(type, function.getNrArgs(), depth + 1), function);
                }
            }
            if (Randomly.getBooleanWithRatherLowProbability() && handler.getOption(GeneratorNode.CAST)) {
                handler.addScore(GeneratorNode.CAST);
                return new GeneralCast(generateExpression(getRandomType(), depth + 1), type,
                        GeneralCastOperator.getRandomByOptions(handler));
            }
            if (Randomly.getBooleanWithRatherLowProbability() && handler.getOption(GeneratorNode.CASE)) {
                handler.addScore(GeneratorNode.CASE);
                GeneralCompositeDataType condType = getRandomType();
                List<Node<GeneralExpression>> conditions = new ArrayList<>();
                List<Node<GeneralExpression>> cases = new ArrayList<>();
                Node<GeneralExpression> switchCond = generateExpression(condType, depth + 1);
                for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
                    conditions.add(generateExpression(condType, depth + 1));
                    cases.add(generateExpression(type, depth + 1));
                }
                Node<GeneralExpression> elseExpr = null;
                if (Randomly.getBoolean()) {
                    elseExpr = generateExpression(type, depth + 1);
                }
                return new NewCaseOperatorNode<GeneralExpression>(switchCond, conditions, cases, elseExpr);

            }

            switch (type.getPrimitiveDataType()) {
            case BOOLEAN:
                return generateBooleanExpression(depth);
            case INT:
                return new NewBinaryOperatorNode<GeneralExpression>(
                        generateExpression(GeneralDataType.INT.get(), depth + 1),
                        generateExpression(GeneralDataType.INT.get(), depth + 1),
                        GeneralBinaryArithmeticOperator.getRandomByOptions(globalState.getHandler()));
            case STRING:
                // case BYTES: // TODO split
                Node<GeneralExpression> stringExpr = generateStringExpression(depth);
                // if (Randomly.getBoolean()) {
                // stringExpr = new CockroachDBCollate(stringExpr, CockroachDBCommon.getRandomCollate());
                // }
                return stringExpr; // TODO
            // case FLOAT:
            // return generateLeafNode(type); // TODO
            default:
                throw new AssertionError(type);
            }
        }
    }

    // private GeneralExpression getAggregate(GeneralCompositeDataType type) {
    // GeneralAggregateFunction agg = Randomly
    // .fromList(CockroachDBAggregate.GeneralAggregateFunction.getAggregates(type.getPrimitiveDataType()));
    // return generateArgsForAggregate(type, agg);
    // }

    // public CockroachDBAggregate generateArgsForAggregate(GeneralCompositeDataType type,
    // GeneralAggregateFunction agg) {
    // List<GeneralDataType> types = agg.getTypes(type.getPrimitiveDataType());
    // List<GeneralExpression> args = new ArrayList<>();
    // allowAggregates = false; //
    // for (GeneralDataType argType : types) {
    // args.add(generateExpression(argType.get()));
    // }
    // return new CockroachDBAggregate(agg, args);
    // }

    private enum BooleanExpression {
        // NOT, COMPARISON, AND_OR_CHAIN, REGEX, IS_NULL, IS_NAN, IN, BETWEEN, MULTI_VALUED_COMPARISON
        UNARY_PREFIX, BINARY_COMPARISON, BINARY_LOGICAL, UNARY_POSTFIX, IN, BETWEEN;

        public static BooleanExpression getRandomByOptions(GeneralErrorHandler handler) {
            BooleanExpression expr;
            GeneratorNode node;
            do {
                expr = Randomly.fromOptions(values());
                node = GeneratorNode.valueOf(expr.toString());
            } while (!handler.getOption(node) || !Randomly.getBooleanWithSmallProbability());
            handler.addScore(node);
            return expr;
        }
    }

    private enum StringExpression {
        CONCAT
    }

    private Node<GeneralExpression> generateStringExpression(int depth) {
        StringExpression exprType = Randomly.fromOptions(StringExpression.values());
        switch (exprType) {
        case CONCAT:
            return new NewBinaryOperatorNode<GeneralExpression>(
                    generateExpression(GeneralDataType.STRING.get(), depth + 1),
                    generateExpression(GeneralDataType.STRING.get(), depth + 1),
                    GeneralBinaryArithmeticOperator.CONCAT);
        default:
            throw new AssertionError(exprType);
        }
    }

    private Node<GeneralExpression> generateBooleanExpression(int depth) {
        GeneralErrorHandler handler = globalState.getHandler();
        BooleanExpression exprType = BooleanExpression.getRandomByOptions(handler);
        Node<GeneralExpression> expr;
        switch (exprType) {
        case UNARY_PREFIX:
            return new NewUnaryPrefixOperatorNode<GeneralExpression>(
                    generateExpression(GeneralDataType.BOOLEAN.get(), depth + 1), GeneralUnaryPrefixOperator.NOT);
        case BINARY_COMPARISON:
            return getBinaryComparison(depth);
        case BINARY_LOGICAL:
            return getAndOrChain(depth);
        // case REGEX:
        // return new CockroachDBRegexOperation(generateExpression(GeneralDataType.STRING.get(), depth + 1),
        // generateExpression(GeneralDataType.STRING.get(), depth + 1),
        // CockroachDBRegexOperator.getRandom());
        case UNARY_POSTFIX:
            return new NewUnaryPostfixOperatorNode<GeneralExpression>(generateExpression(getRandomType(), depth + 1),
                    GeneralUnaryPostfixOperator.getRandomByOptions(handler));
        // case IS_NAN:
        // return new CockroachDBUnaryPostfixOperation(generateExpression(GeneralDataType.FLOAT.get(), depth + 1),
        // Randomly.fromOptions(CockroachDBUnaryPostfixOperator.IS_NAN,
        // CockroachDBUnaryPostfixOperator.IS_NOT_NAN));
        case IN:
            return getInOperation(depth);
        case BETWEEN:
            GeneralCompositeDataType type = getRandomType();
            expr = generateExpression(type, depth + 1);
            Node<GeneralExpression> left = generateExpression(type, depth + 1);
            Node<GeneralExpression> right = generateExpression(type, depth + 1);
            return new NewBetweenOperatorNode<GeneralExpression>(expr, left, right, Randomly.getBoolean());
        // case MULTI_VALUED_COMPARISON: // TODO other operators
        // type = getRandomType();
        // left = generateExpression(type, depth + 1);
        // List<GeneralExpression> rightList = generateExpressions(type, Randomly.smallNumber() + 2, depth + 1);
        // return new CockroachDBMultiValuedComparison(left, rightList, MultiValuedComparisonType.getRandom(),
        // MultiValuedComparisonOperator.getRandomGenericComparisonOperator());
        default:
            throw new AssertionError(exprType);
        }
    }

    private Node<GeneralExpression> getAndOrChain(int depth) {
        Node<GeneralExpression> left = generateExpression(GeneralDataType.BOOLEAN.get(), depth + 1);
        for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
            Node<GeneralExpression> right = generateExpression(GeneralDataType.BOOLEAN.get(), depth + 1);
            left = new NewBinaryOperatorNode<GeneralExpression>(left, right,
                    GeneralBinaryLogicalOperator.getRandomByOptions(globalState.getHandler()));
        }
        return left;
    }

    private Node<GeneralExpression> getInOperation(int depth) {
        GeneralCompositeDataType type = getRandomType();
        return new NewInOperatorNode<GeneralExpression>(generateExpression(type, depth + 1),
                generateExpressions(type, Randomly.smallNumber() + 1, depth + 1), Randomly.getBoolean());
    }

    @Override
    protected GeneralCompositeDataType getRandomType() {
        if (columns.isEmpty() || Randomly.getBooleanWithRatherLowProbability()) {
            return GeneralCompositeDataType.getRandomWithoutNull();
        } else {
            return Randomly.fromList(columns).getType();
        }
    }

    private Node<GeneralExpression> getBinaryComparison(int depth) {
        GeneralCompositeDataType type = getRandomType();
        Node<GeneralExpression> left = generateExpression(type, depth + 1);
        Node<GeneralExpression> right = generateExpression(type, depth + 1);
        return new NewBinaryOperatorNode<GeneralExpression>(left, right,
                GeneralBinaryComparisonOperator.getRandomByOptions(globalState.getHandler()));
    }

    @Override
    protected boolean canGenerateColumnOfType(GeneralCompositeDataType type) {
        return columns.stream().anyMatch(c -> c.getType() == type);
    }

    @Override
    public Node<GeneralExpression> generateConstant(GeneralCompositeDataType type) {
        if (Randomly.getBooleanWithRatherLowProbability()) {
            return GeneralConstant.createNullConstant();
        }
        switch (type.getPrimitiveDataType()) {
        case INT:
            // case SERIAL:
            // case DECIMAL: // TODO: generate random decimals
            return GeneralConstant.createIntConstant(globalState.getRandomly().getInteger());
        case BOOLEAN:
            return GeneralConstant.createBooleanConstant(Randomly.getBoolean());
        case STRING:
            // case BYTES: // TODO: also generate byte constants
            return GeneralConstant.createStringConstant(globalState.getRandomly().getString());
        // case FLOAT:
        // return CockroachDBConstant.createFloatConstant(globalState.getRandomly().getDouble());
        // case BIT:
        // return CockroachDBConstant.createBitConstantWithSize(type.getSize());
        // case VARBIT:
        // if (Randomly.getBoolean()) {
        // return CockroachDBConstant.createBitConstant(globalState.getRandomly().getInteger());
        // } else {
        // return CockroachDBConstant.createBitConstantWithSize((int) Randomly.getNotCachedInteger(1, 10));
        // }
        // case INTERVAL:
        // return CockroachDBConstant.createIntervalConstant(globalState.getRandomly().getInteger(),
        // globalState.getRandomly().getInteger(), globalState.getRandomly().getInteger(),
        // globalState.getRandomly().getInteger(), globalState.getRandomly().getInteger(),
        // globalState.getRandomly().getInteger());
        // case TIMESTAMP:
        // return CockroachDBConstant.createTimestampConstant(globalState.getRandomly().getInteger());
        // case TIMESTAMPTZ:
        // return CockroachDBConstant.createTimestamptzConstant(globalState.getRandomly().getInteger());
        // case TIME:
        // return CockroachDBConstant.createTimeConstant(globalState.getRandomly().getInteger());
        // case TIMETZ:
        // return CockroachDBConstant.createTimetz(globalState.getRandomly().getInteger());
        // case ARRAY:
        // List<GeneralExpression> elements = new ArrayList<>();
        // for (int i = 0; i < Randomly.smallNumber(); i++) {
        // elements.add(generateConstant(type.getElementType()));
        // }
        // return CockroachDBConstant.createArrayConstant(elements);
        // case JSONB:
        // return CockroachDBConstant.createNullConstant(); // TODO
        default:
            throw new AssertionError(type);
        }
    }

    public GeneralGlobalState getGlobalState() {
        return globalState;
    }

    @Override
    protected Node<GeneralExpression> generateColumn(GeneralCompositeDataType type) {
        GeneralColumn column = Randomly
                .fromList(columns.stream().filter(c -> c.getType() == type).collect(Collectors.toList()));
        Node<GeneralExpression> columnReference = new GeneralColumnReference(column);
        // if (column.getType().isString() && Randomly.getBooleanWithRatherLowProbability()) {
        // columnReference = new CockroachDBCollate(columnReference, CockroachDBCommon.getRandomCollate());
        // }
        return columnReference;
    }

    @Override
    public Node<GeneralExpression> generatePredicate() {
        return generateExpression(GeneralDataType.BOOLEAN.get());
    }

    @Override
    public Node<GeneralExpression> negatePredicate(Node<GeneralExpression> predicate) {
        return new NewUnaryPrefixOperatorNode<GeneralExpression>(predicate, GeneralUnaryPrefixOperator.NOT);
    }

    @Override
    public Node<GeneralExpression> isNull(Node<GeneralExpression> expr) {
        return new NewUnaryPostfixOperatorNode<GeneralExpression>(expr, GeneralUnaryPostfixOperator.IS_NULL);
    }

}
