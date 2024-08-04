package sqlancer.general.learner;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.opencsv.CSVReader;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.ColumnReferenceNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.ast.GeneralConstant;
import sqlancer.general.ast.GeneralExpression;

public abstract class GeneralFragments {

    protected enum GeneralFragmentVariable {
        RAND_INT((g) -> {
            return GeneralConstant.createIntConstant(g.getRandomly().getInteger());
        }),
        RAND_STRING((g) -> {
            return GeneralConstant.createStringConstant(g.getRandomly().getString());
        }),
        RAND_COLUMN((g) -> {
            if (g.getUpdateTable() != null) {
                return new ColumnReferenceNode<GeneralExpression, GeneralColumn>(g.getUpdateTable().getRandomColumn());
            } else {
            GeneralTable table = g.getSchema().getRandomTable(t -> !t.isView());
                return new ColumnReferenceNode<GeneralExpression, GeneralColumn>(table.getRandomColumn());
            }
        }),
        NULL((g) -> {
            return null;
        }) {
            @Override
            public String toString() {
                return "";
            }
        };

        private Node<GeneralExpression> node;
        private GeneralVariableGenerator<GeneralGlobalState> generator;

        GeneralFragmentVariable(GeneralVariableGenerator<GeneralGlobalState> generator) {
            this.generator = generator;
        }

        public void genVariable(GeneralGlobalState state) {
            node = generator.generate(state);
        }

        public String toString() {
            return GeneralToStringVisitor.asString(node);
        }
    }

    public class GeneralFragmentChoice {

        private String fmtString;
        private GeneralFragmentVariable var;
        private int index;

        public GeneralFragmentChoice(String fmtString, GeneralFragmentVariable var, int index) {
            this.fmtString = fmtString;
            this.var = var;
            this.index = index;
        }

        public String toString(GeneralGlobalState state) {
            var.genVariable(state);
            return String.format(fmtString, var.toString());
        }

        @Override
        public String toString() {
            return String.format("%s-%d-%s-%s", getStatementType(), index, fmtString, var.name());
        }

    }

    private boolean learnFlag = false;
    public static final String PLACEHOLDER = "{%d}";
    private HashMap<Integer, List<GeneralFragmentChoice>> fragments = new HashMap<>();

    public GeneralFragments() {
        this.fragments = new HashMap<>();
    }

    public void setLearn(boolean learnFlag) {
        this.learnFlag = learnFlag;
    }

    public HashMap<Integer, List<GeneralFragmentChoice>> getFragments() {
        return fragments;
    }

    public void addFragment(int index, String fmtString, GeneralFragmentVariable var) {
        if (!fragments.containsKey(index)) {
            fragments.put(index, new ArrayList<>());
        }
        // avoid duplicate:
        for (GeneralFragmentChoice choice : fragments.get(index)) {
            if (choice.fmtString.equals(fmtString) && choice.var == var) {
                System.out.println("Duplicate fragment");
                return;
            }
        }
        fragments.get(index).add(new GeneralFragmentChoice(fmtString, var, index));
    }

    public String get(int index, GeneralGlobalState state) {
        if (learnFlag) {
            return getPlaceHolder(index);
        }
        if (fragments.containsKey(index)) {
            GeneralFragmentChoice choice = Randomly.fromList(fragments.get(index));
            state.getHandler().addScore(choice);
            return choice.toString(state);
        } else {
            return "";
        }
    }

    public String getPlaceHolder(int index) {
        return String.format(PLACEHOLDER, index);
    }

    public void loadFragmentsFromFile(GeneralGlobalState globalState) {
        File configFile = new File(globalState.getConfigDirectory(), getConfigName());
        if (configFile.exists()) {
            // read from file
            FileReader fileReader;
            System.out.println(String.format("Loading fragments from file %s.", getConfigName()));
            try {
                fileReader = new FileReader(configFile);
                loadFragmentsFromCSV(fileReader);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.err.println(String.format("File %s does not exist", getConfigName()));
        }
    }

    private void loadFragmentsFromCSV(Reader configReader) {
        try (CSVReader reader = new CSVReader(configReader)) {
            List<String[]> r = reader.readAll();
            // GeneralElementChoice choice;
            for (String[] s : r) {
                // parseElements(s);
                try {
                    parseFragments(s);
                } catch (Exception e) {
                    // e.printStackTrace();
                    System.out.println(String.format("Error parsing %s for statement %s", s[1], getStatementType()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // add empty choices for each index of the fragments
        for (int i : fragments.keySet()) {
            addFragment(i, "", GeneralFragmentVariable.NULL);
        }
    }

    private void parseFragments(String[] s) {
        int i = Integer.parseInt(s[0]);

        Pattern pattern = Pattern.compile("<([^>]*)>");
        Matcher matcher = pattern.matcher(s[1]);

        String content = "";
        String output = s[1];

        if (matcher.find()) {
            content = matcher.group(1);
            output = matcher.replaceFirst("%s");
            if (matcher.find()) {
                System.err.println("More than one variable in fragment");
                return;
            }
            addFragment(i, output, GeneralFragmentVariable.valueOf(content.toUpperCase()));
        } else {
            addFragment(i, output, GeneralFragmentVariable.NULL);
        }

    }
    
    public synchronized void updateFragmentByFeedback(GeneralErrorHandler handler) {
        // Iterate the fragments and remove the ones that are not useful
        for (int i : fragments.keySet()) {
            List<GeneralFragmentChoice> choices = fragments.get(i);
            choices.removeIf(choice -> !handler.getFragmentOption(choice));
        }
    }

    public void updateFragmentsFromLearner(GeneralGlobalState globalState) {
        String template = genLearnStatement(globalState);
        String variables = getVariables();
        GeneralTemplateLearner learner = new GeneralTemplateLearner(globalState, getStatementType(), template, variables);
        System.out.println("Updating fragments from learner");
        learner.learn();
        System.out.println("Processing and loading fragments from learner");
        String fragments = learner.getFragments();
        if (fragments != "") {
            loadFragmentsFromCSV(new StringReader(fragments));
        } else {
            System.err.println("No fragments returned from learner");
        }
        printFragments();
    }
    
    public void printFragments() {
        System.out.println(String.format("Fragments for %s", getStatementType()));
        for (int i : fragments.keySet()) {
            System.out.println(String.format("Fragment %d", i));
            for (GeneralFragmentChoice choice : fragments.get(i)) {
                System.out.println(choice.toString());
            }
        }
    }

    private String getVariables() {
        StringBuilder sb = new StringBuilder();
        for (GeneralFragmentVariable var : GeneralFragmentVariable.values()) {
            if(var == GeneralFragmentVariable.NULL) {
                continue;
            }
            sb.append(String.format("<%s>, ", var.name()));
        }
        return sb.toString();
    }

    public abstract String getConfigName();

    public abstract String getStatementType();

    public abstract String genLearnStatement(GeneralGlobalState globalState);

}
