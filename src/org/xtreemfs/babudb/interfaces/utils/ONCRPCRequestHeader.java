/*  Copyright (c) 2009 Konrad-Zuse-Zentrum fuer Informationstechnik Berlin.

    This file is part of XtreemFS. XtreemFS is part of XtreemOS, a Linux-based
    Grid Operating System, see <http://www.xtreemos.eu> for more details.
    The XtreemOS project has been developed with the financial support of the
    European Commission's IST program under contract #FP6-033576.

    XtreemFS is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free
    Software Foundation, either version 2 of the License, or (at your option)
    any later version.

    XtreemFS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with XtreemFS. If not, see <http://www.gnu.org/licenses/>.
*/
/*
 * AUTHORS: Minor Gordon (NEC), Björn Kolbeck (ZIB)
 */

package org.xtreemfs.babudb.interfaces.utils;

import org.xtreemfs.include.common.buffer.ReusableBuffer;
import org.xtreemfs.include.foundation.oncrpc.utils.ONCRPCBufferWriter;

import yidl.runtime.Marshaller;
import yidl.runtime.Unmarshaller;

public class ONCRPCRequestHeader implements yidl.runtime.Object {

    public ONCRPCRequestHeader() {
        xid = prog = vers = proc = 0;
    }

    public ONCRPCRequestHeader(int xid, int prog, int vers, int proc) {
        this.xid = xid;
        this.prog = prog;
        this.vers = vers;
        this.proc = proc;
    }

    public int getXID() {
        return xid;
    }

    public int getInterfaceNumber() {
        return prog - 0x20000000;
    }

    public int getInterfaceVersion() {
        return vers;
    }

    public int getProcedure() {
        return proc;
    }

    public int getMessageType() {
        return msg_type;
    }

    public int getRpcVersion() {
        return rpcvers;
    }

    public String toString() {
        return "ONCRPCRequestHeader( " + Integer.toString(proc) + " )";
    }

    // Serializable    
    public int getTag() { return getProcedure(); }
    public String getTypeName() { return "xtreemfs::interfaces::ONCRPCRequestHeader"; }    
    
    public void marshal(Marshaller writer) {
        writer.writeInt32(null,xid);
        writer.writeInt32(null,0); // CALL
        writer.writeInt32(null,2); // RPC version 2
        writer.writeInt32(null,prog);
        writer.writeInt32(null,vers);
        writer.writeInt32(null,proc);
        writer.writeInt32(null,0); // cred_auth_flavor
        writer.writeInt32(null,0); // cred auth opaque data
        writer.writeInt32(null,0); // verf_auth_flavor
        writer.writeInt32(null,0); // verf auth opaque data
    }

    public void unmarshal(Unmarshaller um) {
        xid = um.readInt32(null);
//        System.out.println( "XID " + Integer.toString( xid ) );
        msg_type = um.readInt32(null);
        rpcvers = um.readInt32(null);
//        System.out.println( "RPC version " + Integer.toString( rpcvers ) );
        prog = um.readInt32(null);
        //       System.out.println( "Prog " + Integer.toString( prog ) );
        vers = um.readInt32(null);
//        System.out.println( "Vers " + Integer.toString( vers ) );        
        proc = um.readInt32(null);
//        System.out.println( "proc " + Integer.toString( proc ) );        
        auth_flavor = um.readInt32(null); // cred_auth_flavor
        um.readInt32(null); // cred auth opaque data
        um.readInt32(null); // verf_auth_flavor
        um.readInt32(null); // verf auth opaque data
    }

    public int getXDRSize() {
        return 10 * Integer.SIZE/8;
    }
    private int xid;

    private int prog;

    private int vers;

    private int proc;

    private int rpcvers;

    private int msg_type;

    private int auth_flavor;

    /**
     * @return the auth_flavor
     */
    public int getAuth_flavor() {
        return auth_flavor;
    }
}
