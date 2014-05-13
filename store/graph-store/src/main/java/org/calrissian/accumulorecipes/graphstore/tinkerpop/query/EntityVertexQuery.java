package org.calrissian.accumulorecipes.graphstore.tinkerpop.query;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.tinkerpop.blueprints.*;
import org.calrissian.accumulorecipes.commons.domain.Auths;
import org.calrissian.accumulorecipes.entitystore.model.EntityIndex;
import org.calrissian.accumulorecipes.graphstore.GraphStore;
import org.calrissian.accumulorecipes.graphstore.model.EdgeEntity;
import org.calrissian.accumulorecipes.graphstore.tinkerpop.model.EntityVertex;
import org.calrissian.mango.collect.CloseableIterable;
import org.calrissian.mango.collect.CloseableIterables;
import org.calrissian.mango.criteria.builder.QueryBuilder;
import org.calrissian.mango.criteria.domain.Node;
import org.calrissian.mango.criteria.support.NodeUtils;
import org.calrissian.mango.domain.Entity;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.collect.Sets.newHashSet;
import static com.tinkerpop.blueprints.Query.Compare.*;
import static java.util.Collections.singleton;
import static org.calrissian.accumulorecipes.graphstore.tinkerpop.EntityGraph.*;
import static org.calrissian.mango.collect.CloseableIterables.*;

/**
 * This builder class allows a set of vertices and/or edges to be queried matching the given criteria. This class
 * will try to perform the most optimal query given the input but complex predicates like custom comparables will
 * require filtering.
 */
public class EntityVertexQuery implements VertexQuery{

  private GraphStore graphStore;
  private EntityVertex vertex;
  private Auths auths;

  private Direction direction = Direction.BOTH;
  private String[] labels = null;
  private int limit = -1;

  private QueryBuilder queryBuilder = new QueryBuilder().and();
  private QueryBuilder filters = new QueryBuilder().and();

  public EntityVertexQuery(EntityVertex vertex, GraphStore graphStore, Auths auths) {
    this.vertex = vertex;
    this.graphStore = graphStore;
    this.auths = auths;
  }

  @Override
  public VertexQuery direction(Direction direction) {
    this.direction = direction;
    return this;
  }

  @Override
  public VertexQuery labels(String... labels) {
    this.labels = labels;
    return this;
  }

  @Override
  public long count() {
    CloseableIterable<Edge> edges = edges();
    long count = Iterables.size(edges);
    edges.closeQuietly();
    return count;
  }

  @Override
  public CloseableIterable<EntityIndex> vertexIds() {
    return transform(vertices(), new EntityIndexXform());
  }

  @Override
  public VertexQuery has(String key) {
    queryBuilder = queryBuilder.has(key);
    return this;
  }

  @Override
  public VertexQuery hasNot(String key) {
    queryBuilder = queryBuilder.hasNot(key);
    return this;
  }

  @Override
  public VertexQuery has(String key, Object value) {
    queryBuilder = queryBuilder.eq(key, value);
    return this;
  }

  @Override
  public VertexQuery hasNot(String key, Object value) {
    queryBuilder = queryBuilder.notEq(key, value);
    return this;
  }

  @Override
  public VertexQuery has(String key, Predicate predicate, Object value) {
    if(predicate == EQUAL)
      return has(key, value);
    else if(predicate == NOT_EQUAL)
      return hasNot(key, value);
    else if(predicate == GREATER_THAN)
      queryBuilder = queryBuilder.greaterThan(key, value);
    else if(predicate == LESS_THAN)
      queryBuilder = queryBuilder.lessThan(key, value);
    else if(predicate == GREATER_THAN_EQUAL)
      queryBuilder = queryBuilder.greaterThanEq(key, value);
    else if(predicate == LESS_THAN_EQUAL)
      queryBuilder = queryBuilder.lessThanEq(key, value);
    else
      throw new UnsupportedOperationException("Predicate with type " + predicate + " is not supported.");

    return this;
  }

  @Override
  public <T extends Comparable<T>> VertexQuery has(String key, T value, Compare compare) {
    return has(key, compare, value);
  }

  @Override
  public <T extends Comparable<?>> VertexQuery interval(String key, T start, T stop) {
    queryBuilder = queryBuilder.range(key, start, stop);
    return this;
  }

  @Override
  public VertexQuery limit(int limit) {
    this.limit = limit;
    return this;
  }

  @Override
  public CloseableIterable<Edge> edges() {

    Node query = queryBuilder.end().build();
    Node filter = filters.end().build();

    Collection<EntityIndex> vertexIndex = singleton(new EntityIndex(vertex.getEntity()));

    org.calrissian.accumulorecipes.graphstore.model.Direction dir =
            org.calrissian.accumulorecipes.graphstore.model.Direction.valueOf(direction.toString());

    CloseableIterable<EdgeEntity> entityEdgies = labels == null ?
            graphStore.adjacentEdges(vertexIndex, query.children().size() > 0 ? query : null, dir, auths) :
            graphStore.adjacentEdges(vertexIndex, query.children().size() > 0 ? query : null, dir, newHashSet(labels), auths);

    CloseableIterable<Edge> finalEdges = transform(entityEdgies, new EdgeEntityXform(graphStore, auths));

    if(filter.children().size() > 0)
      finalEdges = filter(finalEdges, new EntityFilterPredicate(NodeUtils.criteriaFromNode(filter)));

    if(limit > -1)
      return CloseableIterables.limit(finalEdges, limit);
    return finalEdges;
  }

  @Override
  public CloseableIterable<Vertex> vertices() {

    CloseableIterable<EntityIndex> indexes = CloseableIterables.transform(edges(), new EdgeToVertexIndexXform(vertex));
    CloseableIterable<Vertex> vertices = concat(transform(partition(indexes, 50),
      new Function<List<EntityIndex>, Iterable<Vertex>>() {
        @Override
        public Iterable<Vertex> apply(List<EntityIndex> entityIndexes) {
          Collection<Entity> entityCollection = new LinkedList<Entity>();
          CloseableIterable<Entity> entities = graphStore.get(entityIndexes, null, auths);
          Iterables.addAll(entityCollection, entities);
          entities.closeQuietly();
          return Iterables.transform(entityCollection, new VertexEntityXform(graphStore, auths));
        }
      }
    ));

    return vertices;
  }
}