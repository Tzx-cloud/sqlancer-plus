package sqlancer.general.ast;

import sqlancer.common.ast.BinaryOperatorNode.Operator;
import sqlancer.common.ast.newast.NewUnaryPostfixOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;

public class GeneralCast extends NewUnaryPostfixOperatorNode<GeneralExpression> {
    public GeneralCast(Node<GeneralExpression> expr, GeneralCompositeDataType type) {
        super(expr, new Operator() {

            @Override
            public String getTextRepresentation() {
                return "::" + type.toString();
            }
        });
    }
}
