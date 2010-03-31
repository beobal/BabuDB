package org.xtreemfs.babudb.interfaces.ReplicationInterface;

import java.io.StringWriter;
import org.xtreemfs.*;
import org.xtreemfs.babudb.*;
import org.xtreemfs.babudb.interfaces.*;
import org.xtreemfs.foundation.buffer.ReusableBuffer;
import org.xtreemfs.interfaces.utils.*;

import yidl.runtime.Marshaller;
import yidl.runtime.PrettyPrinter;
import yidl.runtime.Struct;
import yidl.runtime.Unmarshaller;




public class ProtocolException extends org.xtreemfs.interfaces.utils.ONCRPCException
{
    public static final int TAG = 1100;
    
    public ProtocolException() {  }
    public ProtocolException( int accept_stat, int error_code, String stack_trace ) { this.accept_stat = accept_stat; this.error_code = error_code; this.stack_trace = stack_trace; }

    public int getAccept_stat() { return accept_stat; }
    public void setAccept_stat( int accept_stat ) { this.accept_stat = accept_stat; }
    public int getError_code() { return error_code; }
    public void setError_code( int error_code ) { this.error_code = error_code; }
    public String getStack_trace() { return stack_trace; }
    public void setStack_trace( String stack_trace ) { this.stack_trace = stack_trace; }

    // java.lang.Object
    public String toString() 
    { 
        StringWriter string_writer = new StringWriter();
        string_writer.append(this.getClass().getCanonicalName());
        string_writer.append(" ");
        PrettyPrinter pretty_printer = new PrettyPrinter( string_writer );
        pretty_printer.writeStruct( "", this );
        return string_writer.toString();
    }


    // java.io.Serializable
    public static final long serialVersionUID = 1100;    

    // yidl.runtime.Object
    public int getTag() { return 1100; }
    public String getTypeName() { return "org::xtreemfs::babudb::interfaces::ReplicationInterface::ProtocolException"; }
    
    public int getXDRSize()
    {
        int my_size = 0;
        my_size += Integer.SIZE / 8; // accept_stat
        my_size += Integer.SIZE / 8; // error_code
        my_size += Integer.SIZE / 8 + ( stack_trace != null ? ( ( stack_trace.getBytes().length % 4 == 0 ) ? stack_trace.getBytes().length : ( stack_trace.getBytes().length + 4 - stack_trace.getBytes().length % 4 ) ) : 0 ); // stack_trace
        return my_size;
    }    
    
    public void marshal( Marshaller marshaller )
    {
        marshaller.writeUint32( "accept_stat", accept_stat );
        marshaller.writeUint32( "error_code", error_code );
        marshaller.writeString( "stack_trace", stack_trace );
    }
    
    public void unmarshal( Unmarshaller unmarshaller ) 
    {
        accept_stat = unmarshaller.readUint32( "accept_stat" );
        error_code = unmarshaller.readUint32( "error_code" );
        stack_trace = unmarshaller.readString( "stack_trace" );    
    }
        
    

    private int accept_stat;
    private int error_code;
    private String stack_trace;    

}

