package sqlancer.general.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.schema.TableIndex;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.general.ast.GeneralExpression;

public final class GeneralIndexGenerator {

    private GeneralIndexGenerator() {
    }

    public static SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        StringBuilder sb = new StringBuilder();
        globalState.getHandler().addScore(GeneratorNode.CREATE_INDEX);
        sb.append("CREATE ");
        if (Randomly.getBoolean()) {
            errors.add("Cant create unique index, table contains duplicate data on indexed column(s)");
            globalState.getHandler().addScore(GeneratorNode.UNIQUE_INDEX);
            sb.append("UNIQUE ");
        }
        sb.append("INDEX ");
        GeneralTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
        // String indexName = table.getName() + Randomly.fromOptions("i0", "i1", "i2", "i3", "i4");
        // TODO: make it schema aware
        String indexName = table.getName() + (table.getIndexes().isEmpty() ? "i0" : "i" + table.getIndexes().size());
        sb.append(indexName);
        sb.append(" ON ");
        sb.append(table.getName());
        sb.append("(");
        List<GeneralColumn> columns = table.getRandomNonEmptyColumnSubset();
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
            sb.append(" ");
            if (Randomly.getBooleanWithRatherLowProbability()) {
                sb.append(Randomly.fromOptions("ASC", "DESC"));
            }
        }
        sb.append(")");
        if (Randomly.getBoolean()) {
            sb.append(" WHERE ");
            Node<GeneralExpression> expr = new GeneralExpressionGenerator(globalState).setColumns(table.getColumns())
                    .generateExpression();
            sb.append(GeneralToStringVisitor.asString(expr));
        }
        errors.add("already exists!");
        errors.add("Syntax");
        errors.addRegex(Pattern.compile(".*", Pattern.DOTALL));
        // Update the indexes of the table
        List<TableIndex> indexes = new ArrayList<>(table.getIndexes());
        TableIndex index = TableIndex.create(indexName);
        // append the index
        indexes.add(index);

        SQLQueryAdapter q = new SQLQueryAdapter(sb.toString(), errors, true, false);
        globalState.setUpdateTable(new GeneralTable(table.getName(), table.getColumns(), indexes, false));
        return q;
    }

}
