package sqlancer;

import sqlancer.common.oracle.TestOracle;
import sqlancer.general.GeneralOptions;
import sqlancer.general.GeneralProvider;

import java.util.List;

public interface DBMSSpecificOptions<F extends OracleFactory<? extends GlobalState<?, ?, ?>>> {

    List<F> getTestOracleFactory();

    GeneralOptions.GeneralDatabaseEngineFactory getDatabaseEngineFactory();
}
