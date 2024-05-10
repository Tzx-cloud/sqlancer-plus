package sqlancer.general.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;

public class GeneralTableGenerator {

    public SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        StringBuilder sb = new StringBuilder();
        String tableName;
        // TODO check if this is correct
        if (globalState.getHandler().getOption(GeneratorNode.CREATE_DATABASE)) {
            tableName = globalState.getSchema().getFreeTableName();
        } else {
            tableName = String.format("%s%s%s", globalState.getDatabaseName(),globalState.getDbmsSpecificOptions().dbTableDelim,
                    globalState.getSchema().getFreeTableName());
        }
        sb.append("CREATE TABLE ");
        sb.append(tableName);
        sb.append("(");
        List<GeneralColumn> columns = getNewColumns(globalState);
        // UntypedExpressionGenerator<Node<GeneralExpression>, GeneralColumn> gen = new GeneralExpressionGenerator(
        // globalState).setColumns(columns);
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
            sb.append(" ");
            sb.append(columns.get(i).getType());
            // if (globalState.getDbmsSpecificOptions().testCollate && Randomly.getBooleanWithRatherLowProbability()
            // && columns.get(i).getType().getPrimitiveDataType() == GeneralDataType.VARCHAR) {
            // sb.append(" COLLATE ");
            // sb.append(getRandomCollate());
            // }
            // if (globalState.getDbmsSpecificOptions().testIndexes && Randomly.getBooleanWithRatherLowProbability()) {
            // sb.append(" UNIQUE");
            // }
            // if (globalState.getDbmsSpecificOptions().testNotNullConstraints
            // && Randomly.getBooleanWithRatherLowProbability()) {
            // sb.append(" NOT NULL");
            // }
            // if (globalState.getDbmsSpecificOptions().testCheckConstraints
            // && Randomly.getBooleanWithRatherLowProbability()) {
            // sb.append(" CHECK(");
            // sb.append(GeneralToStringVisitor.asString(gen.generateExpression()));
            // GeneralErrors.addExpressionErrors(errors);
            // sb.append(")");
            // }
            // if (Randomly.getBoolean() && globalState.getDbmsSpecificOptions().testDefaultValues) {
            // sb.append(" DEFAULT(");
            // sb.append(GeneralToStringVisitor.asString(gen.generateConstant()));
            // sb.append(")");
            // }
        }
        // get a new List that is the columns pop one item
        // List<GeneralColumn> columnsWithoutLast = new ArrayList<>(columns.subList(0, columns.size() - 1));
        List<GeneralColumn> columnsToAdd = new ArrayList<>();
        if (globalState.getDbmsSpecificOptions().testIndexes && !Randomly.getBooleanWithRatherLowProbability()) {
            List<GeneralColumn> primaryKeyColumns = Randomly
                    .nonEmptySubset(new ArrayList<>(columns.subList(0, columns.size() - 1)));
            globalState.getHandler().addScore(GeneratorNode.PRIMARY_KEY);
            sb.append(", PRIMARY KEY(");
            sb.append(primaryKeyColumns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
            sb.append(")");
            // operate on the columns: if the column name is in primaryKeyColumns, then it is a primary key
            for (GeneralColumn c : columns) {
                columnsToAdd.add(new GeneralColumn(c.getName(), c.getType(), primaryKeyColumns.contains(c), false));
            }
        } else {
            columnsToAdd = columns;
        }
        sb.append(")");
        errors.addRegex(Pattern.compile(".*", Pattern.DOTALL));
        GeneralTable newTable = new GeneralTable(tableName, columnsToAdd, false);
        newTable.getColumns().forEach(c -> c.setTable(newTable));
        globalState.setUpdateTable(newTable);
        return new SQLQueryAdapter(sb.toString(), errors, true, false);
    }

    public static String getRandomCollate() {
        return Randomly.fromOptions("NOCASE", "NOACCENT", "NOACCENT.NOCASE", "C", "POSIX");
    }

    private static List<GeneralColumn> getNewColumns(GeneralGlobalState globalState) {
        List<GeneralColumn> columns = new ArrayList<>();
        for (int i = 0; i < Randomly.smallNumber() + 2; i++) {
            String columnName = String.format("c%d", i);
            globalState.getHandler().addScore(GeneratorNode.COLUMN_NUM);
            GeneralCompositeDataType columnType = GeneralCompositeDataType.getRandomWithoutNull();
            // TODO Handle IllegalArgumentExeption?
            globalState.getHandler()
                    .addScore(GeneratorNode.valueOf("COLUMN_" + columnType.getPrimitiveDataType().toString()));
            columns.add(new GeneralColumn(columnName, columnType, false, false));
        }
        return columns;
    }

}
