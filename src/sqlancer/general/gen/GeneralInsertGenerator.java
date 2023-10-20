package sqlancer.general.gen;

import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.gen.AbstractInsertGenerator;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.GeneralToStringVisitor;

public class GeneralInsertGenerator extends AbstractInsertGenerator<GeneralColumn> {

    private final GeneralGlobalState globalState;
    private final ExpectedErrors errors = new ExpectedErrors();

    public GeneralInsertGenerator(GeneralGlobalState globalState) {
        this.globalState = globalState;
    }

    public static SQLQueryAdapter getQuery(GeneralGlobalState globalState) {
        return new GeneralInsertGenerator(globalState).generate();
    }

    private SQLQueryAdapter generate() {
        sb.append("INSERT INTO ");
        GeneralTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
        List<GeneralColumn> columns = table.getRandomNonEmptyColumnSubset();
        sb.append(table.getName());
        sb.append("(");
        sb.append(columns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
        sb.append(")");
        sb.append(" VALUES ");
        insertColumns(columns);
        GeneralErrors.addInsertErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected void insertValue(GeneralColumn columnGeneral) {
        // TODO: select a more meaningful value
        if (Randomly.getBooleanWithRatherLowProbability()) {
            sb.append("DEFAULT");
        } else {
            sb.append(GeneralToStringVisitor.asString(new GeneralExpressionGenerator(globalState).generateConstant()));
        }
    }

}
