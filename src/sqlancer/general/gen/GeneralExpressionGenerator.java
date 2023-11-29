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
import sqlancer.common.ast.newast.NewTernaryNode;
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
import sqlancer.general.ast.GeneralConstant;
import sqlancer.general.ast.GeneralExpression;

public final class GeneralExpressionGenerator
        extends UntypedExpressionGenerator<Node<GeneralExpression>, GeneralColumn> {

    private final GeneralGlobalState globalState;

    public GeneralExpressionGenerator(GeneralGlobalState globalState) {
        this.globalState = globalState;
    }

    private enum Expression {
        UNARY_POSTFIX, UNARY_PREFIX, BINARY_COMPARISON, BINARY_LOGICAL, BINARY_ARITHMETIC, CAST, FUNC, BETWEEN, CASE,
        IN, COLLATE, LIKE_ESCAPE
    }

    @Override
    protected Node<GeneralExpression> generateExpression(int depth) {
        GeneralErrorHandler handler = globalState.getHandler();
        if (depth >= globalState.getOptions().getMaxExpressionDepth() || Randomly.getBoolean()) {
            return generateLeafNode();
        }
        if (allowAggregates && Randomly.getBoolean()) {
            GeneralAggregateFunction aggregate = GeneralAggregateFunction.getRandom();
            allowAggregates = false;
            return new NewFunctionNode<>(generateExpressions(aggregate.getNrArgs(), depth + 1), aggregate);
        }
        List<Expression> possibleOptions = new ArrayList<>(Arrays.asList(Expression.values()));
        if (!globalState.getDbmsSpecificOptions().testCollate | !handler.getOption(GeneratorNode.COLLATE)) {
            possibleOptions.remove(Expression.COLLATE);
        }
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
        possibleOptions.remove(Expression.LIKE_ESCAPE);
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
        case COLLATE:
            return new NewUnaryPostfixOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    GeneralCollate.getRandom());
        case UNARY_PREFIX:
            return new NewUnaryPrefixOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    GeneralUnaryPrefixOperator.getRandom());
        case UNARY_POSTFIX:
            return new NewUnaryPostfixOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    GeneralUnaryPostfixOperator.getRandom());
        case BINARY_COMPARISON:
            Operator op;
            do {
                op = GeneralBinaryComparisonOperator.getRandom();
            } while (!handler.getOption(GeneratorNode.valueOf(op.toString()))
                    | Randomly.getBooleanWithSmallProbability());
            handler.addScore(GeneratorNode.valueOf(op.toString()));
            return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), op);
        case BINARY_LOGICAL:
            op = GeneralBinaryLogicalOperator.getRandom();
            return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), op);
        case BINARY_ARITHMETIC:
            do {
                op = GeneralBinaryArithmeticOperator.getRandom();
            } while (!handler.getOption(GeneratorNode.valueOf("OP" + op.toString()))
                    | Randomly.getBooleanWithSmallProbability());
            handler.addScore(GeneratorNode.valueOf("OP" + op.toString()));
            return new NewBinaryOperatorNode<GeneralExpression>(generateExpression(depth + 1),
                    generateExpression(depth + 1), op);
        case CAST:
            return new GeneralCastOperation(generateExpression(depth + 1),
                    GeneralCompositeDataType.getRandomWithoutNull());
        case FUNC:
            DBFunction func;
            // Check if the function is supported by the DBMS
            do {
                func = DBFunction.getRandom();
                // TODO Handle IllegalArgumentException
            } while (!handler.getOption(GeneratorNode.valueOf(func.toString()))
                    | Randomly.getBooleanWithSmallProbability());
            handler.addScore(GeneratorNode.valueOf(func.toString()));
            return new NewFunctionNode<GeneralExpression, DBFunction>(generateExpressions(func.getNrArgs()), func);
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
        case LIKE_ESCAPE:
            return new NewTernaryNode<GeneralExpression>(generateExpression(depth + 1), generateExpression(depth + 1),
                    generateExpression(depth + 1), "LIKE", "ESCAPE");
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
        // case VARCHAR:
        // if (!globalState.getDbmsSpecificOptions().testStringConstants) {
        // throw new IgnoreMeException();
        // }
        // return GeneralConstant.createStringConstant(globalState.getRandomly().getString());
        // case BOOLEAN:
        // if (!globalState.getDbmsSpecificOptions().testBooleanConstants) {
        // throw new IgnoreMeException();
        // }
        // return GeneralConstant.createBooleanConstant(Randomly.getBoolean());
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

    public static class GeneralCastOperation extends NewUnaryPostfixOperatorNode<GeneralExpression> {

        public GeneralCastOperation(Node<GeneralExpression> expr, GeneralCompositeDataType type) {
            super(expr, new Operator() {

                @Override
                public String getTextRepresentation() {
                    return "::" + type.toString();
                }
            });
        }

    }

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

    public enum DBFunction {
        // trigonometric functions
        ACOS(1), //
        ASIN(1), //
        ATAN(1), //
        COS(1), //
        SIN(1), //
        TAN(1), //
        COT(1), //
        ATAN2(1), //
        // math functions
        ABS(1), //
        CEIL(1), //
        CEILING(1), //
        FLOOR(1), //
        LOG(1), //
        LOG10(1), LOG2(1), //
        LN(1), //
        PI(0), //
        SQRT(1), //
        POWER(1), //
        CBRT(1), //
        ROUND(2), //
        SIGN(1), //
        DEGREES(1), //
        RADIANS(1), //
        MOD(2), //
        XOR(2), //
        // string functions
        LENGTH(1), //
        LOWER(1), //
        UPPER(1), //
        SUBSTRING(3), //
        REVERSE(1), //
        CONCAT(1, true), //
        CONCAT_WS(1, true), CONTAINS(2), //
        PREFIX(2), //
        SUFFIX(2), //
        INSTR(2), //
        PRINTF(1, true), //

        // date functions
        DATE_PART(2), AGE(2),

        COALESCE(3), NULLIF(2),

        // LPAD(3),
        // RPAD(3),
        LTRIM(1), RTRIM(1),
        // LEFT(2), https://github.com/cwida/general/issues/633
        // REPEAT(2),
        REPLACE(3), UNICODE(1),

        BIT_COUNT(1), BIT_LENGTH(1), LAST_DAY(1), MONTHNAME(1), DAYNAME(1), YEARWEEK(1), DAYOFMONTH(1), WEEKDAY(1),
        WEEKOFYEAR(1),

        IFNULL(2), IF(3);

        private int nrArgs;
        private boolean isVariadic;

        DBFunction(int nrArgs) {
            this(nrArgs, false);
        }

        DBFunction(int nrArgs, boolean isVariadic) {
            this.nrArgs = nrArgs;
            this.isVariadic = isVariadic;
        }

        public static DBFunction getRandom() {
            return Randomly.fromOptions(values());
        }

        public int getNrArgs() {
            if (isVariadic) {
                return Randomly.smallNumber() + nrArgs;
            } else {
                return nrArgs;
            }
        }

    }

    public enum GeneralUnaryPostfixOperator implements Operator {

        IS_NULL("IS NULL"), IS_NOT_NULL("IS NOT NULL");

        private String textRepr;

        GeneralUnaryPostfixOperator(String textRepr) {
            this.textRepr = textRepr;
        }

        @Override
        public String getTextRepresentation() {
            return textRepr;
        }

        public static GeneralUnaryPostfixOperator getRandom() {
            return Randomly.fromOptions(values());
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

    public enum GeneralUnaryPrefixOperator implements Operator {

        NOT("NOT"), PLUS("+"), MINUS("-");

        private String textRepr;

        GeneralUnaryPrefixOperator(String textRepr) {
            this.textRepr = textRepr;
        }

        @Override
        public String getTextRepresentation() {
            return textRepr;
        }

        public static GeneralUnaryPrefixOperator getRandom() {
            return Randomly.fromOptions(values());
        }

    }

    public enum GeneralBinaryLogicalOperator implements Operator {

        AND, OR;

        @Override
        public String getTextRepresentation() {
            return toString();
        }

        public static Operator getRandom() {
            return Randomly.fromOptions(values());
        }

    }

    public enum GeneralBinaryArithmeticOperator implements Operator {
        CONCAT("||"), ADD("+"), SUB("-"), MULT("*"), DIV("/"), MOD("%");// AND("&"), OR("|");
        // CONCAT("||"), ADD("+"), SUB("-"), MULT("*"), DIV("/"), MOD("%"), AND("&"), OR("|"), LSHIFT("<<"),
        // RSHIFT(">>");

        private String textRepr;

        GeneralBinaryArithmeticOperator(String textRepr) {
            this.textRepr = textRepr;
        }

        public static Operator getRandom() {
            return Randomly.fromOptions(values());
        }

        @Override
        public String getTextRepresentation() {
            return textRepr;
        }

    }

    public enum GeneralBinaryComparisonOperator implements Operator {
        EQUALS("="), GREATER(">"), GREATER_EQUALS(">="), SMALLER("<"), SMALLER_EQUALS("<="), NOT_EQUALS("!=");
        // LIKE("LIKE"), NOT_LIKE("NOT LIKE"), SIMILAR_TO("SIMILAR TO"), NOT_SIMILAR_TO("NOT SIMILAR TO");
        // REGEX_POSIX("~"), REGEX_POSIT_NOT("!~");

        private String textRepr;

        GeneralBinaryComparisonOperator(String textRepr) {
            this.textRepr = textRepr;
        }

        public static Operator getRandom() {
            return Randomly.fromOptions(values());
        }

        @Override
        public String getTextRepresentation() {
            return textRepr;
        }

    }

    public NewFunctionNode<GeneralExpression, GeneralAggregateFunction> generateArgsForAggregate(
            GeneralAggregateFunction aggregateFunction) {
        return new NewFunctionNode<GeneralExpression, GeneralAggregateFunction>(
                generateExpressions(aggregateFunction.getNrArgs()), aggregateFunction);
    }

    public Node<GeneralExpression> generateAggregate() {
        GeneralAggregateFunction aggrFunc = GeneralAggregateFunction.getRandom();
        return generateArgsForAggregate(aggrFunc);
    }

    @Override
    public Node<GeneralExpression> negatePredicate(Node<GeneralExpression> predicate) {
        return new NewUnaryPrefixOperatorNode<>(predicate, GeneralUnaryPrefixOperator.NOT);
    }

    @Override
    public Node<GeneralExpression> isNull(Node<GeneralExpression> expr) {
        return new NewUnaryPostfixOperatorNode<>(expr, GeneralUnaryPostfixOperator.IS_NULL);
    }

}
