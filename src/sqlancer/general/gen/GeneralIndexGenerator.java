package sqlancer.general.gen;

import java.util.List;
import java.util.regex.Pattern;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralExpression;

public final class GeneralIndexGenerator {

    private GeneralIndexGenerator() {
    }

    public static SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE ");
        if (Randomly.getBoolean()) {
            errors.add("Cant create unique index, table contains duplicate data on indexed column(s)");
            sb.append("UNIQUE ");
        }
        sb.append("INDEX ");
        sb.append(Randomly.fromOptions("i0", "i1", "i2", "i3", "i4")); // cannot query this information
        sb.append(" ON ");
        GeneralTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
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
        if (globalState.getDbmsSpecificOptions().testRowid) {
            errors.add("Cannot create an index on the rowid!");
        }
        errors.add("Syntax");
        errors.addRegex(Pattern.compile(".*", Pattern.DOTALL));
        return new SQLQueryAdapter(sb.toString(), errors, true, false);
    }

}
