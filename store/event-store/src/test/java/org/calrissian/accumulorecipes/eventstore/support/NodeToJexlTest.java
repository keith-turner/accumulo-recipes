package org.calrissian.accumulorecipes.eventstore.support;

import org.calrissian.accumulorecipes.commons.iterators.support.NodeToJexl;
import org.calrissian.mango.criteria.builder.QueryBuilder;
import org.junit.Test;

public class NodeToJexlTest {

  private NodeToJexl nodeToJexl = new NodeToJexl();

  @Test
  public void testSimpleEquals() {

    System.out.println(nodeToJexl.transform(new QueryBuilder().and().greaterThan("hello", "goodbye").eq("key1", true).end().build()));

  }
}