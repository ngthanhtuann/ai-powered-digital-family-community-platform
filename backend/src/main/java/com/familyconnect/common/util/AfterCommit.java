package com.familyconnect.common.util;

import java.util.function.Consumer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Chạy việc phụ (cập nhật chỉ mục AI) sau khi giao dịch hiện tại commit; không có giao dịch thì chạy ngay.
 * ai.md mục 6.1: lỗi của việc phụ chỉ được ghi log, không làm hỏng thao tác chính.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void runQuietly(Runnable action, Consumer<RuntimeException> onError) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException ex) {
                onError.accept(ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safe.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safe.run();
            }
        });
    }
}
