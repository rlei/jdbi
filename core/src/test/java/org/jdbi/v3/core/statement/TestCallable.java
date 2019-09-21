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

import java.sql.Types;

import org.assertj.core.data.Offset;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.rule.PgDatabaseRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestCallable {
    @Rule
    public PgDatabaseRule dbRule = new PgDatabaseRule();

    private Handle h;

    @Before
    public void setUp() {
        h = dbRule.openHandle();
        h.execute("CREATE FUNCTION plus50(IN param INT) RETURNS INT AS $$ BEGIN return param + 50; END; $$ LANGUAGE plpgsql");
        h.execute("CREATE FUNCTION plus100(INOUT param INT) AS $$ BEGIN param := param + 100; END; $$ LANGUAGE plpgsql");
    }

    @After
    public void close() {
        h.close();
    }

    @Test
    public void positionalParam() {
        OutParameters ret = h.createCall("{? = call plus50(?)}")
                .configure(SqlStatements.class, s -> s.setUnusedBindingAllowed(true))
                .registerOutParameter(0, Types.INTEGER)
                .bind(1, 100)
                .invoke();

        Integer expected = 150;
        assertThat(ret.getDouble(0)).isEqualTo(expected, Offset.offset(0.001));
        assertThat(ret.getLong(0).longValue()).isEqualTo(expected.longValue());
        assertThat(ret.getShort(0).shortValue()).isEqualTo(expected.shortValue());
        assertThat(ret.getInt(0).intValue()).isEqualTo(expected.intValue());
        assertThat(ret.getFloat(0).floatValue()).isEqualTo(expected.floatValue(), Offset.offset(0.001f));

        assertThatExceptionOfType(Exception.class).isThrownBy(() -> ret.getDate(1));
        assertThatExceptionOfType(Exception.class).isThrownBy(() -> ret.getDate(2));
    }

    @Test
    public void namedParam() {
        OutParameters ret = h.createCall("{:x = call plus50(:y)}")
                .registerOutParameter("x", Types.INTEGER)
                .bind("y", 100)
                .invoke();

        Integer expected = 150;
        assertThat(ret.getDouble("x")).isEqualTo(expected, Offset.offset(0.001));
        assertThat(ret.getLong("x").longValue()).isEqualTo(expected.longValue());
        assertThat(ret.getShort("x").shortValue()).isEqualTo(expected.shortValue());
        assertThat(ret.getInt("x").intValue()).isEqualTo(expected.intValue());
        assertThat(ret.getFloat("x")).isEqualTo(expected.floatValue());

        assertThatExceptionOfType(Exception.class).isThrownBy(() -> ret.getDate("x"));
        assertThatExceptionOfType(Exception.class).isThrownBy(() -> ret.getDate("y"));
    }

    @Test
    public void nullInout() {
        OutParameters ret = h.createCall("{call plus100(:param)}")
                .registerOutParameter("param", Types.INTEGER)
                .bindNull("param", Types.INTEGER)
                .invoke();

        Integer out = ret.getInt(1);
        assertThat(out).isNull();
    }

    @Test
    public void nonnullInout() {
        OutParameters ret = h.createCall("{call plus100(:param)}")
                .registerOutParameter("param", Types.INTEGER)
                .bind("param", 42)
                .invoke();

        Integer out = ret.getInt(1);
        assertThat(out).isEqualTo(142);
    }
}
