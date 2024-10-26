package sqlancer.general.gen;


import java.util.List;

import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.learner.GeneralFragments;
import sqlancer.general.learner.GeneralStringBuilder;

public class GeneralStatementGenerator {
    
    private static GeneralStatementFragments fragments = new GeneralStatementFragments();
    private static final String CONFIG_NAME = "dmlgenerator.txt";
    private static final String STATEMENT = "DML_COMMAND";

    private final static class GeneralStatementFragments extends GeneralFragments {
        public GeneralStatementFragments() {
            super();
        }

        @Override
        public synchronized String genLearnStatement(GeneralGlobalState globalState) {
            globalState.updateSchema();
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

        @Override
        public String getStatementType() {
            return STATEMENT;
        }

        @Override
        protected String getExamples() {
            StringBuilder sb = new StringBuilder();
            sb.append("0,ANALYZE\n");
            sb.append("1,VACUUM <RANDOM_TABLE>\n");
            sb.append("Note: DO NOT include SQL commands that may create files in OS.\n");
            return sb.toString();
        }

        @Override
        protected void validateFragment(String fmtString, List<GeneralFragmentVariable> vars) {
            if (fmtString.contains("CREATE") || fmtString.contains("DROP") || fmtString.contains("ALTER")) {
                throw new IllegalArgumentException("Should not contain DDL commands. Invalid command: " + fmtString);
            }
            super.validateFragment(fmtString, vars);
        }

    }

    public static SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        GeneralErrors.addExpressionErrors(errors);
        GeneralStringBuilder<GeneralStatementFragments> sb = new GeneralStringBuilder<>(globalState, fragments);

        if (fragments.getLearn()) {
            sb.append("", 0);
            sb.append("; -- Hint: SQL command with concrete string representation\n");
            sb.append("", 1);
            sb.append("; -- Hint: SQL command operating on a random table (include variable <RANDOM_TABLE>)\n");
            sb.append("", 2);
            sb.append("; -- Hint: SQL command with a random expression (include variable <RANDOM_EXPRESSION>)\n");
        } else {
            int option = globalState.getRandomly().getInteger(0, 2);
            sb.append("", option);
            sb.append(";");
        }
        SQLQueryAdapter q = new SQLQueryAdapter(sb.toString(), errors, false, false);
        return q;
    }


    public static GeneralFragments getFragments() {
        return fragments;
    }

}
