/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.blackhole;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.units.Duration;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ConnectorOutputTableHandle;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.security.PrestoPrincipal;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.spi.StandardErrorCode.NOT_FOUND;
import static io.trino.spi.security.PrincipalType.USER;
import static io.trino.testing.TestingConnectorSession.SESSION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestBlackHoleMetadata
{
    private final BlackHoleMetadata metadata = new BlackHoleMetadata();
    private final Map<String, Object> tableProperties = ImmutableMap.of(
            BlackHoleConnector.SPLIT_COUNT_PROPERTY, 0,
            BlackHoleConnector.PAGES_PER_SPLIT_PROPERTY, 0,
            BlackHoleConnector.ROWS_PER_PAGE_PROPERTY, 0,
            BlackHoleConnector.FIELD_LENGTH_PROPERTY, 16,
            BlackHoleConnector.PAGE_PROCESSING_DELAY, new Duration(0, SECONDS));

    @Test
    public void testCreateSchema()
    {
        assertEquals(metadata.listSchemaNames(SESSION), ImmutableList.of("default"));
        metadata.createSchema(SESSION, "test", ImmutableMap.of(), new PrestoPrincipal(USER, SESSION.getUser()));
        assertEquals(metadata.listSchemaNames(SESSION), ImmutableList.of("default", "test"));
    }

    @Test
    public void tableIsCreatedAfterCommits()
    {
        assertThatNoTableIsCreated();

        SchemaTableName schemaTableName = new SchemaTableName("default", "temp_table");

        ConnectorOutputTableHandle table = metadata.beginCreateTable(
                SESSION,
                new ConnectorTableMetadata(schemaTableName, ImmutableList.of(), tableProperties),
                Optional.empty());

        assertThatNoTableIsCreated();

        metadata.finishCreateTable(SESSION, table, ImmutableList.of(), ImmutableList.of());

        List<SchemaTableName> tables = metadata.listTables(SESSION, Optional.empty());
        assertEquals(tables.size(), 1, "Expected only one table.");
        assertEquals(tables.get(0).getTableName(), "temp_table", "Expected table with name 'temp_table'");
    }

    @Test
    public void testCreateTableInNotExistSchema()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema1", "test_table");
        try {
            metadata.beginCreateTable(SESSION, new ConnectorTableMetadata(schemaTableName, ImmutableList.of(), tableProperties), Optional.empty());
            fail("Should fail because schema does not exist");
        }
        catch (TrinoException ex) {
            assertEquals(ex.getErrorCode(), NOT_FOUND.toErrorCode());
            assertEquals(ex.getMessage(), "Schema schema1 not found");
        }
    }

    private void assertThatNoTableIsCreated()
    {
        assertEquals(metadata.listTables(SESSION, Optional.empty()), ImmutableList.of(), "No table was expected");
    }
}
