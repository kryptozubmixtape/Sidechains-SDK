package com.horizen.validation

import java.io.{BufferedReader, FileReader}
import java.lang.{Byte => JByte}
import java.util
import java.util.{ArrayList => JArrayList}

import com.horizen.box.ForgerBox
import com.horizen.fixtures.BoxFixture
import com.horizen.utils.{BytesUtils, ForgerBoxMerklePathInfo, ForgerBoxMerklePathInfoSerializer, MerklePath, Pair}
import org.junit.Assert.{assertEquals, assertNotEquals, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class ForgerBoxMerklePathInfoTest extends JUnitSuite with BoxFixture {
  val forgerBox: ForgerBox = getForgerBox(
    getPrivateKey25519("123".getBytes()).publicImage(),
    1000L,
    100L,
    getPrivateKey25519("456".getBytes()).publicImage(),
    getVRFPublicKey(222L)
  )
  val emptyMerklePath: MerklePath = new MerklePath(new JArrayList())

  val nonEmptyMerklePath: MerklePath = new MerklePath(util.Arrays.asList(
    new Pair[JByte, Array[Byte]](0.toByte, BytesUtils.fromHexString("29d000eee85f08b6482026be2d92d081d6f9418346e6b2e9fe2e9b985f24ed1e")),
    new Pair[JByte, Array[Byte]](1.toByte, BytesUtils.fromHexString("61bfbdf7038dc7f21e2bcf193faef8e6caa8222af016a6ed86b9e9d860f046df"))
  ))

  @Test
  def comparison(): Unit = {
    assertNotEquals("Box merkle path info expected to be different.", emptyMerklePath, nonEmptyMerklePath)
  }

  @Test
  def serialization(): Unit = {
    // Test 1: empty merkle path (single element in merkle tree)
    val boxWithEmptyPath = ForgerBoxMerklePathInfo(forgerBox, emptyMerklePath)
    var boxBytes = boxWithEmptyPath.bytes
    var deserializedBox = ForgerBoxMerklePathInfoSerializer.parseBytes(boxBytes)
    assertEquals("Deserialized box merkle path info hashCode expected to be equal to the original one.", boxWithEmptyPath.hashCode(), deserializedBox.hashCode())
    assertEquals("Deserialized box merkle path info expected to be equal to the original one.", boxWithEmptyPath, deserializedBox)


    // Test 2: non empty merkle path
    val boxWithNonEmptyPath = ForgerBoxMerklePathInfo(forgerBox, nonEmptyMerklePath)
    boxBytes = boxWithNonEmptyPath.bytes
    deserializedBox = ForgerBoxMerklePathInfoSerializer.parseBytes(boxBytes)
    assertEquals("Deserialized box merkle path info hashCode expected to be equal to the original one.", boxWithNonEmptyPath.hashCode(), deserializedBox.hashCode())
    assertEquals("Deserialized box merkle path info expected to be equal to the original one.", boxWithNonEmptyPath, deserializedBox)

    // Uncomment and run if you want to update regression data.
    /* val out = new BufferedWriter(new FileWriter("src/test/resources/boxmerklepathinfo_hex"))
     out.write(BytesUtils.toHexString(boxBytes))
     out.close()*/


    // Test 3: try to deserialize broken bytes.
    assertTrue("ForgerBoxMerklePathInfo expected to be not parsed due to broken data.", ForgerBoxMerklePathInfoSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serializationRegression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("boxmerklepathinfo_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        fail(e.toString)
    }

    val boxMerklePathInfoTry = ForgerBoxMerklePathInfoSerializer.parseBytesTry(bytes)
    assertTrue("ForgerBoxMerklePathInfo expected to by parsed.", boxMerklePathInfoTry.isSuccess)

    val boxWithNonEmptyPath = ForgerBoxMerklePathInfo(forgerBox, nonEmptyMerklePath)
    assertEquals("Parsed info is different to original.", boxWithNonEmptyPath, boxMerklePathInfoTry.get)
  }
}
