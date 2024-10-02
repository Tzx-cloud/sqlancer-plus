package sqlancer.general.ast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;
import sqlancer.general.learner.GeneralFragments;
import sqlancer.general.learner.GeneralStringBuilder;
import sqlancer.general.learner.GeneralFragments.GeneralFragmentChoice;

public class GeneralFunction {
    private static final String CONFIG_NAME = "functions.txt";

    private int nrArgs;
    private boolean isVariadic;
    private String name;
    // String: function name
    // Integer: number of arguments, if negative then variadic
    private static HashMap<String, Integer> functions = initFunctions();
    private static GeneralFunctionFragments fragments = new GeneralFunctionFragments();

    private final static class GeneralFunctionFragments extends GeneralFragments {
        public GeneralFunctionFragments() {
            super();
        }

        @Override
        public synchronized String genLearnStatement(GeneralGlobalState globalState) {
            setLearn(true);
            String stmt = getQuery(globalState).getQueryString();
            return stmt;
        }

        @Override
        public String getConfigName() {
            return CONFIG_NAME;
        }

        public String getStatementType() {
            return "FUNCTION";
        }

        @Override
        protected String getVariables() {
            return "";
        }

        protected String getExamples() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                final int ind = i;
                String funcName = Randomly.fromList(
                        functions.keySet().stream().filter(f -> functions.get(f) == ind).collect(Collectors.toList()));
                sb.append(String.format("%d,%s\n", i, funcName));
            }
            sb.append(
                    "Note: DO NOT include functions that may generate data according to arguments. For example, REPEAT, LPAD, RPAD, etc.\n");
            return sb.toString();
        }

        @Override
        public void updateFragmentsFromLearner(GeneralGlobalState globalState) {
            super.updateFragmentsFromLearner(globalState);
            loadFunctionsFromFragments(globalState);
        }

    }

    public static int getNrFunctionsNum() {
        return functions.size();
    }

    public GeneralFunction(int nrArgs, boolean isVariadic, String name) {
        this.nrArgs = nrArgs;
        this.isVariadic = isVariadic;
        this.name = name;
    }

    GeneralFunction(int nrArgs, String name) {
        this(Math.abs(nrArgs), nrArgs < 0, name);
    }

    public String toString() {
        return name;
    }

    public int getNrArgs() {
        if (isVariadic) {
            return Randomly.smallNumber() + nrArgs;
        } else {
            return nrArgs;
        }
    }

    public static SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        GeneralStringBuilder<GeneralFunctionFragments> sb = new GeneralStringBuilder<GeneralFunctionFragments>(
                globalState, fragments);
        // loop 1 to 4
        for (int i = 0; i < 4; i++) {
            sb.append("SELECT ", i);
            // string of nulls with length of i
            sb.append("(");
            for (int j = 0; j < i; j++) {
                sb.append("NULL");
                if (j != i - 1) {
                    sb.append(", ");
                }
            }
            sb.append(");");
            sb.append(String.format(" -- Hint: Function with %d arguments\n", i));
        }
        return new SQLQueryAdapter(sb.toString());
    }

    private static HashMap<String, Integer> initFunctions() {
        // put all functions from GeneralDBFunction into hashmap functions
        HashMap<String, Integer> initFunctions = new HashMap<>();
        for (GeneralDBFunction func : GeneralDBFunction.values()) {
            initFunctions.put(func.toString(), func.getVarArgs());
        }
        return initFunctions;
    }

    public static List<String> getFuncNames() {
        // return all the keys in functions
        return List.copyOf(functions.keySet());
    }

    public static HashMap<String, Integer> getFunctions() {
        return functions;
    }

    public static GeneralFunction getRandomByOptions(GeneralErrorHandler handler) {
        GeneralFunction op;
        String node;
        if (functions.size() <= 0) {
            return null;
        }
        do {
            // select a random string-integer pair from hashmap functions
            String funcName = Randomly.fromList(List.copyOf(functions.keySet()));
            int funcArgs = functions.get(funcName);
            node = "FUNCTION" + "-" + funcName;
            op = new GeneralFunction(funcArgs, funcName);
        } while (!handler.getCompositeOption(node) || !Randomly.getBooleanWithSmallProbability());
        handler.addScore(node);
        return op;
    }

    public static List<GeneralFunction> getRandomCompatibleFunctions(GeneralErrorHandler handler,
            GeneralCompositeDataType returnType) {
        List<String> funcNames = functions.keySet().stream()
                .filter(f -> handler.getCompositeOption("FUNCTION", f))
                .filter(f -> handler.getCompositeOption(returnType.toString(), f)).collect(Collectors.toList());

        return funcNames.stream().map(f -> new GeneralFunction(functions.get(f), f)).collect(Collectors.toList());
    }

    public static void loadFunctionsFromFile(GeneralGlobalState globalState) {
        if (globalState.getOptions().debugLogs()) {
            System.out.println("Loading external functions from " + globalState.getConfigDirectory() + " ...");
        }
        File configFile = new File(globalState.getConfigDirectory(), CONFIG_NAME);
        HashMap<String, Integer> newFuncs = new HashMap<>();
        if (configFile.exists()) {
            String line = "";
            try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
                while ((line = br.readLine()) != null) {
                    String[] args = line.split(" ");
                    newFuncs.put(args[0].toUpperCase(), Integer.parseInt(args[1]));
                }
            } catch (Exception e) {
                System.out.println(line);
                throw new AssertionError(e);
            }
            mergeFunctions(newFuncs);
        } else {
            if (globalState.getOptions().debugLogs()) {
                System.out.println("WARNING: No external function file found");
            }
        }
    }

    public static void loadFunctionsFromFragments(GeneralGlobalState globalState) {
        HashMap<String, Integer> newFuncs = new HashMap<>();
        for (String fragment : fragments.getFragments().keySet()) {
            List<GeneralFragmentChoice> choices = fragments.getFragments().get(fragment);
            for (GeneralFragmentChoice choice : choices) {
                try {
                    newFuncs.put(choice.toString(globalState), Integer.parseInt(fragment));
                } catch (NumberFormatException e) {
                    System.out.println("Error: " + fragment);
                    throw new AssertionError(e);
                }
            }
        }
        mergeFunctions(newFuncs);
    }

    public static void mergeFunctions(HashMap<String, Integer> newFunctions) {
        functions.putAll(newFunctions);
    }

    public static GeneralFragments getFragments() {
        return fragments;
    }

}
