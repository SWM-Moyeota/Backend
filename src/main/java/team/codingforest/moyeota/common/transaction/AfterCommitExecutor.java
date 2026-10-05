package team.codingforest.moyeota.common.transaction;

public interface AfterCommitExecutor {

    void execute(String name, Runnable task);
}
