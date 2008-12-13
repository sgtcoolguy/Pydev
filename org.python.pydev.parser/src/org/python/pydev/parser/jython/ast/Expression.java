// Autogenerated AST node
package org.python.pydev.parser.jython.ast;
import org.python.pydev.parser.jython.SimpleNode;

public class Expression extends modType {
    public exprType body;

    public Expression(exprType body) {
        this.body = body;
    }

    public Expression(exprType body, SimpleNode parent) {
        this(body);
        this.beginLine = parent.beginLine;
        this.beginColumn = parent.beginColumn;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("Expression[");
        sb.append("body=");
        sb.append(dumpThis(this.body));
        sb.append("]");
        return sb.toString();
    }

    public Object accept(VisitorIF visitor) throws Exception {
        return visitor.visitExpression(this);
    }

    public void traverse(VisitorIF visitor) throws Exception {
        if (body != null)
            body.accept(visitor);
    }

}
