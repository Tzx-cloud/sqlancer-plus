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
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.ast.GeneralConstant;
import sqlancer.general.ast.GeneralExpression;

public abstract class GeneralElements {

    protected enum GeneralElementVariable {
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

        GeneralElementVariable(GeneralVariableGenerator<GeneralGlobalState> generator) {
            this.generator = generator;
        }

        public void genVariable(GeneralGlobalState state) {
            node = generator.generate(state);
        }

        public String toString() {
            return GeneralToStringVisitor.asString(node);
        }
    }

    protected class GeneralElementChoice {

        private String fmtString;
        private GeneralElementVariable var;

        public GeneralElementChoice(String fmtString, GeneralElementVariable var) {
            this.fmtString = fmtString;
            this.var = var;
        }

        public String toString(GeneralGlobalState state) {
            var.genVariable(state);
            return String.format(fmtString, var.toString());
        }

    }

    private boolean learnFlag = false;
    public static final String PLACEHOLDER = "{%d}";
    private HashMap<Integer, List<GeneralElementChoice>> elements = new HashMap<>();

    public GeneralElements() {
        this.elements = new HashMap<>();
    }

    public void setLearn(boolean learnFlag) {
        this.learnFlag = learnFlag;
    }

    public HashMap<Integer, List<GeneralElementChoice>> getElements() {
        return elements;
    }

    public void addElement(int index, String fmtString, GeneralElementVariable var) {
        if (!elements.containsKey(index)) {
            elements.put(index, new ArrayList<>());
        }
        elements.get(index).add(new GeneralElementChoice(fmtString, var));
    }

    public String get(int index, GeneralGlobalState state) {
        if (learnFlag) {
            return getPlaceHolder(index);
        }
        if (elements.containsKey(index)) {
            return Randomly.fromList(elements.get(index)).toString(state);
        } else {
            return "";
        }
    }

    public String getPlaceHolder(int index) {
        return String.format(PLACEHOLDER, index);
    }

    public void loadElementsFromFile(GeneralGlobalState globalState) {
        File configFile = new File(globalState.getConfigDirectory(), getConfigName());
        if (configFile.exists()) {
            // read from file
            try (CSVReader reader = new CSVReader(new FileReader(configFile))) {
                List<String[]> r = reader.readAll();
                // GeneralElementChoice choice;
                for (String[] s : r) {
                    // parseElements(s);
                    try {
                        parseElements(s);
                    } catch (Exception e) {
                        System.out.println(String.format("Error parsing %s from file %s", s[1], getConfigName()));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void parseElements(String[] s) {
        int i = Integer.parseInt(s[0]);

        Pattern pattern = Pattern.compile("<([^>]*)>");
        Matcher matcher = pattern.matcher(s[1]);

        String content = "";
        String output = s[1];

        if (matcher.find()) {
            content = matcher.group(1);
            output = matcher.replaceAll("%s");
            addElement(i, output, GeneralElementVariable.valueOf(content.toUpperCase()));
        } else {
            addElement(i, output, GeneralElementVariable.NULL);
        }
        
    }

    public abstract String getConfigName();

    public abstract String genLearnStatement(GeneralGlobalState globalState);

}
