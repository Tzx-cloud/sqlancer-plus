package sqlancer.general.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralToStringVisitor;

public class GeneralQueryPartitioningWhere extends GeneralQueryPartitioningBase {

    public GeneralQueryPartitioningWhere(GeneralGlobalState state) {
        super(state);
        GeneralErrors.addGroupByErrors(errors);
        GeneralErrors.addExpressionErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        super.check();
        select.setWhereClause(null);
        String originalQueryString = GeneralToStringVisitor.asString(select);
        List<String> resultSet;
        try {
            resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);
        } catch (Exception e) {
            state.getHandler().appendScoreToTable(false);
            throw e;
        }

        boolean orderBy = Randomly.getBooleanWithRatherLowProbability();
        if (orderBy) {
            select.setOrderByExpressions(gen.generateOrderBys());
        }
        select.setWhereClause(predicate);
        String firstQueryString = GeneralToStringVisitor.asString(select);
        select.setWhereClause(negatedPredicate);
        String secondQueryString = GeneralToStringVisitor.asString(select);
        select.setWhereClause(isNullPredicate);
        String thirdQueryString = GeneralToStringVisitor.asString(select);
        List<String> combinedString = new ArrayList<>();

        List<String> secondResultSet;
        try {
            secondResultSet = ComparatorHelper.getCombinedResultSet(firstQueryString, secondQueryString,
                    thirdQueryString, combinedString, !orderBy, state, errors);
        } catch (Exception e) {
            state.getHandler().appendScoreToTable(false);
            throw e;
        }
        try {
            ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet, originalQueryString, combinedString,
                    state, ComparatorHelper::canonicalizeResultValue);
        } catch (AssertionError e) {
            // TODO we need to give some information to the handler here
            // state.getHandler().printStatistics();
            throw e;
        }
        state.getHandler().appendScoreToTable(true);
    }

}
