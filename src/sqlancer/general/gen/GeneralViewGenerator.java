package sqlancer.general.gen;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralToStringVisitor;

public final class GeneralViewGenerator {

    private GeneralViewGenerator() {
    }

    public static SQLQueryAdapter generate(GeneralGlobalState globalState) {
        int nrColumns = Randomly.smallNumber() + 1;
        StringBuilder sb = new StringBuilder("CREATE ");
        sb.append("VIEW ");
        sb.append(globalState.getSchema().getFreeViewName());
        sb.append("(");
        for (int i = 0; i < nrColumns; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append("c");
            sb.append(i);
        }
        sb.append(") AS ");
        sb.append(GeneralToStringVisitor.asString(GeneralRandomQuerySynthesizer.generateSelect(globalState, nrColumns)));
        ExpectedErrors errors = new ExpectedErrors();
        GeneralErrors.addExpressionErrors(errors);
        GeneralErrors.addGroupByErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

}
