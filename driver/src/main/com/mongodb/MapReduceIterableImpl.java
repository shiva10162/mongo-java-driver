/*
 * Copyright 2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb;

import com.mongodb.client.MapReduceIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.FindOptions;
import com.mongodb.client.model.MapReduceAction;
import com.mongodb.operation.MapReduceToCollectionOperation;
import com.mongodb.operation.MapReduceWithInlineResultsOperation;
import com.mongodb.operation.OperationExecutor;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.BsonJavaScript;
import org.bson.codecs.configuration.CodecRegistry;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static com.mongodb.ReadPreference.primary;
import static com.mongodb.assertions.Assertions.notNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class MapReduceIterableImpl<TResult, TDocument> implements MapReduceIterable<TResult> {
    private final MongoNamespace namespace;
    private final Class<TResult> clazz;
    private final ReadPreference readPreference;
    private final CodecRegistry codecRegistry;
    private final OperationExecutor executor;
    private final String mapFunction;
    private final String reduceFunction;
    private final Class<TDocument> collectionClass;

    private boolean inline = true;
    private String collectionName;
    private String finalizeFunction;
    private Object scope;
    private Object filter;
    private Object sort;
    private int limit;
    private boolean jsMode;
    private boolean verbose = true;
    private long maxTimeMS;
    private MapReduceAction action = MapReduceAction.REPLACE;
    private String databaseName;
    private boolean sharded;
    private boolean nonAtomic;
    private int batchSize;

    MapReduceIterableImpl(final MongoNamespace namespace, final Class<TResult> clazz, final Class<TDocument> collectionClass,
                          final CodecRegistry codecRegistry,
                          final ReadPreference readPreference, final OperationExecutor executor, final String mapFunction,
                          final String reduceFunction) {
        this.namespace = notNull("namespace", namespace);
        this.clazz = notNull("clazz", clazz);
        this.collectionClass = notNull("collectionClass", collectionClass);
        this.codecRegistry = notNull("codecRegistry", codecRegistry);
        this.readPreference = notNull("readPreference", readPreference);
        this.executor = notNull("executor", executor);
        this.mapFunction = notNull("mapFunction", mapFunction);
        this.reduceFunction = notNull("reduceFunction", reduceFunction);
    }

    @Override
    public MapReduceIterable<TResult> collectionName(final String collectionName) {
        this.collectionName = notNull("collectionName", collectionName);
        this.inline = false;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> finalizeFunction(final String finalizeFunction) {
        this.finalizeFunction = finalizeFunction;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> scope(final Object scope) {
        this.scope = scope;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> sort(final Object sort) {
        this.sort = sort;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> filter(final Object filter) {
        this.filter = filter;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> limit(final int limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> jsMode(final boolean jsMode) {
        this.jsMode = jsMode;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> verbose(final boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> maxTime(final long maxTime, final TimeUnit timeUnit) {
        notNull("timeUnit", timeUnit);
        this.maxTimeMS = TimeUnit.MILLISECONDS.convert(maxTime, timeUnit);
        return this;
    }

    @Override
    public MapReduceIterable<TResult> action(final MapReduceAction action) {
        this.action = action;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> databaseName(final String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> sharded(final boolean sharded) {
        this.sharded = sharded;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> nonAtomic(final boolean nonAtomic) {
        this.nonAtomic = nonAtomic;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> batchSize(final int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    @Override
    public MongoCursor<TResult> iterator() {
        return execute().iterator();
    }

    @Override
    public TResult first() {
        return execute().first();
    }

    @Override
    public <U> MongoIterable<U> map(final Function<TResult, U> mapper) {
        return new MappingIterable<TResult, U>(this, mapper);
    }

    @Override
    public void forEach(final Block<? super TResult> block) {
        execute().forEach(block);
    }

    @Override
    public <A extends Collection<? super TResult>> A into(final A target) {
        return execute().into(target);
    }

    MongoIterable<TResult> execute() {
        if (inline) {
            MapReduceWithInlineResultsOperation<TResult> operation =
                    new MapReduceWithInlineResultsOperation<TResult>(namespace,
                            new BsonJavaScript(mapFunction),
                            new BsonJavaScript(reduceFunction),
                            codecRegistry.get(clazz))
                            .filter(asBson(filter))
                            .limit(limit)
                            .maxTime(maxTimeMS, MILLISECONDS)
                            .jsMode(jsMode)
                            .scope(asBson(scope))
                            .sort(asBson(sort))
                            .verbose(verbose);
            if (finalizeFunction != null) {
                operation.finalizeFunction(new BsonJavaScript(finalizeFunction));
            }
            return new OperationIterable<TResult>(operation, readPreference, executor);
        } else {
            MapReduceToCollectionOperation operation =
                    new MapReduceToCollectionOperation(namespace, new BsonJavaScript(mapFunction), new BsonJavaScript(reduceFunction),
                            collectionName)
                            .filter(asBson(filter))
                            .limit(limit)
                            .maxTime(maxTimeMS, MILLISECONDS)
                            .jsMode(jsMode)
                            .scope(asBson(scope))
                            .sort(asBson(sort))
                            .verbose(verbose)
                            .action(action.getValue())
                            .nonAtomic(nonAtomic)
                            .sharded(sharded)
                            .databaseName(databaseName);

            if (finalizeFunction != null) {
                operation.finalizeFunction(new BsonJavaScript(finalizeFunction));
            }
            executor.execute(operation);

            String dbName = databaseName != null ? databaseName : namespace.getDatabaseName();
            return new FindIterableImpl<TResult, TDocument>(new MongoNamespace(dbName, collectionName), clazz, collectionClass,
                                                            codecRegistry, primary(), executor, new BsonDocument(),
                                                            new FindOptions()).batchSize(batchSize);
        }
    }

    private BsonDocument asBson(final Object document) {
        return BsonDocumentWrapper.asBsonDocument(document, codecRegistry);
    }

}
