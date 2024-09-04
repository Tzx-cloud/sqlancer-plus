package sqlancer.general;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sqlancer.Randomly;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.schema.AbstractRelationalTable;
import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.AbstractTables;
import sqlancer.common.schema.TableIndex;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.learner.GeneralFragments;
import sqlancer.general.learner.GeneralStringBuilder;

public class GeneralSchema extends AbstractSchema<GeneralGlobalState, GeneralTable> {
    private static GeneralTypeFragments fragments = new GeneralTypeFragments();
    private static final String CONFIG_NAME = "typegenerator.txt";
    private static final String STATEMENT = "TYPE";

    private static int typeCounter = 0;
    private static HashMap<Integer, String> typeMap = new HashMap<>();

    private final static class GeneralTypeFragments extends GeneralFragments {
        public GeneralTypeFragments() {
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

        @Override
        // rare keywords
        // use options
        protected String getSystemPrompt() {
            return "This GPT is an expert in SQL dialects. It helps users generate correct SQL statements for different DBMSs. Users specify a DBMS, provide a SQL template with SQL keywords and placeholders, and give random variable generators. The GPT fills placeholders with data types ({0}) and the format of the data types ({1}), consists of concrete strings or random variable generators user provided. You should check whether the format The response is a CSV file with two columns: one for data type names and one for the format, without a header. Each data type is split into separate rows. Provide at least 20 different answers. Be rare and complex. Avoid explanations.";
        }

        @Override
        protected String getExamples() {
            return "INT,<RANDOM_INT>\n"
                    + "VARCHAR,'<RANDOM_STRING>'\n"
                    + "BOOLEAN,TRUE\n"
                    + "DATE,'<RANDOM_DATE>'\n"
                    + "DATE,'2021-01-01'\n"
                    ;
        }

        @Override
        protected void parseFragments(String[] s) {
            String key = s[0];

            Pattern pattern = Pattern.compile("<([^>]*)>");
            Matcher matcher = pattern.matcher(s[1]);

            String content = "";
            StringBuffer output = new StringBuffer();

            List<GeneralFragmentVariable> vars = new ArrayList<>();

            while (matcher.find()) {
                content = matcher.group(1);
                matcher.appendReplacement(output, "%s");
                vars.add(GeneralFragmentVariable.valueOf(content.toUpperCase()));
            }
            matcher.appendTail(output);
            
            addFragment(key, output.toString(), vars);

            typeMap.put(typeCounter, key);
            typeCounter++;

        }

        @Override
        public String get(int index, GeneralGlobalState state) {
            if (getLearn()) {
                return super.get(index, state);
            }

            String key = typeMap.get(index);
            if (getFragments().containsKey(key)) {
                GeneralFragmentChoice choice = Randomly.fromList(getFragments().get(key));
                state.getHandler().addScore(choice);
                return choice.toString(state);
            } else {
                return "NULL";
            }
        }
    }

    public static SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        GeneralStringBuilder<GeneralTypeFragments> sb = new GeneralStringBuilder<GeneralTypeFragments>(globalState,
                fragments);
        sb.append("CREATE TABLE test (c0 ", 0);
        sb.append(");\n");

        sb.append("INSERT INTO test VALUES (", 1);
        sb.append(")");

        return new SQLQueryAdapter(sb.toString());
    }

    public enum GeneralDataType {

        // INT, VARCHAR, BOOLEAN, FLOAT, DATE, TIMESTAMP, NULL;
        INT, NULL, BOOLEAN, STRING, VARTYPE;

        public static GeneralDataType getRandomWithoutNull() {
            GeneralDataType dt;
            do {
                dt = Randomly.fromOptions(values());
            } while (dt == GeneralDataType.NULL);
            return dt;
        }

        public static GeneralDataType getRandomWithProb() {
            GeneralDataType[] types = { INT, BOOLEAN, STRING, VARTYPE, VARTYPE, VARTYPE };
            return Randomly.fromOptions(types);
        }

        public GeneralCompositeDataType get() {
            return new GeneralCompositeDataType(this, 0);
        }

    }

    public static class GeneralCompositeDataType {

        private final GeneralDataType dataType;

        private final int id;

        public GeneralCompositeDataType(GeneralDataType dataType, int id) {
            this.dataType = dataType;
            this.id = id;
        }

        public GeneralDataType getPrimitiveDataType() {
            return dataType;
        }

        public int getId() {
            if (id == -1) {
                throw new AssertionError(this);
            }
            return id;
        }

        public static List<GeneralCompositeDataType> getSupportedTypes() {
            List<GeneralCompositeDataType> types = new ArrayList<>();
            for (GeneralDataType dt : GeneralDataType.values()) {
                if (dt == GeneralDataType.NULL) {
                    continue;
                }
                types.add(new GeneralCompositeDataType(dt, 0));
            }
            return types;
        }

        public static GeneralCompositeDataType getRandomWithoutNull() {
            GeneralDataType type = GeneralDataType.getRandomWithoutNull();
            int typeID = -1;
            switch (type) {
            case INT:
                typeID = Randomly.fromOptions(1, 2, 4, 8);
                break;
            // case FLOAT:
            // size = Randomly.fromOptions(4, 8);
            // break;
            case BOOLEAN:
            case STRING:
                // case DATE:
                // case TIMESTAMP:
                if (Randomly.getBoolean()) {
                    typeID = 500; // As MySQL Generator here is 500
                } else {
                    typeID = 0;
                }
                break;
            case VARTYPE:
                // pick a random type id from the typeMap
                // TODO an exception here
                typeID = Randomly.fromList(List.copyOf(typeMap.keySet()));
                break;
            default:
                throw new AssertionError(type);
            }

            return new GeneralCompositeDataType(type, typeID);
        }

        @Override
        public String toString() {
            switch (getPrimitiveDataType()) {
            case INT:
                return Randomly.fromOptions("INT", "INTEGER");
            // switch (size) {
            // case 8:
            // return Randomly.fromOptions("BIGINT", "INT8");
            // case 4:
            // return Randomly.fromOptions("INTEGER", "INT", "INT4", "SIGNED");
            // case 2:
            // return Randomly.fromOptions("SMALLINT", "INT2");
            // case 1:
            // return Randomly.fromOptions("TINYINT", "INT1");
            // default:
            // throw new AssertionError(size);
            // }
            case STRING:
                if (id == 0) {
                    return "VARCHAR";
                } else {
                    return "VARCHAR(" + id + ")";
                }
                // case FLOAT:
                // switch (size) {
                // case 8:
                // return Randomly.fromOptions("DOUBLE");
                // case 4:
                // return Randomly.fromOptions("REAL", "FLOAT4");
                // default:
                // throw new AssertionError(size);
                // }
            case BOOLEAN:
                return Randomly.fromOptions("BOOLEAN", "BOOL");
            // case TIMESTAMP:
            // return Randomly.fromOptions("TIMESTAMP", "DATETIME");
            // case DATE:
            // return Randomly.fromOptions("DATE");
            case NULL:
                return Randomly.fromOptions("NULL");
            case VARTYPE:
            //TODO catch exception here
                return typeMap.get(id).toUpperCase();
            default:
                throw new AssertionError(getPrimitiveDataType());
            }
        }

    }

    public static class GeneralColumn extends AbstractTableColumn<GeneralTable, GeneralCompositeDataType> {

        private final boolean isPrimaryKey;
        private final boolean isNullable;

        public GeneralColumn(String name, GeneralCompositeDataType columnType, boolean isPrimaryKey,
                boolean isNullable) {
            super(name, null, columnType);
            this.isPrimaryKey = isPrimaryKey;
            this.isNullable = isNullable;
        }

        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }

        public boolean isNullable() {
            return isNullable;
        }

    }

    public static class GeneralTables extends AbstractTables<GeneralTable, GeneralColumn> {

        public GeneralTables(List<GeneralTable> tables) {
            super(tables);
        }

    }

    public GeneralSchema(List<GeneralTable> databaseTables) {
        super(databaseTables);
    }

    public GeneralTables getRandomTableNonEmptyTables() {
        return new GeneralTables(Randomly.nonEmptySubset(getDatabaseTables()));
    }

    public static class GeneralTable extends AbstractRelationalTable<GeneralColumn, TableIndex, GeneralGlobalState> {

        public GeneralTable(String tableName, List<GeneralColumn> columns, boolean isView) {
            super(tableName, columns, Collections.emptyList(), isView);
        }

        public GeneralTable(String tableName, List<GeneralColumn> columns, List<TableIndex> indexes, boolean isView) {
            super(tableName, columns, indexes, isView);
        }

    }


    @Override
    public String getFreeTableName() {
        // TODO Auto-generated method stub
        int i = 0;
        if (Randomly.getBooleanWithRatherLowProbability()) {
            i = (int) Randomly.getNotCachedInteger(0, 10);
        }
        do {
            String tableName = String.format("t%d", i++);
            if (super.getDatabaseTables().stream().noneMatch(t -> t.getName().endsWith(tableName))) {
                return tableName;
            }
        } while (true);
    }

    public void printTables() {
        for (GeneralTable t : getDatabaseTables()) {
            System.out.println(t.getName() + " -------");
            for (GeneralColumn c : t.getColumns()) {
                System.out.println(c.getName() + " " + c.getType());
            }
        }
    }

    public static GeneralFragments getFragments() {
        return fragments;
    }
}
