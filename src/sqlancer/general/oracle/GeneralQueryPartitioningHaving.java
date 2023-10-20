package sqlancer.general.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.oracle.TestOracle;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralExpression;

public class GeneralQueryPartitioningHaving extends GeneralQueryPartitioningBase
        implements TestOracle<GeneralGlobalState> {

    public GeneralQueryPartitioningHaving(GeneralGlobalState state) {
        super(state);
        GeneralErrors.addGroupByErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        super.check();
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateExpression());
        }
        boolean orderBy = Randomly.getBoolean();
        if (orderBy) {
            select.setOrderByExpressions(gen.generateOrderBys());
        }
        select.setGroupByExpressions(gen.generateExpressions(Randomly.smallNumber() + 1));
        select.setHavingClause(null);
        String originalQueryString = GeneralToStringVisitor.asString(select);
        List<String> resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);

        select.setHavingClause(predicate);
        String firstQueryString = GeneralToStringVisitor.asString(select);
        select.setHavingClause(negatedPredicate);
        String secondQueryString = GeneralToStringVisitor.asString(select);
        select.setHavingClause(isNullPredicate);
        String thirdQueryString = GeneralToStringVisitor.asString(select);
        List<String> combinedString = new ArrayList<>();
        List<String> secondResultSet = ComparatorHelper.getCombinedResultSet(firstQueryString, secondQueryString,
                thirdQueryString, combinedString, !orderBy, state, errors);
        ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet, originalQueryString, combinedString,
                state, ComparatorHelper::canonicalizeResultValue);
    }

    @Override
    protected Node<GeneralExpression> generatePredicate() {
        return gen.generateHavingClause();
    }

    @Override
    List<Node<GeneralExpression>> generateFetchColumns() {
        return Arrays.asList(gen.generateHavingClause());
    }

}
