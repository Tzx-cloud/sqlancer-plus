package sqlancer.general;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import sqlancer.ErrorHandler;
import sqlancer.IgnoreMeException;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;
import sqlancer.general.ast.GeneralBinaryArithmeticOperator;
import sqlancer.general.ast.GeneralBinaryComparisonOperator;
import sqlancer.general.ast.GeneralCast;
import sqlancer.general.ast.GeneralFunction;
import sqlancer.general.ast.GeneralUnaryPostfixOperator;
import sqlancer.general.ast.GeneralUnaryPrefixOperator;
import sqlancer.general.gen.GeneralIndexGenerator;
import sqlancer.general.gen.GeneralTableGenerator;
import sqlancer.general.learner.GeneralFragments.GeneralFragmentChoice;

public class GeneralErrorHandler implements ErrorHandler {

    private GeneratorInfoTable generatorTable;
    private GeneratorInfo generatorInfo;

    // expression depth for each DATABASE --> it is thread unique parameter
    // TODO concurrent
    public class GeneratorInfo {
        private HashMap<GeneratorNode, Integer> generatorScore;
        private HashMap<String, Integer> compositeGeneratorScore;
        private HashMap<GeneralFragmentChoice, Integer> fragmentScore;
        private boolean status;
        private boolean isQuery;

        public GeneratorInfo() {
            this.generatorScore = new HashMap<>();
            this.compositeGeneratorScore = new HashMap<>();
            this.fragmentScore = new HashMap<>();
            this.status = false;
            this.isQuery = false;
        }

        public HashMap<GeneratorNode, Integer> getGeneratorScore() {
            return generatorScore;
        }

        public HashMap<String, Integer> getCompositeGeneratorScore() {
            return compositeGeneratorScore;
        }

        public HashMap<GeneralFragmentChoice, Integer> getFragmentScore() {
            return fragmentScore;
        }

        public <N> void countSuccess(HashMap<N, Integer> success, HashMap<N, Integer> count,
                HashMap<N, Integer> score) {
            // HashMap<N, Integer> generator = supplier.get();
            int executionStatus = status ? 1 : 0;

            // sum up all the successful generator options
            for (Map.Entry<N, Integer> entry : score.entrySet()) {
                N key = entry.getKey();
                int value = entry.getValue();
                if (success.containsKey(key)) {
                    success.put(key, success.get(key) + value * executionStatus);
                    count.put(key, count.get(key) + 1);
                } else {
                    success.put(key, value * executionStatus);
                    count.put(key, 1);
                }
            }
        }

        public boolean getStatus() {
            return status;
        }

        public boolean isQuery() {
            return isQuery;
        }

        public void setQuery(boolean isQuery) {
            this.isQuery = isQuery;
        }

        public void setStatus(boolean status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "GeneratorInfo [generatorScore=" + generatorScore + ", status=" + status + "]";
        }
    }

    private class GeneratorInfoTable {
        private ArrayList<GeneratorInfo> generatorTable;

        private HashMap<GeneratorNode, Integer> nodeSuccess = new HashMap<>();
        private HashMap<GeneratorNode, Integer> nodeCount = new HashMap<>();
        private HashMap<String, Integer> compositeSuccess = new HashMap<>();
        private HashMap<String, Integer> compositeCount = new HashMap<>();
        private HashMap<GeneralFragmentChoice, Integer> fragmentSuccess = new HashMap<>();
        private HashMap<GeneralFragmentChoice, Integer> fragmentCount = new HashMap<>();

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

        public void calcNodeSuccess() {
            int stmtNum = 0;
            int queryNum = 0;
            int qsuccess = 0;
            int ssuccess = 0;
            for (GeneratorInfo info : generatorTable) {
                info.countSuccess(nodeSuccess, nodeCount, info.getGeneratorScore());
                // logging info
                if (info.isQuery()) {
                    qsuccess += info.getStatus() ? 1 : 0;
                    queryNum++;
                } else {
                    ssuccess += info.getStatus() ? 1 : 0;
                    stmtNum++;
                }
            }
            System.out.println("Successful query pairs rate: " + (double) qsuccess / queryNum);
            System.out.println("Successful statements rate: " + (double) ssuccess / stmtNum);
        }

        public void calcCompositeSuccess() {
            for (GeneratorInfo info : generatorTable) {
                info.countSuccess(compositeSuccess, compositeCount, info.getCompositeGeneratorScore());
            }
        }

        public void calcFragmentSuccess() {
            for (GeneratorInfo info : generatorTable) {
                info.countSuccess(fragmentSuccess, fragmentCount, info.getFragmentScore());
            }
        }

        public synchronized <N> HashMap<N, Double> calcAverageScore(HashMap<N, Integer> success,
                HashMap<N, Integer> count, HashMap<N, Integer> allSuccess, HashMap<N, Integer> allCount, int minCnt,
                boolean quickStart) {
            HashMap<N, Double> average = new HashMap<>();
            // sum the success and count
            for (Map.Entry<N, Integer> entry : success.entrySet()) {
                N key = entry.getKey();
                if (allSuccess.containsKey(key)) {
                    allSuccess.put(key, allSuccess.get(key) + entry.getValue());
                    allCount.put(key, allCount.get(key) + count.get(key));
                } else {
                    allSuccess.put(key, entry.getValue());
                    allCount.put(key, count.get(key));
                }
            }
            for (Map.Entry<N, Integer> entry : allSuccess.entrySet()) {
                int cnt = allCount.get(entry.getKey());
                if (cnt > minCnt || (quickStart && entry.getValue() > 0)) {
                    average.put(entry.getKey(), (double) entry.getValue() / cnt);
                }
            }
            return average;
        }

    }

    // volatile
    private static HashMap<String, Integer> curDepth = new HashMap<>();
    private static volatile int execDatabaseNum = 0;
    private static volatile HashMap<String, GeneratorInfo> assertionGeneratorHistory = new HashMap<>();
    private static volatile HashMap<GeneratorNode, Boolean> generatorOptions = new HashMap<>();
    private static volatile HashMap<String, Boolean> compositeGeneratorOptions = new HashMap<>();
    private static volatile HashMap<GeneralFragmentChoice, Boolean> fragmentOptions = new HashMap<>();

    private static volatile HashMap<String, Integer> allCompositeSuccess = new HashMap<>();
    private static volatile HashMap<String, Integer> allCompositeCount = new HashMap<>();
    private static volatile HashMap<GeneralFragmentChoice, Integer> allFragmentSuccess = new HashMap<>();
    private static volatile HashMap<GeneralFragmentChoice, Integer> allFragmentCount = new HashMap<>();
    private static volatile HashMap<GeneratorNode, Integer> allNodeSuccess = new HashMap<>();
    private static volatile HashMap<GeneratorNode, Integer> allNodeCount = new HashMap<>();

    private static volatile HashMap<GeneratorNode, Double> generatorAverage = new HashMap<>();
    private static volatile HashMap<String, Double> compositeAverage = new HashMap<>();
    private static volatile HashMap<GeneralFragmentChoice, Double> fragmentAverage = new HashMap<>();

    private double nodeNum = GeneratorNode.values().length;

    public enum GeneratorNode {
        // Meta nodes
        UNTYPE_EXPR,

        // Statement-level nodes
        CREATE_TABLE, CREATE_INDEX, INSERT, SELECT, UPDATE, DELETE, CREATE_VIEW, EXPLAIN, ANALYZE, VACUUM, ALTER_TABLE,
        CREATE_DATABASE,
        // Clause level nodes
        UNIQUE_INDEX, UPDATE_WHERE, PRIMARY_KEY, COLUMN_NUM, COLUMN_INT, COLUMN_BOOLEAN, COLUMN_STRING, JOIN, INNER_JOIN, LEFT_JOIN,
        RIGHT_JOIN, NATURAL_JOIN, LEFT_NATURAL_JOIN, RIGHT_NATURAL_JOIN, FULL_NATURAL_JOIN,
        // Expression level nodes
        UNARY_POSTFIX, UNARY_PREFIX, BINARY_COMPARISON, BINARY_LOGICAL, BINARY_ARITHMETIC, CAST, FUNC, BETWEEN, CASE,
        IN, COLLATE, LIKE_ESCAPE, UNTYPE_FUNC, CAST_FUNC, CAST_COLON, IS_NULL, IS_NOT_NULL, IS_TRUE, IS_FALSE,
        IS_NOT_UNKNOWN,
        // UnaryPrefix
        UNOT, UPLUS, UMINUS, USQT_ROOT, UABS_VAL, UBIT_NOT, UCUBE_ROOT,
        // Comparison Operator nodes
        EQUALS, GREATER, GREATER_EQUALS, SMALLER, SMALLER_EQUALS, NOT_EQUALS, NOT_EQUALS2, LIKE, NOT_LIKE, DISTINCT,
        NOT_DISTINCT, IS, IS_NOT, EQUALS2,
        // Arithmetic Operator nodes
        OPADD, OPSUB, OPMULT, OPDIV, OPMOD, OPCONCAT, OPAND, OPOR, OPLSHIFT, OPRSHIFT, OPDIV_STR, OPMOD_STR,
        OPBITWISE_XOR,
        // Logical Operator nodes
        LOPAND, LOPOR;
    }

    public double getNodeNum() {
        return nodeNum;
    }

    public void incrementExecDatabaseNum() {
        execDatabaseNum++;
    }

    public GeneralErrorHandler() {
        this.generatorTable = new GeneratorInfoTable();
        this.generatorInfo = new GeneratorInfo();
        if (generatorOptions.isEmpty()) {
            initGeneratorOptions();
        }
        updateGeneratorNodeNum();
    }

    public HashMap<GeneratorNode, Boolean> getGeneratorOptions() {
        return GeneralErrorHandler.generatorOptions;
    }

    public int getCurDepth(String databaseName) {
        databaseName = databaseName.split("_")[0]; // for experiment usage
        if (curDepth.containsKey(databaseName)) {
            return curDepth.get(databaseName);
        } else {
            // We currently don't explicitly initiate the depth of the database
            return 1;
        }
    }

    public void incrementCurDepth(String databaseName) {
        databaseName = databaseName.split("_")[0];
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

    public synchronized void updateFragments() {
        GeneralTableGenerator.getFragments().updateFragmentByFeedback(this);
        GeneralIndexGenerator.getFragments().updateFragmentByFeedback(this);
        GeneralSchema.getFragments().updateFragmentByFeedback(this);
    }

    public void calcAverageScore() {
        generatorTable.calcNodeSuccess();
        generatorAverage = generatorTable.calcAverageScore(generatorTable.nodeSuccess, generatorTable.nodeCount,
                allNodeSuccess, allNodeCount, 100, true);
        // generatorTable.calcAverageCompositeScore();
        generatorTable.calcCompositeSuccess();
        compositeAverage = generatorTable.calcAverageScore(generatorTable.compositeSuccess,
                generatorTable.compositeCount, allCompositeSuccess, allCompositeCount, 500, false);

        generatorTable.calcFragmentSuccess();
        fragmentAverage = generatorTable.calcAverageScore(generatorTable.fragmentSuccess, generatorTable.fragmentCount,
                allFragmentSuccess, allFragmentCount, 10, true);
    }

    public void updateGeneratorOptions() {

        // if not zero then the option is true
        updateByLeastOnce(generatorAverage, generatorOptions);
        updateByLeastOnce(compositeAverage, compositeGeneratorOptions);
        postUpdateFunctionOptions();
        updateByLeastOnce(fragmentAverage, fragmentOptions);

        // Special handling for the untype_expr option
        if (generatorOptions.get(GeneratorNode.UNTYPE_EXPR)) {
            // TODO make it super parameter
            generatorOptions.put(GeneratorNode.UNTYPE_EXPR, generatorAverage.get(GeneratorNode.UNTYPE_EXPR) > 0.5);
        }
    }

    private synchronized void postUpdateFunctionOptions() {
        // iterate funtions 
        for (Map.Entry<String, Integer> entry : GeneralFunction.getFunctions().entrySet()) {
            String funcName = entry.getKey();
            for (int i = 0; i < entry.getValue(); i++) {
                final int ind = i;
                List<GeneralCompositeDataType> availTypes = GeneralCompositeDataType.getSupportedTypes().stream()
                        .filter(t -> getCompositeOption(funcName, ind + t.toString())).collect(Collectors.toList());
                if (availTypes.size() == 0) {
                    setCompositeOption("FUNCTION-" + funcName, false);
                }
            }
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
                    if (option.contains("-")) {
                        setCompositeOptionIfNonExist(option, false);
                    } else {
                        GeneratorNode generatorNode = GeneratorNode.valueOf(option);
                        setOptionIfNonExist(generatorNode, false);
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Option " + option + " not found");
                }
            }
            postUpdateFunctionOptions();
        } catch (Exception e) {
            System.out.println("Error reading file: " + fileName);
            System.out.println(e.getMessage());
        }
    }

    private void updateGeneratorNodeNum() {
        nodeNum = 0;
        nodeNum += GeneralUnaryPrefixOperator.values().length;
        nodeNum += GeneralUnaryPostfixOperator.values().length;
        nodeNum += GeneralCast.GeneralCastOperator.values().length;
        nodeNum += GeneralBinaryComparisonOperator.values().length;
        nodeNum += GeneralBinaryArithmeticOperator.values().length;
        nodeNum += GeneralFunction.getNrFunctionsNum();
        nodeNum += GeneratorNode.values().length; // plus 1 padding
        // System.out.println("NodeNum: " + nodeNum);
    }

    public GeneratorInfo getGeneratorInfo() {
        return generatorInfo;
    }

    // public void loadGeneratorInfo(GeneratorInfo info) {
    // this.generatorInfo = info;
    // }

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

    public void addScore(GeneralFragmentChoice fragment) {
        HashMap<GeneralFragmentChoice, Integer> score = generatorInfo.getFragmentScore();
        if (score.containsKey(fragment)) {
            score.put(fragment, score.get(fragment) + 1);
        } else {
            score.put(fragment, 1);
        }
    }

    public void setScore(GeneratorNode generatorName, Integer score) {
        generatorInfo.getGeneratorScore().put(generatorName, score);
    }

    public void setScore(String generatorName, Integer score) {
        generatorInfo.getCompositeGeneratorScore().put(generatorName, score);
    }

    public void loadCompositeScore(HashMap<String, Integer> compositeScore) {
        generatorInfo.compositeGeneratorScore = compositeScore;
    }

    public void setExecutionStatus(boolean status) {
        generatorInfo.setStatus(status);
    }

    public GeneratorInfo getLastGeneratorScore() {
        return generatorTable.getLastGeneratorScore();
    }

    public void appendScoreToTable(boolean status, boolean isQuery) {
        setExecutionStatus(status);
        generatorInfo.setQuery(isQuery);
        generatorTable.add(generatorInfo);
        generatorInfo = new GeneratorInfo();
    }

    public void appendHistory(String databaseName) {
        assertionGeneratorHistory.put(databaseName, getLastGeneratorScore());
    }

    public void printStatistics() {
        System.out.println("Executed Databases: " + execDatabaseNum);
        // System.out.println("Generator Score: " + generatorInfo);
        // System.out.println("Generator Table: " + generatorTable);
        // System.out.println("Generator Options: " + generatorOptions);
        // System.out.println("Composite Generator Options: " +
        // compositeGeneratorOptions);
        // System.out.println("Fragment Options: " + fragmentOptions);
        // System.out.println("Fragment Success " + fragmentSuccess);
        // System.out.println("Fragment Count" + fragmentCount);

        // get the average value for each key for all the hashmap in the
        // successGeneratorTable
        // HashMap<GeneratorNode, Double> average = getAverageScore(generatorTable);
        System.out.println("Total queries: " + generatorTable.getGeneratorTable().size());
        // System.out.println("Average: " + average);

        // HashMap<String, Double> compositeAverage =
        // getAverageScore(compositeGeneratorTable);
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
        System.out.println("Features: " + nodes);
        // System.out.println("History: " + history);

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
                    System.out.println("Duplicated bug found, ignore it.");
                    break;
                } else {
                    continue;
                }
            }
            if (nodes.containsAll(generatorNodes)) {
                duplicate = true;
                System.out.println("Duplicated bug found, ignore it.");
                if (isError) {
                    System.out.println("Skip the rest of the current test");
                    throw new IgnoreMeException();
                }
                break;
            }
        }

        return duplicate;
    }

    public synchronized void saveStatistics(GeneralGlobalState globalState) {
        // TODO It is a quite ugly function
        try (FileWriter file = new FileWriter(
                "logs/" + globalState.getDbmsSpecificOptions().getDatabaseEngineFactory().toString() + "Options.txt")) {
            for (Map.Entry<GeneratorNode, Boolean> entry : generatorOptions.entrySet()) {
                file.write(entry.getKey() + " : " + entry.getValue() + "\n");
            }
            for (Map.Entry<String, Boolean> entry : compositeGeneratorOptions.entrySet()) {
                file.write(entry.getKey() + " : " + entry.getValue() + "\n");
            }
            for (Map.Entry<GeneralFragmentChoice, Boolean> entry : fragmentOptions.entrySet()) {
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

    public void setCompositeOptionIfNonExist(String option, boolean value) {
        if (!compositeGeneratorOptions.containsKey(option)) {
            setCompositeOption(option, value);
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

    public boolean getFragmentOption(GeneralFragmentChoice option) {
        if (fragmentOptions.containsKey(option)) {
            return fragmentOptions.get(option);
        } else {
            return true;
        }
    }

    public boolean getCompositeOption(String option1, String option2) {
        String option = option1 + "-" + option2;
        return getCompositeOption(option);
    }
}
