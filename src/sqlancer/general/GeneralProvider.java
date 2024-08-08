package sqlancer.general;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
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
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.Query;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.ast.GeneralFunction;
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
        ANALYZE((g) -> {
            ExpectedErrors errors = new ExpectedErrors();
            GeneralErrors.addExpressionErrors(errors);
            g.handler.addScore(GeneratorNode.ANALYZE);
            return new SQLQueryAdapter("ANALYZE;", errors);
        }), //
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

        public static Action[] getAvailableActions(GeneralErrorHandler handler) {
            // return all the actions that is true in the generator options
            return Arrays.stream(values()).filter(a -> handler.getOption(GeneratorNode.valueOf(a.toString())))
                    .toArray(Action[]::new);
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
            return r.getInteger(1, globalState.getDbmsSpecificOptions().maxNumUpdates + 1);
        // case VACUUM: // seems to be ignored
        case ANALYZE: // seems to be ignored
        return r.getInteger(0, 2);
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

        private static final File CONFIG_DIRECTORY = new File("dbconfigs");

        @Override
        public GeneralSchema getSchema() {
            // TODO should we also check here if the saved schema match the jdbc schema?
            return schema;
        }

        public GeneralErrorHandler getHandler() {
            return handler;
        }

        public String getProviderName() {
            return getDbmsSpecificOptions().getDatabaseEngineFactory().toString();
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
        public void updateSchema(){
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
            if (logExecutionTime && success) {
                getLogger().writeCurrent(" -- " + timer.end().asString());
            }
            if (q.couldAffectSchema() && success) {
                updateSchema();
            }
        }

        @Override
        protected GeneralSchema readSchema() throws SQLException {
            //TODO refactor things here
            return schema;
        }

        // Override execute statement
        @Override
        public boolean executeStatement(Query<SQLConnection> q, String... fills) throws Exception {
            boolean success = false;
            try {
                success = super.executeStatement(q, fills);
            } catch (Exception e) {
                handler.appendScoreToTable(false, false);
                getLogger().writeCurrent(" -- " + e.getMessage());
                throw e;
            }
            // I guess we want to make sure if the syntax is correct
            handler.appendScoreToTable(success, false);
            return success;
        }

        @Override
        public void updateHandler(boolean status) {
            // status means whether the execution is stopped by bug or not
            String databaseName = getDatabaseName();
            if (getDbmsSpecificOptions().enableLearning && status && Randomly.getBooleanWithSmallProbability()) {
                GeneralTableGenerator.getFragments().updateFragmentsFromLearner(this);
                GeneralIndexGenerator.getFragments().updateFragmentsFromLearner(this);
            }
            if (getDbmsSpecificOptions().enableErrorHandling) {
                handler.incrementExecDatabaseNum();
                if (!status) {
                    // print the last item of handler.
                    System.out.println(databaseName);
                    System.out.println(handler.getLastGeneratorScore());
                    handler.appendHistory(databaseName);
                } else {
                    handler.calcAverageScore();
                    if (getDbmsSpecificOptions().enableFeedback) {
                        handler.updateGeneratorOptions();
                    }
                    if (getDbmsSpecificOptions().untypeExpr) {
                        handler.setOption(GeneratorNode.UNTYPE_EXPR, true);
                    }
                }
                if (getOptions().debugLogs()) {
                    handler.printStatistics();
                }
                handler.saveStatistics(this);
                if (handler.getCurDepth(databaseName) < getOptions().getMaxExpressionDepth()) {
                    handler.incrementCurDepth(databaseName);
                }
                if (getOptions().debugLogs()) {
                    System.out.println(databaseName + "Current depth: " + handler.getCurDepth(databaseName));
                }
            }
        }

        @Override
        public boolean checkIfDuplicate() {
            if (!getDbmsSpecificOptions().useDeduplicator) {
                return false;
            }
            return handler.checkIfDuplicate();
        }

        public File getConfigDirectory() {
            return new File(CONFIG_DIRECTORY, getDbmsSpecificOptions().getDatabaseEngineFactory().toString().toLowerCase());
        }

    }

    // TODO: we might need another method to check if there's any data in the table
    @Override
    protected void checkViewsAreValid(GeneralGlobalState globalState) {
        List<GeneralTable> views = globalState.getSchema().getViews();
        for (GeneralTable view : views) {
            SQLQueryAdapter q = new SQLQueryAdapter("SELECT * FROM " + view.getName());
            try {
                if (!q.execute(globalState)) {
                    dropView(globalState, view.getName());
                }
            } catch (Throwable t) {
                dropView(globalState, view.getName());
            }
        }
        String sb = "SELECT COUNT(*) FROM ";
        List<GeneralTable> databaseTables = globalState.getSchema().getDatabaseTables();
        // Select all the tables using cross join
        for (int i = 0; i < databaseTables.size(); i++) {
            sb += databaseTables.get(i).getName();
            if (i != databaseTables.size() - 1) {
                sb += ", ";
            }
        }
        // check if query result is larger than 1000 rows
        SQLQueryAdapter q2 = new SQLQueryAdapter(sb);
        SQLancerResultSet resultSet;
        try {
            resultSet = q2.executeAndGet(globalState);
            globalState.getLogger().writeCurrent(sb);
            // check if the result is larger than 100K
            while (resultSet.next()) {
                // System.out.println("Join table size: " + resultSet.getLong(1));
                if (resultSet.getLong(1) > 10000) {
                    // drop all the views
                    globalState.getLogger().writeCurrent("-- size:" + resultSet.getLong(1));
                    System.out.println("Join table size exceeds 10000, dropping all views");
                    for (GeneralTable view : views) {
                        dropView(globalState, view.getName());
                    }
                }
            } 
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
        if (globalState.getOptions().debugLogs()){
            globalState.getSchema().printTables();
        }
    }

    private void dropView(GeneralGlobalState globalState, String viewName) {
        try {
            globalState.getLogger().writeCurrent("DROP VIEW " + viewName);
            globalState.executeStatement(new SQLQueryAdapter("DROP VIEW " + viewName, true));
        } catch (Throwable t2) {
            globalState.getLogger().writeCurrent(" -- " + t2.getMessage());
        } finally {
            List<GeneralTable> databaseTables = new ArrayList<>(globalState.getSchema().getDatabaseTables());
            for (int i = 0; i < databaseTables.size(); i++) {
                if (databaseTables.get(i).getName().equals(viewName)) {
                    databaseTables.remove(i);
                    break;
                }
            }
            globalState.setSchema(databaseTables);
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
                SQLQueryAdapter qt = GeneralTableGenerator.getQuery(globalState);
                // TODO add error handling here
                success = globalState.executeStatement(qt);
            } while (!success && nrTries++ < 200);
        }
        if (globalState.getSchema().getDatabaseTables().isEmpty()) {
            throw new AssertionError("Failed to create any table"); // TODO
        }
        StatementExecutor<GeneralGlobalState, Action> se = new StatementExecutor<>(globalState, Action.getAvailableActions(globalState.getHandler()),
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

    @Override
    public void initializeFeatures(GeneralGlobalState globalState) {
        // do nothing
        GeneralFunction.loadFunctionsFromFile(globalState);
        GeneralSchema.getFragments().updateFragmentsFromLearner(globalState);
        // GeneralTableGenerator.getFragments().genLearnStatement(globalState);
        // GeneralTableGenerator.getFragments().loadFragmentsFromFile(globalState);

        // GeneralIndexGenerator.getFragments().genLearnStatement(globalState);
        // GeneralIndexGenerator.getFragments().loadFragmentsFromFile(globalState);

        // GeneralTableGenerator.getFragments().updateFragmentsFromLearner(globalState);
        // GeneralIndexGenerator.getFragments().updateFragmentsFromLearner(globalState);
        // GeneralTableGenerator.getTemplateQuery(globalState);
        // GeneralTableGenerator.initializeFragments(globalState);

    }

}
