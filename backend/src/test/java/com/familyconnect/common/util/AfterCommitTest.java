package com.familyconnect.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void runsImmediatelyWithoutTransaction() {
        List<String> calls = new ArrayList<>();
        AfterCommit.runQuietly(() -> calls.add("run"), ex -> calls.add("error"));
        assertThat(calls).containsExactly("run");
    }

    @Test
    void defersUntilCommitAndSwallowsFailure() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> calls = new ArrayList<>();
        AfterCommit.runQuietly(() -> {
            calls.add("run");
            throw new IllegalStateException("AI lỗi");
        }, ex -> calls.add("error:" + ex.getMessage()));
        assertThat(calls).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(calls).containsExactly("run", "error:AI lỗi");
    }
}
