package com.horizen.box

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.api.http.SidechainJsonSerializer
import com.horizen.proposition.PublicKey25519Proposition
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.core.utils.ScorexEncoder
import scorex.crypto.signatures.Curve25519

class CertifierRightBoxScalaTest
  extends JUnitSuite
{

  @Test
  def testToJson(): Unit = {
    val seed = "12345".getBytes
    val keyPair = Curve25519.createKeyPair(seed)
    val privateKey = keyPair._1
    val publicKey = keyPair._2

    val proposition = new PublicKey25519Proposition(publicKey)
    val nonce = 12345
    val value = 10
    val minimumWithdrawalEpoch = 5
    val box = new CertifierRightBox(proposition, nonce, value, minimumWithdrawalEpoch)

    val serializer = new SidechainJsonSerializer
    serializer.setDefaultConfiguration()

    val jsonStr = serializer.serialize(box)

    val node: JsonNode = serializer.getObjectMapper().readTree(jsonStr)

    assertEquals("Json must contain only 1 proposition.",
      1, node.findValues("proposition").size())
    assertEquals("Proposition json content must be the same.",
      serializer.serialize(proposition).replaceAll("\n", "").replaceAll(" ", ""),
      node.path("proposition").toString.replaceAll("\n", "").replaceAll(" ", ""))

    assertEquals("Json must contain only 1 id.",
      1, node.findValues("id").size())
    assertTrue("Id json value must be the same.",
      box.id().sameElements(ScorexEncoder.default.decode(node.path("id").asText()).get))

    assertEquals("Json must contain only 1 nonce.",
      1, node.findValues("nonce").size())
    assertEquals("Nonce json value must be the same.",
      box.nonce(), node.path("nonce").asLong())

    assertEquals("Json must contain only 1 value.",
      1, node.findValues("value").size())
    assertEquals("Value json value must be the same.",
      box.value(), node.path("value").asLong())

    assertEquals("Json must contain only 1 activeFromWithdrawalEpoch.",
      1, node.findValues("activeFromWithdrawalEpoch").size())
    assertEquals("ActiveFromWithdrawalEpoch json value must be the same.",
      box.activeFromWithdrawalEpoch(), node.path("activeFromWithdrawalEpoch").asLong())
  }
}

