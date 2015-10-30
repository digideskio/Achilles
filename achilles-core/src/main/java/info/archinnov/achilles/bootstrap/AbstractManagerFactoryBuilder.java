/*
 * Copyright (C) 2012-2015 DuyHai DOAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.archinnov.achilles.bootstrap;

import static info.archinnov.achilles.configuration.ConfigurationParameters.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.archinnov.achilles.configuration.ArgumentExtractor;
import info.archinnov.achilles.configuration.ConfigurationParameters;
import info.archinnov.achilles.internals.cache.StatementsCache;
import info.archinnov.achilles.internals.context.ConfigurationContext;
import info.archinnov.achilles.internals.runtime.AbstractManagerFactory;
import info.archinnov.achilles.internals.types.ConfigMap;
import info.archinnov.achilles.json.JacksonMapperFactory;
import info.archinnov.achilles.type.SchemaNameProvider;
import info.archinnov.achilles.type.interceptor.Interceptor;
import info.archinnov.achilles.type.strategy.InsertStrategy;
import info.archinnov.achilles.validation.Validator;

public abstract class AbstractManagerFactoryBuilder<T extends AbstractManagerFactoryBuilder<T>> {

    protected ConfigMap configMap = new ConfigMap();
    protected Cluster cluster;


    protected AbstractManagerFactoryBuilder(Cluster cluster) {
        this.cluster = cluster;
        Validator.validateNotNull(cluster, "Cluster object should not be null");
    }


    protected static ConfigurationContext buildConfigContext(Cluster cluster, ConfigMap configMap) {
        return ArgumentExtractor.initConfigContext(cluster, configMap);
    }

    protected abstract T getThis();

    public abstract <M extends AbstractManagerFactory> M build();

    /**
     * Define a pre-configured Jackson Object Mapper for serialization of
     * non-primitive types
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#json-serialization" target="_blank">JSON serialization</a>
     */
    public T withJacksonMapper(ObjectMapper objectMapper) {
        configMap.put(JACKSON_MAPPER, objectMapper);
        return getThis();
    }

    /**
     * Define a pre-configured map of Jackson Object Mapper for
     * serialization of non-primitive types
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#json-serialization" target="_blank">JSON serialization</a>
     */
    public T withJacksonMapperFactory(JacksonMapperFactory jacksonMapperFactory) {
        configMap.put(JACKSON_MAPPER_FACTORY, jacksonMapperFactory);
        return getThis();
    }

    /**
     * Define the default Consistency level to be used for all READ
     * operations
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#consistency-level" target="_blank">Consistency configuration</a>
     */
    public T withDefaultReadConsistency(ConsistencyLevel defaultReadConsistency) {
        configMap.put(CONSISTENCY_LEVEL_READ_DEFAULT, defaultReadConsistency);
        return getThis();
    }

    /**
     * Define the default Consistency level to be used for all WRITE
     * operations
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#consistency-level" target="_blank">Consistency configuration</a>
     */
    public T withDefaultWriteConsistency(ConsistencyLevel defaultWriteConsistency) {
        configMap.put(CONSISTENCY_LEVEL_WRITE_DEFAULT, defaultWriteConsistency);
        return getThis();
    }

    /**
     * Define the default Consistency level map to be used for all READ
     * operations The map keys represent table names and values represent
     * the corresponding consistency level
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#consistency-level" target="_blank">Consistency configuration</a>
     */
    public T withDefaultReadConsistencyMap(Map<String, ConsistencyLevel> readConsistencyMap) {
        configMap.put(CONSISTENCY_LEVEL_READ_MAP, readConsistencyMap);
        return getThis();
    }

    /**
     * Define the default Consistency level map to be used for all WRITE
     * operations The map keys represent table names and values represent
     * the corresponding consistency level
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#consistency-level" target="_blank">Consistency configuration</a>
     */
    public T withDefaultWriteConsistencyMap(Map<String, ConsistencyLevel> writeConsistencyMap) {
        configMap.put(CONSISTENCY_LEVEL_WRITE_MAP, writeConsistencyMap);
        return getThis();
    }

    /**
     * Whether Achilles should force table creation if they do not already
     * exist in the keyspace This flag is useful for dev only. <strong>It
     * should be disabled in production</strong>
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#ddl" target="_blank">Table generation</a>
     */
    public T doForceSchemaCreation(boolean forceSchemaCreation) {
        configMap.put(FORCE_SCHEMA_GENERATION, forceSchemaCreation);
        return getThis();
    }

    /**
     * Define the pre-configured {@code com.datastax.driver.core.Session} object to
     * be used instead of creating a new one
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#native-session" target="_blank">Native Session</a>
     */
    public T withNativeSession(Session nativeSession) {
        configMap.put(NATIVE_SESSION, nativeSession);
        return getThis();
    }

    /**
     * Define the keyspace name to be used by Achilles. It is mandatory if you
     * do not define the keyspace name statically on your entity with the annotation
     * {@literal @}Entity
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#keyspace" target="_blank">Keyspace</a>
     */
    public T withDefaultKeyspaceName(String defaultKeyspaceName) {
        configMap.put(KEYSPACE_NAME, defaultKeyspaceName);
        return getThis();
    }

    /**
     * Provide a list of event interceptors
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#events-interceptors" target="_blank">Event interceptors</a>
     */
    public T withEventInterceptors(List<Interceptor<?>> interceptors) {
        configMap.put(EVENT_INTERCEPTORS, interceptors);
        return getThis();
    }

    /**
     * Activate Bean Validation (JSR303)
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#bean-validation" target="_blank">Bean validation</a>
     */
    public T withBeanValidation(boolean enableBeanValidation) {
        configMap.put(BEAN_VALIDATION_ENABLE, enableBeanValidation);
        return getThis();
    }

    /**
     * Activate Bean Validation (JSR303)
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#bean-validation" target="_blank">Bean validation</a>
     */
    public T withPostLoadBeanValidation(boolean enablePostLoadBeanValidation) {
        if (enablePostLoadBeanValidation) {
            Validator.validateTrue(configMap.containsKey(BEAN_VALIDATION_ENABLE) && configMap.<Boolean>getTyped(BEAN_VALIDATION_ENABLE),
                    "Before activating Post Load Bean Validation, you should activate first Bean Validation by calling 'withBeanValidation(true)' ");
        }
        configMap.put(POST_LOAD_BEAN_VALIDATION_ENABLE, enablePostLoadBeanValidation);
        return getThis();
    }

    /**
     * Provide custom validator for Bean Validation (JSR303)
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#bean-validation" target="_blank">Bean validation</a>
     */
    public T withBeanValidator(javax.validation.Validator validator) {
        if (validator != null) {
            configMap.put(BEAN_VALIDATION_VALIDATOR, validator);
            configMap.put(BEAN_VALIDATION_ENABLE, true);
        }
        return getThis();
    }

    /**
     * Specify maximum size for the internal prepared statements LRU cache.
     * If the cache is full, oldest prepared statements will be dropped, leading to unexpected behavior.
     * <br/><br/>
     * Default value is <strong>5000</strong>, which is a pretty safe limit.
     * <br/><br/>
     * For information, only selects on counter fields and updates are put into the cache because they cannot be
     * prepared before hand since the updated properties are not known in advance.
     *
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Configuration-Parameters#prepared-statements-cache" target="_blank">Prepared statements cache</a>
     */
    public T withMaxPreparedStatementCacheSize(int maxPreparedStatementCacheSize) {
        configMap.put(PREPARED_STATEMENTS_CACHE_SIZE, maxPreparedStatementCacheSize);
        return getThis();
    }

    /**
     * Define the global insert strategy
     *
     * @param globalInsertStrategy
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Insert-Strategy" target="_blank">Insert Strategy</a>
     */
    public T withGlobalInsertStrategy(InsertStrategy globalInsertStrategy) {
        configMap.put(GLOBAL_INSERT_STRATEGY, globalInsertStrategy);
        return getThis();
    }

    /**
     * Define the schema name provider to be used instead of default keyspace/table names
     *
     * @param schemaNameProvider
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Insert-Strategy" target="_blank">Insert Strategy</a>
     */
    public T withSchemaNameProvider(SchemaNameProvider schemaNameProvider) {
        configMap.put(SCHEMA_NAME_PROVIDER, schemaNameProvider);
        return getThis();
    }

    /**
     * Pass an ExecutorService (ThreadPool) to Achilles to be used internally for asynchronous operations.
     * <br/>
     * If omitted, the default ExecutorService is configured as below:
     * <pre class="code"><code class="java">
     * new ThreadPoolExecutor(5, 20, 60, TimeUnit.SECONDS,
     * new LinkedBlockingQueue<Runnable>(1000),
     * new DefaultExecutorThreadFactory())
     * </code></pre>
     *
     * @param executorService an executor service (thread pool) to be used by Achilles for internal for asynchronous operations
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Asynchronous-Operations">Asynchronous Operations</a>
     */
    public T withDefaultExecutorService(ExecutorService executorService) {
        configMap.put(EXECUTOR_SERVICE, executorService);
        return getThis();
    }

    /**
     * Define the min thread count for the ExecutorService (ThreadPool) to be used internally for asynchronous operations.
     * <br/>
     * The default ExecutorService is configured as below:
     * <pre class="code"><code class="java">
     * // Create proxy
     * new ThreadPoolExecutor(minThreadCount, 20, 60, TimeUnit.SECONDS,
     * new LinkedBlockingQueue<Runnable>(1000),
     * new DefaultExecutorThreadFactory())
     * </code></pre>
     *
     * @param minThreadCount min thread count for the executor service
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Asynchronous-Operations">Asynchronous Operations</a>
     */
    public T withExecutorServiceMinThreadCount(int minThreadCount) {
        configMap.put(DEFAULT_EXECUTOR_SERVICE_MIN_THREAD, minThreadCount);
        return getThis();
    }

    /**
     * Define the max thread count for the ExecutorService (ThreadPool) to be used internally for asynchronous operations.
     * <br/>
     * The default ExecutorService is configured as below:
     * <pre class="code"><code class="java">
     * // Create proxy
     * new ThreadPoolExecutor(5, maxThreadCount, 60, TimeUnit.SECONDS,
     * new LinkedBlockingQueue<Runnable>(1000),
     * new DefaultExecutorThreadFactory())
     * </code></pre>
     *
     * @param maxThreadCount max thread count for the executor service
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Asynchronous-Operations">Asynchronous Operations</a>
     */
    public T withExecutorServiceMaxThreadCount(int maxThreadCount) {
        configMap.put(DEFAULT_EXECUTOR_SERVICE_MAX_THREAD, maxThreadCount);
        return getThis();
    }

    /**
     * Define the thread keep-alive duration in second for the ExecutorService (ThreadPool) to be used internally for asynchronous operations.
     * <br/>
     * The default ExecutorService is configured as below:
     * <pre class="code"><code class="java">
     * // Create proxy
     * new ThreadPoolExecutor(5, 20, keepAliveDuration, TimeUnit.SECONDS,
     * new LinkedBlockingQueue<Runnable>(1000),
     * new DefaultExecutorThreadFactory())
     * </code></pre>
     *
     * @param keepAliveDuration thread keep-alive duration in second for the executor service
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Asynchronous-Operations">Asynchronous Operations</a>
     */
    public T withExecutorServiceThreadKeepAliveDuration(int keepAliveDuration) {
        configMap.put(DEFAULT_EXECUTOR_SERVICE_THREAD_KEEPALIVE, keepAliveDuration);
        return getThis();
    }

    /**
     * Define the LinkedBlockingQueue size for the ExecutorService (ThreadPool) to be used internally for asynchronous operations.
     * <br/>
     * The default ExecutorService is configured as below:
     * <pre class="code"><code class="java">
     * // Create proxy
     * new ThreadPoolExecutor(5, 20, 60, TimeUnit.SECONDS,
     * new LinkedBlockingQueue<Runnable>(threadQueueSize),
     * new DefaultExecutorThreadFactory())
     * </code></pre>
     *
     * @param threadQueueSize the LinkedBlockingQueue size for the executor service
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Asynchronous-Operations">Asynchronous Operations</a>
     */
    public T withExecutorServiceThreadQueueSize(int threadQueueSize) {
        configMap.put(DEFAULT_EXECUTOR_SERVICE_QUEUE_SIZE, threadQueueSize);
        return getThis();
    }

    /**
     * Define the Thread Factory for the ExecutorService (ThreadPool) to be used internally for asynchronous operations.
     * <br/>
     * The default ExecutorService is configured as below:
     * <pre class="code"><code class="java">
     * // Create proxy
     * new ThreadPoolExecutor(5, 20, 60, TimeUnit.SECONDS,
     * new LinkedBlockingQueue<Runnable>(threadQueueSize),
     * new DefaultExecutorThreadFactory())
     * </code></pre>
     *
     * @param factory the thread factory
     * @return ManagerFactoryBuilder
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Asynchronous-Operations">Asynchronous Operations</a>
     */
    public T withExecutorServiceThreadFactory(ThreadFactory factory) {
        configMap.put(DEFAULT_EXECUTOR_SERVICE_THREAD_FACTORY, factory);
        return getThis();
    }

    /**
     * Define a list of entities to be managed by <strong>Achilles</strong>.
     * Specifically, schema validation will be performed at bootstrap for those entities
     *
     * @param entityClasses entities to be managed by <strong>Achilles</strong>
     * @return ManagerFactoryBuilder
     */
    public T withManagedEntityClasses(List<Class<?>> entityClasses) {
        configMap.put(MANAGED_ENTITIES, entityClasses);
        return getThis();
    }

    /**
     * Define a list of entities to be managed by <strong>Achilles</strong>.
     * Specifically, schema validation will be performed at bootstrap for those entities
     *
     * @param entityClasses entities to be managed by <strong>Achilles</strong>
     * @return ManagerFactoryBuilder
     */
    public T withManagedEntityClasses(Class<?>... entityClasses) {
        configMap.put(MANAGED_ENTITIES, Arrays.asList(entityClasses));
        return getThis();
    }

    /**
     * Define the statements cache object to be used for prepared statements. This object is an instance of
     * {@link info.archinnov.achilles.internals.cache.StatementsCache}
     *
     * @param statementsCache cache object for the prepared statements
     * @return ManagerFactoryBuilder
     */
    public T withStatementsCache(StatementsCache statementsCache) {
        configMap.put(STATEMENTS_CACHE, statementsCache);
        return getThis();
    }


    /**
     * Pass an arbitrary parameter to configure Achilles
     *
     * @param parameter an instance of the ConfigurationParameters enum
     * @param value     the value of the parameter
     * @return ManagerFactoryBuilder
     */
    public T withParameter(ConfigurationParameters parameter, Object value) {
        configMap.put(parameter, value);
        return getThis();
    }
}
