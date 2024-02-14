package sqlancer.general.ast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;


public class GeneralFunction{
    private int nrArgs;
    private boolean isVariadic;
    private String name;
    // String: function name
    // Integer: number of arguments, if negative then variadic
    private static HashMap<String, Integer> functions = initFunctions();

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
        // read all functions from file.txt and put them into hashmap functions
        try {
            // Make it hardcode for now
            // not sure if need to switch to ALL FUNCTIONS
            BufferedReader reader = new BufferedReader(new FileReader("logs/ExternalFunctions.txt"));
            String line = reader.readLine();
            while (line != null) {
                String[] parts = line.split(" ");
                initFunctions.put(parts[0].toUpperCase(), Integer.parseInt(parts[1]));
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
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
            node = "FUNCTION" + "_" + funcName;
            op = new GeneralFunction(funcArgs, funcName);
        } while (!handler.getCompositeOption(node) || !Randomly.getBooleanWithSmallProbability());
        handler.addScore(node);
        return op;
    }

    public static List<GeneralFunction> getRandomCompatibleFunctions(GeneralErrorHandler handler, GeneralCompositeDataType returnType) {
        List<String> funcNames = functions.keySet().stream()
                .filter(f -> handler.getCompositeOption("FUNCTION" + "_" + f))
                .filter(f -> handler.getCompositeOption(f, returnType.getPrimitiveDataType().toString())).collect(Collectors.toList());
        
        return funcNames.stream().map(f -> new GeneralFunction(functions.get(f), f)).collect(Collectors.toList());
    }
}
