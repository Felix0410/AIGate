package com.felix.aigate.support;

import org.testcontainers.containers.MySQLContainer;

/**
 * 单例 MySQL 测试容器（Testcontainers "singleton container" 复用模式）。
 *
 * <p>整个 JVM 只启动一个 MySQL 容器，供所有集成测试类共享，避免
 * {@code @Container} 逐测试类停/启导致的连接失效问题。
 * 容器由 Ryuk 在测试 JVM 退出时统一回收。
 */
public final class MySqlTestContainer {

    public static final MySQLContainer<?> MYSQL;

    static {
        MYSQL = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("aigate_test")
                .withUsername("test")
                .withPassword("test");
        MYSQL.start();
    }

    private MySqlTestContainer() {
    }
}
