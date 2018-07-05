/*
 * Copyright © 2016 AT&T and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.test.common;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import javassist.ClassPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.binding.impl.BindingDOMDataBrokerAdapter;
import org.opendaylight.controller.md.sal.binding.impl.BindingDOMNotificationPublishServiceAdapter;
import org.opendaylight.controller.md.sal.binding.impl.BindingDOMNotificationServiceAdapter;
import org.opendaylight.controller.md.sal.binding.impl.BindingToNormalizedNodeCodec;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.broker.impl.DOMNotificationRouter;
import org.opendaylight.controller.md.sal.dom.broker.impl.SerializedDOMDataBroker;
import org.opendaylight.controller.md.sal.dom.store.impl.InMemoryDOMDataStore;
import org.opendaylight.controller.sal.core.spi.data.DOMStore;
import org.opendaylight.mdsal.binding.dom.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.GeneratedClassLoadingStrategy;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.util.ListenerRegistry;
import org.opendaylight.yangtools.yang.binding.YangModelBindingProvider;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.opendaylight.yangtools.yang.model.api.SchemaContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataStoreContextImpl implements DataStoreContext {

    private static final Logger LOG = LoggerFactory.getLogger(DataStoreContextImpl.class);

    private final Map<LogicalDatastoreType, DOMStore> datastores;
    private final SchemaContextHolder mockedSchemaContext;
    private final DOMNotificationRouter domNotificationRouter;
    private final DOMDataBroker domDataBroker;
    private final DataBroker dataBroker;
    private final NotificationService notificationService;
    private final NotificationPublishService notificationPublishService;

    public DataStoreContextImpl() {
        this(false);
    }

    public DataStoreContextImpl(boolean fromClasspath) {
        this.mockedSchemaContext = new SchemaContextHolder(fromClasspath);
        this.datastores = createDatastores();
        this.domNotificationRouter = DOMNotificationRouter.create(16);
        this.domDataBroker = createDOMDataBroker();
        this.dataBroker = createDataBroker();
        this.notificationService = createNotificationService();
        this.notificationPublishService = createNotificationPublishService();
        for (ListenerRegistration<SchemaContextListener> listener : mockedSchemaContext.listeners) {
            listener.getInstance().onGlobalContextUpdated(mockedSchemaContext.schemaContext);
        }
    }

    @Override
    public DataBroker getDataBroker() {
        return this.dataBroker;
    }

    @Override
    public DOMDataBroker getDOMDataBroker() {
        return this.domDataBroker;
    }

    @Override
    public NotificationService createNotificationService() {
        return new BindingDOMNotificationServiceAdapter(this.mockedSchemaContext.bindingStreamCodecs,
                this.domNotificationRouter);
    }

    @Override
    public NotificationPublishService createNotificationPublishService() {
        return new BindingDOMNotificationPublishServiceAdapter(this.mockedSchemaContext.bindingToNormalized,
                this.domNotificationRouter);
    }

    @Override
    public SchemaContext getSchemaContext() {
        return mockedSchemaContext.schemaContext;
    }

    @Override
    public BindingNormalizedNodeCodecRegistry getBindingToNormalizedNodeCodec() {
        return mockedSchemaContext.bindingStreamCodecs;
    }

    @Override
    public NotificationService getNotificationService() {
        return notificationService;
    }

    @Override
    public NotificationPublishService getNotificationPublishService() {
        return notificationPublishService;
    }

    private DOMDataBroker createDOMDataBroker() {
        return new SerializedDOMDataBroker(datastores,
                MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()));
    }

    private ListeningExecutorService getDataTreeChangeListenerExecutor() {
        return MoreExecutors.newDirectExecutorService();
    }

    private DataBroker createDataBroker() {
        return new BindingDOMDataBrokerAdapter(getDOMDataBroker(), this.mockedSchemaContext.bindingToNormalized);
    }

    private Map<LogicalDatastoreType, DOMStore> createDatastores() {
        return ImmutableMap.<LogicalDatastoreType, DOMStore>builder()
                .put(LogicalDatastoreType.OPERATIONAL, createOperationalDatastore())
                .put(LogicalDatastoreType.CONFIGURATION, createConfigurationDatastore()).build();
    }

    private DOMStore createConfigurationDatastore() {
        final InMemoryDOMDataStore store = new InMemoryDOMDataStore("CFG", getDataTreeChangeListenerExecutor());
        this.mockedSchemaContext.registerSchemaContextListener(store);
        return store;
    }

    private DOMStore createOperationalDatastore() {
        final InMemoryDOMDataStore store = new InMemoryDOMDataStore("OPER", getDataTreeChangeListenerExecutor());
        this.mockedSchemaContext.registerSchemaContextListener(store);
        return store;
    }

    private final class SchemaContextHolder implements DOMSchemaService, SchemaContextProvider {

        private final SchemaContext schemaContext;
        private final ListenerRegistry<SchemaContextListener> listeners;
        private final BindingNormalizedNodeCodecRegistry bindingStreamCodecs;
        private final BindingToNormalizedNodeCodec bindingToNormalized;
        private final ModuleInfoBackedContext moduleInfoBackedCntxt;

        private SchemaContextHolder(boolean fromClasspath) {
            List<YangModuleInfo> moduleInfos = loadModuleInfos();
            this.moduleInfoBackedCntxt = ModuleInfoBackedContext.create();
            this.schemaContext = getSchemaContext(moduleInfos);
            this.listeners = ListenerRegistry.create();
            this.bindingStreamCodecs = createBindingRegistry();
            GeneratedClassLoadingStrategy loading = GeneratedClassLoadingStrategy.getTCCLClassLoadingStrategy();
            this.bindingToNormalized = new BindingToNormalizedNodeCodec(loading, bindingStreamCodecs);
            registerSchemaContextListener(this.bindingToNormalized);
        }

        @Override
        public SchemaContext getSchemaContext() {
            return schemaContext;
        }

        /**
         * Get the schemacontext from loaded modules on classpath.
         *
         * @param moduleInfos a list of Yang module Infos
         * @return SchemaContext a schema context
         */
        private SchemaContext getSchemaContext(List<YangModuleInfo> moduleInfos) {
            moduleInfoBackedCntxt.addModuleInfos(moduleInfos);
            Optional<SchemaContext> tryToCreateSchemaContext =
                    moduleInfoBackedCntxt.tryToCreateSchemaContext().toJavaUtil();
            if (!tryToCreateSchemaContext.isPresent()) {
                LOG.error("Could not create the initial schema context. Schema context is empty");
                throw new IllegalStateException();
            }
            return tryToCreateSchemaContext.get();
        }

        @Override
        public SchemaContext getGlobalContext() {
            return schemaContext;
        }

        @Override
        public SchemaContext getSessionContext() {
            return schemaContext;
        }

        @Override
        public ListenerRegistration<SchemaContextListener> registerSchemaContextListener(
                SchemaContextListener listener) {
            return listeners.register(listener);
        }

        /**
         * Loads all {@link YangModelBindingProvider} on the classpath.
         *
         * @return list of known {@link YangModuleInfo}
         */
        private List<YangModuleInfo> loadModuleInfos() {
            List<YangModuleInfo> moduleInfos = new LinkedList<>();
            ServiceLoader<YangModelBindingProvider> yangProviderLoader =
                    ServiceLoader.load(YangModelBindingProvider.class);
            for (YangModelBindingProvider yangModelBindingProvider : yangProviderLoader) {
                moduleInfos.add(yangModelBindingProvider.getModuleInfo());
                LOG.debug("Adding [{}] module into known modules", yangModelBindingProvider.getModuleInfo());
            }
            return moduleInfos;
        }

        /**
         * Creates binding registry.
         *
         * @return BindingNormalizedNodeCodecRegistry the resulting binding registry
         */
        private BindingNormalizedNodeCodecRegistry createBindingRegistry() {
            BindingRuntimeContext bindingContext = BindingRuntimeContext.create(moduleInfoBackedCntxt, schemaContext);
            BindingNormalizedNodeCodecRegistry bindingNormalizedNodeCodecRegistry =
                    new BindingNormalizedNodeCodecRegistry(
                            StreamWriterGenerator.create(JavassistUtils.forClassPool(ClassPool.getDefault())));
            bindingNormalizedNodeCodecRegistry.onBindingRuntimeContextUpdated(bindingContext);
            return bindingNormalizedNodeCodecRegistry;
        }
    }
}
