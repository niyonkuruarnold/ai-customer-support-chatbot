package com.codafriqa.ai_customer_support_chatbot.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Holds the Spring {@link ApplicationContext} in a static field so that
 * non-Spring-managed classes (JPA {@code @EntityListeners}) can look up
 * beans at runtime.
 *
 * <p>This is the recommended pattern from the Spring documentation for
 * accessing beans from JPA entity listeners, which are instantiated by
 * JPA (Hibernate) rather than by the Spring container.
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /**
     * Look up a bean by its class.
     *
     * @throws IllegalStateException if the context has not been initialized yet
     */
    public static <T> T getBean(Class<T> clazz) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext has not been initialized yet");
        }
        return context.getBean(clazz);
    }
}
