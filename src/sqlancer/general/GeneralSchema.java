package sqlancer.general;

import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.DBMSCommon;
import sqlancer.common.schema.AbstractRelationalTable;
import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.AbstractTables;
import sqlancer.common.schema.TableIndex;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralTable;

public class GeneralSchema extends AbstractSchema<GeneralGlobalState, GeneralTable> {

    public enum GeneralDataType {

        // INT, VARCHAR, BOOLEAN, FLOAT, DATE, TIMESTAMP, NULL;
        INT, NULL, BOOLEAN, STRING;

        public static GeneralDataType getRandomWithoutNull() {
            GeneralDataType dt;
            do {
                dt = Randomly.fromOptions(values());
            } while (dt == GeneralDataType.NULL);
            return dt;
        }

        public GeneralCompositeDataType get() {
            return new GeneralCompositeDataType(this, 0);
        }

    }

    public static class GeneralCompositeDataType {

        private final GeneralDataType dataType;

        private final int size;

        public GeneralCompositeDataType(GeneralDataType dataType, int size) {
            this.dataType = dataType;
            this.size = size;
        }

        public GeneralDataType getPrimitiveDataType() {
            return dataType;
        }

        public int getSize() {
            if (size == -1) {
                throw new AssertionError(this);
            }
            return size;
        }

        public static GeneralCompositeDataType getRandomWithoutNull() {
            GeneralDataType type = GeneralDataType.getRandomWithoutNull();
            int size = -1;
            switch (type) {
            case INT:
                size = Randomly.fromOptions(1, 2, 4, 8);
                break;
            // case FLOAT:
            // size = Randomly.fromOptions(4, 8);
            // break;
            case BOOLEAN:
            case STRING:
                // case DATE:
                // case TIMESTAMP:
                if (Randomly.getBoolean()) {
                    size = 500; // As MySQL Generator here is 500
                } else {
                    size = 0;
                }
                break;
            default:
                throw new AssertionError(type);
            }

            return new GeneralCompositeDataType(type, size);
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
            if (size == 0) {
                return "VARCHAR";
            } else {
                return "VARCHAR(" + size + ")";
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

    private static GeneralCompositeDataType getColumnType(int type) {
        switch (type) {
        case Types.INTEGER:
            return new GeneralCompositeDataType(GeneralDataType.INT, 4);
        case Types.SMALLINT:
            return new GeneralCompositeDataType(GeneralDataType.INT, 2);
        case Types.BIGINT:
            return new GeneralCompositeDataType(GeneralDataType.INT, 8);
        case Types.TINYINT:
            return new GeneralCompositeDataType(GeneralDataType.INT, 1);
        case Types.VARCHAR:
            return new GeneralCompositeDataType(GeneralDataType.INT, 0);
        case Types.FLOAT:
            return new GeneralCompositeDataType(GeneralDataType.INT, 4);
        case Types.DOUBLE:
            return new GeneralCompositeDataType(GeneralDataType.INT, 8);
        case Types.BOOLEAN:
            return new GeneralCompositeDataType(GeneralDataType.INT, 0);
        case Types.DATE:
            return new GeneralCompositeDataType(GeneralDataType.INT, 0);
        case Types.TIMESTAMP:
            return new GeneralCompositeDataType(GeneralDataType.INT, 0);
        case Types.NULL:
            return new GeneralCompositeDataType(GeneralDataType.NULL, 0);
        default:
            throw new AssertionError(type);
        }
    }

    // private static GeneralCompositeDataType getColumnType(String typeString) {
    // GeneralDataType primitiveType;
    // int size = -1;
    // // if (typeString.startsWith("DECIMAL")) { // Ugly hack
    // // return new GeneralCompositeDataType(GeneralDataType.FLOAT, 8);
    // // }
    // switch (typeString) {
    // case "INTEGER":
    // primitiveType = GeneralDataType.INT;
    // size = 4;
    // break;
    // case "4":
    // primitiveType = GeneralDataType.INT;
    // size = 4;
    // break;
    // case "SMALLINT":
    // primitiveType = GeneralDataType.INT;
    // size = 2;
    // break;
    // case "BIGINT":
    // case "HUGEINT": // TODO: 16-bit int
    // primitiveType = GeneralDataType.INT;
    // size = 8;
    // break;
    // case "TINYINT":
    // primitiveType = GeneralDataType.INT;
    // size = 1;
    // break;
    // // case "VARCHAR":
    // // primitiveType = GeneralDataType.VARCHAR;
    // // break;
    // // case "FLOAT":
    // // primitiveType = GeneralDataType.FLOAT;
    // // size = 4;
    // // break;
    // // case "DOUBLE":
    // // primitiveType = GeneralDataType.FLOAT;
    // // size = 8;
    // // break;
    // // case "BOOLEAN":
    // // primitiveType = GeneralDataType.BOOLEAN;
    // // break;
    // // case "DATE":
    // // primitiveType = GeneralDataType.DATE;
    // // break;
    // // case "TIMESTAMP":
    // // primitiveType = GeneralDataType.TIMESTAMP;
    // // break;
    // case "NULL":
    // primitiveType = GeneralDataType.NULL;
    // break;
    // case "INTERVAL":
    // throw new IgnoreMeException();
    // // TODO: caused when a view contains a computation like ((TIMESTAMP '1970-01-05 11:26:57')-(TIMESTAMP
    // // '1969-12-29 06:50:27'))
    // default:
    // throw new AssertionError(typeString);
    // }
    // return new GeneralCompositeDataType(primitiveType, size);
    // }

    public static class GeneralTable extends AbstractRelationalTable<GeneralColumn, TableIndex, GeneralGlobalState> {

        public GeneralTable(String tableName, List<GeneralColumn> columns, boolean isView) {
            super(tableName, columns, Collections.emptyList(), isView);
        }

        public GeneralTable(String tableName, List<GeneralColumn> columns, List<TableIndex> indexes, boolean isView) {
            super(tableName, columns, indexes, isView);
        }

    }

    public static GeneralSchema fromConnection(SQLConnection con, String databaseName) throws SQLException {
        List<GeneralTable> databaseTables = new ArrayList<>();
        List<String> tableNames = getTableNames(con);

        for (String tableName : tableNames) {
            if (DBMSCommon.matchesIndexName(tableName)) {
                continue; // TODO: unexpected?
            }
            List<GeneralColumn> databaseColumns = getTableColumns(con, tableName);
            boolean isView = tableName.startsWith("v");
            GeneralTable t = new GeneralTable(tableName, databaseColumns, isView);
            for (GeneralColumn c : databaseColumns) {
                c.setTable(t);
            }
            databaseTables.add(t);

        }
        return new GeneralSchema(databaseTables);
    }

    private static List<String> getTableNames(SQLConnection con) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        // try (Statement s = con.createStatement()) {
        // try (ResultSet rs = s.executeQuery("SELECT * FROM sqlite_master WHERE type='table' or type='view'")) {
        // while (rs.next()) {
        // tableNames.add(rs.getString("name"));
        // }
        // }
        // }
        DatabaseMetaData metaData = con.getMetaData();
        // TODO only for less than 10 tables
        ResultSet tables = metaData.getTables(null, null, "T_", null);
        while (tables.next()) {
            tableNames.add(tables.getString("TABLE_NAME"));
        }
        return tableNames;
    }

    private static List<GeneralColumn> getTableColumns(SQLConnection con, String tableName) throws SQLException {
        List<GeneralColumn> columns = new ArrayList<>();
        String primaryColumnName = null;
        DatabaseMetaData metaData = con.getMetaData();
        // try (Statement s = con.createStatement()) {
        try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
            // ResultSetMetaData rsmd = rs.getMetaData();
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                boolean isNullable = rs.getString("IS_NULLABLE").contentEquals("true");
                // try (ResultSet rs2 = metaData.getPrimaryKeys(null, null, tableName)) {
                // while (rs2.next()) {
                // primaryColumnName = rs2.getString("COLUMN_NAME");
                // }
                // }
                boolean isPrimaryKey = primaryColumnName != null && primaryColumnName.contentEquals(columnName);
                GeneralColumn c = new GeneralColumn(columnName, getColumnType(dataType), isPrimaryKey, isNullable);
                columns.add(c);
            }
        }
        // }
        // if (columns.stream().noneMatch(c -> c.isPrimaryKey())) {
        // // https://github.com/cwida/General/issues/589
        // // https://github.com/cwida/General/issues/588
        // // TODO: implement an option to enable/disable rowids
        // columns.add(new GeneralColumn("rowid", new GeneralCompositeDataType(GeneralDataType.INT, 4), false, false));
        // }
        return columns;
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
}
