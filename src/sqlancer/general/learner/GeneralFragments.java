package sqlancer.general.learner;

import java.io.File;
import java.io.FileReader;
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
        CONSTANT((g) -> {
            return GeneralConstant.createIntConstant(g.getRandomly().getInteger());
        }),
        COLUMN((g) -> {
            GeneralTable table = g.getSchema().getRandomTable(t -> !t.isView());
            return new ColumnReferenceNode<GeneralExpression, GeneralColumn>(table.getRandomColumn());
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

        public GeneralFragmentChoice(String fmtString, GeneralFragmentVariable var) {
            this.fmtString = fmtString;
            this.var = var;
        }

        public String toString(GeneralGlobalState state) {
            var.genVariable(state);
            return String.format(fmtString, var.toString());
        }

        @Override
        public String toString() {
            return String.format("%s-%s-%s", getStatementType(), fmtString, var.name());
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
        fragments.get(index).add(new GeneralFragmentChoice(fmtString, var));
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
            try (CSVReader reader = new CSVReader(new FileReader(configFile))) {
                List<String[]> r = reader.readAll();
                // GeneralElementChoice choice;
                for (String[] s : r) {
                    // parseElements(s);
                    try {
                        parseFragments(s);
                    } catch (Exception e) {
                        System.out.println(String.format("Error parsing %s from file %s", s[1], getConfigName()));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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
            output = matcher.replaceAll("%s");
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

    public abstract String getConfigName();

    public abstract String getStatementType();

    public abstract String genLearnStatement(GeneralGlobalState globalState);

}
