package sqlancer.general.ast;

import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.ast.newast.TableReferenceNode;
import sqlancer.general.GeneralErrorHandler;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralColumn;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.gen.GeneralExpressionGenerator;

public class GeneralJoin implements Node<GeneralExpression> {

    private final TableReferenceNode<GeneralExpression, GeneralTable> leftTable;
    private final TableReferenceNode<GeneralExpression, GeneralTable> rightTable;
    private final JoinType joinType;
    private final Node<GeneralExpression> onCondition;
    private OuterType outerType;

    public enum JoinType {
        INNER, NATURAL, LEFT, RIGHT;

        public static JoinType getRandom() {
            return Randomly.fromOptions(values());
        }

        public static JoinType getRandomByOptions(GeneralErrorHandler handler) {
            // TODO refactor this function and also DBFunction one
            JoinType joinType;
            GeneratorNode node;
            do {
                joinType = getRandom();
                node = GeneratorNode.valueOf(joinType.name() + "_JOIN");
            } while (!handler.getOption(node) || !Randomly.getBooleanWithSmallProbability());
            handler.addScore(node);
            return joinType;
        }
    }

    public enum OuterType {
        FULL, LEFT, RIGHT;

        public static OuterType getRandom() {
            return Randomly.fromOptions(values());
        }

        public static OuterType getRandomByOptions(GeneralErrorHandler handler) {
            // TODO refactor this function and also DBFunction one
            OuterType outerType;
            GeneratorNode node;
            do {
                outerType = getRandom();
                node = GeneratorNode.valueOf(outerType.name() + "_NATURAL_JOIN");
            } while (!handler.getOption(node) || !Randomly.getBooleanWithSmallProbability());
            handler.addScore(node);
            return outerType;
        }
    }

    public GeneralJoin(TableReferenceNode<GeneralExpression, GeneralTable> leftTable,
            TableReferenceNode<GeneralExpression, GeneralTable> rightTable, JoinType joinType,
            Node<GeneralExpression> whereCondition) {
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.joinType = joinType;
        this.onCondition = whereCondition;
    }

    public TableReferenceNode<GeneralExpression, GeneralTable> getLeftTable() {
        return leftTable;
    }

    public TableReferenceNode<GeneralExpression, GeneralTable> getRightTable() {
        return rightTable;
    }

    public JoinType getJoinType() {
        return joinType;
    }

    public Node<GeneralExpression> getOnCondition() {
        return onCondition;
    }

    private void setOuterType(OuterType outerType) {
        this.outerType = outerType;
    }

    public OuterType getOuterType() {
        return outerType;
    }

    public static List<Node<GeneralExpression>> getJoins(
            List<TableReferenceNode<GeneralExpression, GeneralTable>> tableList, GeneralGlobalState globalState) {
        List<Node<GeneralExpression>> joinExpressions = new ArrayList<>();
        GeneralErrorHandler handler = globalState.getHandler();
        while (tableList.size() >= 2 && Randomly.getBooleanWithRatherLowProbability() && handler.getOption(GeneratorNode.JOIN)) {
            TableReferenceNode<GeneralExpression, GeneralTable> leftTable = tableList.remove(0);
            TableReferenceNode<GeneralExpression, GeneralTable> rightTable = tableList.remove(0);
            List<GeneralColumn> columns = new ArrayList<>(leftTable.getTable().getColumns());
            columns.addAll(rightTable.getTable().getColumns());
            GeneralExpressionGenerator joinGen = new GeneralExpressionGenerator(globalState).setColumns(columns);
            JoinType joinType = JoinType.getRandomByOptions(handler);
            switch (joinType) {
            case INNER:
                joinExpressions.add(GeneralJoin.createInnerJoin(leftTable, rightTable, joinGen.generateExpression()));
                break;
            case NATURAL:
                joinExpressions.add(GeneralJoin.createNaturalJoin(leftTable, rightTable, OuterType.getRandomByOptions(handler)));
                break;
            case LEFT:
                joinExpressions
                        .add(GeneralJoin.createLeftOuterJoin(leftTable, rightTable, joinGen.generateExpression()));
                break;
            case RIGHT:
                joinExpressions
                        .add(GeneralJoin.createRightOuterJoin(leftTable, rightTable, joinGen.generateExpression()));
                break;
            default:
                throw new AssertionError();
            }
        }
        return joinExpressions;
    }

    public static GeneralJoin createRightOuterJoin(TableReferenceNode<GeneralExpression, GeneralTable> left,
            TableReferenceNode<GeneralExpression, GeneralTable> right, Node<GeneralExpression> predicate) {
        return new GeneralJoin(left, right, JoinType.RIGHT, predicate);
    }

    public static GeneralJoin createLeftOuterJoin(TableReferenceNode<GeneralExpression, GeneralTable> left,
            TableReferenceNode<GeneralExpression, GeneralTable> right, Node<GeneralExpression> predicate) {
        return new GeneralJoin(left, right, JoinType.LEFT, predicate);
    }

    public static GeneralJoin createInnerJoin(TableReferenceNode<GeneralExpression, GeneralTable> left,
            TableReferenceNode<GeneralExpression, GeneralTable> right, Node<GeneralExpression> predicate) {
        return new GeneralJoin(left, right, JoinType.INNER, predicate);
    }

    public static Node<GeneralExpression> createNaturalJoin(TableReferenceNode<GeneralExpression, GeneralTable> left,
            TableReferenceNode<GeneralExpression, GeneralTable> right, OuterType naturalJoinType) {
        GeneralJoin join = new GeneralJoin(left, right, JoinType.NATURAL, null);
        join.setOuterType(naturalJoinType);
        return join;
    }

}
