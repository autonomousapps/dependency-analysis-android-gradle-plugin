package com.autonomousapps.graph

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NodeTest {

  @Test
  fun `nodes with different subtypes can be equal`() {
    val producerNode = ProducerNode("n1")
    val consumerNode = ConsumerNode("n1")
    val bareNode = BareNode("n1")

    assertThat(producerNode).isEqualTo(consumerNode)
    assertThat(producerNode).isEqualTo(bareNode)
    assertThat(consumerNode).isEqualTo(bareNode)
  }
}