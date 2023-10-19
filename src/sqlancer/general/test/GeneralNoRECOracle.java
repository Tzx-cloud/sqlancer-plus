package sqlancer.general.test;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.ast.newast.ColumnReferenceNode;
import sqlancer.common.ast.newast.NewPostfixTextNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.ast.newast.TableReferenceNode;
import sqlancer.common.oracle.NoRECBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.*;
import sqlancer.general.gen.GeneralExpressionGenerator.GeneralCastOperation;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralSchema;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralJoin;
import sqlancer.general.ast.GeneralSelect;
import sqlancer.general.gen.GeneralExpressionGenerator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GeneralNoRECOracle extends NoRECBase<GeneralGlobalState> implements TestOracle<GeneralGlobalState> {

    private final GeneralSchema s;

    public GeneralNoRECOracle(GeneralGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        GeneralErrors.addExpressionErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        GeneralTables randomTables = s.getRandomTableNonEmptyTables();
        List<GeneralColumn> columns = randomTables.getColumns();
        GeneralExpressionGenerator gen = new GeneralExpressionGenerator(state).setColumns(columns);
        Node<GeneralExpression> randomWhereCondition = gen.generateExpression();
        List<GeneralTable> tables = randomTables.getTables();
        List<TableReferenceNode<GeneralExpression, GeneralTable>> tableList = tables.stream()
                .map(t -> new TableReferenceNode<GeneralExpression, GeneralTable>(t)).collect(Collectors.toList());
        List<Node<GeneralExpression>> joins = GeneralJoin.getJoins(tableList, state);
        int secondCount = getSecondQuery(tableList.stream().collect(Collectors.toList()), randomWhereCondition, joins);
        int firstCount = getFirstQueryCount(con, tableList.stream().collect(Collectors.toList()), columns,
                randomWhereCondition, joins);
        if (firstCount == -1 || secondCount == -1) {
            throw new IgnoreMeException();
        }
        if (firstCount != secondCount) {
            throw new AssertionError(
                    optimizedQueryString + "; -- " + firstCount + "\n" + unoptimizedQueryString + " -- " + secondCount);
        }
    }

    private int getSecondQuery(List<Node<GeneralExpression>> tableList, Node<GeneralExpression> randomWhereCondition,
                               List<Node<GeneralExpression>> joins) throws SQLException {
        GeneralSelect select = new GeneralSelect();
        // select.setGroupByClause(groupBys);
        // GeneralExpression isTrue = GeneralPostfixOperation.create(randomWhereCondition,
        // PostfixOperator.IS_TRUE);
        Node<GeneralExpression> asText = new NewPostfixTextNode<>(new GeneralCastOperation(
                new NewPostfixTextNode<GeneralExpression>(randomWhereCondition,
                        " IS NOT NULL AND " + GeneralToStringVisitor.asString(randomWhereCondition)),
                new GeneralCompositeDataType(GeneralDataType.INT, 8)), "as count");
        select.setFetchColumns(Arrays.asList(asText));
        select.setFromList(tableList);
        // select.setSelectType(SelectType.ALL);
        select.setJoinList(joins);
        int secondCount = 0;
        unoptimizedQueryString = "SELECT SUM(count) FROM (" + GeneralToStringVisitor.asString(select) + ") as res";
        errors.add("canceling statement due to statement timeout");
        SQLQueryAdapter q = new SQLQueryAdapter(unoptimizedQueryString, errors);
        SQLancerResultSet rs;
        try {
            rs = q.executeAndGetLogged(state);
        } catch (Exception e) {
            throw new AssertionError(unoptimizedQueryString, e);
        }
        if (rs == null) {
            return -1;
        }
        if (rs.next()) {
            secondCount += rs.getLong(1);
        }
        rs.close();
        return secondCount;
    }

    private int getFirstQueryCount(SQLConnection con, List<Node<GeneralExpression>> tableList,
                                   List<GeneralColumn> columns, Node<GeneralExpression> randomWhereCondition, List<Node<GeneralExpression>> joins)
            throws SQLException {
        GeneralSelect select = new GeneralSelect();
        // select.setGroupByClause(groupBys);
        // GeneralAggregate aggr = new GeneralAggregate(
        List<Node<GeneralExpression>> allColumns = columns.stream()
                .map((c) -> new ColumnReferenceNode<GeneralExpression, GeneralColumn>(c)).collect(Collectors.toList());
        // GeneralAggregateFunction.COUNT);
        // select.setFetchColumns(Arrays.asList(aggr));
        select.setFetchColumns(allColumns);
        select.setFromList(tableList);
        select.setWhereClause(randomWhereCondition);
        if (Randomly.getBooleanWithSmallProbability()) {
            select.setOrderByExpressions(new GeneralExpressionGenerator(state).setColumns(columns).generateOrderBys());
        }
        // select.setSelectType(SelectType.ALL);
        select.setJoinList(joins);
        int firstCount = 0;
        try (Statement stat = con.createStatement()) {
            optimizedQueryString = GeneralToStringVisitor.asString(select);
            if (options.logEachSelect()) {
                logger.writeCurrent(optimizedQueryString);
            }
            try (ResultSet rs = stat.executeQuery(optimizedQueryString)) {
                while (rs.next()) {
                    firstCount++;
                }
            }
        } catch (SQLException e) {
            throw new IgnoreMeException();
        }
        return firstCount;
    }

}
