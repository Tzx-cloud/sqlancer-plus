package sqlancer.general;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import sqlancer.DBMSSpecificOptions;
import sqlancer.DatabaseEngineFactory;
import sqlancer.OracleFactory;
import sqlancer.common.oracle.CompositeTestOracle;
import sqlancer.common.oracle.TestOracle;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.oracle.GeneralNoRECOracle;
import sqlancer.general.oracle.GeneralQueryPartitioningAggregate;
import sqlancer.general.oracle.GeneralQueryPartitioningDistinct;
import sqlancer.general.oracle.GeneralQueryPartitioningGroupBy;
import sqlancer.general.oracle.GeneralQueryPartitioningHaving;
import sqlancer.general.oracle.GeneralQueryPartitioningWhere;

@Parameters(commandDescription = "General")
public class GeneralOptions implements DBMSSpecificOptions<GeneralOptions.GeneralOracleFactory> {

    @Parameter(names = "--test-collate", arity = 1)
    public boolean testCollate = true;

    @Parameter(names = "--test-check", description = "Allow generating CHECK constraints in tables", arity = 1)
    public boolean testCheckConstraints = true;

    @Parameter(names = "--test-default-values", description = "Allow generating DEFAULT values in tables", arity = 1)
    public boolean testDefaultValues = true;

    @Parameter(names = "--test-not-null", description = "Allow generating NOT NULL constraints in tables", arity = 1)
    public boolean testNotNullConstraints = true;

    @Parameter(names = "--test-functions", description = "Allow generating functions in expressions", arity = 1)
    public boolean testFunctions = true;

    @Parameter(names = "--test-casts", description = "Allow generating casts in expressions", arity = 1)
    public boolean testCasts = true;

    @Parameter(names = "--test-between", description = "Allow generating the BETWEEN operator in expressions", arity = 1)
    public boolean testBetween = true;

    @Parameter(names = "--test-in", description = "Allow generating the IN operator in expressions", arity = 1)
    public boolean testIn = true;

    @Parameter(names = "--test-case", description = "Allow generating the CASE operator in expressions", arity = 1)
    public boolean testCase = true;

    @Parameter(names = "--test-binary-logicals", description = "Allow generating AND and OR in expressions", arity = 1)
    public boolean testBinaryLogicals = true;

    @Parameter(names = "--test-int-constants", description = "Allow generating INTEGER constants", arity = 1)
    public boolean testIntConstants = true;

    @Parameter(names = "--test-varchar-constants", description = "Allow generating VARCHAR constants", arity = 1)
    public boolean testStringConstants = true;

    @Parameter(names = "--test-date-constants", description = "Allow generating DATE constants", arity = 1)
    public boolean testDateConstants = true;

    @Parameter(names = "--test-timestamp-constants", description = "Allow generating TIMESTAMP constants", arity = 1)
    public boolean testTimestampConstants = true;

    @Parameter(names = "--test-float-constants", description = "Allow generating floating-point constants", arity = 1)
    public boolean testFloatConstants = true;

    @Parameter(names = "--test-boolean-constants", description = "Allow generating boolean constants", arity = 1)
    public boolean testBooleanConstants = true;

    @Parameter(names = "--test-binary-comparisons", description = "Allow generating binary comparison operators (e.g., >= or LIKE)", arity = 1)
    public boolean testBinaryComparisons = true;

    @Parameter(names = "--test-indexes", description = "Allow explicit (i.e. CREATE INDEX) and implicit (i.e., UNIQUE and PRIMARY KEY) indexes", arity = 1)
    public boolean testIndexes = true;

    @Parameter(names = "--test-rowid", description = "Test tables' rowid columns", arity = 1)
    public boolean testRowid = true;

    @Parameter(names = "--max-num-views", description = "The maximum number of views that can be generated for a database", arity = 1)
    public int maxNumViews = 1;

    @Parameter(names = "--max-num-deletes", description = "The maximum number of DELETE statements that are issued for a database", arity = 1)
    public int maxNumDeletes = 1;

    @Parameter(names = "--max-num-updates", description = "The maximum number of UPDATE statements that are issued for a database", arity = 1)
    public int maxNumUpdates = 5;

    @Parameter(names = "--oracle")
    public List<GeneralOracleFactory> oracles = Arrays.asList(GeneralOracleFactory.QUERY_PARTITIONING);

    public enum GeneralOracleFactory implements OracleFactory<GeneralGlobalState> {
        NOREC {

            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                return new GeneralNoRECOracle(globalState);
            }

        },
        HAVING {
            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                return new GeneralQueryPartitioningHaving(globalState);
            }
        },
        WHERE {
            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                return new GeneralQueryPartitioningWhere(globalState);
            }
        },
        GROUP_BY {
            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                return new GeneralQueryPartitioningGroupBy(globalState);
            }
        },
        AGGREGATE {

            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                return new GeneralQueryPartitioningAggregate(globalState);
            }

        },
        DISTINCT {
            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                return new GeneralQueryPartitioningDistinct(globalState);
            }
        },
        QUERY_PARTITIONING {
            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                List<TestOracle<GeneralGlobalState>> oracles = new ArrayList<>();
                oracles.add(new GeneralQueryPartitioningWhere(globalState));
                oracles.add(new GeneralQueryPartitioningHaving(globalState));
                oracles.add(new GeneralQueryPartitioningAggregate(globalState));
                oracles.add(new GeneralQueryPartitioningDistinct(globalState));
                oracles.add(new GeneralQueryPartitioningGroupBy(globalState));
                return new CompositeTestOracle<GeneralGlobalState>(oracles, globalState);
            }
        };

    };

    @Parameter(names = "--database-engine")
    public GeneralDatabaseEngineFactory databaseEngine = GeneralDatabaseEngineFactory.CRATE;

    public enum GeneralDatabaseEngineFactory implements DatabaseEngineFactory<GeneralGlobalState> {
        CRATE {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:postgresql://localhost:10004/?user=crate");
            }
        },
        FIREBIRD {

            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:firebirdsql://localhost:10008/default?user=SYSDBA&password=masterkey");
            }

        },
        MYSQL {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:mysql://localhost:23306/?user=root&password=root");
            }
        },
        DOLT {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:mysql://localhost:10007/?user=root");
            }
        },
        RISINGWAVE {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:postgresql://localhost:10009/dev?user=root");
            }
        };

        private boolean isNewSchema = true;

        public boolean isNewSchema() {
            return isNewSchema;
        }

        @Override
        public Connection cleanOrSetUpDatabase(GeneralGlobalState globalState, String databaseName)
                throws SQLException {
            Connection conn = DriverManager.getConnection(getJDBCString(globalState));
            try (Statement s = conn.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS " + databaseName);
                globalState.getState().logStatement("DROP DATABASE IF EXISTS " + databaseName);
                s.execute("CREATE DATABASE " + databaseName);
                globalState.getState().logStatement("CREATE DATABASE " + databaseName);
                s.execute("USE " + databaseName);
                globalState.getState().logStatement("USE " + databaseName);
                isNewSchema = true;
            } catch (SQLException e) {
                isNewSchema = false;
                for (int i = 0; i < 100; i++) {
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP TABLE %s_t%d", databaseName, i));
                    } catch (SQLException e1) {
                    }
                }
            }
            return conn;
        }

    }

    @Override
    public List<GeneralOracleFactory> getTestOracleFactory() {
        return oracles;
    }

    public GeneralDatabaseEngineFactory getDatabaseEngineFactory() {
        return databaseEngine;
    }

}
