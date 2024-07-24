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
import sqlancer.general.learner.GeneralFragments;
import sqlancer.general.learner.GeneralStringBuilder;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;

public class GeneralTableGenerator {

    private static GeneralTableFragments fragments = new GeneralTableFragments();
    private static final String CONFIG_NAME = "tablegenerator.txt";
    private static final String STATEMENT = "CREATE_TABLE";

    private final static class GeneralTableFragments extends GeneralFragments {
        public GeneralTableFragments() {
            super();
        }

        @Override
        public synchronized String genLearnStatement(GeneralGlobalState globalState) {
            setLearn(true);
            String stmt = getQuery(globalState).getQueryString();
            setLearn(false);
            if (globalState.getOptions().debugLogs()) {
                System.out.println(stmt);
            }
            return stmt;
        }

        @Override
        public String getConfigName() {
            return CONFIG_NAME;
        }

        public String getStatementType() {
            return STATEMENT;
        }
    }

    public static SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        String tableName;
        // TODO check if this is correct
        if (globalState.getHandler().getOption(GeneratorNode.CREATE_DATABASE)) {
            tableName = globalState.getSchema().getFreeTableName();
        } else {
            tableName = String.format("%s%s%s", globalState.getDatabaseName(),
                    globalState.getDbmsSpecificOptions().dbTableDelim,
                    globalState.getSchema().getFreeTableName());
        }
        GeneralStringBuilder<GeneralTableFragments> sb = new GeneralStringBuilder<GeneralTableFragments>(globalState,
                fragments);
        sb.append("CREATE TABLE ", 0);
        sb.append(tableName, 1);
        sb.append("(");
        List<GeneralColumn> columns = getNewColumns(globalState);
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
            sb.append(" ");
            sb.append(columns.get(i).getType(), 2);
        }
        List<GeneralColumn> columnsToAdd = new ArrayList<>();
        if (globalState.getDbmsSpecificOptions().testIndexes && !Randomly.getBooleanWithRatherLowProbability()) {
            List<GeneralColumn> primaryKeyColumns = Randomly
                    .nonEmptySubset(new ArrayList<>(columns.subList(0, columns.size() - 1)));
            globalState.getHandler().addScore(GeneratorNode.PRIMARY_KEY);
            sb.append(", PRIMARY KEY(");
            sb.append(primaryKeyColumns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
            sb.append(")", 3);
            // operate on the columns: if the column name is in primaryKeyColumns, then it
            // is a primary key
            for (GeneralColumn c : columns) {
                columnsToAdd.add(new GeneralColumn(c.getName(), c.getType(), primaryKeyColumns.contains(c), false));
            }
        } else {
            columnsToAdd = columns;
        }
        sb.append(")", 4);
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

    public static GeneralFragments getFragments() {
        return fragments;
    }

}
