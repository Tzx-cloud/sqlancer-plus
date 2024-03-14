package sqlancer.general.ast;

import sqlancer.common.ast.BinaryOperatorNode.Operator;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.Randomly;

public enum GeneralUnaryPostfixOperator implements Operator {

    IS_NULL("IS NULL"), IS_NOT_NULL("IS NOT NULL"), IS_NOT_UNKNOWN("IS NOT UNKNOWN"), IS_TRUE("IS TRUE"), IS_FALSE("IS FALSE");

    private String textRepr;

    GeneralUnaryPostfixOperator(String textRepr) {
        this.textRepr = textRepr;
    }

    @Override
    public String getTextRepresentation() {
        return textRepr;
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

}
