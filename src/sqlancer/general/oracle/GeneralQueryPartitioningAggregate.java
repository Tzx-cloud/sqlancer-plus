package sqlancer.general.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.ComparatorHelper;
import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.ast.newast.NewAliasNode;
import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.NewFunctionNode;
import sqlancer.common.ast.newast.NewUnaryPostfixOperatorNode;
import sqlancer.common.ast.newast.NewUnaryPrefixOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;
import sqlancer.general.GeneralSchema.GeneralDataType;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;
import sqlancer.general.gen.GeneralExpressionGenerator.GeneralAggregateFunction;
import sqlancer.general.gen.GeneralExpressionGenerator.GeneralBinaryArithmeticOperator;
import sqlancer.general.gen.GeneralExpressionGenerator.GeneralCastOperation;
import sqlancer.general.gen.GeneralExpressionGenerator.GeneralUnaryPostfixOperator;
import sqlancer.general.gen.GeneralExpressionGenerator.GeneralUnaryPrefixOperator;

public class GeneralQueryPartitioningAggregate extends GeneralQueryPartitioningBase
        implements TestOracle<GeneralGlobalState> {

    private String firstResult;
    private String secondResult;
    private String originalQuery;
    private String metamorphicQuery;

    public GeneralQueryPartitioningAggregate(GeneralGlobalState state) {
        super(state);
        GeneralErrors.addGroupByErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        super.check();
        GeneralAggregateFunction aggregateFunction = Randomly.fromOptions(GeneralAggregateFunction.MAX,
                GeneralAggregateFunction.MIN, GeneralAggregateFunction.SUM, GeneralAggregateFunction.COUNT,
                GeneralAggregateFunction.AVG/* , GeneralAggregateFunction.STDDEV_POP */);
        NewFunctionNode<GeneralExpression, GeneralAggregateFunction> aggregate = gen
                .generateArgsForAggregate(aggregateFunction);
        List<Node<GeneralExpression>> fetchColumns = new ArrayList<>();
        fetchColumns.add(aggregate);
        while (Randomly.getBooleanWithRatherLowProbability()) {
            fetchColumns.add(gen.generateAggregate());
        }
        select.setFetchColumns(Arrays.asList(aggregate));
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setOrderByExpressions(gen.generateOrderBys());
        }
        originalQuery = GeneralToStringVisitor.asString(select);
        firstResult = getAggregateResult(originalQuery);
        metamorphicQuery = createMetamorphicUnionQuery(select, aggregate, select.getFromList());
        secondResult = getAggregateResult(metamorphicQuery);

        state.getState().getLocalState().log(
                "--" + originalQuery + ";\n--" + metamorphicQuery + "\n-- " + firstResult + "\n-- " + secondResult);
        if (firstResult == null && secondResult != null
                || firstResult != null && (!firstResult.contentEquals(secondResult)
                        && !ComparatorHelper.isEqualDouble(firstResult, secondResult))) {
            if (secondResult.contains("Inf")) {
                throw new IgnoreMeException(); // FIXME: average computation
            }
            throw new AssertionError();
        }

    }

    private String createMetamorphicUnionQuery(GeneralSelect select,
            NewFunctionNode<GeneralExpression, GeneralAggregateFunction> aggregate,
            List<Node<GeneralExpression>> from) {
        String metamorphicQuery;
        Node<GeneralExpression> whereClause = gen.generateExpression();
        Node<GeneralExpression> negatedClause = new NewUnaryPrefixOperatorNode<>(whereClause,
                GeneralUnaryPrefixOperator.NOT);
        Node<GeneralExpression> notNullClause = new NewUnaryPostfixOperatorNode<>(whereClause,
                GeneralUnaryPostfixOperator.IS_NULL);
        List<Node<GeneralExpression>> mappedAggregate = mapped(aggregate);
        GeneralSelect leftSelect = getSelect(mappedAggregate, from, whereClause, select.getJoinList());
        GeneralSelect middleSelect = getSelect(mappedAggregate, from, negatedClause, select.getJoinList());
        GeneralSelect rightSelect = getSelect(mappedAggregate, from, notNullClause, select.getJoinList());
        metamorphicQuery = "SELECT " + getOuterAggregateFunction(aggregate) + " FROM (";
        metamorphicQuery += GeneralToStringVisitor.asString(leftSelect) + " UNION ALL "
                + GeneralToStringVisitor.asString(middleSelect) + " UNION ALL "
                + GeneralToStringVisitor.asString(rightSelect);
        metamorphicQuery += ") as asdf";
        return metamorphicQuery;
    }

    private String getAggregateResult(String queryString) throws SQLException {
        String resultString;
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors);
        try (SQLancerResultSet result = q.executeAndGet(state)) {
            if (result == null) {
                throw new IgnoreMeException();
            }
            if (!result.next()) {
                resultString = null;
            } else {
                resultString = result.getString(1);
            }
            return resultString;
        } catch (SQLException e) {
            if (!e.getMessage().contains("Not implemented type")) {
                throw new AssertionError(queryString, e);
            } else {
                throw new IgnoreMeException();
            }
        }
    }

    private List<Node<GeneralExpression>> mapped(
            NewFunctionNode<GeneralExpression, GeneralAggregateFunction> aggregate) {
        GeneralCastOperation count;
        switch (aggregate.getFunc()) {
        case COUNT:
        case MAX:
        case MIN:
        case SUM:
            return aliasArgs(Arrays.asList(aggregate));
        case AVG:
            NewFunctionNode<GeneralExpression, GeneralAggregateFunction> sum = new NewFunctionNode<>(
                    aggregate.getArgs(), GeneralAggregateFunction.SUM);
            count = new GeneralCastOperation(new NewFunctionNode<>(aggregate.getArgs(), GeneralAggregateFunction.COUNT),
                    new GeneralCompositeDataType(GeneralDataType.FLOAT, 8));
            return aliasArgs(Arrays.asList(sum, count));
        case STDDEV_POP:
            NewFunctionNode<GeneralExpression, GeneralAggregateFunction> sumSquared = new NewFunctionNode<>(
                    Arrays.asList(new NewBinaryOperatorNode<>(aggregate.getArgs().get(0), aggregate.getArgs().get(0),
                            GeneralBinaryArithmeticOperator.MULT)),
                    GeneralAggregateFunction.SUM);
            count = new GeneralCastOperation(
                    new NewFunctionNode<GeneralExpression, GeneralAggregateFunction>(aggregate.getArgs(),
                            GeneralAggregateFunction.COUNT),
                    new GeneralCompositeDataType(GeneralDataType.FLOAT, 8));
            NewFunctionNode<GeneralExpression, GeneralAggregateFunction> avg = new NewFunctionNode<>(
                    aggregate.getArgs(), GeneralAggregateFunction.AVG);
            return aliasArgs(Arrays.asList(sumSquared, count, avg));
        default:
            throw new AssertionError(aggregate.getFunc());
        }
    }

    private List<Node<GeneralExpression>> aliasArgs(List<Node<GeneralExpression>> originalAggregateArgs) {
        List<Node<GeneralExpression>> args = new ArrayList<>();
        int i = 0;
        for (Node<GeneralExpression> expr : originalAggregateArgs) {
            args.add(new NewAliasNode<GeneralExpression>(expr, "agg" + i++));
        }
        return args;
    }

    private String getOuterAggregateFunction(NewFunctionNode<GeneralExpression, GeneralAggregateFunction> aggregate) {
        switch (aggregate.getFunc()) {
        case STDDEV_POP:
            return "sqrt(SUM(agg0)/SUM(agg1)-SUM(agg2)*SUM(agg2))";
        case AVG:
            return "SUM(agg0::FLOAT)/SUM(agg1)::FLOAT";
        case COUNT:
            return GeneralAggregateFunction.SUM.toString() + "(agg0)";
        default:
            return aggregate.getFunc().toString() + "(agg0)";
        }
    }

    private GeneralSelect getSelect(List<Node<GeneralExpression>> aggregates, List<Node<GeneralExpression>> from,
            Node<GeneralExpression> whereClause, List<Node<GeneralExpression>> joinList) {
        GeneralSelect leftSelect = new GeneralSelect();
        leftSelect.setFetchColumns(aggregates);
        leftSelect.setFromList(from);
        leftSelect.setWhereClause(whereClause);
        leftSelect.setJoinList(joinList);
        if (Randomly.getBooleanWithSmallProbability()) {
            leftSelect.setGroupByExpressions(gen.generateExpressions(Randomly.smallNumber() + 1));
        }
        return leftSelect;
    }

}
