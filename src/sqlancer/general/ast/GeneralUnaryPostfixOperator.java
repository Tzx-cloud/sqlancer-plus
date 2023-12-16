package sqlancer.general.ast;

import sqlancer.common.ast.BinaryOperatorNode.Operator;
import sqlancer.Randomly;

public enum GeneralUnaryPostfixOperator implements Operator {

    IS_NULL("IS NULL"), IS_NOT_NULL("IS NOT NULL");

    private String textRepr;

    GeneralUnaryPostfixOperator(String textRepr) {
        this.textRepr = textRepr;
    }

    @Override
    public String getTextRepresentation() {
        return textRepr;
    }

    public static GeneralUnaryPostfixOperator getRandom() {
        return Randomly.fromOptions(values());
    }

}
