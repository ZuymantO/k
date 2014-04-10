package org.kframework.kil;

import org.kframework.kil.loader.Constants;
import org.kframework.kil.visitors.Transformer;
import org.kframework.kil.visitors.Visitor;
import org.kframework.kil.visitors.exceptions.TransformerException;
import org.w3c.dom.Element;

/**
 * Represents either an explicit attribute on a {@link Rule} or {@link Production},
 * or node metadata like location.
 * The inherited member attributes is used for location information
 * if this represents an explicitly written attribute. 
 */
public class Attribute extends ASTNode {

    public static final String BUILTIN_KEY = "builtin";
    public static final String FUNCTION_KEY = "function";
    public static final String PREDICATE_KEY = "predicate";
    public static final String HOOK_KEY = "hook";
    public static final String AUX_HOOK_KEY = "aux";
    public static final String MACRO_KEY = "macro";
    public static final String SIMPLIFICATION_KEY = "simplification";


    public static final Attribute BRACKET = new Attribute("bracket", "");
    public static final Attribute FUNCTION = new Attribute(FUNCTION_KEY, "");
    public static final Attribute PREDICATE = new Attribute(PREDICATE_KEY, "");
    public static final Attribute ANYWHERE = new Attribute("anywhere", "");
    public static final Attribute EQUALITY = new Attribute("equality", "");
    public static final String CELL_KEY = "cell";

    private String key;
    private String value;

    public Attribute(String key, String value) {
        super();
        this.key = key;
        this.value = value;
    }

    public Attribute(Element elm) {
        super(elm);

        key = elm.getAttribute(Constants.KEY_key_ATTR);
        value = elm.getAttribute(Constants.VALUE_value_ATTR);
    }

    public Attribute(Attribute attribute) {
        super(attribute);
        key = attribute.key;
        value = attribute.value;
    }

    @Override
    public String toString() {
        return " " + this.getKey() + "(" + this.getValue() + ")";
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);

    }

    @Override
    public ASTNode accept(Transformer transformer) throws TransformerException {
        return transformer.transform(this);
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Attribute shallowCopy() {
        return new Attribute(this);
    }
}
