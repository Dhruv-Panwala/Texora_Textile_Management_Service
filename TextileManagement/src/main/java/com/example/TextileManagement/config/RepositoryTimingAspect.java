package com.example.TextileManagement.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RepositoryTimingAspect {
    @Around("execution(* com.example.TextileManagement.repository..*(..)) "
            + "|| execution(* org.springframework.jdbc.core.JdbcOperations.*(..))")
    public Object timeRepositoryCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            RequestTimingContext.recordDatabaseQuery(System.nanoTime() - started);
        }
    }
}
