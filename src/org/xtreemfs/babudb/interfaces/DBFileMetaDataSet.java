package org.xtreemfs.babudb.interfaces;

import java.io.StringWriter;
import java.util.Iterator;
import org.xtreemfs.*;
import org.xtreemfs.babudb.*;
import org.xtreemfs.foundation.buffer.ReusableBuffer;
import org.xtreemfs.foundation.oncrpc.utils.*;
import yidl.runtime.Marshaller;
import yidl.runtime.PrettyPrinter;
import yidl.runtime.Sequence;
import yidl.runtime.Struct;
import yidl.runtime.Unmarshaller;




public class DBFileMetaDataSet extends Sequence<DBFileMetaData>
{
    public DBFileMetaDataSet() { }

    // java.lang.Object
    public String toString()
    {
        StringWriter string_writer = new StringWriter();
        string_writer.append(this.getClass().getCanonicalName());
        string_writer.append(" ");
        PrettyPrinter pretty_printer = new PrettyPrinter( string_writer );
        pretty_printer.writeSequence( "", this );
        return string_writer.toString();
    }

    // yidl.runtime.Object
    public int getTag() { return 1021; }
    public String getTypeName() { return "org::xtreemfs::babudb::interfaces::DBFileMetaDataSet"; }

    public int getXDRSize()
    {
        int my_size = 4; // Length of the sequence

        for ( Iterator<DBFileMetaData> i = iterator(); i.hasNext(); )
        {
            DBFileMetaData value = i.next();
            my_size += value.getXDRSize(); // Size of value
        }

        return my_size;
    }

    public void marshal( Marshaller marshaller )
    {
        for ( Iterator<DBFileMetaData> i = iterator(); i.hasNext(); )
            marshaller.writeStruct( "value", i.next() );;
    }

    public void unmarshal( Unmarshaller unmarshaller )
    {
        DBFileMetaData value;
        value = new DBFileMetaData(); unmarshaller.readStruct( "value", value );
        this.add( value );
    }

}
