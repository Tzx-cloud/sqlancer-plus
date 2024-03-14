package sqlancer.general;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import sqlancer.ErrorHandler;
import sqlancer.IgnoreMeException;
import sqlancer.general.GeneralProvider.GeneralGlobalState;

public class GeneralErrorHandler implements ErrorHandler {

    private GeneratorInfoTable generatorTable;
    private GeneratorInfo generatorInfo;
    // private GeneratorScore<GeneratorNode> generatorScore;
    // private ArrayList<GeneratorInfo<String>> compositeGeneratorTable;
    // private GeneratorInfo<String> compositeGeneratorScore;
    // expression depth for each DATABASE --> it is thread unique parameter
    // TODO concurrent
    private class GeneratorInfo {
        private HashMap<GeneratorNode, Integer> generatorScore;
        private HashMap<String, Integer> compositeGeneratorScore;
        private boolean status;

        public GeneratorInfo() {
            this.generatorScore = new HashMap<>();
            this.compositeGeneratorScore = new HashMap<>();
            this.status = false;
        }

        public HashMap<GeneratorNode, Integer> getGeneratorScore() {
            return generatorScore;
        }

        public HashMap<String, Integer> getCompositeGeneratorScore() {
            return compositeGeneratorScore;
        }

        public boolean getStatus() {
            return status;
        }

        public void setStatus(boolean status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "GeneratorInfo [generatorScore=" + generatorScore + ", status=" + status + "]";
        }
    }
    
    private class GeneratorInfoTable{
        private ArrayList<GeneratorInfo> generatorTable;
        private HashMap<GeneratorNode, Double> generatorAverage;
        private HashMap<String, Double> compositeAverage;


        public GeneratorInfoTable() {
            this.generatorTable = new ArrayList<>();
        }

        public ArrayList<GeneratorInfo> getGeneratorTable() {
            return generatorTable;
        }

        public void add(GeneratorInfo generatorInfo) {
            generatorTable.add(generatorInfo);
        }

        public GeneratorInfo getLastGeneratorScore() {
            return generatorTable.get(generatorTable.size() - 1);
        }

        public HashMap<GeneratorNode, Double> calcAverageGeneratorScore() {
            generatorAverage = new HashMap<>();
            HashMap<GeneratorNode, Double> tmpAverage = new HashMap<>();
            HashMap<GeneratorNode, Integer> count = new HashMap<>();
            int entryNum = generatorTable.size();
            int success = 0;
            for (GeneratorInfo info : generatorTable) {
                HashMap<GeneratorNode, Integer> generator = info.getGeneratorScore();
                int executionStatus = info.getStatus() ? 1 : 0;
                success += executionStatus;

                // sum up all the successful generator options
                for (Map.Entry<GeneratorNode, Integer> entry : generator.entrySet()) {
                    GeneratorNode key = entry.getKey();
                    int value = entry.getValue();
                    if (tmpAverage.containsKey(key)) {
                        tmpAverage.put(key, tmpAverage.get(key) + value * executionStatus);
                        count.put(key, count.get(key) + 1);
                    } else {
                        tmpAverage.put(key, (double) (value * executionStatus));
                        count.put(key, 1);
                    }
                }
            }
            for (Map.Entry<GeneratorNode, Double> entry : tmpAverage.entrySet()) {
                int cnt = count.get(entry.getKey());
                // TODO: in case the option hasn't been tested enough
                generatorAverage.put(entry.getKey(), entry.getValue() / cnt);
            }
            System.out.println("Successful rate: " + (double) success / entryNum);
            System.out.println("Generator Average: " + generatorAverage);
            return generatorAverage;
        }

        public HashMap<String, Double> calcAverageCompositeScore() {
            compositeAverage = new HashMap<>();
            HashMap<String, Double> tmpAverage = new HashMap<>();
            HashMap<String, Integer> count = new HashMap<>();
            for (GeneratorInfo info : generatorTable) {
                HashMap<String, Integer> generator = info.getCompositeGeneratorScore();
                int executionStatus = info.getStatus() ? 1 : 0;

                // sum up all the successful generator options
                for (Map.Entry<String, Integer> entry : generator.entrySet()) {
                    String key = entry.getKey();
                    int value = entry.getValue();
                    if (tmpAverage.containsKey(key)) {
                        tmpAverage.put(key, tmpAverage.get(key) + value * executionStatus);
                        count.put(key, count.get(key) + 1);
                    } else {
                        tmpAverage.put(key, (double) (value * executionStatus));
                        count.put(key, 1);
                    }
                }
            }
            for (Map.Entry<String, Double> entry : tmpAverage.entrySet()) {
                compositeAverage.put(entry.getKey(), entry.getValue() / count.get(entry.getKey()));
            }
            // System.out.println("Composite Average: " + compositeAverage);
            // System.out.println(String.format("Count[%d]: ", count.size()) + count);
            return compositeAverage;
        }

        public HashMap<GeneratorNode, Double> getGeneratorAverage() {
            return generatorAverage;
        }

        public HashMap<String, Double> getCompositeAverage() {
            return compositeAverage;
        }

    }

    // volatile
    private static HashMap<String, Integer> curDepth = new HashMap<>();
    private static volatile HashMap<String, GeneratorInfo> assertionGeneratorHistory = new HashMap<>();
    private static volatile HashMap<GeneratorNode, Boolean> generatorOptions = new HashMap<>();
    private static volatile HashMap<String, Boolean> compositeGeneratorOptions = new HashMap<>();

    public enum GeneratorNode {
        // Meta nodes
        UNTYPE_EXPR,

        // Statement-level nodes
        CREATE_TABLE, CREATE_INDEX, INSERT, SELECT, UPDATE, DELETE, CREATE_VIEW, EXPLAIN, ANALYZE, VACUUM,
        CREATE_DATABASE,
        // Clause level nodes
        UNIQUE_INDEX, PRIMARY_KEY, COLUMN_NUM, COLUMN_INT, COLUMN_BOOLEAN, COLUMN_STRING, JOIN, INNER_JOIN, LEFT_JOIN,
        RIGHT_JOIN, NATURAL_JOIN, LEFT_NATURAL_JOIN, RIGHT_NATURAL_JOIN, FULL_NATURAL_JOIN,
        // Expression level nodes
        UNARY_POSTFIX, UNARY_PREFIX, BINARY_COMPARISON, BINARY_LOGICAL, BINARY_ARITHMETIC, CAST, FUNC, BETWEEN, CASE,
        IN, COLLATE, LIKE_ESCAPE, UNTYPE_FUNC, CAST_FUNC, CAST_COLON, IS_NULL, IS_NOT_NULL, IS_TRUE, IS_FALSE, IS_NOT_UNKNOWN,
        // Comparison Operator nodes
        EQUALS, GREATER, GREATER_EQUALS, SMALLER, SMALLER_EQUALS, NOT_EQUALS, NOT_EQUALS2, LIKE, NOT_LIKE, DISTINCT, NOT_DISTINCT,
        // Arithmetic Operator nodes
        OPADD, OPSUB, OPMULT, OPDIV, OPMOD, OPCONCAT, OPAND, OPOR, OPLSHIFT, OPRSHIFT,
        // Logical Operator nodes
        LOPAND, LOPOR;
    }

    public GeneralErrorHandler() {
        this.generatorTable = new GeneratorInfoTable();
        this.generatorInfo = new GeneratorInfo();
        if (generatorOptions.isEmpty()){
            initGeneratorOptions();
        }
    }

    public HashMap<GeneratorNode, Boolean> getGeneratorOptions() {
        return GeneralErrorHandler.generatorOptions;
    }

    public int getCurDepth(String databaseName) {
        if (curDepth.containsKey(databaseName)) {
            return curDepth.get(databaseName);
        } else {
            // We currently don't explicitly initiate the depth of the database
            return 1;
        }
    }

    public void incrementCurDepth(String databaseName) {
        if (curDepth.containsKey(databaseName)) {
            curDepth.put(databaseName, curDepth.get(databaseName) + 1);
        } else {
            // we initiate the depth of the database here.
            curDepth.put(databaseName, 2);
        }
    }

    private synchronized <N> void updateByLeastOnce(HashMap<N, Double> score, HashMap<N, Boolean> options) {
        for (Map.Entry<N, Double> entry : score.entrySet()) {
            if (options.containsKey(entry.getKey())) {
                if (options.get(entry.getKey())) {
                    // If true, then continue, don't make available function unavailable
                    continue;
                }
            }
            if (entry.getValue() > 0) {
                options.put(entry.getKey(), true);
            } else {
                options.put(entry.getKey(), false);
            }
        }

    }

    public void calcAverageScore() {
        generatorTable.calcAverageGeneratorScore();
        generatorTable.calcAverageCompositeScore();
    }

    public void updateGeneratorOptions() {
        HashMap<GeneratorNode, Double> average = generatorTable.getGeneratorAverage();
        HashMap<String, Double> compositeAverage = generatorTable.getCompositeAverage();

        // if not zero then the option is true
        updateByLeastOnce(average, generatorOptions);
        updateByLeastOnce(compositeAverage, compositeGeneratorOptions);

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
        HashMap<GeneratorNode, Integer> score = generatorInfo.getGeneratorScore();
        if (score.containsKey(generatorName)) {
            score.put(generatorName, score.get(generatorName) + 1);
        } else {
            score.put(generatorName, 1);
        }
    }

    public void addScore(String generatorName) {
        HashMap<String, Integer> score = generatorInfo.getCompositeGeneratorScore();
        if (score.containsKey(generatorName)) {
            score.put(generatorName, score.get(generatorName) + 1);
        } else {
            score.put(generatorName, 1);
        }
    }

    public void setScore(GeneratorNode generatorName, Integer score) {
        generatorInfo.getGeneratorScore().put(generatorName, score);
    }

    public void setScore(String generatorName, Integer score) {
        generatorInfo.getCompositeGeneratorScore().put(generatorName, score);
    }

    public void setExecutionStatus(boolean status) {
        generatorInfo.setStatus(status);
    }

    public GeneratorInfo getLastGeneratorScore() {
        return generatorTable.getLastGeneratorScore();
    }

    public void appendScoreToTable(boolean status) {
        setExecutionStatus(status);
        generatorTable.add(generatorInfo);
        generatorInfo = new GeneratorInfo();
    }

    public void appendHistory(String databaseName) {
        assertionGeneratorHistory.put(databaseName, getLastGeneratorScore());
    }

    public void printStatistics() {
        System.out.println("Generator Score: " + generatorInfo);
        // System.out.println("Generator Table: " + generatorTable);
        System.out.println("Generator Options: " + generatorOptions);
        System.out.println("Composite Generator Options: " + compositeGeneratorOptions);

        // get the average value for each key for all the hashmap in the
        // successGeneratorTable
        // HashMap<GeneratorNode, Double> average = getAverageScore(generatorTable);
        System.out.println("Total queries: " + generatorTable.getGeneratorTable().size());
        // System.out.println("Average: " + average);

        // HashMap<String, Double> compositeAverage = getAverageScore(compositeGeneratorTable);
        // System.out.println("Composite Average: " + compositeAverage);

        // Print the history failed generator options
        System.out.println("Assertion Generator History: " + assertionGeneratorHistory);
    }

    public boolean checkIfDuplicate() {
        // iterate assertionGeneratorHistory values
        boolean duplicate = false;

        boolean isError = !getLastGeneratorScore().getStatus();
        Set<GeneratorNode> nodes = new HashSet<>(getLastGeneratorScore().getGeneratorScore().keySet());
        ArrayList<GeneratorInfo> history = new ArrayList<>(assertionGeneratorHistory.values());

        // remove meta nodes
        nodes.remove(GeneratorNode.UNTYPE_EXPR);
        System.out.println("Nodes: " + nodes);
        System.out.println("History: " + history);

        for (GeneratorInfo generator : history) {
            if (isError != (!generator.getStatus())) {
                continue;
            }
            // change the generator to set
            Set<GeneratorNode> generatorNodes = new HashSet<>(generator.getGeneratorScore().keySet());
            generatorNodes.remove(GeneratorNode.UNTYPE_EXPR);
            if (generatorNodes.size() == 0) {
                if (nodes.size() == 0) {
                    duplicate = true;
                    System.out.println("Duplicate found");
                    break;
                } else {
                    continue;
                }
            }
            if (nodes.containsAll(generatorNodes)) {
                duplicate = true;
                System.out.println("Duplicate found");
                if (isError) {
                    System.out.println("Skip the rest of the current test");
                    throw new IgnoreMeException();
                }
                break;
            }
        }

        return duplicate;
    }

    public void saveStatistics(GeneralGlobalState globalState) {
        // TODO It is a quite ugly function
        try (FileWriter file = new FileWriter(
                "logs/" + globalState.getDbmsSpecificOptions().getDatabaseEngineFactory().toString() + "Options.txt")) {
            for (Map.Entry<GeneratorNode, Boolean> entry : generatorOptions.entrySet()) {
                file.write(entry.getKey() + " : " + entry.getValue() + "\n");
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }

        // write each generator score to a file
        // file: logs/general/generator/database*.txt
        File historyFileDir = new File("logs/general/generator");
        if (!historyFileDir.exists()) {
            historyFileDir.mkdirs();
        }
        for (Map.Entry<String, GeneratorInfo> entry : assertionGeneratorHistory.entrySet()) {
            String databaseName = entry.getKey();
            HashMap<GeneratorNode, Integer> generatorScore = entry.getValue().getGeneratorScore();
            try (FileWriter file = new FileWriter("logs/general/generator/" + databaseName + "Options.txt")) {
                for (Map.Entry<GeneratorNode, Integer> generator : generatorScore.entrySet()) {
                    file.write(generator.getKey() + " : " + generator.getValue() + "\n");
                }
            } catch (Exception e) {

            }
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
        Boolean value = generatorOptions.get(option);
        if (value != null) {
            return value;
        } else {
            return true;
        }
    }

    public void setCompositeOption(String option, boolean value) {
        compositeGeneratorOptions.put(option, value);
    }

    public boolean getCompositeOption(String option) {
        if (compositeGeneratorOptions.containsKey(option)) {
            return compositeGeneratorOptions.get(option);
        } else {
            return true;
        }
    }

    public boolean getCompositeOption(String option1, String option2) {
        String option = option1 + "_" + option2;
        return getCompositeOption(option);
    }
}
