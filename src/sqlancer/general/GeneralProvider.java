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
import sqlancer.ExecutionTimer;
import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.SQLGlobalState;
import sqlancer.SQLProviderAdapter;
import sqlancer.StatementExecutor;
import sqlancer.common.query.Query;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.gen.GeneralIndexGenerator;
import sqlancer.general.gen.GeneralInsertGenerator;
import sqlancer.general.gen.GeneralTableGenerator;
import sqlancer.general.gen.GeneralViewGenerator;

@AutoService(DatabaseProvider.class)
public class GeneralProvider extends SQLProviderAdapter<GeneralProvider.GeneralGlobalState, GeneralOptions> {

    public GeneralProvider() {
        super(GeneralGlobalState.class, GeneralOptions.class);
    }

    public enum Action implements AbstractAction<GeneralGlobalState> {

        INSERT(GeneralInsertGenerator::getQuery), //
        CREATE_INDEX(GeneralIndexGenerator::getQuery), //
        // VACUUM((g) -> new SQLQueryAdapter("VACUUM;")), //
        // ANALYZE((g) -> new SQLQueryAdapter("ANALYZE;")); //
        // DELETE(GeneralDeleteGenerator::generate), //
        // UPDATE(GeneralUpdateGenerator::getQuery), //
        CREATE_VIEW(GeneralViewGenerator::generate); //
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
        case CREATE_VIEW:
            return r.getInteger(0, globalState.getDbmsSpecificOptions().maxNumViews + 1);
        default:
            throw new AssertionError(a);
        }
    }

    public static class GeneralGlobalState extends SQLGlobalState<GeneralOptions, GeneralSchema> {
        private GeneralSchema schema = new GeneralSchema(new ArrayList<>());
        private GeneralErrorHandler handler = new GeneralErrorHandler();
        private GeneralTable updateTable;

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

        public void setUpdateTable(GeneralTable updateTable) {
            this.updateTable = updateTable;
        }

        public GeneralTable getUpdateTable() {
            return updateTable;
        }

        @Override
        public void updateSchema() throws Exception {
            if (updateTable != null) {
                List<GeneralTable> databaseTables = new ArrayList<>(schema.getDatabaseTables());
                boolean found = false;
                // substitute or add the table with the new one according to getName
                for (int i = 0; i < databaseTables.size(); i++) {
                    if (databaseTables.get(i).getName().equals(updateTable.getName())) {
                        databaseTables.set(i, updateTable);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    databaseTables.add(updateTable);
                }
                setSchema(databaseTables);
            }
            updateTable = null;
        }

        @Override
        public void executeEpilogue(Query<?> q, boolean success, ExecutionTimer timer) throws Exception {
            boolean logExecutionTime = getOptions().logExecutionTime();
            if (success && getOptions().printSucceedingStatements()) {
                System.out.println(q.getQueryString());
            }
            if (logExecutionTime) {
                getLogger().writeCurrent(" -- " + timer.end().asString());
            }
            if (q.couldAffectSchema() && success) {
                updateSchema();
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
        public void updateHandler(boolean status) {
            String databaseName = getDatabaseName();
            if (getDbmsSpecificOptions().enableErrorHandling) {
                if (!status) {
                    // print the last item of handler.
                    System.out.println(databaseName);
                    System.out.println(handler.getLastGeneratorScore());
                    handler.appendHistory(databaseName);
                } else {
                    handler.updateGeneratorOptions();
                }
                handler.printStatistics();
                handler.saveStatistics(this);
                if (handler.getCurDepth(databaseName) < getOptions().getMaxExpressionDepth()) {
                    handler.incrementCurDepth(databaseName);
                }
                System.out.println(databaseName + "Current depth: " + handler.getCurDepth(databaseName));
            }
        }

        @Override
        public boolean checkIfDuplicate() {
            return handler.checkIfDuplicate();
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
    protected void checkViewsAreValid(GeneralGlobalState globalState) {
        List<GeneralTable> views = globalState.getSchema().getViews();
        for (GeneralTable view : views) {
            SQLQueryAdapter q = new SQLQueryAdapter("SELECT * FROM " + view.getName() + " LIMIT 1");
            try {
                if (!q.execute(globalState)) {
                    dropView(globalState, view.getName());
                }
            } catch (Throwable t) {
                dropView(globalState, view.getName());
            }
        }
        globalState.getSchema().printTables();
    }

    private void dropView(GeneralGlobalState globalState, String viewName) {
        try {
            globalState.executeStatement(new SQLQueryAdapter("DROP VIEW " + viewName, true));
            List<GeneralTable> databaseTables = new ArrayList<>(globalState.getSchema().getDatabaseTables());
            for (int i = 0; i < databaseTables.size(); i++) {
                if (databaseTables.get(i).getName().equals(viewName)) {
                    databaseTables.remove(i);
                    break;
                }
            }
            globalState.setSchema(databaseTables);
        } catch (Throwable t2) {
            throw new IgnoreMeException();
        }
    }

    @Override
    public void generateDatabase(GeneralGlobalState globalState) throws Exception {
        DatabaseEngineFactory<GeneralGlobalState> databaseEngineFactory = globalState.getDbmsSpecificOptions()
                .getDatabaseEngineFactory();
        for (int i = 0; i < Randomly.fromOptions(1, 2); i++) {
            boolean success;
            int nrTries = 0;
            do {
                SQLQueryAdapter qt = new GeneralTableGenerator().getQuery(globalState);
                // TODO add error handling here
                success = globalState.executeStatement(qt);
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
                    // check if the update table consumed
                    if (q.couldAffectSchema() && globalState.getUpdateTable() != null) {
                        throw new AssertionError();
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
