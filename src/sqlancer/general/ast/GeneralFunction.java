package sqlancer.general.ast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;


public class GeneralFunction {
    private static final String CONFIG_NAME = "functions.txt";

    private int nrArgs;
    private boolean isVariadic;
    private String name;
    // String: function name
    // Integer: number of arguments, if negative then variadic
    private static HashMap<String, Integer> functions = initFunctions();

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

    private static HashMap<String, Integer> initFunctions() {
        // put all functions from GeneralDBFunction into hashmap functions
        HashMap<String, Integer> initFunctions = new HashMap<>();
        for (GeneralDBFunction func : GeneralDBFunction.values()) {
            initFunctions.put(func.toString(), func.getVarArgs());
        }
        return initFunctions;
    }

    public List<String> getFuncNames() {
        // return all the keys in functions
        return List.copyOf(functions.keySet());
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

    public static List<GeneralFunction> getRandomCompatibleFunctions(GeneralErrorHandler handler, GeneralCompositeDataType returnType) {
        List<String> funcNames = functions.keySet().stream()
                // .filter(f -> handler.getCompositeOption("FUNCTION" + "-" + f))
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
    
    public static void mergeFunctions(HashMap<String, Integer> newFunctions) {
        functions.putAll(newFunctions);
    }

}
