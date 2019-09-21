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
package org.jdbi.v3.core.statement;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.argument.NullArgument;
import org.jdbi.v3.core.internal.exceptions.Unchecked;

/**
 * Used for invoking stored procedures.
 */
public class Call extends SqlStatement<Call> {
    private final List<OutParam> outParams = new ArrayList<>();

    public Call(Handle handle, String sql) {
        super(handle, sql);
    }

    /**
     * Register a positional output parameter.
     * @param position the parameter position (zero-based)
     * @param sqlType an SQL type constant as defined by {@link java.sql.Types} or by the JDBC vendor.
     * @return self
     */
    public Call registerOutParameter(int position, int sqlType) {
        return registerOutParameter(position, sqlType, null);
    }

    /**
     * Register a positional output parameter.
     * @param position the parameter position (zero-based)
     * @param sqlType an SQL type constant as defined by {@link java.sql.Types} or by the JDBC vendor.
     * @param mapper a mapper which converts the {@link CallableStatement} to a desired output type.
     * @return self
     */
    public Call registerOutParameter(int position, int sqlType, CallableStatementMapper mapper) {
        outParams.add(new PositionalOutParam(sqlType, mapper, position));
        return this;
    }

    /**
     * Register a named output parameter.
     * @param name the parameter name
     * @param sqlType an SQL type constant as defined by {@link java.sql.Types} or by the JDBC vendor.
     * @return self
     */
    public Call registerOutParameter(String name, int sqlType) {
        return registerOutParameter(name, sqlType, null);
    }

    /**
     * Register a named output parameter.
     * @param name the parameter name
     * @param sqlType an SQL type constant as defined by {@link java.sql.Types} or by the JDBC vendor.
     * @param mapper a mapper which converts the {@link CallableStatement} to a desired output type.
     * @return self
     */
    public Call registerOutParameter(String name, int sqlType, CallableStatementMapper mapper) {
        outParams.add(new NamedOutParam(sqlType, mapper, name));
        return this;
    }

    /**
     * Invoke the callable statement.  Note that the statement will be {@link #close()}d,
     * so cursor-typed values may not work.
     * @return the output parameters resulting from the invocation.
     */
    public OutParameters invoke() {
        return invoke(Function.identity());
    }

    /**
     * Invoke the callable statement and process its {@link OutParameters} results.
     */
    public void invoke(Consumer<OutParameters> resultConsumer) {
        invoke((Function<OutParameters, Void>) r -> {
            resultConsumer.accept(r);
            return null;
        });
    }

    @Override
    void beforeBinding() {
        super.beforeBinding();
        outParams.forEach(Unchecked.consumer(OutParam::preBind));
    }

    @Override
    void beforeExecution() {
        super.beforeExecution();
        outParams.forEach(Unchecked.consumer(OutParam::register));
    }

    /**
     * Invoke the callable statement and process its {@link OutParameters} results,
     * returning a computed value of type {@code T}.
     */
    public <T> T invoke(Function<OutParameters, T> resultComputer) {
        try {
            internalExecute();
            OutParameters out = new OutParameters(getContext());
            for (OutParam param : outParams) {
                out.getMap().put(
                        param.resultKey(),
                        param.map((CallableStatement) stmt));
            }
            return resultComputer.apply(out);
        } finally {
            close();
        }
    }

    abstract class OutParam {
        final int sqlType;
        final CallableStatementMapper mapper;

        OutParam(int sqlType, CallableStatementMapper mapper) {
            this.sqlType = sqlType;
            this.mapper = mapper;
        }

        void register() throws SQLException {
            register((CallableStatement) stmt);
        }

        abstract void preBind();
        abstract void register(CallableStatement stmt) throws SQLException;
        abstract Object resultKey();

        abstract Object map(CallableStatement stmt);
    }

    class PositionalOutParam extends OutParam {
        private final int position;

        PositionalOutParam(int sqlType, CallableStatementMapper mapper, int position) {
            super(sqlType, mapper);
            this.position = position;
        }

        @Override
        void preBind() {
            if (!getBinding().findForPosition(position).isPresent()) {
                getBinding().addPositional(position, new NullArgument(sqlType));
            }
        }

        @Override
        void register(CallableStatement stmt) throws SQLException {
            stmt.registerOutParameter(position + 1, sqlType);
        }

        @Override
        Object resultKey() {
            return position;
        }

        @Override
        Object map(CallableStatement stmt) {
            final int jdbcPos = position + 1;
            try {
                if (mapper != null) {
                    return mapper.map(jdbcPos, stmt);
                }
                switch (sqlType) {
                    case Types.CLOB:
                    case Types.VARCHAR:
                    case Types.LONGNVARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.NCLOB:
                    case Types.NVARCHAR:
                        return stmt.getString(jdbcPos);
                    case Types.BLOB:
                    case Types.VARBINARY:
                        return stmt.getBytes(jdbcPos);
                    case Types.SMALLINT:
                        return stmt.getShort(jdbcPos);
                    case Types.INTEGER:
                        return stmt.getInt(jdbcPos);
                    case Types.BIGINT:
                        return stmt.getLong(jdbcPos);
                    case Types.TIMESTAMP:
                        case Types.TIME:
                        return stmt.getTimestamp(jdbcPos);
                    case Types.DATE:
                        return stmt.getDate(jdbcPos);
                    case Types.FLOAT:
                        return stmt.getFloat(jdbcPos);
                    case Types.DECIMAL:
                    case Types.DOUBLE:
                        return stmt.getDouble(jdbcPos);
                    default:
                        return stmt.getObject(jdbcPos);
                }
            } catch (SQLException e) {
                throw new UnableToExecuteStatementException("Could not get OUT parameter from statement", e, getContext());
            }
        }
    }

    class NamedOutParam extends OutParam {
        private final String name;

        NamedOutParam(int sqlType, CallableStatementMapper mapper, String name) {
            super(sqlType, mapper);
            this.name = name;
        }

        @Override
        void preBind() {
            if (!getBinding().findForName(name, getContext()).isPresent()) {
                getBinding().addNamed(name, new NullArgument(sqlType));
            }
        }

        @Override
        void register(CallableStatement stmt) throws SQLException {
            stmt.registerOutParameter(name, sqlType);
        }

        @Override
        Object resultKey() {
            return name;
        }

        @Override
        Object map(CallableStatement stmt) {
            try {
                if (mapper != null) {
                    return mapper.map(name, stmt);
                }
                switch (sqlType) {
                    case Types.CLOB:
                    case Types.VARCHAR:
                    case Types.LONGNVARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.NCLOB:
                    case Types.NVARCHAR:
                        return stmt.getString(name);
                    case Types.BLOB:
                    case Types.VARBINARY:
                        return stmt.getBytes(name);
                    case Types.SMALLINT:
                        return stmt.getShort(name);
                    case Types.INTEGER:
                        return stmt.getInt(name);
                    case Types.BIGINT:
                        return stmt.getLong(name);
                    case Types.TIMESTAMP:
                        case Types.TIME:
                        return stmt.getTimestamp(name);
                    case Types.DATE:
                        return stmt.getDate(name);
                    case Types.FLOAT:
                        return stmt.getFloat(name);
                    case Types.DECIMAL:
                    case Types.DOUBLE:
                        return stmt.getDouble(name);
                    default:
                        return stmt.getObject(name);
                }
            } catch (SQLException e) {
                throw new UnableToExecuteStatementException("Could not get OUT parameter from statement", e, getContext());
            }
        }
    }
}
