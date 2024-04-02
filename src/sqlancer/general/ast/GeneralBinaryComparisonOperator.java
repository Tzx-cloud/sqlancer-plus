package sqlancer.general.ast;

import sqlancer.Randomly;
import sqlancer.common.ast.BinaryOperatorNode.Operator;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;

public enum GeneralBinaryComparisonOperator implements Operator {
    EQUALS("="), GREATER(">"), GREATER_EQUALS(">="), SMALLER("<"), SMALLER_EQUALS("<="), NOT_EQUALS("!="),
    NOT_EQUALS2("<>"), DISTINCT("IS DISTINCT FROM"), NOT_DISTINCT("IS NOT DISTINCT FROM"),
    LIKE("LIKE"), NOT_LIKE("NOT LIKE"),
    // MYSQL
    IS("IS"), IS_NOT("IS NOT"), EQUALS2("<=>");
    // SIMILAR_TO("SIMILAR TO"),
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
        } while (!handler.getOption(node) || !Randomly.getBooleanWithSmallProbability());
        handler.addScore(node);
        return op;
    }

    @Override
    public String getTextRepresentation() {
        return textRepr;
    }

}