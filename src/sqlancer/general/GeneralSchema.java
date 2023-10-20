package sqlancer.general;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sqlancer.IgnoreMeException;
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

        INT, VARCHAR, BOOLEAN, FLOAT, DATE, TIMESTAMP, NULL;

        public static GeneralDataType getRandomWithoutNull() {
            GeneralDataType dt;
            do {
                dt = Randomly.fromOptions(values());
            } while (dt == GeneralDataType.NULL);
            return dt;
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
            case FLOAT:
                size = Randomly.fromOptions(4, 8);
                break;
            case BOOLEAN:
            case VARCHAR:
            case DATE:
            case TIMESTAMP:
                size = 0;
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
                switch (size) {
                case 8:
                    return Randomly.fromOptions("BIGINT", "INT8");
                case 4:
                    return Randomly.fromOptions("INTEGER", "INT", "INT4", "SIGNED");
                case 2:
                    return Randomly.fromOptions("SMALLINT", "INT2");
                case 1:
                    return Randomly.fromOptions("TINYINT", "INT1");
                default:
                    throw new AssertionError(size);
                }
            case VARCHAR:
                return "VARCHAR";
            case FLOAT:
                switch (size) {
                case 8:
                    return Randomly.fromOptions("DOUBLE");
                case 4:
                    return Randomly.fromOptions("REAL", "FLOAT4");
                default:
                    throw new AssertionError(size);
                }
            case BOOLEAN:
                return Randomly.fromOptions("BOOLEAN", "BOOL");
            case TIMESTAMP:
                return Randomly.fromOptions("TIMESTAMP", "DATETIME");
            case DATE:
                return Randomly.fromOptions("DATE");
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

    private static GeneralCompositeDataType getColumnType(String typeString) {
        GeneralDataType primitiveType;
        int size = -1;
        if (typeString.startsWith("DECIMAL")) { // Ugly hack
            return new GeneralCompositeDataType(GeneralDataType.FLOAT, 8);
        }
        switch (typeString) {
        case "INTEGER":
            primitiveType = GeneralDataType.INT;
            size = 4;
            break;
        case "SMALLINT":
            primitiveType = GeneralDataType.INT;
            size = 2;
            break;
        case "BIGINT":
        case "HUGEINT": // TODO: 16-bit int
            primitiveType = GeneralDataType.INT;
            size = 8;
            break;
        case "TINYINT":
            primitiveType = GeneralDataType.INT;
            size = 1;
            break;
        case "VARCHAR":
            primitiveType = GeneralDataType.VARCHAR;
            break;
        case "FLOAT":
            primitiveType = GeneralDataType.FLOAT;
            size = 4;
            break;
        case "DOUBLE":
            primitiveType = GeneralDataType.FLOAT;
            size = 8;
            break;
        case "BOOLEAN":
            primitiveType = GeneralDataType.BOOLEAN;
            break;
        case "DATE":
            primitiveType = GeneralDataType.DATE;
            break;
        case "TIMESTAMP":
            primitiveType = GeneralDataType.TIMESTAMP;
            break;
        case "NULL":
            primitiveType = GeneralDataType.NULL;
            break;
        case "INTERVAL":
            throw new IgnoreMeException();
        // TODO: caused when a view contains a computation like ((TIMESTAMP '1970-01-05 11:26:57')-(TIMESTAMP
        // '1969-12-29 06:50:27'))
        default:
            throw new AssertionError(typeString);
        }
        return new GeneralCompositeDataType(primitiveType, size);
    }

    public static class GeneralTable extends AbstractRelationalTable<GeneralColumn, TableIndex, GeneralGlobalState> {

        public GeneralTable(String tableName, List<GeneralColumn> columns, boolean isView) {
            super(tableName, columns, Collections.emptyList(), isView);
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
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT * FROM sqlite_master WHERE type='table' or type='view'")) {
                while (rs.next()) {
                    tableNames.add(rs.getString("name"));
                }
            }
        }
        return tableNames;
    }

    private static List<GeneralColumn> getTableColumns(SQLConnection con, String tableName) throws SQLException {
        List<GeneralColumn> columns = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery(String.format("SELECT * FROM pragma_table_info('%s');", tableName))) {
                while (rs.next()) {
                    String columnName = rs.getString("name");
                    String dataType = rs.getString("type");
                    boolean isNullable = rs.getString("notnull").contentEquals("false");
                    boolean isPrimaryKey = rs.getString("pk").contains("true");
                    GeneralColumn c = new GeneralColumn(columnName, getColumnType(dataType), isPrimaryKey, isNullable);
                    columns.add(c);
                }
            }
        }
        if (columns.stream().noneMatch(c -> c.isPrimaryKey())) {
            // https://github.com/cwida/General/issues/589
            // https://github.com/cwida/General/issues/588
            // TODO: implement an option to enable/disable rowids
            columns.add(new GeneralColumn("rowid", new GeneralCompositeDataType(GeneralDataType.INT, 4), false, false));
        }
        return columns;
    }

}
