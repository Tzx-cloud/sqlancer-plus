package sqlancer.common.oracle;

import sqlancer.GlobalState;
import sqlancer.Reproducer;

import java.sql.SQLException;

public interface TestOracle<G extends GlobalState<?, ?, ?>> {

    void check() throws Exception;
    default void genSelect()throws SQLException {}
    default Reproducer<G> getLastReproducer() {
        return null;
    }

    default String getLastQueryString() {
        throw new AssertionError("Not supported!");
    }
}
