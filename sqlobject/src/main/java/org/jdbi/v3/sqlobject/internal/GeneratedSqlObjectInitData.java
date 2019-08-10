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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

import org.jdbi.v3.core.extension.ExtensionMethod;
import org.jdbi.v3.sqlobject.Handler;

public final class GeneratedSqlObjectInitData {
    public static final ThreadLocal<Class<?>> EXTENSION_TYPE = new ThreadLocal<>();
    public static final ThreadLocal<Function<Method, Handler>> INITIALIZER = new ThreadLocal<>();

    private GeneratedSqlObjectInitData() {}

    public static Function<Method, Handler> initializer() {
        final Function<Method, Handler> result = INITIALIZER.get();
        if (result == null) {
            throw new IllegalStateException("Implemented SqlObject types must be initialized by SqlObjectFactory");
        }
        return result;
    }

    public static ExtensionMethod lookupMethod(String methodName, Class<?>... parameterTypes) {
        final Class<?> klass = EXTENSION_TYPE.get();
        return new ExtensionMethod(klass, lookup0(klass, methodName, parameterTypes));
    }

    private static Method lookup0(Class<?> klass, String methodName, Class<?>... parameterTypes) {
        try {
            return klass.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException | SecurityException e) {
            try {
                return klass.getDeclaredMethod(methodName, parameterTypes);
            } catch (Exception x) {
                e.addSuppressed(x);
            }
            throw new IllegalStateException(
                    String.format("can't find %s#%s%s", klass.getName(), methodName, Arrays.asList(parameterTypes)), e);
        }
    }
}
