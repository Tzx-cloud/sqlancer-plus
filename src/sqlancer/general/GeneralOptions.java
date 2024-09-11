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
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.oracle.GeneralNoRECOracle;
import sqlancer.general.oracle.GeneralQueryPartitioningWhere;

@Parameters(commandDescription = "General")
public class GeneralOptions implements DBMSSpecificOptions<GeneralOptions.GeneralOracleFactory> {

    @Parameter(names = "--test-not-null", description = "Allow generating NOT NULL constraints in tables", arity = 1)
    public boolean testNotNullConstraints = true;

    @Parameter(names = "--test-int-constants", description = "Allow generating INTEGER constants", arity = 1)
    public boolean testIntConstants = true;

    @Parameter(names = "--test-varchar-constants", description = "Allow generating VARCHAR constants", arity = 1)
    public boolean testStringConstants = true;

    @Parameter(names = "--test-boolean-constants", description = "Allow generating boolean constants", arity = 1)
    public boolean testBooleanConstants = true;

    @Parameter(names = "--test-indexes", description = "Allow explicit (i.e. CREATE INDEX) and implicit (i.e., UNIQUE and PRIMARY KEY) indexes", arity = 1)
    public boolean testIndexes = true;

    @Parameter(names = "--max-num-views", description = "The maximum number of views that can be generated for a database", arity = 1)
    public int maxNumViews = 1;

    @Parameter(names = "--max-num-updates", description = "The maximum number of UPDATE statements that are issued for a database", arity = 1)
    public int maxNumUpdates = 5;

    @Parameter(names = "--enable-error-handling", description = "Enable error handling", arity = 1)
    public boolean enableErrorHandling = true;

    @Parameter(names = "--oracle")
    public List<GeneralOracleFactory> oracles = Arrays.asList(GeneralOracleFactory.WHERE);

    @Parameter(names = "--enable-feedback", description = "Enable feedback for generator", arity = 1)
    public boolean enableFeedback = true;

    @Parameter(names = "--enable-learning", description = "Enable learning for generator", arity = 1)
    public boolean enableLearning = true;

    @Parameter(names = "--untype-expr", description = "Allow untyped expressions", arity = 1)
    public boolean untypeExpr = false;

    @Parameter(names = "--database-table-delim", description = "The delimiter for database tables", arity = 1)
    public String dbTableDelim = "_";

    @Parameter(names = "--use-deduplicator", description = "Use the deduplicator")
    public boolean useDeduplicator = false;

    public enum GeneralOracleFactory implements OracleFactory<GeneralGlobalState> {
        NOREC {

            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                return new GeneralNoRECOracle(globalState);
            }

        },
        WHERE {
            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                return new GeneralQueryPartitioningWhere(globalState);
            }
        },
        QUERY_PARTITIONING {
            @Override
            public TestOracle<GeneralGlobalState> create(GeneralGlobalState globalState) throws SQLException {
                List<TestOracle<GeneralGlobalState>> oracles = new ArrayList<>();
                oracles.add(new GeneralQueryPartitioningWhere(globalState));
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

            @Override
            public void syncData(GeneralGlobalState globalState) throws SQLException {
                for (GeneralTable table : globalState.getSchema().getDatabaseTablesWithoutViews()) {
                    try (Statement s = globalState.getConnection().createStatement()) {
                        s.execute(String.format("REFRESH TABLE %s", table.getName()));
                        globalState.getState().logStatement(String.format("REFRESH TABLE %s", table.getName()));
                    } catch (SQLException e) {
                        // ignore
                    }
                }
            }
        },
        FIREBIRD {

            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:firebirdsql://localhost:10009//app/default?user=SYSDBA&password=masterkey");
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
                return String.format("jdbc:postgresql://localhost:10008/dev?user=root");
            }

            @Override
            public Connection cleanOrSetUpDatabase(GeneralGlobalState globalState, String databaseName)
                    throws SQLException {
                Connection conn = DriverManager.getConnection(getJDBCString(globalState));
                setIsNewSchema(false);
                for (int i = 0; i < 100; i++) {
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP TABLE %s_t%d", databaseName, i));
                    } catch (SQLException e1) {
                    }
                    globalState.getState().logStatement(String.format("DROP TABLE %s_t%d", databaseName, i));
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP VIEW %s_v%d", databaseName, i));
                    } catch (SQLException e1) {
                    }
                    globalState.getState().logStatement(String.format("DROP VIEW %s_v%d", databaseName, i));
                }
                try (Statement s = conn.createStatement()) {
                    s.execute("set query_mode to local;");
                    globalState.getState().logStatement("set query_mode to local;");
                }
                return conn;
            }

            @Override
            public void syncData(GeneralGlobalState globalState) throws SQLException {
                try (Statement s = globalState.getConnection().createStatement()) {
                    s.execute(String.format("FLUSH;"));
                    globalState.getState().logStatement(String.format("FLUSH;"));
                } catch (SQLException e) {
                    // ignore
                }
            }
        },
        DUCKDB {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                String databaseFile = System.getProperty("duckdb.database.file", "");
                return String.format("jdbc:duckdb:" + databaseFile);
            }

            @Override
            public Connection cleanOrSetUpDatabase(GeneralGlobalState globalState, String databaseName)
                    throws SQLException {
                Connection conn = DriverManager.getConnection(getJDBCString(globalState));
                return conn;
            }
        },
        POSTGRESQL {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:postgresql://localhost:10010/?user=postgres&password=postgres");
            }
        },
        COCKROACHDB {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:postgresql://localhost:10011/?user=root");
            }
        },
        TIDB {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:mysql://localhost:10013/?user=root");
            }
        },
        SQLITE {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:sqlite:file:%s?mode=memory&cache=private", globalState.getDatabaseName());
            }

            @Override
            public Connection cleanOrSetUpDatabase(GeneralGlobalState globalState, String databaseName)
                    throws SQLException {
                Connection conn = DriverManager.getConnection(getJDBCString(globalState));
                return conn;
            }
        },
        UMBRA {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:postgresql://localhost:10015/?user=postgres&password=postgres");
            }

            @Override
            public Connection cleanOrSetUpDatabase(GeneralGlobalState globalState, String databaseName)
                    throws SQLException {
                Connection conn = DriverManager.getConnection(getJDBCString(globalState));
                setIsNewSchema(false);
                for (int i = 0; i < 100; i++) {
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP TABLE %s_t%d", databaseName, i));
                    } catch (SQLException e1) {
                    }
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP VIEW %s_v%d", databaseName, i));
                    } catch (SQLException e1) {
                    }
                }
                try (Statement s = conn.createStatement()) {
                    s.execute("set debug.storage = 'P';");
                    globalState.getState().logStatement("set debug.storage = 'P';");
                }
                return conn;
            }
        },
        MARIADB {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:mariadb://localhost:10016/?user=root&password=root");
            }
        },
        IMMUDB {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format(
                        "jdbc:postgresql://localhost:10017/defaultdb?sslmode=allow&preferQueryMode=simple&user=immudb&password=immudb");
            }
        },
        QUESTDB {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:postgresql://localhost:10018/?user=admin&password=quest");
            }
        },
        PERCONA {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:mysql://localhost:10022/?user=root&password=root");
            }
        },
        VIRTUOSO {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:virtuoso://localhost:10020/UID=dba/PWD=dba");
            }
        },
        MONETDB {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:monetdb://localhost:10021/monetdb?user=monetdb&password=monetdb");
            }

            @Override
            public Connection cleanOrSetUpDatabase(GeneralGlobalState globalState, String databaseName)
                    throws SQLException {
                Connection conn = DriverManager.getConnection(getJDBCString(globalState));
                setIsNewSchema(false);
                for (int i = 0; i < 100; i++) {
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP TABLE %s_t%d CASCADE", databaseName, i));
                    } catch (SQLException e1) {
                    }
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP VIEW %s_v%d CASCADE", databaseName, i));
                    } catch (SQLException e1) {
                    }
                }
                return conn;
            }
        },
        H2 {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:h2:./databases/h2/%s;DB_CLOSE_ON_EXIT=FALSE", globalState.getDatabaseName());
            }

            @Override
            public Connection cleanOrSetUpDatabase(GeneralGlobalState globalState, String databaseName)
                    throws SQLException {
                Connection conn = DriverManager.getConnection(getJDBCString(globalState));
                conn.createStatement().execute("DROP ALL OBJECTS DELETE FILES");
                conn.close();
                conn = DriverManager.getConnection(getJDBCString(globalState));
                return conn;
            }
        },
        CLICKHOUSE {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:clickhouse://localhost:10023/default");
            }

            @Override
            public Connection cleanOrSetUpDatabase(GeneralGlobalState globalState, String databaseName)
                    throws SQLException {
                Connection conn = DriverManager.getConnection(getJDBCString(globalState));
                setIsNewSchema(false);
                String dbTableDelim = globalState.getDbmsSpecificOptions().dbTableDelim;
                for (int i = 0; i < 100; i++) {
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP TABLE %s%st%d", databaseName, dbTableDelim, i));
                    } catch (SQLException e1) {
                    }
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP VIEW %s%sv%d", databaseName, dbTableDelim, i));
                    } catch (SQLException e1) {
                    }
                }
                return conn;
            }
        },
        VITESS {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:mysql://localhost:10027/?user=root");
            }
        },
        PRESTO {
            @Override
            public String getJDBCString(GeneralGlobalState globalState) {
                return String.format("jdbc:presto://localhost:10028/memory?user=test");
            }

            @Override
            public Connection cleanOrSetUpDatabase(GeneralGlobalState globalState, String databaseName) throws SQLException {
                Connection conn = DriverManager.getConnection(getJDBCString(globalState));
                for (int i = 0; i < 100; i++) {
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP TABLE IF EXISTS MEMORY.%s.t%d", databaseName, i));
                    } catch (SQLException e1) {
                    }
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP VIEW IF EXISTS MEMORY.%s.v%d", databaseName, i));
                    } catch (SQLException e1) {
                    }
                }
                try (Statement s = conn.createStatement()) {
                    s.execute("DROP SCHEMA IF EXISTS MEMORY." + databaseName);
                    globalState.getState().logStatement("DROP SCHEMA IF EXISTS MEMORY." + databaseName);
                    s.execute("CREATE SCHEMA MEMORY." + databaseName);
                    globalState.getState().logStatement("CREATE SCHEMA MEMORY." + databaseName);
                    s.execute("USE MEMORY." + databaseName);
                    globalState.getState().logStatement("USE MEMORY." + databaseName);
                    setIsNewSchema(true);
                } catch (SQLException e) {
                    setIsNewSchema(false);
                    e.printStackTrace();
                }
                return conn;
            }
        },
        ;

        private boolean isNewSchema = true;

        public boolean isNewSchema() {
            return isNewSchema;
        }

        public void setIsNewSchema(boolean isNewSchema) {
            this.isNewSchema = isNewSchema;
        }

        public String getDropTableStatement(String tableName) {
            return String.format("DROP TABLE %s", tableName);
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
                String dbTableDelim = globalState.getDbmsSpecificOptions().dbTableDelim;
                for (int i = 0; i < 100; i++) {
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP TABLE %s%st%d", databaseName, dbTableDelim, i));
                    } catch (SQLException e1) {
                    }
                    try (Statement s = conn.createStatement()) {
                        s.execute(String.format("DROP VIEW %s%sv%d", databaseName, dbTableDelim, i));
                    } catch (SQLException e1) {
                    }
                }
            }
            return conn;
        }

        @Override
        public void syncData(GeneralGlobalState globalState) throws SQLException {
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
