package com.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * 统一认证中心启动类。
 * <p>
 * Mapper 扫描覆盖全部模块（com.auth.**.mapper）；Bean 名用全限定类名生成器，
 * 避免不同模块出现同名 Mapper 时 Bean 名冲突（照抄 ERP 启动约定）。
 */
@SpringBootApplication
@MapperScan(basePackages = "com.auth.**.mapper", nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
