package org.kframework.backend.pdmc.pda;

import junit.framework.Assert;
import org.junit.Test;

/**
 * @author Traian
 */
public class RuleTest {
    @Test
    public void testOf1() throws Exception {
        Rule<String, String> rule = Rule.of("<a,b> => <c>" );
        Assert.assertEquals(Configuration.of("<a,b>").getHead(), rule.getHead());
        Assert.assertEquals(Configuration.of("<c>"), rule.endConfiguration());

    }

    @Test
    public void testOf2() throws Exception {
        Rule<String, String> rule = Rule.of("myrule : <a,b> => <c>" );
        Assert.assertEquals(Configuration.of("<a,b>").getHead(), rule.getHead());
        Assert.assertEquals(Configuration.of("<c>"), rule.endConfiguration());
        Assert.assertEquals(rule.getLabel(), "myrule");

    }

}
