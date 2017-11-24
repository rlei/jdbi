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
import java.util.Objects;

public class HandlerKey {
    private final String name;
    private final Class<?> returnType;
    private final Class<?>[] parameterTypes;

    public HandlerKey(String name, Class<?> returnType, Class<?>[] parameterTypes) {
        this.name = name;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes.clone();
    }

    public static HandlerKey of(Method method) {
        return new HandlerKey(method.getName(), method.getReturnType(), method.getParameterTypes());
    }
    public static HandlerKey of(String methodName, Class<?> returnType, Class<?>[] parameterTypes) {
        return new HandlerKey(methodName, returnType, parameterTypes);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(parameterTypes);
        result = prime * result + Objects.hash(name, returnType);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        HandlerKey other = (HandlerKey) obj;
        return Objects.equals(name, other.name)
                && Arrays.equals(parameterTypes, other.parameterTypes)
                && Objects.equals(returnType, other.returnType);
    }

    @Override
    public String toString() {
        return "(" + returnType + ')' + name + Arrays.asList(parameterTypes);
    }
}
