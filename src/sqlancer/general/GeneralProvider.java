package sqlancer.general;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.auto.service.AutoService;

import sqlancer.AbstractAction;
import sqlancer.DatabaseProvider;
import sqlancer.IgnoreMeException;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.SQLGlobalState;
import sqlancer.SQLProviderAdapter;
import sqlancer.StatementExecutor;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.schema.AbstractTable;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.GeneralSchema.GeneralTables;
import sqlancer.general.gen.GeneralIndexGenerator;
import sqlancer.general.gen.GeneralInsertGenerator;
import sqlancer.general.gen.GeneralTableGenerator;

@AutoService(DatabaseProvider.class)
public class GeneralProvider extends SQLProviderAdapter<GeneralProvider.GeneralGlobalState, GeneralOptions> {

    public GeneralProvider() {
        super(GeneralGlobalState.class, GeneralOptions.class);
    }

    public enum Action implements AbstractAction<GeneralGlobalState> {

        INSERT(GeneralInsertGenerator::getQuery), //
        CREATE_INDEX(GeneralIndexGenerator::getQuery); //
        // VACUUM((g) -> new SQLQueryAdapter("VACUUM;")), //
        // ANALYZE((g) -> new SQLQueryAdapter("ANALYZE;")); //
        // DELETE(GeneralDeleteGenerator::generate), //
        // UPDATE(GeneralUpdateGenerator::getQuery), //
        // CREATE_VIEW(GeneralViewGenerator::generate), //
        // EXPLAIN((g) -> {
        // ExpectedErrors errors = new ExpectedErrors();
        // GeneralErrors.addExpressionErrors(errors);
        // GeneralErrors.addGroupByErrors(errors);
        // return new SQLQueryAdapter(
        // "EXPLAIN " + GeneralToStringVisitor
        // .asString(GeneralRandomQuerySynthesizer.generateSelect(g, Randomly.smallNumber() + 1)),
        // errors);
        // })

        private final SQLQueryProvider<GeneralGlobalState> sqlQueryProvider;

        Action(SQLQueryProvider<GeneralGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        @Override
        public SQLQueryAdapter getQuery(GeneralGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery(state);
        }
    }

    private static int mapActions(GeneralGlobalState globalState, Action a) {
        Randomly r = globalState.getRandomly();
        switch (a) {
        case INSERT:
            return r.getInteger(0, globalState.getOptions().getMaxNumberInserts());
        case CREATE_INDEX:
            if (!globalState.getDbmsSpecificOptions().testIndexes) {
                return 0;
            }
            // fall through
            return r.getInteger(0, globalState.getDbmsSpecificOptions().maxNumUpdates + 1);
        // case VACUUM: // seems to be ignored
        // case ANALYZE: // seems to be ignored
            // case EXPLAIN:
            // return r.getInteger(0, 2);
        // case DELETE:
        // return r.getInteger(0, globalState.getDbmsSpecificOptions().maxNumDeletes + 1);
        // case CREATE_VIEW:
        // return r.getInteger(0, globalState.getDbmsSpecificOptions().maxNumViews + 1);
        default:
            throw new AssertionError(a);
        }
    }

    public static class GeneralGlobalState extends SQLGlobalState<GeneralOptions, GeneralSchema> {
        private GeneralSchema schema = new GeneralSchema(new ArrayList<>());
        private HashMap<String, Float> overallGeneratorScore;
        private HashMap<String, Float> singleStatementScore;

        @Override
        public GeneralSchema getSchema() {
            // TODO should we also check here if the saved schema match the jdbc schema?
            return schema;
        }

        public void setSchema(List<GeneralTable> tables) {
            this.schema = new GeneralSchema(tables);
        }

        @Override
        public void updateSchema() throws Exception {
            for (AbstractTable<?, ?, ?> table : schema.getDatabaseTables()) {
            table.recomputeCount();
        }
        }

        @Override
        protected GeneralSchema readSchema() throws SQLException {
            return GeneralSchema.fromConnection(getConnection(), getDatabaseName());
        }

        public void addScore() {

        }
    }

    @Override
    public void generateDatabase(GeneralGlobalState globalState) throws Exception {
        for (int i = 0; i < Randomly.fromOptions(1, 2); i++) {
            boolean success;
            int nrTries = 0;
            do {
                GeneralTableGenerator tableGenerator = new GeneralTableGenerator();
                SQLQueryAdapter qt = tableGenerator.getQuery(globalState);
                GeneralTable table = tableGenerator.getTable();
                //TODO add error handling here
                
                success = globalState.executeStatement(qt);
                if (success) {
                    List<GeneralTable> databaseTables = new ArrayList<>(globalState.getSchema().getDatabaseTables());
                    // globalState.getSchema().addDatabaseTable(table);
                    databaseTables.add(table);
                    globalState.setSchema(databaseTables);
                }
            } while (!success && nrTries++ < 100);
        }
        if (globalState.getSchema().getDatabaseTables().isEmpty()) {
            throw new AssertionError("Failed to create any table"); // TODO
        }
        StatementExecutor<GeneralGlobalState, Action> se = new StatementExecutor<>(globalState, Action.values(),
                GeneralProvider::mapActions, (q) -> {
                    if (globalState.getSchema().getDatabaseTables().isEmpty()) {
                        throw new IgnoreMeException();
                    }
                });
        se.executeStatements();
    }

    public void tryDeleteFile(String fname) {
        try {
            File f = new File(fname);
            f.delete();
        } catch (Exception e) {
        }
    }

    public void tryDeleteDatabase(String dbpath) {
        if (dbpath.equals("") || dbpath.equals(":memory:")) {
            return;
        }
        tryDeleteFile(dbpath);
        tryDeleteFile(dbpath + ".wal");
    }

    @Override
    public SQLConnection createDatabase(GeneralGlobalState globalState) throws SQLException {
        // String databaseFile = System.getProperty("duckdb.database.file", "");
        // String url = "jdbc:duckdb:" + databaseFile;
        // tryDeleteDatabase(databaseFile);

        // MainOptions options = globalState.getOptions();
        // if (!(options.isDefaultUsername() && options.isDefaultPassword())) {
        //     throw new AssertionError("DuckDB doesn't support credentials (username/password)");
        // }

        // Connection conn = DriverManager.getConnection(url);
        // Statement stmt = conn.createStatement();
        // stmt.execute("PRAGMA checkpoint_threshold='1 byte';");
        // stmt.close();
        String username = globalState.getOptions().getUserName();
        String password = globalState.getOptions().getPassword();
        String host = globalState.getOptions().getHost();
        int port = globalState.getOptions().getPort();
        // String url = String.format("jdbc:trino://%s:%d", host, port);
        // Connection conn = DriverManager.getConnection(url, username, password);
        // String databaseName = "memory." + globalState.getDatabaseName();
        // try (Statement s = conn.createStatement()) {
        //     s.execute("DROP SCHEMA IF EXISTS " + databaseName + " CASCADE");
        // }
        // try (Statement s = conn.createStatement()) {
        //     s.execute("CREATE SCHEMA " +databaseName);
        // }
        // try (Statement s = conn.createStatement()) {
        //     s.execute("USE " +databaseName);
        // }
        String databaseName =  globalState.getDatabaseName();
        String url = String.format("jdbc:postgresql://%s:%d/", host, port, databaseName);
        Connection conn = DriverManager.getConnection(url, username, password);
        // Connection conn = DriverManager.getConnection(url);
        // try (Statement s = conn.createStatement()) {
        //     s.execute("DROP SCHEMA IF EXISTS " + databaseName);
        // }
        // try (Statement s = conn.createStatement()) {
        //     s.execute("CREATE SCHEMA " +databaseName);
        // }
        // try (Statement s = conn.createStatement()) {
        //     s.execute("USE " +databaseName);
        // }

        return new SQLConnection(conn);
    }

    @Override
    public String getDBMSName() {
        return "general";
    }

}
