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


package com.oltpbenchmark.api;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;

import com.oltpbenchmark.WorkloadConfiguration;
import com.oltpbenchmark.catalog.Catalog;
import com.oltpbenchmark.types.DatabaseType;
import com.oltpbenchmark.util.ClassUtil;
import com.oltpbenchmark.util.ScriptRunner;

/**
 * Base class for all benchmark implementations
 */
public abstract class BenchmarkModule {
    private static final Logger LOG = Logger.getLogger(BenchmarkModule.class);

    protected final String benchmarkName;

    /**
     * The workload configuration for this benchmark invocation
     */
    protected final WorkloadConfiguration workConf;

    /**
     * These are the variations of the Procedure's Statment SQL
     */
    protected final StatementDialects dialects;

    /**
     * Database Catalog
     */
    protected final Catalog catalog;

    /**
     * Supplemental Procedures
     */
    private final Set<Class<? extends Procedure>> supplementalProcedures = new HashSet<Class<? extends Procedure>>();

    /**
     * The last Connection that was created using this BenchmarkModule
     */
    private Connection last_connection;

    /**
     * A single Random object that should be re-used by all a benchmark's components
     */
    private final Random rng = new Random();
    
    private final String schemaName;
    
    public static final String DEFAULT_SCHEMA_NAME = null;

    /**
     * Whether to use verbose output messages
     * @deprecated
     */
    protected boolean verbose;

    public BenchmarkModule(String benchmarkName, 
            WorkloadConfiguration workConf, 
            boolean withCatalog,
            String schemaName) {
        assert (workConf != null) : "The WorkloadConfiguration instance is null.";

        this.benchmarkName = benchmarkName;
        this.workConf = workConf;
        this.schemaName = schemaName;
        this.catalog = (withCatalog ? new Catalog(this) : null);
        File xmlFile = this.getSQLDialect();
        this.dialects = new StatementDialects(this.workConf.getDBType(), xmlFile);
    }
    
    public String getSchemaName() {
        return schemaName;
    }

    // --------------------------------------------------------------------------
    // DATABASE CONNETION
    // --------------------------------------------------------------------------

    /**
     * 
     * @return
     * @throws SQLException
     */
    protected final Connection makeConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(workConf.getDBConnection(),
                workConf.getDBUsername(),
                workConf.getDBPassword());
        Catalog.setSeparator(conn);
        this.last_connection = conn;
        return (conn);
    }

    /**
     * Return the last Connection handle created by this BenchmarkModule
     * @return
     */
    protected final Connection getLastConnection() {
        return (this.last_connection);
    }

    // --------------------------------------------------------------------------
    // IMPLEMENTING CLASS INTERFACE
    // --------------------------------------------------------------------------

    /**
     * @param verbose
     * @return
     * @throws IOException
     */
    protected abstract List<Worker> makeWorkersImpl(boolean verbose) throws IOException;

    /**
     * Each BenchmarkModule needs to implement this method to load a sample
     * dataset into the database. The Connection handle will already be
     * configured for you, and the base class will commit+close it once this
     * method returns
     * 
     * @param conn
     *            TODO
     * @return TODO
     * @throws SQLException
     *             TODO
     */
    protected abstract Loader makeLoaderImpl(Connection conn) throws SQLException;

    /**
     * @param txns
     * @return
     */
    protected abstract Package getProcedurePackageImpl();

    // --------------------------------------------------------------------------
    // PUBLIC INTERFACE
    // --------------------------------------------------------------------------

    /**
     * Return the Random generator that should be used by all this benchmark's components
     */
    public Random rng() {
        return (this.rng);
    }

    /**
     * 
     * @return
     */
    public URL getDatabaseDDL() {
        return (this.getDatabaseDDL(this.workConf.getDBType()));
    }

    /**
     * Return the URL handle to the DDL used to load the benchmark's database
     * schema.
     * @param conn 
     * @throws SQLException 
     */
    public URL getDatabaseDDL(DatabaseType db_type) {

        ArrayList<Object> ddlNames = new ArrayList<Object>();
        if (db_type.name().equalsIgnoreCase("nuodb") && this.workConf.getPartitionCount() > 0) {
            try {
                ddlNames.add(this.buildTpsgDDL());
            } catch (IOException ex) {
                throw new RuntimeException(String.format("Unexpected error when trying generate TPSG DDL for %s database", this.benchmarkName), ex);
            }
        } else {
            ddlNames.add(this.benchmarkName + "-" + (db_type != null ? db_type.name().toLowerCase() : "") + "-ddl.sql");
        }
        ddlNames.add(this.benchmarkName + "-ddl.sql");

        for (Object ddlName : ddlNames) {
            if (ddlName == null) continue;
            if (ddlName instanceof String) {
                URL ddlURL = this.getClass().getResource((String) ddlName);
                if (ddlURL != null) return ddlURL;
            }
            if (ddlName instanceof File) {
                try {
                    return ((File) ddlName).toURI().toURL();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        } // FOR
        LOG.trace(ddlNames.get(0).toString() + " :or: " + ddlNames.get(1).toString());
        LOG.error("Failed to find DDL file for " + this.benchmarkName);
        return null;
    }

    /**
     * Return the File handle to the SQL Dialect XML file
     * used for this benchmark 
     * @return
     */
    public File getSQLDialect() {
        String xmlName = this.benchmarkName + "-dialects.xml";
        URL ddlURL = this.getClass().getResource(xmlName);
        if (ddlURL != null) return new File(ddlURL.getPath());
        if (LOG.isDebugEnabled())
            LOG.warn(String.format("Failed to find SQL Dialect XML file '%s'", xmlName));
        return (null);
    }

    public final List<Worker> makeWorkers(boolean verbose) throws IOException {
        return (this.makeWorkersImpl(verbose));
    }

    /**
     * Create the Benchmark Database
     * This is the main method used to create all the database 
     * objects (e.g., table, indexes, etc) needed for this benchmark 
     */
    public final void createDatabase() {
        try {
            Connection conn = this.makeConnection();
            this.createDatabase(this.workConf.getDBType(), conn);
            conn.close();
        } catch (SQLException ex) {
            throw new RuntimeException(String.format("Unexpected error when trying to create the %s database", this.benchmarkName), ex);
        }
    }

    /**
     * Create the Benchmark Database
     * This is the main method used to create all the database 
     * objects (e.g., table, indexes, etc) needed for this benchmark 
     */
    public final void createDatabase(DatabaseType dbType, Connection conn) throws SQLException {
        try {
            URL ddl = this.getDatabaseDDL(dbType);
            assert(ddl != null) : "Failed to get DDL for " + this;
            initializeDBNamespace(dbType, conn);
            ScriptRunner runner = new ScriptRunner(conn, true, true);
            if (LOG.isDebugEnabled()) LOG.debug("Executing script '" + ddl + "'");
            runner.runScript(ddl);
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Unexpected error when trying to create the %s database", this.benchmarkName), ex);
        }
    }
    
    protected void initializeDBNamespace(DatabaseType dbType, Connection conn) throws SQLException {
        //create schema and setup connection to use the schema
        if (dbType == DatabaseType.HSQLDB) {
            /*
            Statement schemaStmt = conn.createStatement();
            boolean success = schemaStmt.execute("CREATE SCHEMA " + getSchemaName() + " AUTHORIZATION DBA;");
            success = schemaStmt.execute("SET SCHEMA " + getSchemaName());
            conn.commit();
            schemaStmt.close();
            */
        }
    }

    /**
     * Run a script on a Database
     */
    public final void runScript(String script) {
        try {
            Connection conn = this.makeConnection();
            ScriptRunner runner = new ScriptRunner(conn, true, true);
            File scriptFile= new File(script);
            runner.runScript(scriptFile.toURI().toURL());
            conn.close();
        } catch (SQLException ex) {
            throw new RuntimeException(String.format("Unexpected error when trying to run: %s", script), ex);
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unexpected error when trying to open: %s", script), ex);
        }
    }

    /**
     * Invoke this benchmark's database loader
     */
    public final void loadDatabase() {
        try {
            Connection conn = this.makeConnection();
            this.loadDatabase(conn);
            conn.close();
        } catch (SQLException ex) {
            throw new RuntimeException(String.format("Unexpected error when trying to load the %s database", this.benchmarkName), ex);
        }
    }

    /**
     * Invoke this benchmark's database loader using the given Connection handle
     * @param conn
     */
    protected final void loadDatabase(Connection conn) {
        try {
            Loader loader = this.makeLoaderImpl(conn);
            if (loader != null) {
                conn.setAutoCommit(false);
                loader.load();
                conn.commit();

                if (loader.getTableCounts().isEmpty() == false) {
                    LOG.info("Table Counts:\n" + loader.getTableCounts());
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(String.format("Unexpected error when trying to load the %s database", this.benchmarkName), ex);
        }
    }

    /**
     * @param DB_CONN
     * @throws SQLException
     */
    public final void clearDatabase() {
        try {
            Connection conn = this.makeConnection();
            Loader loader = this.makeLoaderImpl(conn);
            if (loader != null) {
                conn.setAutoCommit(false);
                loader.unload(this.catalog);
                conn.commit();
            }
        } catch (SQLException ex) {
            throw new RuntimeException(String.format("Unexpected error when trying to delete the %s database", this.benchmarkName), ex);
        }
    }

    // --------------------------------------------------------------------------
    // UTILITY METHODS
    // --------------------------------------------------------------------------

    /**
     * Return the unique identifier for this benchmark
     */
    public final String getBenchmarkName() {
        return (this.benchmarkName);
    }
    /**
     * Return the database's catalog
     */
    public final Catalog getCatalog() {
        return (this.catalog);
    }
    /**
     * Return the StatementDialects loaded for this benchmark
     */
    public final StatementDialects getStatementDialects() {
        return (this.dialects);
    }
    @Override
    public final String toString() {
        return benchmarkName.toUpperCase();
    }

    /**
     * Initialize a TransactionType handle for the get procedure name and id
     * This should only be invoked a start-up time
     * @param procName
     * @param id
     * @return
     */
    @SuppressWarnings("unchecked")
    public final TransactionType initTransactionType(String procName, int id) {
        if (id == TransactionType.INVALID_ID) {
            LOG.error(String.format("Procedure %s.%s cannot the reserved id '%d' for %s",
                    this.benchmarkName, procName, id,
                    TransactionType.INVALID.getClass().getSimpleName()));
            return null;
        }

        Package pkg = this.getProcedurePackageImpl();
        assert (pkg != null) : "Null Procedure package for " + this.benchmarkName;
        String fullName = pkg.getName() + "." + procName;
        Class<? extends Procedure> procClass = (Class<? extends Procedure>) ClassUtil.getClass(fullName);
        assert (procClass != null) : "Unexpected Procedure name " + this.benchmarkName + "." + procName;
        return new TransactionType(procClass, id);
    }

    public final WorkloadConfiguration getWorkloadConfiguration() {
        return (this.workConf);
    }

    /**
     * Return a mapping from TransactionTypes to Procedure invocations
     * @param txns
     * @param pkg
     * @return
     */
    public Map<TransactionType, Procedure> getProcedures() {
        Map<TransactionType, Procedure> proc_xref = new HashMap<TransactionType, Procedure>();
        TransactionTypes txns = this.workConf.getTransTypes();

        if (txns != null) {
            for (Class<? extends Procedure> procClass : this.supplementalProcedures) {
                TransactionType txn = txns.getType(procClass);
                if (txn == null) {
                    txn = new TransactionType(procClass, procClass.hashCode(), true);
                    txns.add(txn);
                }
            } // FOR

            for (TransactionType txn : txns) {
                Procedure proc = (Procedure)ClassUtil.newInstance(txn.getProcedureClass(),
                        new Object[0],
                        new Class<?>[0]);
                proc.initialize(this.workConf.getDBType());
                proc_xref.put(txn, proc);
                proc.loadSQLDialect(this.dialects);
            } // FOR
        }
        if (proc_xref.isEmpty()) {
            LOG.warn("No procedures defined for " + this);
        }
        return (proc_xref);
    }

    /**
     * 
     * @param procClass
     */
    public final void registerSupplementalProcedure(Class<? extends Procedure> procClass) {
        this.supplementalProcedures.add(procClass);
    }

    public final File buildTpsgDDL() throws IOException {
        File tpsgDDLFile;
            int partitionCount = this.workConf.getPartitionCount();
            List<String> storageGroups = this.workConf.getStorageGroups();
            int storageGroupCount = storageGroups.size();
            int keySpaceMax = (int)(this.workConf.getScaleFactor() * 1000);
            int partitionSize = keySpaceMax / storageGroupCount;

            String tpsgTemplate = this.benchmarkName + "-nuodb-tpsg-ddl.template";
            tpsgDDLFile = File.createTempFile(this.benchmarkName + "-nuodb-tpsg-ddl", "sql");
            FileWriter tpsgDDLWriter = new FileWriter(tpsgDDLFile);

            URL templateURL = this.getClass().getResource(tpsgTemplate);
            BufferedReader tpsgTemplateFile = new BufferedReader(new InputStreamReader(templateURL.openStream()));
            StringBuilder fullSchema = new StringBuilder();
            String line = null;
            while ((line = tpsgTemplateFile.readLine()) != null) {
                tpsgDDLWriter.write(line);
                tpsgDDLWriter.write("\n");
                fullSchema.append(line).append("\n");
            }
            tpsgTemplateFile.close();

            tpsgDDLWriter.write("(\n");
            fullSchema.append("\n");
            int sg = -1;
            for (int i = 0; i < partitionCount; i++) {
                if ((i % (partitionCount/storageGroupCount)) == 0 ) {
                    sg++;
                }
                if ((i+1) == partitionCount) {
                    line = String.format("PARTITION p%d VALUES LESS THAN (MAXVALUE) STORE IN %s\n", i, storageGroups.get(sg));
                } else {
                    line = String.format("PARTITION p%d VALUES LESS THAN ('%d') STORE IN %s\n", i, (i + 1) * partitionSize, storageGroups.get(sg));
                }
                tpsgDDLWriter.write(line);
                fullSchema.append(line);
            }
            tpsgDDLWriter.write(");\n");
            fullSchema.append("\n");
            tpsgDDLWriter.close();

            // Normally, one could just look at the ddl file in the source.
            // line parameters, log for posterity
            LOG.info("generated storage group schema:\n" + fullSchema.toString());

        return tpsgDDLFile;
    }

}
