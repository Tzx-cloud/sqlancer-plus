package sqlancer.general.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.gen.UntypedExpressionGenerator;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;
import sqlancer.general.GeneralSchema.GeneralDataType;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralExpression;

public class GeneralTableGenerator {

    public SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        StringBuilder sb = new StringBuilder();
        String tableName = globalState.getSchema().getFreeTableName();
        sb.append("CREATE TABLE ");
        sb.append(tableName);
        sb.append("(");
        List<GeneralColumn> columns = getNewColumns();
        UntypedExpressionGenerator<Node<GeneralExpression>, GeneralColumn> gen = new GeneralExpressionGenerator(
                globalState).setColumns(columns);
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
            sb.append(" ");
            sb.append(columns.get(i).getType());
            if (globalState.getDbmsSpecificOptions().testCollate && Randomly.getBooleanWithRatherLowProbability()
                    && columns.get(i).getType().getPrimitiveDataType() == GeneralDataType.VARCHAR) {
                sb.append(" COLLATE ");
                sb.append(getRandomCollate());
            }
            if (globalState.getDbmsSpecificOptions().testIndexes && Randomly.getBooleanWithRatherLowProbability()) {
                sb.append(" UNIQUE");
            }
            if (globalState.getDbmsSpecificOptions().testNotNullConstraints
                    && Randomly.getBooleanWithRatherLowProbability()) {
                sb.append(" NOT NULL");
            }
            if (globalState.getDbmsSpecificOptions().testCheckConstraints
                    && Randomly.getBooleanWithRatherLowProbability()) {
                sb.append(" CHECK(");
                sb.append(GeneralToStringVisitor.asString(gen.generateExpression()));
                GeneralErrors.addExpressionErrors(errors);
                sb.append(")");
            }
            if (Randomly.getBoolean() && globalState.getDbmsSpecificOptions().testDefaultValues) {
                sb.append(" DEFAULT(");
                sb.append(GeneralToStringVisitor.asString(gen.generateConstant()));
                sb.append(")");
            }
        }
        if (globalState.getDbmsSpecificOptions().testIndexes && Randomly.getBoolean()) {
            errors.add("Invalid type for index");
            List<GeneralColumn> primaryKeyColumns = Randomly.nonEmptySubset(columns);
            sb.append(", PRIMARY KEY(");
            sb.append(primaryKeyColumns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
            sb.append(")");
        }
        sb.append(")");
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    public static String getRandomCollate() {
        return Randomly.fromOptions("NOCASE", "NOACCENT", "NOACCENT.NOCASE", "C", "POSIX");
    }

    private static List<GeneralColumn> getNewColumns() {
        List<GeneralColumn> columns = new ArrayList<>();
        for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
            String columnName = String.format("c%d", i);
            GeneralCompositeDataType columnType = GeneralCompositeDataType.getRandomWithoutNull();
            columns.add(new GeneralColumn(columnName, columnType, false, false));
        }
        return columns;
    }

}
