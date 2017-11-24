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
package org.jdbi.v3.sqlobject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

public class ConcreteImplStubHandlerFactory implements HandlerFactory {
    @Override
    public Optional<Handler> buildHandler(Class<?> sqlObjectType, Method method) {
        if (!Modifier.isAbstract(sqlObjectType.getModifiers()) || Modifier.isAbstract(method.getModifiers())) {
            return Optional.empty();
        }

        // concrete class has an implementation, we will never use the handler.
        return Optional.of((t, a, h) -> {
            throw new UnsupportedOperationException();
        });
    }
}
