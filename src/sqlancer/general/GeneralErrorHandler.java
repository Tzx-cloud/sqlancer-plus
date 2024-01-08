package sqlancer.general;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.FileReader;

import sqlancer.ErrorHandler;
import sqlancer.general.GeneralProvider.GeneralGlobalState;

public class GeneralErrorHandler implements ErrorHandler {

    private ArrayList<HashMap<GeneratorNode, Integer>> generatorTable;
    private HashMap<GeneratorNode, Integer> generatorScore;
    private int curDepth = 1; // Control the expression depth, initial small for duduplication
    private static HashMap<GeneratorNode, Boolean> generatorOptions = new HashMap<>();
    private static HashMap<String, Boolean> generatorCompositeOptions = new HashMap<>();

    public enum GeneratorNode {
        // Meta nodes
        EXECUTION_STATUS, UNTYPE_EXPR,

        // Statement-level nodes
        CREATE_TABLE, CREATE_INDEX, INSERT, SELECT, UPDATE, DELETE, CREATE_VIEW, EXPLAIN, ANALYZE, VACUUM,
        CREATE_DATABASE,
        // Clause level nodes
        UNIQUE_INDEX, PRIMARY_KEY, COLUMN_NUM, COLUMN_INT, COLUMN_BOOLEAN, COLUMN_STRING, JOIN, INNER_JOIN, LEFT_JOIN, RIGHT_JOIN, NATURAL_JOIN, LEFT_NATURAL_JOIN, RIGHT_NATURAL_JOIN, FULL_NATURAL_JOIN,
        // Expression level nodes
        UNARY_POSTFIX, UNARY_PREFIX, BINARY_COMPARISON, BINARY_LOGICAL, BINARY_ARITHMETIC, CAST, FUNC, BETWEEN, CASE,
        IN, COLLATE, LIKE_ESCAPE, UNTYPE_FUNC,
        CAST_FUNC, CAST_COLON, IS_NULL, IS_NOT_NULL,
        // Function level nodes
        ACOS, ASIN, ATAN, COS, SIN, TAN, COT, ATAN2, ABS, CEIL, CEILING, FLOOR, LOG, LOG10, LOG2, LN, PI, SQRT, POWER,
        CBRT, ROUND, SIGN, DEGREES, RADIANS, MOD, XOR, // math functions
        LENGTH, LOWER, UPPER, SUBSTRING, REVERSE, CONCAT, CONCAT_WS, CONTAINS, PREFIX, SUFFIX, INSTR, PRINTF,
        REGEXP_MATCHES, REGEXP_REPLACE, STRIP_ACCENTS, // string functions
        DATE_PART, AGE, // date functions
        COALESCE, NULLIF, LTRIM, RTRIM, REPLACE, UNICODE, BIT_COUNT, BIT_LENGTH, LAST_DAY, MONTHNAME, DAYNAME, YEARWEEK,
        DAYOFMONTH, WEEKDAY, WEEKOFYEAR, IFNULL, IF,
        // Comparison Operator nodes
        EQUALS, GREATER, GREATER_EQUALS, SMALLER, SMALLER_EQUALS, NOT_EQUALS,
        // Arithmetic Operator nodes
        OPADD, OPSUB, OPMULT, OPDIV, OPMOD, OPCONCAT, OPAND, OPOR,;
    }

    public GeneralErrorHandler() {
        this.generatorTable = new ArrayList<>();
        this.generatorScore = new HashMap<>();
        if (generatorOptions.isEmpty()){
            initGeneratorOptions();
        }
    }

    public ArrayList<HashMap<GeneratorNode, Integer>> getGeneratorTable() {
        return this.generatorTable;
    }

    public HashMap<GeneratorNode, Integer> getGeneratorScore() {
        return this.generatorScore;
    }

    public HashMap<GeneratorNode, Boolean> getGeneratorOptions() {
        return GeneralErrorHandler.generatorOptions;
    }

    public int getCurDepth() {
        return this.curDepth;
    }

    public void incrementCurDepth() {
        this.curDepth++;
    }

    public void updateGeneratorOptions() {
        HashMap<GeneratorNode, Double> average = getAverageScore();

        // if not zero then the option is true
        for (Map.Entry<GeneratorNode, Double> entry : average.entrySet()) {
            if (generatorOptions.containsKey(entry.getKey())) {
                if (generatorOptions.get(entry.getKey())) {
                    // If true, then continue, don't make available function unavailable
                    continue;
                } 
            } 
            if (entry.getValue() > 0) {
                generatorOptions.put(entry.getKey(), true);
            } else {
                generatorOptions.put(entry.getKey(), false);
            }
        }

        // Special handling for the untype_expr option
        if (generatorOptions.get(GeneratorNode.UNTYPE_EXPR)) {
            // TODO make it super parameter
            generatorOptions.put(GeneratorNode.UNTYPE_EXPR, average.get(GeneratorNode.UNTYPE_EXPR) > 0.5);
        }
    }

    public void initGeneratorOptions() {
        // First try typed expression, if some of the untyped ok then untyped
        setOptionIfNonExist(GeneratorNode.UNTYPE_EXPR, false);

        // Read file disabled_options.txt line by line and set the option to false
        // if the option is not in the file then it is true
        String fileName = "logs/disabled_options.txt";
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                String option = line;
                try {
                    GeneratorNode generatorNode = GeneratorNode.valueOf(option);
                    setOptionIfNonExist(generatorNode, false);
                } catch (IllegalArgumentException e) {
                    System.out.println("Option " + option + " not found");
                }
            }
        } catch (Exception e) {
            System.out.println("Error reading file: " + fileName);
            System.out.println(e.getMessage());
        }
    }

    public void addScore(GeneratorNode generatorName) {
        if (generatorScore.containsKey(generatorName)) {
            generatorScore.put(generatorName, generatorScore.get(generatorName) + 1);
        } else {
            generatorScore.put(generatorName, 1);
        }
    }

    public void setScore(GeneratorNode generatorName, Integer score) {
        generatorScore.put(generatorName, score);
    }

    public void setExecutionStatus(boolean status) {
        generatorScore.put(GeneratorNode.EXECUTION_STATUS, status ? 1 : 0);
    }

    public void appendScoreToTable() {
        generatorTable.add(generatorScore);
        generatorScore = new HashMap<>();
    }

    public HashMap<GeneratorNode, Integer> getLastGeneratorScore() {
        return generatorTable.get(generatorTable.size() - 1);
    }

    public void appendScoreToTable(boolean status) {
        setExecutionStatus(status);
        generatorTable.add(generatorScore);
        generatorScore = new HashMap<>();
    }

    HashMap<GeneratorNode, Double> getAverageScore() {
        HashMap<GeneratorNode, Double> average = new HashMap<>();
        HashMap<GeneratorNode, Integer> count = new HashMap<>();
        for (HashMap<GeneratorNode, Integer> generator : generatorTable) {
            int executionStatus = 0;
            if (generator.containsKey(GeneratorNode.EXECUTION_STATUS)) {
                executionStatus = generator.get(GeneratorNode.EXECUTION_STATUS);
            } else {
                throw new AssertionError("No execution status found");
            }

            // sum up all the successful generator options
            for (Map.Entry<GeneratorNode, Integer> entry : generator.entrySet()) {
                GeneratorNode key = entry.getKey();
                int value = entry.getValue();
                if (average.containsKey(key)) {
                    average.put(key, average.get(key) + value * executionStatus);
                    count.put(key, count.get(key) + 1);
                } else {
                    average.put(key, (double) (value * executionStatus));
                    count.put(key, 1);
                }
            }
        }
        for (Map.Entry<GeneratorNode, Double> entry : average.entrySet()) {
            average.put(entry.getKey(), entry.getValue() / count.get(entry.getKey()));
        }
        return average;
    }

    public void printStatistics() {
        System.out.println("Generator Score: " + generatorScore);
        // System.out.println("Generator Table: " + generatorTable);
        System.out.println("Generator Options: " + generatorOptions);

        // get the average value for each key for all the hashmap in the
        // successGeneratorTable
        HashMap<GeneratorNode, Double> average = getAverageScore();
        System.out.println("Total queries: " + generatorTable.size());
        System.out.println("Average: " + average);
    }

    public void saveStatistics(GeneralGlobalState globalState) {
        // TODO Auto-generated method stub
        try (FileWriter file = new FileWriter(
                "logs/" + globalState.getDbmsSpecificOptions().getDatabaseEngineFactory().toString() + "Options.txt")) {
            for (Map.Entry<GeneratorNode, Boolean> entry : generatorOptions.entrySet()) {
                file.write(entry.getKey() + " : " + entry.getValue() + "\n");
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
    }

    public void setOption(GeneratorNode option, boolean value) {
        generatorOptions.put(option, value);
    }

    public void setOptionIfNonExist(GeneratorNode option, boolean value) {
        if (!generatorOptions.containsKey(option)) {
            setOption(option, value);
        }
    }

    public boolean getOption(GeneratorNode option) {
        if (generatorOptions.containsKey(option)) {
            return generatorOptions.get(option);
        } else {
            return true;
        }
    }

    public void setCompositeOption(String option, boolean value) {
        generatorCompositeOptions.put(option, value);
    }

    public boolean getCompositeOption(String option) {
        if (generatorCompositeOptions.containsKey(option)) {
            return generatorCompositeOptions.get(option);
        } else {
            return true;
        }
    }

    public boolean getCompositeOption(String option1, String option2) {
        String option = option1 + option2;
        return getCompositeOption(option);
    }
}
