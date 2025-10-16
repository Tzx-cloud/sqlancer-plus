package sqlancer.general.gen.Configuration;

import sqlancer.GlobalState;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.common.query.SQLQueryAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class PostgresConfigurationGenerator extends BaseConfigurationGenerator{
    private static volatile PostgresConfigurationGenerator INSTANCE;
    public PostgresConfigurationGenerator(Randomly r, MainOptions options) {
        super(r, options);
    }

    @Override
    protected String getDatabaseType() {
        return "postgres";
    }

    @Override
    public ConfigurationAction[] getAllActions() {
        return PostgresConfigurationGenerator.Action.values();
    }

    @Override
    protected SQLQueryAdapter generateConfigForAction(Object action) {
        PostgresConfigurationGenerator.Action postgresAction = (PostgresConfigurationGenerator.Action) action;
        return generateConfigForParameter(postgresAction);
    }


    @Override
    protected String getActionName(Object action) {
        return ((PostgresConfigurationGenerator.Action) action).name();
    }

    public static SQLQueryAdapter set(GlobalState globalState) {
        return new PostgresConfigurationGenerator(globalState.getRandomly(), globalState.getOptions()).get();
    }


    private enum Action implements ConfigurationAction  {
        // https://www.postgresql.org/docs/13/runtime-config-wal.html
        // This parameter can only be set at server start.
        // WAL_LEVEL("wal_level", (r) -> Randomly.fromOptions("replica", "minimal", "logical")),
        // FSYNC("fsync", (r) -> Randomly.fromOptions(1, 0)),
        SYNCHRONOUS_COMMIT("synchronous_commit",
                (r) -> Randomly.fromOptions("remote_apply", "remote_write", "local", "off"),
                Scope.GLOBAL, Scope.SESSION),
        WAL_COMPRESSION("wal_compression", (r) -> Randomly.fromOptions(1, 0),
                Scope.GLOBAL),
        COMMIT_DELAY("commit_delay", (r) -> r.getInteger(0, 100000),
                Scope.GLOBAL, Scope.SESSION),
        COMMIT_SIBLINGS("commit_siblings", (r) -> r.getInteger(0, 1000),
                Scope.GLOBAL, Scope.SESSION),

        // 统计相关参数
        TRACK_ACTIVITIES("track_activities", (r) -> Randomly.fromOptions(1, 0),
                Scope.GLOBAL, Scope.SESSION),
        TRACK_COUNTS("track_counts", (r) -> Randomly.fromOptions(1, 0),
                Scope.GLOBAL, Scope.SESSION),
        TRACK_IO_TIMING("track_io_timing", (r) -> Randomly.fromOptions(1, 0),
                Scope.GLOBAL, Scope.SESSION),
        TRACK_FUNCTIONS("track_functions", (r) -> Randomly.fromOptions("'none'", "'pl'", "'all'"),
                Scope.GLOBAL, Scope.SESSION),

        // 真空相关参数
        VACUUM_FREEZE_TABLE_AGE("vacuum_freeze_table_age",
                (r) -> Randomly.fromOptions(0, 5, 10, 100, 500, 2000000000),
                Scope.GLOBAL, Scope.SESSION),
        VACUUM_FREEZE_MIN_AGE("vacuum_freeze_min_age",
                (r) -> Randomly.fromOptions(0, 5, 10, 100, 500, 1000000000),
                Scope.GLOBAL, Scope.SESSION),
        VACUUM_MULTIXACT_FREEZE_TABLE_AGE("vacuum_multixact_freeze_table_age",
                (r) -> Randomly.fromOptions(0, 5, 10, 100, 500, 2000000000),
                Scope.GLOBAL, Scope.SESSION),
        VACUUM_MULTIXACT_FREEZE_MIN_AGE("vacuum_multixact_freeze_min_age",
                (r) -> Randomly.fromOptions(0, 5, 10, 100, 500, 1000000000),
                Scope.GLOBAL, Scope.SESSION),

        GIN_FUZZY_SEARCH_LIMIT("gin_fuzzy_search_limit", (r) -> r.getInteger(0, 2147483647),
                Scope.GLOBAL, Scope.SESSION),
        DEFAULT_WITH_OIDS("default_with_oids", (r) -> Randomly.fromOptions(0, 1),
                Scope.GLOBAL, Scope.SESSION),
        SYNCHRONIZED_SEQSCANS("synchronize_seqscans", (r) -> Randomly.fromOptions(0, 1),
                Scope.GLOBAL, Scope.SESSION),

        // 查询规划器参数 - 通常只能在 SESSION 级别设置
        ENABLE_BITMAPSCAN("enable_bitmapscan", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_GATHERMERGE("enable_gathermerge", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_HASHJOIN("enable_hashjoin", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_INDEXSCAN("enable_indexscan", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_INDEXONLYSCAN("enable_indexonlyscan", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_MATERIAL("enable_material", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_MERGEJOIN("enable_mergejoin", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_NESTLOOP("enable_nestloop", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_PARALLEL_APPEND("enable_parallel_append", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_PARALLEL_HASH("enable_parallel_hash", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_PARTITION_PRUNING("enable_partition_pruning", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_PARTITIONWISE_JOIN("enable_partitionwise_join", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_PARTITIONWISE_AGGREGATE("enable_partitionwise_aggregate", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_SEGSCAN("enable_seqscan", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_SORT("enable_sort", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),
        ENABLE_TIDSCAN("enable_tidscan", (r) -> Randomly.fromOptions(1, 0),
                Scope.SESSION),

        // 成本参数 - 可以在两个级别设置
        SEQ_PAGE_COST("seq_page_cost", (r) -> Randomly.fromOptions(0d, 0.00001, 0.05, 0.1, 1, 10, 10000),
                Scope.GLOBAL, Scope.SESSION),
        RANDOM_PAGE_COST("random_page_cost", (r) -> Randomly.fromOptions(0d, 0.00001, 0.05, 0.1, 1, 10, 10000),
                Scope.GLOBAL, Scope.SESSION),
        CPU_TUPLE_COST("cpu_tuple_cost", (r) -> Randomly.fromOptions(0d, 0.00001, 0.05, 0.1, 1, 10, 10000),
                Scope.GLOBAL, Scope.SESSION),
        CPU_INDEX_TUPLE_COST("cpu_index_tuple_cost", (r) -> Randomly.fromOptions(0d, 0.00001, 0.05, 0.1, 1, 10, 10000),
                Scope.GLOBAL, Scope.SESSION),
        CPU_OPERATOR_COST("cpu_operator_cost", (r) -> Randomly.fromOptions(0d, 0.000001, 0.0025, 0.1, 1, 10, 10000),
                Scope.GLOBAL, Scope.SESSION),
        PARALLEL_SETUP_COST("parallel_setup_cost", (r) -> r.getLong(0, Long.MAX_VALUE),
                Scope.GLOBAL, Scope.SESSION),
        PARALLEL_TUPLE_COST("parallel_tuple_cost", (r) -> r.getLong(0, Long.MAX_VALUE),
                Scope.GLOBAL, Scope.SESSION),
        MIN_PARALLEL_TABLE_SCAN_SIZE("min_parallel_table_scan_size", (r) -> r.getInteger(0, 715827882),
                Scope.GLOBAL, Scope.SESSION),
        MIN_PARALLEL_INDEX_SCAN_SIZE("min_parallel_index_scan_size", (r) -> r.getInteger(0, 715827882),
                Scope.GLOBAL, Scope.SESSION),
        EFFECTIVE_CACHE_SIZE("effective_cache_size", (r) -> r.getInteger(1, 2147483647),
                Scope.GLOBAL, Scope.SESSION),

        // JIT 相关
        JIT_ABOVE_COST("jit_above_cost", (r) -> Randomly.fromOptions(0, r.getLong(-1, Long.MAX_VALUE - 1)),
                Scope.GLOBAL, Scope.SESSION),
        JIT_INLINE_ABOVE_COST("jit_inline_above_cost", (r) -> Randomly.fromOptions(0, r.getLong(-1, Long.MAX_VALUE)),
                Scope.GLOBAL, Scope.SESSION),
        JIT_OPTIMIZE_ABOVE_COST("jit_optimize_above_cost",
                (r) -> Randomly.fromOptions(0, r.getLong(-1, Long.MAX_VALUE)),
                Scope.GLOBAL, Scope.SESSION),

        // 遗传查询优化器
        GEQO("geqo", (r) -> Randomly.fromOptions(1, 0),
                Scope.GLOBAL, Scope.SESSION),
        GEQO_THRESHOLD("geqo_threshold", (r) -> r.getInteger(2, 2147483647),
                Scope.GLOBAL, Scope.SESSION),
        GEQO_EFFORT("geqo_effort", (r) -> r.getInteger(1, 10),
                Scope.GLOBAL, Scope.SESSION),
        GEQO_POO_SIZE("geqo_pool_size", (r) -> r.getInteger(0, 2147483647),
                Scope.GLOBAL, Scope.SESSION),
        GEQO_GENERATIONS("geqo_generations", (r) -> r.getInteger(0, 2147483647),
                Scope.GLOBAL, Scope.SESSION),
        GEQO_SELECTION_BIAS("geqo_selection_bias", (r) -> Randomly.fromOptions(1.5, 1.8, 2.0),
                Scope.GLOBAL, Scope.SESSION),
        GEQO_SEED("geqo_seed", (r) -> Randomly.fromOptions(0, 0.5, 1),
                Scope.GLOBAL, Scope.SESSION),

        // 其他规划器选项
        DEFAULT_STATISTICS_TARGET("default_statistics_target", (r) -> r.getInteger(1, 10000),
                Scope.GLOBAL, Scope.SESSION),
        CONSTRAINT_EXCLUSION("constraint_exclusion", (r) -> Randomly.fromOptions("on", "off", "partition"),
                Scope.GLOBAL, Scope.SESSION),
        CURSOR_TUPLE_FRACTION("cursor_tuple_fraction",
                (r) -> Randomly.fromOptions(0.0, 0.1, 0.000001, 1, 0.5, 0.9999999),
                Scope.GLOBAL, Scope.SESSION),
        FROM_COLLAPSE_LIMIT("from_collapse_limit", (r) -> r.getInteger(1, Integer.MAX_VALUE),
                Scope.GLOBAL, Scope.SESSION),
        JIT("jit", (r) -> Randomly.fromOptions(1, 0),
                Scope.GLOBAL, Scope.SESSION),
        JOIN_COLLAPSE_LIMIT("join_collapse_limit", (r) -> r.getInteger(1, Integer.MAX_VALUE),
                Scope.GLOBAL, Scope.SESSION),
        PARALLEL_LEADER_PARTICIPATION("parallel_leader_participation", (r) -> Randomly.fromOptions(1, 0),
                Scope.GLOBAL, Scope.SESSION),
        PLAN_CACHE_MODE("plan_cache_mode",
                (r) -> Randomly.fromOptions("auto", "force_generic_plan", "force_custom_plan"),
                Scope.GLOBAL, Scope.SESSION);



        private final GenericAction delegate;

        Action(String name, Function<Randomly, Object> prod, Scope... scopes) {
            this.delegate = new GenericAction(name, prod, scopes);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object generateValue(Randomly r) {
            return delegate.generateValue(r);
        }

        @Override
        public Scope[] getScopes() {
            return delegate.getScopes();
        }

        @Override
        public boolean canBeUsedInScope(Scope scope) {
            return delegate.canBeUsedInScope(scope);
        }


    }

    private SQLQueryAdapter get() {
        sb.append("SET ");
        Action a;
        if (isSingleThreaded) {
            a = Randomly.fromOptions(Action.values());
            Scope[] scopes = a.getScopes();
            Scope randomScope = Randomly.fromOptions(scopes);
            switch (randomScope) {
                case GLOBAL:
                    sb.append("GLOBAL");
                    break;
                case SESSION:
                    sb.append("SESSION");
                    break;
                default:
                    throw new AssertionError(randomScope);
            }

        } else {
            do {
                a = Randomly.fromOptions(Action.values());
            } while (!a.canBeUsedInScope(Scope.SESSION));
            sb.append("SESSION");
        }
        sb.append(" ");
        sb.append(a.getName());
        sb.append(" = ");
        sb.append(a.generateValue(r));
        return new SQLQueryAdapter(sb.toString());
    }

    @Override
    public SQLQueryAdapter generateConfigForParameter(ConfigurationAction action) {
        StringBuilder sb = new StringBuilder();
        sb.append("SET ");


        // PostgreSQL 中大多数参数不需要显式指定 GLOBAL/SESSION
        // 但某些参数可能需要
        sb.append(action.getName());
        sb.append(" = ");
        sb.append(action.generateValue(r));

        return new SQLQueryAdapter(sb.toString());
    }

    @Override
    public SQLQueryAdapter generateDefaultConfigForParameter(ConfigurationAction action) {
        StringBuilder sb = new StringBuilder();
        sb.append("SET ");
        sb.append(action.getName());
        sb.append(" = DEFAULT");
        return new SQLQueryAdapter(sb.toString());
    }

    public static BaseConfigurationGenerator getInstance(Randomly r, MainOptions options) {

            if (INSTANCE == null) {
                synchronized (MySQLConfigurationGenerator.class) {
                    if (INSTANCE == null) {
                        INSTANCE = new PostgresConfigurationGenerator(r, options);
                    }
                }
            }
            return INSTANCE;
        }
    }

