package sqlancer.general.gen;

import sqlancer.GlobalState;
import sqlancer.MainOptions;
import sqlancer.general.GeneralOptions;
import sqlancer.general.gen.Configuration.BaseConfigurationGenerator;
import sqlancer.general.gen.Configuration.MySQLConfigurationGenerator;
import sqlancer.general.gen.Configuration.PostgresConfigurationGenerator;

public class GeneralConfigurationGenerator {


    public static BaseConfigurationGenerator createGenerator(GeneralOptions.GeneralDatabaseEngineFactory dbType, GlobalState globalState) {
        switch (dbType) {
            case MYSQL:
                return MySQLConfigurationGenerator.getInstance(globalState.getRandomly(), globalState.getOptions());
            case POSTGRESQL:
                return PostgresConfigurationGenerator.getInstance(globalState.getRandomly(), globalState.getOptions());
            default:
                throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
    }

}
