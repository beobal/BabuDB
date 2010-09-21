/*
 * Copyright (c) 2008, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */

package org.xtreemfs.babudb;

import java.io.File;
import java.util.Iterator;
import java.util.Map.Entry;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xtreemfs.babudb.config.BabuDBConfig;
import org.xtreemfs.babudb.log.DiskLogger.SyncMode;
import org.xtreemfs.babudb.lsmdb.BabuDBInsertGroup;
import org.xtreemfs.babudb.lsmdb.Database;
import org.xtreemfs.babudb.lsmdb.LSMLookupInterface;
import org.xtreemfs.foundation.logging.Logging;
import org.xtreemfs.foundation.util.FSUtils;

/**
 * 
 * @author bjko
 */
public class BabuDBTest extends TestCase {
    
    public static final String  baseDir          = "/tmp/lsmdb-test/";
    
    public static final boolean compression      = false;
    
    private static final int    maxNumRecs       = 16;
    
    private static final int    maxBlockFileSize = 1024 * 1024 * 512;
    
    private BabuDB              database;
    
    public BabuDBTest() {
        Logging.start(Logging.LEVEL_DEBUG);
    }
    
    @Before
    public void setUp() throws Exception {
        FSUtils.delTree(new File(baseDir));
    }
    
    @After
    public void tearDown() throws Exception {
    }
    
    // TODO add test methods here.
    // The methods must be annotated with annotation @Test. For example:
    //
    // @Test
    // public void hello() {}
    @Test
    public void testReplayAfterCrash() throws Exception {
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 1, 0, 0,
            SyncMode.SYNC_WRITE, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        Database db = database.getDatabaseManager().createDatabase("test", 2);
        db.singleInsert(0, "Yagga".getBytes(), "Brabbel".getBytes(), null).get();
        database.getCheckpointer().checkpoint();
        byte[] result = db.lookup(0, "Yagga".getBytes(), null).get();
        String value = new String(result);
        assertEquals(value, "Brabbel");
        
        db.singleInsert(0, "Brabbel".getBytes(), "Blupp".getBytes(), null).get();
        result = db.lookup(0, "Brabbel".getBytes(), null).get();
        value = new String(result);
        assertEquals(value, "Blupp");
        
        db.singleInsert(0, "Blupp".getBytes(), "Blahh".getBytes(), null).get();
        result = db.lookup(0, "Blupp".getBytes(), null).get();
        value = new String(result);
        assertEquals(value, "Blahh");
        
        database.__test_killDB_dangerous();
        Thread.sleep(500);
        
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 2, 0, 0,
            SyncMode.SYNC_WRITE, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        db = database.getDatabaseManager().getDatabase("test");
        result = db.lookup(0, "Yagga".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Brabbel");
        
        result = db.lookup(0, "Brabbel".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Blupp");
        
        result = db.lookup(0, "Blupp".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Blahh");
        
        System.out.println("shutting down database...");
        
        database.shutdown();
    }
    
    @Test
    public void testShutdownAfterCheckpoint() throws Exception {
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 1, 0, 0,
            SyncMode.SYNC_WRITE, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        Database db = database.getDatabaseManager().createDatabase("test", 2);
        db.singleInsert(0, "Yagga".getBytes(), "Brabbel".getBytes(), null).get();
        
        byte[] result = db.lookup(0, "Yagga".getBytes(), null).get();
        String value = new String(result);
        assertEquals(value, "Brabbel");
        
        db.singleInsert(0, "Brabbel".getBytes(), "Blupp".getBytes(), null).get();
        result = db.lookup(0, "Brabbel".getBytes(), null).get();
        value = new String(result);
        assertEquals(value, "Blupp");
        
        db.singleInsert(0, "Blupp".getBytes(), "Blahh".getBytes(), null).get();
        result = db.lookup(0, "Blupp".getBytes(), null).get();
        value = new String(result);
        assertEquals(value, "Blahh");
        
        database.getCheckpointer().checkpoint();
        database.shutdown();
        
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 2, 0, 0,
            SyncMode.SYNC_WRITE, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        db = database.getDatabaseManager().getDatabase("test");
        result = db.lookup(0, "Yagga".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Brabbel");
        
        result = db.lookup(0, "Brabbel".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Blupp");
        
        result = db.lookup(0, "Blupp".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Blahh");
        
        System.out.println("shutting down database...");
        
        database.shutdown();
    }
    
    @Test
    public void testMultipleIndices() throws Exception {
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 1, 0, 0,
            SyncMode.SYNC_WRITE, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        Database db = database.getDatabaseManager().createDatabase("test", 3);
        
        BabuDBInsertGroup ir = db.createInsertGroup();
        ir.addInsert(0, "Key1".getBytes(), "Value1".getBytes());
        ir.addInsert(1, "Key2".getBytes(), "Value2".getBytes());
        ir.addInsert(2, "Key3".getBytes(), "Value3".getBytes());
        db.insert(ir, null).get();
        
        database.getCheckpointer().checkpoint();
        database.shutdown();
        
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 2, 0, 0,
            SyncMode.SYNC_WRITE, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        db = database.getDatabaseManager().getDatabase("test");
        
        byte[] result = db.lookup(0, "Key1".getBytes(), null).get();
        assertNotNull(result);
        String value = new String(result);
        assertEquals(value, "Value1");
        
        result = db.lookup(1, "Key2".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Value2");
        
        result = db.lookup(2, "Key3".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Value3");
        
        System.out.println("shutting down database...");
        
        database.shutdown();
    }
    
    @Test
    public void testMultipleIndicesAndCheckpoint() throws Exception {
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 1, 0, 0,
            SyncMode.SYNC_WRITE, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        Database db = database.getDatabaseManager().createDatabase("test", 4);
        
        BabuDBInsertGroup ir = db.createInsertGroup();
        ir.addInsert(0, "Key1".getBytes(), "Value1".getBytes());
        ir.addInsert(1, "Key2".getBytes(), "Value2".getBytes());
        ir.addInsert(2, "Key3".getBytes(), "Value3".getBytes());
        db.insert(ir, null).get();
        
        database.getCheckpointer().checkpoint();
        
        byte[] result = db.lookup(0, "Key1".getBytes(), null).get();
        assertNotNull(result);
        String value = new String(result);
        assertEquals(value, "Value1");
        
        result = db.lookup(1, "Key2".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Value2");
        
        result = db.lookup(2, "Key3".getBytes(), null).get();
        assertNotNull(result);
        value = new String(result);
        assertEquals(value, "Value3");
        
        Iterator<Entry<byte[], byte[]>> iter = db.prefixLookup(3, "Key3".getBytes(), null).get();
        assertNotNull(iter);
        ir = db.createInsertGroup();
        ir.addDelete(0, "Key1".getBytes());
        ir.addInsert(1, "Key2".getBytes(), "Value2.2".getBytes());
        ir.addInsert(2, "Key3".getBytes(), "Value2.3".getBytes());
        db.insert(ir, null).get();
        database.getCheckpointer().checkpoint();
        
        iter = db.prefixLookup(0, "Key3".getBytes(), null).get();
        assertNotNull(iter);
        
        System.out.println("shutting down database...");
        
        database.shutdown();
    }
    
    @Test
    public void testUserDefinedLookup() throws Exception {
        
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 1, 0, 0,
            SyncMode.SYNC_WRITE, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        Database db = database.getDatabaseManager().createDatabase("test", 3);
        
        BabuDBInsertGroup ir = db.createInsertGroup();
        ir.addInsert(0, "Key1".getBytes(), "Value1".getBytes());
        ir.addInsert(1, "Key2".getBytes(), "Value2".getBytes());
        ir.addInsert(2, "Key3".getBytes(), "Value3".getBytes());
        db.insert(ir, null).get();
        
        UserDefinedLookup lookup = new UserDefinedLookup() {
            
            public Object execute(LSMLookupInterface database) throws BabuDBException {
                if ((database.lookup(0, "Key1".getBytes()) != null)
                    && (database.lookup(1, "Key2".getBytes()) != null)) {
                    return new Boolean(true);
                } else {
                    return new Boolean(false);
                }
            }
        };
        
        Boolean result = (Boolean) db.userDefinedLookup(lookup, null).get();
        assertTrue(result);
        
        System.out.println("shutting down database...");
        
        database.shutdown();
        
    }
    
    @Test
    public void testDirectAccess() throws Exception {
        
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 0, 0, 0,
            SyncMode.ASYNC, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        Database db = database.getDatabaseManager().createDatabase("test", 2);
        
        for (int i = 0; i < 100000; i++) {
            BabuDBInsertGroup ir = db.createInsertGroup();
            ir.addInsert(0, (i + "").getBytes(), "bla".getBytes());
            ir.addInsert(1, (i + "").getBytes(), "bla".getBytes());
            db.insert(ir, null).get();
        }
        
        database.getCheckpointer().checkpoint();
        
        for (int i = 0; i < 100000; i++) {
            
            byte[] v0 = db.lookup(0, (i + "").getBytes(), null).get();
            byte[] v1 = db.lookup(1, (i + "").getBytes(), null).get();
            
            assertEquals("bla", new String(v0));
            assertEquals("bla", new String(v1));
        }
    }
    
    @Test
    public void testInsDelGet() throws Exception {
        
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 0, 0, 0,
            SyncMode.ASYNC, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        Database db = database.getDatabaseManager().createDatabase("test", 3);
        
        for (int i = 0; i < 1000; i++) {
            BabuDBInsertGroup ir = db.createInsertGroup();
            ir.addInsert(0, (i + "").getBytes(), "bla".getBytes());
            ir.addInsert(1, (i + "").getBytes(), "bla".getBytes());
            ir.addInsert(2, (i + "").getBytes(), "bla".getBytes());
            db.insert(ir, null).get();
        }
        
        byte[] data = new byte[2048];
        for (int i = 0; i < 1000; i++) {
            BabuDBInsertGroup ir = db.createInsertGroup();
            ir.addInsert(0, (i + "").getBytes(), data);
            ir.addInsert(1, (i + "").getBytes(), data);
            ir.addInsert(2, (i + "").getBytes(), data);
            db.insert(ir, null).get();
        }
        
        database.getCheckpointer().checkpoint();
        
        for (int i = 0; i < 1000; i++) {
            
            byte[] v0 = db.lookup(0, (i + "").getBytes(), null).get();
            byte[] v1 = db.lookup(1, (i + "").getBytes(), null).get();
            byte[] v2 = db.lookup(2, (i + "").getBytes(), null).get();
            
            assertNotNull(v0);
            assertNotNull(v1);
            assertNotNull(v2);
        }
        
        for (int i = 0; i < 1000; i++) {
            BabuDBInsertGroup ir = db.createInsertGroup();
            ir.addDelete(0, (i + "").getBytes());
            ir.addDelete(1, (i + "").getBytes());
            ir.addDelete(2, (i + "").getBytes());
            db.insert(ir, null).get();
        }
        
        for (int i = 0; i < 1000; i++) {
            
            byte[] v0 = db.lookup(0, (i + "").getBytes(), null).get();
            byte[] v1 = db.lookup(1, (i + "").getBytes(), null).get();
            byte[] v2 = db.lookup(2, (i + "").getBytes(), null).get();
            
            assertNull(v0);
            assertNull(v1);
            assertNull(v2);
        }
    }
    
    @Test
    public void testInsPrefLookup() throws Exception {
        
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 0, 0, 0,
            SyncMode.ASYNC, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        Database db = database.getDatabaseManager().createDatabase("test", 3);
        
        for (int i = 1000; i < 2000; i++) {
            BabuDBInsertGroup ir = db.createInsertGroup();
            ir.addInsert(0, (i + "").getBytes(), (i + "").getBytes());
            ir.addInsert(1, (i + "").getBytes(), (i + "").getBytes());
            ir.addInsert(2, (i + "").getBytes(), (i + "").getBytes());
            db.insert(ir, null);
        }
        
        Iterator<Entry<byte[], byte[]>> it = db.prefixLookup(0, new byte[0], null).get();
        for (int i = 1000; i < 2000; i++)
            assertEquals(i + "", new String(it.next().getValue()));
        assertFalse(it.hasNext());
        
        it = db.prefixLookup(0, "15".getBytes(), null).get();
        for (int i = 1500; i < 1600; i++)
            assertEquals(i + "", new String(it.next().getValue()));
        assertFalse(it.hasNext());
        
        database.getCheckpointer().checkpoint();
        
        it = db.prefixLookup(0, "15".getBytes(), null).get();
        for (int i = 1500; i < 1600; i++)
            assertEquals(i + "", new String(it.next().getValue()));
        assertFalse(it.hasNext());
        
        database.shutdown();
    }
    
    @Test
    public void testInsRangeLookup() throws Exception {
        
        database = (BabuDB) BabuDBFactory.createBabuDB(new BabuDBConfig(baseDir, baseDir, 0, 0, 0,
            SyncMode.ASYNC, 0, 0, compression, maxNumRecs, maxBlockFileSize), null);
        Database db = database.getDatabaseManager().createDatabase("test", 3);
        
        for (int i = 1000; i < 2000; i++) {
            BabuDBInsertGroup ir = db.createInsertGroup();
            ir.addInsert(0, (i + "").getBytes(), (i + "").getBytes());
            ir.addInsert(1, (i + "").getBytes(), (i + "").getBytes());
            ir.addInsert(2, (i + "").getBytes(), (i + "").getBytes());
            db.insert(ir, null);
        }
        
        Iterator<Entry<byte[], byte[]>> it = db.rangeLookup(0, new byte[0], new byte[0], null).get();
        for (int i = 1000; i < 2000; i++)
            assertEquals(i + "", new String(it.next().getValue()));
        assertFalse(it.hasNext());
        
        it = db.rangeLookup(0, "1500".getBytes(), "1600".getBytes(), null).get();
        for (int i = 1500; i < 1600; i++)
            assertEquals(i + "", new String(it.next().getValue()));
        assertFalse(it.hasNext());
        
        database.getCheckpointer().checkpoint();
        
        it = db.rangeLookup(0, "1500".getBytes(), "1600".getBytes(), null).get();
        for (int i = 1500; i < 1600; i++)
            assertEquals(i + "", new String(it.next().getValue()));
        assertFalse(it.hasNext());
        
        database.shutdown();
    }
    
    public static void main(String[] args) {
        TestRunner.run(BabuDBTest.class);
    }
    
}