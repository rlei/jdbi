/*
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
package org.jdbi.v3.sqlobject.internal;

import java.util.function.Function;

import org.jdbi.v3.sqlobject.Handler;

public final class GeneratedSqlObjectInitData {
    public static final ThreadLocal<Function<HandlerKey, Handler>> INITIALIZER = new ThreadLocal<>();

    private GeneratedSqlObjectInitData() {}

    public static Function<HandlerKey, Handler> initializer() {
        final Function<HandlerKey, Handler> result = INITIALIZER.get();
        if (result == null) {
            throw new IllegalStateException("Implemented SqlObject types must be initialized by SqlObjectFactory");
        }
        return result;
    }
}
