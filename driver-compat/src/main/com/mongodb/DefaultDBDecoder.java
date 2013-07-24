/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb;

import org.bson.BasicBSONDecoder;

import java.io.IOException;
import java.io.InputStream;

/**
 * TODO Documentation
 */
public class DefaultDBDecoder extends BasicBSONDecoder implements DBDecoder {

    @Override
    public DBCallback getDBCallback(final DBCollection collection) {
        return new DefaultDBCallback(collection);
    }

    @Override
    public DBObject decode(final InputStream in, final DBCollection collection) throws IOException {
        final DBCallback callback = getDBCallback(collection);
        decode(in, callback);
        return (DBObject) callback.get();
    }

    @Override
    public DBObject decode(final byte[] bytes, final DBCollection collection) {
        final DBCallback callback = getDBCallback(collection);
        decode(bytes, callback);
        return (DBObject) callback.get();
    }

    @Override
    public String toString() {
        return String.format("Decoder{class=%s}", getClass().getName());
    }

    public static final DBDecoderFactory FACTORY = new DBDecoderFactory() {
        @Override
        public DBDecoder create() {
            return new DefaultDBDecoder();
        }
    };
}