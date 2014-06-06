// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.kil;

import org.kframework.kil.loader.Constants;
import org.kframework.kil.loader.JavaClassesFactory;
import org.kframework.kil.visitors.Visitor;
import org.kframework.utils.xml.XML;
import org.w3c.dom.Element;

/** MapItem is a map item with key {@link #key} and value the inherited {@link #value} */
public class MapItem extends CollectionItem {
    private Term key;

    public MapItem(Element element) {
        super(element);
        Element elm = XML.getChildrenElementsByTagName(element, Constants.KEY).get(0);
        Element elmBody = XML.getChildrenElements(elm).get(0);
        this.key = (Term) JavaClassesFactory.getTerm(elmBody);

        elm = XML.getChildrenElementsByTagName(element, Constants.VALUE).get(0);
        elmBody = XML.getChildrenElements(elm).get(0);
        this.value = (Term) JavaClassesFactory.getTerm(elmBody);
    }

    public MapItem(String location, String filename) {
        super(location, filename, "MapItem");
    }

    public MapItem(MapItem node) {
        super(node);
        this.key = node.key;
    }

    public MapItem() {
        super("MapItem");
    }

    public MapItem(Term key, Term value) {
        this();
        this.key = key;
        this.value = value;
    }

    public void setKey(Term key) {
        this.key = key;
    }

    public Term getKey() {
        return key;
    }

    public Term getValue() {
        return value;
    }

    public void setValue(Term t) {
        value = t;
    }

    public String toString() {
        return this.key + " |-> " + this.value;
    }

    @Override
    protected <P, R, E extends Throwable> R accept(Visitor<P, R, E> visitor, P p) throws E {
        return visitor.complete(this, visitor.visit(this, p));
    }

    @Override
    public MapItem shallowCopy() {
        return new MapItem(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MapItem))
            return false;
        MapItem m = (MapItem) o;
        return key.equals(m.key) && value.equals(m.value);
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof Bracket)
            return contains(((Bracket) o).getContent());
        if (o instanceof Cast)
            return contains(((Cast) o).getContent());
        if (!(o instanceof MapItem))
            return false;
        MapItem m = (MapItem) o;
        return key.contains(m.key) && value.contains(m.value);
    }

    @Override
    public int hashCode() {
        return key.hashCode() * 31 + value.hashCode();
    }
    
    @Override
    public Term getChild(Children type) {
        if (type == Children.KEY) {
            return key;
        }
        return super.getChild(type);
    }
    
    @Override
    public void setChild(Term child, Children type) {
        if (type == Children.KEY) {
            this.key = child;
        } else {
            super.setChild(child, type);
        }
    }

}
