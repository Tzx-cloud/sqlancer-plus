package sqlancer.general.gen;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.gen.AbstractUpdateGenerator;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralExpression;

import java.util.List;

public final class GeneralUpdateGenerator extends AbstractUpdateGenerator<GeneralColumn> {

    private final GeneralGlobalState globalState;
    private GeneralExpressionGenerator gen;

    private GeneralUpdateGenerator(GeneralGlobalState globalState) {
        this.globalState = globalState;
    }

    public static SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        return new GeneralUpdateGenerator(globalState).generate();
    }

    private SQLQueryAdapter generate() {
        GeneralTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
        List<GeneralColumn> columns = table.getRandomNonEmptyColumnSubset();
        gen = new GeneralExpressionGenerator(globalState).setColumns(table.getColumns());
        sb.append("UPDATE ");
        sb.append(table.getName());
        sb.append(" SET ");
        updateColumns(columns);
        GeneralErrors.addInsertErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected void updateValue(GeneralColumn column) {
        Node<GeneralExpression> expr;
        if (Randomly.getBooleanWithSmallProbability()) {
            expr = gen.generateExpression();
            GeneralErrors.addExpressionErrors(errors);
        } else {
            expr = gen.generateConstant();
        }
        sb.append(GeneralToStringVisitor.asString(expr));
    }

}
