package com.autonomousapps.graph

import com.autonomousapps.internal.utils.getLogger
import java.util.*

internal class DepthFirstOrder(
  private val graph: DependencyGraph
) {

  private val logger = getLogger<DepthFirstOrder>()

  /** `marked.get(name)` = has name been marked in dfs? */
  private val marked = linkedMapOf<String, Boolean>()

  /** pre.get(name) = preorder number of name */
  private val pre = linkedMapOf<String, Int>()

  /** post.get(name) = postorder number of name */
  private val post = linkedMapOf<String, Int>()

  /** vertices in preorder */
  private val preorder: Queue<String> = LinkedList()

  /** vertices in postorder */
  private val postorder: Queue<String> = LinkedList()

  /** counter or preorder numbering */
  private var preCounter = 0

  /** counter or postorder numbering */
  private var postCounter = 0

  init {
    graph.nodes().forEach { node ->
      if (!isMarked(node)) {
        dfs(node)
      }
    }
    assert(check())
  }

  private fun isMarked(node: Node): Boolean = isMarked(node.identifier)
  private fun isMarked(identifier: String): Boolean = marked.getOrDefault(identifier, false)

  private fun dfs(node: Node) {
    marked[node.identifier] = true
    pre[node.identifier] = preCounter++
    preorder.add(node.identifier)
    for (edge in graph.adj(node)) {
      val to = edge.to
      if (!isMarked(to)) {
        dfs(to)
      }
    }
    postorder.add(node.identifier)
    post[node.identifier] = postCounter++
  }

  /**
   * Returns the preorder number of node `node`.
   *
   * @param  node the node
   * @return the preorder number of node `node`
   * @throws IllegalArgumentException unless `node` is in the graph.
   */
  private fun pre(node: String): Int = pre[node] ?: missingNode(node)

  /**
   * Returns the postorder number of node `node`.
   * @param  node the node
   * @return the postorder number of node `node`
   * @throws IllegalArgumentException unless `node` is in the graph
   */
  private fun post(node: String): Int = post[node] ?: missingNode(node)

  /**
   * Returns the nodes in postorder.
   * @return the nodes in postorder, as an iterable of nodes
   */
  private fun post(): Iterable<String> = postorder

  /**
   * Returns the nodes in preorder.
   * @return the nodes in preorder, as an iterable of nodes
   */
  private fun pre(): Iterable<String> = preorder

  /**
   * Returns the vertices in reverse postorder.
   * @return the vertices in reverse postorder, as an iterable of vertices
   */
  fun reversePost(): Iterable<String> {
    val reverse = ArrayDeque<String>()
    for (node in postorder) reverse.push(node)
    return reverse
  }

  /**
   * Check that pre() and post() are consistent with pre(node) and post(node).
   */
  private fun check(): Boolean {
    // check that post(node) is consistent with post()
    for ((r, node) in post().withIndex()) {
      if (post(node) != r) {
        logger.error("post($node) and post() inconsistent")
        return false
      }
    }

    // check that pre(node) is consistent with pre()
    for ((r, node) in pre().withIndex()) {
      if (pre(node) != r) {
        logger.error("pre($node) and pre() inconsistent")
        return false
      }
    }

    return true
  }
}
