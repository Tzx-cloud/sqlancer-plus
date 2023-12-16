package sqlancer.general.ast;

import sqlancer.common.ast.BinaryOperatorNode.Operator;
import sqlancer.Randomly;

public enum GeneralUnaryPrefixOperator implements Operator {

    NOT("NOT"), PLUS("+"), MINUS("-");

    private String textRepr;

    GeneralUnaryPrefixOperator(String textRepr) {
        this.textRepr = textRepr;
    }

    @Override
    public String getTextRepresentation() {
        return textRepr;
    }

    public static GeneralUnaryPrefixOperator getRandom() {
        return Randomly.fromOptions(values());
    }

}