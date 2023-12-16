package sqlancer.general;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.google.auto.service.AutoService;

import sqlancer.AbstractAction;
import sqlancer.DatabaseEngineFactory;
import sqlancer.DatabaseProvider;
import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.SQLGlobalState;
import sqlancer.SQLProviderAdapter;
import sqlancer.StatementExecutor;
import sqlancer.common.query.Query;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.schema.AbstractTable;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.general.GeneralSchema.GeneralTable;
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
        // .asString(GeneralRandomQuerySynthesizer.generateSelect(g,
        // Randomly.smallNumber() + 1)),
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
        // return r.getInteger(0, globalState.getDbmsSpecificOptions().maxNumDeletes +
        // 1);
        // case CREATE_VIEW:
        // return r.getInteger(0, globalState.getDbmsSpecificOptions().maxNumViews + 1);
        default:
            throw new AssertionError(a);
        }
    }

    public static class GeneralGlobalState extends SQLGlobalState<GeneralOptions, GeneralSchema> {
        private GeneralSchema schema = new GeneralSchema(new ArrayList<>());
        private GeneralErrorHandler handler = new GeneralErrorHandler();

        @Override
        public GeneralSchema getSchema() {
            // TODO should we also check here if the saved schema match the jdbc schema?
            return schema;
        }

        public GeneralErrorHandler getHandler() {
            return handler;
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

        // Override execute statement
        @Override
        public boolean executeStatement(Query<SQLConnection> q, String... fills) throws Exception {
            boolean success = false;
            try {
                success = super.executeStatement(q, fills);
            } catch (Exception e) {
                handler.appendScoreToTable(false);
                throw e;
            }
            // I guess we want to make sure if the syntax is correct
            handler.appendScoreToTable(success);
            return success;
        }

        @Override
        public void updateOptions() {
            if (getDbmsSpecificOptions().enableErrorHandling) {
                handler.updateGeneratorOptions();
                handler.printStatistics();
                handler.saveStatistics(this);
            }
        }

        // @Override
        // public SQLancerResultSet executeStatementAndGet(Query<SQLConnection> q,
        // String... fills) throws Exception {
        // SQLancerResultSet result;
        // try {
        // result = super.executeStatementAndGet(q, fills);
        // } catch (Exception e) {
        // // if the query just fails
        // handler.appendScoreToTable(false);
        // throw e;
        // }
        // // We need to append later, because we need to know if the oracle check it
        // correct
        // handler.setExecutionStatus(result != null);
        // return result;
        // }
    }

    @Override
    public void generateDatabase(GeneralGlobalState globalState) throws Exception {
        DatabaseEngineFactory<GeneralGlobalState> databaseEngineFactory = globalState.getDbmsSpecificOptions()
                .getDatabaseEngineFactory();
        for (int i = 0; i < Randomly.fromOptions(1, 2); i++) {
            boolean success;
            int nrTries = 0;
            do {
                GeneralTableGenerator tableGenerator = new GeneralTableGenerator();
                SQLQueryAdapter qt = tableGenerator.getQuery(globalState);
                GeneralTable table = tableGenerator.getTable();
                // TODO add error handling here

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
        databaseEngineFactory.syncData(globalState);
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
        DatabaseEngineFactory<GeneralGlobalState> databaseEngineFactory = globalState.getDbmsSpecificOptions()
                .getDatabaseEngineFactory();
        String databaseName = globalState.getDatabaseName();

        // Try CREATE DATABASE:
        Connection conn = databaseEngineFactory.cleanOrSetUpDatabase(globalState, databaseName);
        globalState.getHandler().setOption(GeneratorNode.CREATE_DATABASE, databaseEngineFactory.isNewSchema());

        return new SQLConnection(conn);
    }

    @Override
    public String getDBMSName() {
        return "general";
    }

}
