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
    }

    @Override
    public void check() throws SQLException {
        super.check();
        select.setWhereClause(null);
        String originalQueryString = GeneralToStringVisitor.asString(select);

        List<String> resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);

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
        List<String> secondResultSet = ComparatorHelper.getCombinedResultSet(firstQueryString, secondQueryString,
                thirdQueryString, combinedString, !orderBy, state, errors);
        ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet, originalQueryString, combinedString,
                state, ComparatorHelper::canonicalizeResultValue);
    }

}
