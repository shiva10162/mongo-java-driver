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

package com.mongodb.async.client;

import com.mongodb.Block;
import com.mongodb.Function;
import com.mongodb.MongoNamespace;
import com.mongodb.ReadPreference;
import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.client.model.FindOptions;
import com.mongodb.client.model.MapReduceAction;
import com.mongodb.operation.AsyncOperationExecutor;
import com.mongodb.operation.MapReduceStatistics;
import com.mongodb.operation.MapReduceToCollectionOperation;
import com.mongodb.operation.MapReduceWithInlineResultsOperation;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.BsonJavaScript;
import org.bson.codecs.configuration.CodecRegistry;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static com.mongodb.ReadPreference.primary;
import static com.mongodb.assertions.Assertions.notNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class MapReduceIterableImpl<T> implements MapReduceIterable<T> {
    private final MongoNamespace namespace;
    private final Class<T> clazz;
    private final ReadPreference readPreference;
    private final CodecRegistry codecRegistry;
    private final AsyncOperationExecutor executor;
    private final String mapFunction;
    private final String reduceFunction;

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

    MapReduceIterableImpl(final MongoNamespace namespace, final Class<T> clazz, final CodecRegistry codecRegistry,
                          final ReadPreference readPreference, final AsyncOperationExecutor executor, final String mapFunction,
                          final String reduceFunction) {
        this.namespace = notNull("namespace", namespace);
        this.clazz = notNull("clazz", clazz);
        this.codecRegistry = notNull("codecRegistry", codecRegistry);
        this.readPreference = notNull("readPreference", readPreference);
        this.executor = notNull("executor", executor);
        this.mapFunction = notNull("mapFunction", mapFunction);
        this.reduceFunction = notNull("reduceFunction", reduceFunction);
    }

    @Override
    public MapReduceIterable<T> collectionName(final String collectionName) {
        this.collectionName = notNull("collectionName", collectionName);
        this.inline = false;
        return this;
    }

    @Override
    public MapReduceIterable<T> finalizeFunction(final String finalizeFunction) {
        this.finalizeFunction = finalizeFunction;
        return this;
    }

    @Override
    public MapReduceIterable<T> scope(final Object scope) {
        this.scope = scope;
        return this;
    }

    @Override
    public MapReduceIterable<T> sort(final Object sort) {
        this.sort = sort;
        return this;
    }

    @Override
    public MapReduceIterable<T> filter(final Object filter) {
        this.filter = filter;
        return this;
    }

    @Override
    public MapReduceIterable<T> limit(final int limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public MapReduceIterable<T> jsMode(final boolean jsMode) {
        this.jsMode = jsMode;
        return this;
    }

    @Override
    public MapReduceIterable<T> verbose(final boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    @Override
    public MapReduceIterable<T> maxTime(final long maxTime, final TimeUnit timeUnit) {
        notNull("timeUnit", timeUnit);
        this.maxTimeMS = TimeUnit.MILLISECONDS.convert(maxTime, timeUnit);
        return this;
    }

    @Override
    public MapReduceIterable<T> action(final MapReduceAction action) {
        this.action = action;
        return this;
    }

    @Override
    public MapReduceIterable<T> databaseName(final String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    @Override
    public MapReduceIterable<T> sharded(final boolean sharded) {
        this.sharded = sharded;
        return this;
    }

    @Override
    public MapReduceIterable<T> nonAtomic(final boolean nonAtomic) {
        this.nonAtomic = nonAtomic;
        return this;
    }

    @Override
    public MapReduceIterable<T> batchSize(final int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    @Override
    public void toCollection(final SingleResultCallback<Void> callback) {
        if (inline) {
            throw new IllegalArgumentException("The options must specify a non-inline result");
        }
        executor.execute(createMapReduceToCollectionOperation(),
                new SingleResultCallback<MapReduceStatistics>() {
                    @Override
                    public void onResult(final MapReduceStatistics result, final Throwable t) {
                        callback.onResult(null, t);
                    }
                });
    }

    @Override
    public void first(final SingleResultCallback<T> callback) {
        execute().first(callback);
    }

    @Override
    public void forEach(final Block<? super T> block, final SingleResultCallback<Void> callback) {
        execute().forEach(block, callback);
    }

    @Override
    public <A extends Collection<? super T>> void into(final A target, final SingleResultCallback<A> callback) {
        execute().into(target, callback);
    }

    @Override
    public <U> MongoIterable<U> map(final Function<T, U> mapper) {
        return new MappingIterable<T, U>(this, mapper);
    }

    @Override
    public void batchCursor(final SingleResultCallback<AsyncBatchCursor<T>> callback) {
        execute().batchCursor(callback);
    }

    MongoIterable<T> execute() {
        if (inline) {
            MapReduceWithInlineResultsOperation<T> operation =
                    new MapReduceWithInlineResultsOperation<T>(namespace,
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
            return new OperationIterable<T>(operation, readPreference, executor);
        } else {
            MapReduceToCollectionOperation operation = createMapReduceToCollectionOperation();

            String dbName = databaseName != null ? databaseName : namespace.getDatabaseName();
            MongoIterable<T> delegated = new FindIterableImpl<T>(new MongoNamespace(dbName, collectionName),
                    clazz, codecRegistry, primary(), executor, new BsonDocument(), new FindOptions()).batchSize(batchSize);
            return new AwaitingWriteOperationIterable<T, MapReduceStatistics>(operation, executor, delegated);
        }
    }

    private BsonDocument asBson(final Object document) {
        return BsonDocumentWrapper.asBsonDocument(document, codecRegistry);
    }

    private MapReduceToCollectionOperation createMapReduceToCollectionOperation() {
        MapReduceToCollectionOperation operation = new MapReduceToCollectionOperation(namespace, new BsonJavaScript(mapFunction),
                new BsonJavaScript(reduceFunction), collectionName)
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
        return operation;
    }
}
