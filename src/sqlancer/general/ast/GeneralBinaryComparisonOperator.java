package sqlancer.general.ast;

import sqlancer.Randomly;
import sqlancer.common.ast.BinaryOperatorNode.Operator;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;

public enum GeneralBinaryComparisonOperator implements Operator {
    EQUALS("="), GREATER(">"), GREATER_EQUALS(">="), SMALLER("<"), SMALLER_EQUALS("<="), NOT_EQUALS("!=");
    // LIKE("LIKE"), NOT_LIKE("NOT LIKE"), SIMILAR_TO("SIMILAR TO"),
    // NOT_SIMILAR_TO("NOT SIMILAR TO");
    // REGEX_POSIX("~"), REGEX_POSIT_NOT("!~");

    private String textRepr;

    GeneralBinaryComparisonOperator(String textRepr) {
        this.textRepr = textRepr;
    }

    public static Operator getRandom() {
        return Randomly.fromOptions(values());
    }

    public static Operator getRandomByOptions(GeneralErrorHandler handler) {
        Operator op;
        GeneratorNode node;
        do {
            op = Randomly.fromOptions(values());
            node = GeneratorNode.valueOf(op.toString());
        } while (!handler.getOption(node) || Randomly.getBooleanWithSmallProbability());
        handler.addScore(node);
        return op;
    }

    @Override
    public String getTextRepresentation() {
        return textRepr;
    }

}