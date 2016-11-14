/******************************************************************************
 *  Copyright 2015 by OLTPBenchmark Project                                   *
 *                                                                            *
 *  Licensed under the Apache License, Version 2.0 (the "License");           *
 *  you may not use this file except in compliance with the License.          *
 *  You may obtain a copy of the License at                                   *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 *  Unless required by applicable law or agreed to in writing, software       *
 *  distributed under the License is distributed on an "AS IS" BASIS,         *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 *  See the License for the specific language governing permissions and       *
 *  limitations under the License.                                            *
 ******************************************************************************/

package com.oltpbenchmark.benchmarks.ycsb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.log4j.Logger;

import com.oltpbenchmark.api.Loader;
import com.oltpbenchmark.api.StatementDialects;
import com.oltpbenchmark.catalog.Table;
import com.oltpbenchmark.util.SQLUtil;
import com.oltpbenchmark.util.TextGenerator;

public class YCSBLoader extends Loader {
    private static final Logger LOG = Logger.getLogger(YCSBLoader.class);
    private final int num_record;

    public YCSBLoader(YCSBBenchmark benchmark, Connection c) {
        super(benchmark, c);
        this.num_record = (int) Math.round(YCSBConstants.RECORD_COUNT * this.scaleFactor);
        if (LOG.isDebugEnabled()) {
            LOG.debug("# of RECORDS:  " + this.num_record);
        }
    }

    @Override
    public void load() throws SQLException {
        Table catalog_tbl = this.getTableCatalog(YCSBConstants.YCSB_TABLENAME);
        assert (catalog_tbl != null);
        
        Statement fooStmt = conn.createStatement();
        StringBuilder sb = new StringBuilder();
        StatementDialects dialects =  this.benchmark.getStatementDialects();
        if (!dialects.getDatabaseType().name().equals("NUODB"))
        {
            ResultSet results = fooStmt.executeQuery("SELECT * FROM INFORMATION_SCHEMA.TABLES;");
            while (results.next()) {
                sb.append(results.getString(2)).append(".").append(results.getString(3)).append("\n");
            }
        } else {
            ResultSet results = fooStmt.executeQuery("SELECT TABLENAME, SCHEMA FROM SYSTEM.TABLES WHERE SCHEMA != 'SYSTEM';");
            while (results.next()) {
                sb.append(results.getString(2)).append(".").append(results.getString(1)).append("\n");
            }
        }
        System.out.println("existing tables = " + sb.toString());

        String sql = SQLUtil.getInsertSQL(catalog_tbl);
        PreparedStatement stmt = this.conn.prepareStatement(sql);
        long total = 0;
        int batch = 0;
        for (int i = 0; i < this.num_record; i++) {
            stmt.setInt(1, i);
            for (int j = 2; j <= 11; j++) {
                stmt.setString(j, TextGenerator.randomStr(rng(), 100));
            }
            stmt.addBatch();
            total++;
            if (++batch >= YCSBConstants.configCommitCount) {
                int result[] = stmt.executeBatch();
                assert (result != null);
                conn.commit();
                batch = 0;
                if (LOG.isDebugEnabled())
                    LOG.debug(String.format("Records Loaded %d / %d", total, this.num_record));
            }
        } // FOR
        if (batch > 0) {
            stmt.executeBatch();
            if (LOG.isDebugEnabled())
                LOG.debug(String.format("Records Loaded %d / %d", total, this.num_record));
        }
        stmt.close();
        if (LOG.isDebugEnabled()) LOG.debug("Finished loading " + catalog_tbl.getName());
    }
}
