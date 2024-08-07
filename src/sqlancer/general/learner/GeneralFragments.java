package sqlancer.general.learner;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        private String key;

        public GeneralFragmentChoice(String fmtString, GeneralFragmentVariable var, String key) {
            this.fmtString = fmtString;
            this.var = var;
            this.key = key;
        }

        public String toString(GeneralGlobalState state) {
            var.genVariable(state);
            return String.format(fmtString, var.toString());
        }

        @Override
        public String toString() {
            return String.format("%s-%s-%s-%s", getStatementType(), key, fmtString, var.name());
        }

    }

    private boolean learnFlag = false;
    public static final String PLACEHOLDER = "{%d}";
    private HashMap<String, List<GeneralFragmentChoice>> fragments = new HashMap<>();

    public GeneralFragments() {
        this.fragments = new HashMap<>();
    }

    public void setLearn(boolean learnFlag) {
        this.learnFlag = learnFlag;
    }

    public HashMap<String, List<GeneralFragmentChoice>> getFragments() {
        return fragments;
    }

    public void addFragment(String key, String fmtString, GeneralFragmentVariable var) {
        if (!fragments.containsKey(key)) {
            fragments.put(key, new ArrayList<>());
        }
        // avoid duplicate:
        for (GeneralFragmentChoice choice : fragments.get(key)) {
            if (choice.fmtString.equals(fmtString) && choice.var == var) {
                System.out.println("Duplicate fragment");
                return;
            }
        }
        fragments.get(key).add(new GeneralFragmentChoice(fmtString, var, key));
    }

    public String get(int index, GeneralGlobalState state) {
        String key = String.valueOf(index);
        if (learnFlag) {
            return getPlaceHolder(index);
        }
        if (fragments.containsKey(key)) {
            GeneralFragmentChoice choice = Randomly.fromList(fragments.get(key));
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

    protected void loadFragmentsFromCSV(Reader configReader) {
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
        for (String key : fragments.keySet()) {
            addFragment(key, "", GeneralFragmentVariable.NULL);
        }
    }

    protected void parseFragments(String[] s) {
        // assume all the rows are in the format "integer index, <content>"

        try {
            Integer.parseInt(s[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid fragment key");
            return;
        }
        String key = s[0];

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
            addFragment(key, output, GeneralFragmentVariable.valueOf(content.toUpperCase()));
        } else {
            addFragment(key, output, GeneralFragmentVariable.NULL);
        }

    }
    
    public synchronized void updateFragmentByFeedback(GeneralErrorHandler handler) {
        // Iterate the fragments and remove the ones that are not useful
        for (String key : fragments.keySet()) {
            List<GeneralFragmentChoice> choices = fragments.get(key);
            choices.removeIf(choice -> !handler.getFragmentOption(choice));
        }
    }

    public void updateFragmentsFromLearner(GeneralGlobalState globalState) {
        String template = genLearnStatement(globalState);
        String variables = getVariables();
        String systemPrompt = getSystemPrompt();
        GeneralTemplateLearner learner = new GeneralTemplateLearner(globalState, getStatementType(), template,
                variables, systemPrompt);
        System.out.println("Updating fragments from learner");
        learner.learn();
        System.out.println("Processing and loading fragments from learner");
        String fragments = learner.getFragments();
        if (fragments != "") {
            loadFragmentsFromCSV(new StringReader(fragments));
        } else {
            System.err.println("No fragments returned from learner");
        }
        // printFragments();
    }
    
    protected String getSystemPrompt() {
        return "This GPT is an expert in SQL dialects. It helps users generate correct SQL statements for different DBMSs. Users specify a DBMS and provide a SQL template with SQL keywords and placeholders. The GPT fills placeholders with concrete string alternatives unless the user specifies variables. The response is a CSV file with two columns: one for placeholders (without brackets) and one for alternatives, without a header. Each alternative is split into separate rows. Provide as many and detailed answers as possible for each placeholder. Avoid explanations.";
    }
    
    public void printFragments() {
        System.out.println(String.format("Fragments for %s", getStatementType()));
        for (String key : fragments.keySet()) {
            System.out.println(String.format("Fragment %s", key));
            for (GeneralFragmentChoice choice : fragments.get(key)) {
                System.out.println(choice.toString());
            }
        }
    }

    protected String getVariables() {
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
