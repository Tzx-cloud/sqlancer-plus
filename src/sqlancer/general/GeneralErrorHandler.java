package sqlancer.general;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import sqlancer.ErrorHandler;
import sqlancer.general.GeneralProvider.GeneralGlobalState;

public class GeneralErrorHandler implements ErrorHandler {

    private ArrayList<HashMap<GeneratorNode, Integer>> generatorTable;
    private HashMap<GeneratorNode, Integer> generatorScore;
    private static HashMap<GeneratorNode, Boolean> generatorOptions = new HashMap<>();

    public enum GeneratorNode {
        // Meta nodes
        EXECUTION_STATUS,

        // Statement-level nodes
        CREATE_TABLE, CREATE_INDEX, INSERT, SELECT, UPDATE, DELETE, CREATE_VIEW, EXPLAIN, ANALYZE, VACUUM,
        CREATE_DATABASE,
        // Clause level nodes
        UNIQUE_INDEX, PRIMARY_KEY, COLUMN_NUM, COLUMN_INT,
        // Expression level nodes
        UNARY_POSTFIX, UNARY_PREFIX, BINARY_COMPARISON, BINARY_LOGICAL, BINARY_ARITHMETIC, CAST, FUNC, BETWEEN, CASE,
        IN, COLLATE, LIKE_ESCAPE,
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
        OPADD, OPSUB, OPMULT, OPDIV, OPMOD, OPCONCAT,;
    }

    public GeneralErrorHandler() {
        this.generatorTable = new ArrayList<>();
        this.generatorScore = new HashMap<>();
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

    public void updateGeneratorOptions() {
        HashMap<GeneratorNode, Double> average = getAverageScore();

        // if not zero then the option is true
        for (Map.Entry<GeneratorNode, Double> entry : average.entrySet()) {
            if (entry.getValue() > 0) {
                generatorOptions.put(entry.getKey(), true);
            } else {
                generatorOptions.put(entry.getKey(), false);
            }
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

    public void appendScoreToTable(boolean status) {
        setExecutionStatus(status);
        generatorTable.add(generatorScore);
        generatorScore = new HashMap<>();
    }

    public void summaryTable() {
        HashMap<GeneratorNode, Integer> summary = new HashMap<>();
        for (HashMap<GeneratorNode, Integer> generator : generatorTable) {
            for (Map.Entry<GeneratorNode, Integer> entry : generator.entrySet()) {
                if (summary.containsKey(entry.getKey())) {
                    summary.put(entry.getKey(), summary.get(entry.getKey()) + entry.getValue());
                } else {
                    summary.put(entry.getKey(), entry.getValue());
                }
            }
        }
        System.out.println(summary);
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

    public boolean getOption(GeneratorNode option) {
        if (generatorOptions.containsKey(option)) {
            return generatorOptions.get(option);
        } else {
            return true;
        }
    }

}
