// Autogenerated AST node
package org.python.pydev.parser.jython.ast;
import org.python.pydev.parser.jython.SimpleNode;

public class Break extends stmtType {

    public Break() {
    }

    public Break(SimpleNode parent) {
        this();
        this.beginLine = parent.beginLine;
        this.beginColumn = parent.beginColumn;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("Break[");
        sb.append("]");
        return sb.toString();
    }

    public Object accept(VisitorIF visitor) throws Exception {
        return visitor.visitBreak(this);
    }

    public void traverse(VisitorIF visitor) throws Exception {
    }

}
